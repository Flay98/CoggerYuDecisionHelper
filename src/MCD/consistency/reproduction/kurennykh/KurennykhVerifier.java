package MCD.consistency.reproduction.kurennykh;

import MCD.consistency.*;
import MCD.consistency.decision.PerronWeights;
import MCD.consistency.reproduction.PublishedReferences;
import MCD.consistency.reproduction.ReproductionFiles;
import com.google.gson.JsonElement;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import static MCD.consistency.reproduction.ReproductionFiles.require;

/** Streaming audit, no corrections executed: regenerate inputs and replay saved accepted changes. */
public final class KurennykhVerifier {
    private KurennykhVerifier() { }
    public static void main(String[] args) throws IOException {
        if(args.length>2)throw new IllegalArgumentException("Usage: KurennykhVerifier [REPORT|SMOKE] [root]");
        String profile=args.length>0?args[0]:"REPORT";Path root=Path.of(args.length>1?args[1]:"experiments/kurennykh2018");
        System.out.println(profile+": verified "+verify(root,profile)+" input/output matrices, histories, priorities and statistics");
    }
    public static int verify(Path root,String profile) throws IOException {
        Path summary=root.resolve("summary/"+profile);
        var manifest=KurennykhResultWriter.JSON.fromJson(ReproductionFiles.readJson(summary.resolve("manifest.json")),KurennykhReproduction.Manifest.class);
        require(manifest.status().equals("COMPLETE"),"Incomplete experiment cannot be verified as REPORT");
        var p=KurennykhResultWriter.JSON.fromJson(ReproductionFiles.readJson(summary.resolve("plan.json")),KurennykhExperimentPlan.class);
        require(p.profile().equals(profile)&&manifest.profile().equals(profile),"Saved profile mismatch");
        require(ReproductionFiles.readJson(summary.resolve("plan.initial.json")).equals(ReproductionFiles.readJson(summary.resolve("plan.json"))),"Predeclared plan changed");
        String hash=KurennykhResultWriter.hash(KurennykhResultWriter.JSON.toJson(p).getBytes(StandardCharsets.UTF_8));
        require(hash.equals(manifest.planSha256())&&manifest.expectedMatrices()==p.totalMatrices()&&manifest.completedMatrices()==p.totalMatrices(),"Plan or completion count mismatch");
        require(ReproductionFiles.sha256(root.resolve("PROTOCOL.md")).equals(manifest.protocolSha256()),"Predeclared protocol changed");
        PublishedReferences.verifyIntegrity(Path.of("experiments/reproduction"));
        require(ReproductionFiles.sha256(KurennykhComparison.REFERENCE).equals(manifest.referenceSha256()),"Reference hash mismatch");
        var environment=ReproductionFiles.readJson(summary.resolve("environment.json"));require(environment.get("javaVersion").getAsString().startsWith("17"),"Recorded run did not use Java 17");
        Path inputs=root.resolve(manifest.inputArchive()),outputs=root.resolve(manifest.outputArchive());
        require(ReproductionFiles.sha256(inputs).equals(manifest.inputArchiveSha256())&&ReproductionFiles.sha256(outputs).equals(manifest.outputArchiveSha256()),"Archive SHA-256 mismatch");
        var prefix=prefix(root,profile,p);var rows=new ArrayList<KurennykhRunResult>();var inputRows=new ArrayList<KurennykhReproduction.InputRow>();
        try(var in=KurennykhResultWriter.readArchive(inputs);var out=KurennykhResultWriter.readArchive(outputs)) {
            for(int n:p.sizes())for(int sample=0;sample<p.matricesPerSize();sample++) {
                String il=in.readLine(),ol=out.readLine();require(il!=null&&ol!=null,"Truncated archive");
                var originalRecord=KurennykhResultWriter.JSON.fromJson(il,KurennykhResultWriter.Input.class);
                var saved=KurennykhResultWriter.JSON.fromJson(ol,KurennykhResultWriter.Output.class);
                String id=KurennykhExperimentPlan.id(n,sample);long seed=p.seed(n,sample);
                require(originalRecord.schemaVersion()==1&&saved.schemaVersion()==1&&id.equals(originalRecord.id())
                        &&originalRecord.size()==n&&originalRecord.sample()==sample&&originalRecord.generationSeed()==seed,"Input archive sequence mismatch");
                var a=new PairwiseComparisonMatrix(originalRecord.matrix());var b=new PairwiseComparisonMatrix(saved.matrix());
                KurennykhReproduction.validateMatrix(a);KurennykhReproduction.validateMatrix(b);
                String inputHash=KurennykhResultWriter.matrixHash(a);
                require(originalRecord.sha256().equals(inputHash)&&inputHash.equals(saved.run().inputSha256()),"Matrix hash mismatch");
                require(KurennykhResultWriter.matrixCsv(a).equals(KurennykhResultWriter.matrixCsv(KurennykhReproduction.generate(n,seed))),"Input not reproduced by the fixed generator");
                if(prefix.containsKey(id))require(prefix.get(id).equals(il),"SMOKE and REPORT prefixes differ");
                validatePriorities(a,saved.initialPriorities());validatePriorities(b,saved.finalPriorities());
                require(KurennykhMetrics.rank(saved.initialPriorities().weights()).equals(saved.initialRanking())
                        &&KurennykhMetrics.rank(saved.finalPriorities().weights()).equals(saved.finalRanking()),"Saved ranks disagree with priorities");
                double initialCi=ConsistencyMetrics.saatyIndex(a),finalCi=ConsistencyMetrics.saatyIndex(b);
                double initialK=ConsistencyMetrics.koczkodajIndex(a).value(),finalK=ConsistencyMetrics.koczkodajIndex(b).value();
                replay(a,b,saved.history(),initialCi,finalCi,p);
                require(saved.run().elapsedMillis()>=0&&Double.isFinite(saved.run().elapsedMillis()),"Invalid elapsed time");
                var core=new CosineCorrectionResult(a,b,initialCi,finalCi,initialK,finalK,p.options(),CosineCorrectionResult.Status.valueOf(saved.run().status()),saved.run().detail(),saved.history());
                var computed=KurennykhReproduction.calculate(p,hash,id,n,sample,seed,inputHash,new KurennykhReproduction.Timed(core,saved.run().elapsedMillis()),saved.initialPriorities(),saved.finalPriorities());
                require(equivalent(KurennykhResultWriter.JSON.toJsonTree(computed),KurennykhResultWriter.JSON.toJsonTree(saved.run())),"Recalculated run metrics disagree: "+id);
                rows.add(saved.run());inputRows.add(new KurennykhReproduction.InputRow(id,n,sample,seed,inputHash,rows.size(),saved.run().initialCi(),saved.run().initialK()));
            }
            require(in.readLine()==null&&out.readLine()==null,"Unexpected extra archive records");
        }
        require(Files.readString(summary.resolve("runs.csv")).equals(KurennykhResultWriter.csvText(rows)),"runs.csv disagrees with archive records");
        require(Files.readString(summary.resolve("inputs.csv")).equals(KurennykhResultWriter.csvText(inputRows)),"inputs.csv disagrees with archive records");
        var aggregates=KurennykhStatistics.aggregate(p,rows);var groups=KurennykhStatistics.summaries(p,rows);
        var comparison=KurennykhComparison.compare(p,aggregates,groups,KurennykhComparison.REFERENCE);
        for(var entry:Map.of("aggregated",aggregates,"groups",groups,"comparison_with_article",comparison).entrySet()) {
            require(KurennykhResultWriter.JSON.toJsonTree(entry.getValue()).equals(ReproductionFiles.readJson(summary.resolve(entry.getKey()+".json")).get("rows")),"Saved aggregates disagree: "+entry.getKey());
            require(Files.readString(summary.resolve(entry.getKey()+".csv")).equals(KurennykhResultWriter.csvText(entry.getValue())),"CSV/JSON disagree: "+entry.getKey());
        }
        int warmups=ReproductionFiles.readJson(summary.resolve("warmup.json")).getAsJsonArray("runs").size();
        require(warmups==p.sizes().size()*p.warmupPerSize()&&warmups==manifest.warmups(),"Warmup count mismatch");
        return rows.size();
    }

    private static Map<String,String> prefix(Path root,String profile,KurennykhExperimentPlan plan) throws IOException {
        Path source=root.resolve("inputs/SMOKE/matrices.jsonl.gz");var result=new HashMap<String,String>();
        if(!profile.equals("REPORT")||!Files.exists(source))return result;
        var smoke=KurennykhResultWriter.JSON.fromJson(ReproductionFiles.readJson(root.resolve("summary/SMOKE/plan.json")),KurennykhExperimentPlan.class);
        require(smoke.masterSeed()==plan.masterSeed()&&smoke.generator().equals(plan.generator())
                &&plan.sizes().containsAll(smoke.sizes())&&smoke.matricesPerSize()<=plan.matricesPerSize(),"SMOKE and REPORT generators/prefix sizes differ");
        var manifest=KurennykhResultWriter.JSON.fromJson(ReproductionFiles.readJson(root.resolve("summary/SMOKE/manifest.json")),KurennykhReproduction.Manifest.class);
        require(manifest.status().equals("COMPLETE")&&ReproductionFiles.sha256(source).equals(manifest.inputArchiveSha256()),"SMOKE prefix archive incomplete or modified");
        try(var in=KurennykhResultWriter.readArchive(source)){String line;while((line=in.readLine())!=null){var row=KurennykhResultWriter.JSON.fromJson(line,KurennykhResultWriter.Input.class);
            require(result.put(row.id(),line)==null,"Duplicate SMOKE input ID");}}
        require(result.size()==smoke.totalMatrices(),"Missing SMOKE prefix records");
        return result;
    }
    private static void validatePriorities(PairwiseComparisonMatrix matrix,PerronWeights.Result saved) {
        var calculated=new PerronWeights().calculate(matrix);
        require(saved.tolerance()==PerronWeights.DEFAULT_TOLERANCE&&saved.maxIterations()==PerronWeights.DEFAULT_MAX_ITERATIONS
                &&saved.iterations()>0&&saved.iterations()<=saved.maxIterations()&&saved.maxRelativeResidual()<=4*saved.tolerance(),"Invalid saved eigenvector convergence information");
        require(saved.weights().size()==matrix.size()&&Math.abs(saved.weights().stream().mapToDouble(Double::doubleValue).sum()-1)<1e-12,"Unnormalized priorities");
        for(int i=0;i<matrix.size();i++)require(saved.weights().get(i)>0&&close(saved.weights().get(i),calculated.weights().get(i)),"Saved priority mismatch");
        require(close(saved.eigenvalue(),calculated.eigenvalue()),"Saved eigenvalue mismatch");
    }
    private static void replay(PairwiseComparisonMatrix a,PairwiseComparisonMatrix b,List<CosineCorrectionResult.Change> history,double initial,double last,KurennykhExperimentPlan p) {
        require(history.size()<=p.maxCorrections(),"Too many accepted changes");
        double[][] current=a.toArray();double ci=initial;
        for(int index=0;index<history.size();index++) {
            var c=history.get(index);require(c.iteration()==index+1&&c.i()>=0&&c.k()<a.size()&&c.i()<c.k()&&c.via()>=0&&c.via()<a.size()&&c.via()!=c.i()&&c.via()!=c.k(),"Invalid history indices");
            require(c.oldValue()==current[c.i()][c.k()]&&c.delta()==c.newValue()-c.oldValue()&&c.newValue()==current[c.i()][c.via()]*current[c.via()][c.k()],"History does not follow the saved multiplicative recommendation");
            require(close(c.ciBefore(),ci)&&c.ciBefore()-c.ciAfter()>p.options().minimumCiDecrease(),"History does not strictly improve CI");
            current[c.i()][c.k()]=c.newValue();current[c.k()][c.i()]=1/c.newValue();
            KurennykhReproduction.validateMatrix(new PairwiseComparisonMatrix(current));ci=c.ciAfter();
        }
        require(KurennykhResultWriter.matrixCsv(new PairwiseComparisonMatrix(current)).equals(KurennykhResultWriter.matrixCsv(b))&&close(ci,last),"Replayed history differs from the final matrix");
    }
    private static boolean close(double a,double b){return Double.isFinite(a)&&Double.isFinite(b)&&Math.abs(a-b)<=1e-11*Math.max(1,Math.max(Math.abs(a),Math.abs(b)));}
    private static boolean equivalent(JsonElement a,JsonElement b) {
        if(a==null||b==null||a.isJsonNull()||b.isJsonNull())return Objects.equals(a,b);
        if(a.isJsonObject()&&b.isJsonObject()){if(!a.getAsJsonObject().keySet().equals(b.getAsJsonObject().keySet()))return false;for(String key:a.getAsJsonObject().keySet())if(!equivalent(a.getAsJsonObject().get(key),b.getAsJsonObject().get(key)))return false;return true;}
        if(a.isJsonPrimitive()&&b.isJsonPrimitive()&&a.getAsJsonPrimitive().isNumber()&&b.getAsJsonPrimitive().isNumber()){
            String x=a.getAsString(),y=b.getAsString();if(!x.contains(".")&&!x.contains("E")&&!y.contains(".")&&!y.contains("E"))return a.getAsBigDecimal().compareTo(b.getAsBigDecimal())==0;
            return close(a.getAsDouble(),b.getAsDouble());}
        return a.equals(b);
    }
}
