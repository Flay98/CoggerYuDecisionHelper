package MCD.consistency.reproduction.kurennykh;

import MCD.consistency.*;
import MCD.consistency.decision.PerronWeights;
import MCD.consistency.experiments.BenchmarkEnvironment;
import MCD.consistency.experiments.MatrixGenerator;
import MCD.consistency.reproduction.PublishedReferences;
import MCD.consistency.reproduction.ReproductionFiles;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import static MCD.consistency.reproduction.ReproductionFiles.require;

/** Bounded streaming experiment. All optimization remains in the existing cosine corrector. */
public final class KurennykhReproduction {
    private KurennykhReproduction() { }
    static final MatrixGenerator.Parameters GENERATOR_PARAMETERS=new MatrixGenerator.Parameters(.4,3,Math.log(3),64);
    public record InputRow(String id,int size,int sample,long generationSeed,String sha256,int archiveLine,double initialCi,double initialK) { }
    public record Warmup(int size,int round,long generationSeed,double elapsedMillis,int iterations,String status) { }
    public record Timed(CosineCorrectionResult result,double elapsedMillis) { }
    public record Completion(int matrices,int warmups,double wallSeconds,List<KurennykhRunResult> runs) { }
    public record Manifest(int schemaVersion,String profile,String status,String planSha256,int expectedMatrices,int completedMatrices,
            int warmups,double wallSeconds,String inputArchive,String outputArchive,String inputArchiveSha256,String outputArchiveSha256,
            String referenceSha256,String protocolSha256,String failureId,String failureReason) { }

    public static void main(String[] args) throws IOException {
        if(args.length<1||args.length>2)throw new IllegalArgumentException("Usage: KurennykhReproduction SMOKE|REPORT [experiment-root]");
        Path root=Path.of(args.length==2?args[1]:"experiments/kurennykh2018");
        var p=KurennykhExperimentPlan.load(root,args[0]);var result=run(root,p);
        System.out.printf(Locale.ROOT,"%s COMPLETE: %d matrices, %d warmups, %.3f seconds%n",p.profile(),result.matrices(),result.warmups(),result.wallSeconds());
    }

    public static Completion run(Path directory,KurennykhExperimentPlan p) throws IOException {
        Path root=directory.toRealPath(),summary=root.resolve("summary/"+p.profile());
        for(String name:List.of("config","inputs","outputs","summary","figures"))rejectLinks(root.resolve(name));
        PublishedReferences.verifyIntegrity(Path.of("experiments/reproduction"));
        KurennykhComparison.published(KurennykhComparison.REFERENCE);
        String hash=KurennykhResultWriter.hash(KurennykhResultWriter.JSON.toJson(p).getBytes(StandardCharsets.UTF_8));
        Path initialPlan=summary.resolve("plan.initial.json");
        if(Files.exists(initialPlan))require(ReproductionFiles.readJson(initialPlan).equals(KurennykhResultWriter.JSON.toJsonTree(p)),"Plan changed: use a fresh experiment root");
        ReproductionFiles.writeJson(initialPlan,p);ReproductionFiles.writeJson(summary.resolve("plan.json"),p);
        ReproductionFiles.writeJson(summary.resolve("environment.json"),BenchmarkEnvironment.capture());
        String protocolHash=ReproductionFiles.sha256(root.resolve("PROTOCOL.md"));
        Path input=root.resolve("inputs/"+p.profile()+"/matrices.jsonl.gz"),output=root.resolve("outputs/"+p.profile()+"/results.jsonl.gz");
        var rows=new ArrayList<KurennykhRunResult>();var inputRows=new ArrayList<InputRow>();var warmups=new ArrayList<Warmup>();
        long started=System.nanoTime();String active="WARMUP";
        ExecutorService worker=Executors.newSingleThreadExecutor(task->{var t=new Thread(task,"kurennykh-corrector");t.setDaemon(true);return t;});
        writeManifest(root,p,hash,"RUNNING",rows.size(),warmups.size(),seconds(started),input,output,protocolHash,null,null);
        try(var inputWriter=KurennykhResultWriter.archive(input);var outputWriter=KurennykhResultWriter.archive(output)) {
            for(int n:p.sizes()) {
                for(int round=0;round<p.warmupPerSize();round++) {
                    active="WARMUP_n"+n+"_"+round;
                    long seed=MatrixGenerator.seed(p.masterSeed()^0x5741524dL,MatrixGenerator.Type.DISCRETE_EXPERT,n,60000+round);
                    var timed=correct(worker,p,generate(n,seed));warmups.add(new Warmup(n,round,seed,timed.elapsedMillis(),timed.result().iterations(),timed.result().status().name()));
                }
                for(int sample=0;sample<p.matricesPerSize();sample++) {
                    active=KurennykhExperimentPlan.id(n,sample);require(seconds(started)<=p.maxWallSeconds(),"Profile wall-time budget exceeded");
                    long seed=p.seed(n,sample);var original=generate(n,seed);String matrixHash=KurennykhResultWriter.matrixHash(original);
                    var before=new PerronWeights().calculate(original);
                    double initialCi=ConsistencyMetrics.saatyIndex(original),initialK=ConsistencyMetrics.koczkodajIndex(original).value();
                    KurennykhResultWriter.line(inputWriter,new KurennykhResultWriter.Input(1,active,n,sample,seed,matrixHash,original.toArray()));
                    var timed=correct(worker,p,original);var corrected=timed.result().corrected();
                    var after=new PerronWeights().calculate(corrected);var rankBefore=KurennykhMetrics.rank(before.weights());var rankAfter=KurennykhMetrics.rank(after.weights());
                    var row=calculate(p,hash,active,n,sample,seed,matrixHash,timed, before,after);
                    require(row.initialCi()==initialCi&&row.initialK()==initialK,"Initial metrics disagree");
                    require(timed.result().original()==original,"Corrector input changed");
                    validateMatrix(corrected);require(row.finalCi()==ConsistencyMetrics.saatyIndex(corrected)
                            &&row.finalK()==ConsistencyMetrics.koczkodajIndex(corrected).value(),"Final metrics disagree");
                    var saved=new KurennykhResultWriter.Output(1,row,corrected.toArray(),before,after,rankBefore,rankAfter,timed.result().history());
                    KurennykhResultWriter.line(outputWriter,saved);rows.add(row);
                    inputRows.add(new InputRow(active,n,sample,seed,matrixHash,rows.size(),initialCi,initialK));
                    if(rows.size()%250==0||rows.size()==p.totalMatrices()) {
                        inputWriter.flush();outputWriter.flush();require(Files.size(input)+Files.size(output)<=p.maxArchiveBytes(),"Archive size budget exceeded");
                        KurennykhResultWriter.csv(summary.resolve("runs.partial.csv"),rows);
                        writeManifest(root,p,hash,"RUNNING",rows.size(),warmups.size(),seconds(started),input,output,protocolHash,null,null);
                        System.out.printf(Locale.ROOT,"%s %d/%d matrices; %.1f seconds%n",p.profile(),rows.size(),p.totalMatrices(),seconds(started));
                    }
                }
            }
        } catch(IOException|RuntimeException error) {
            if(!rows.isEmpty())KurennykhResultWriter.csv(summary.resolve("runs.partial.csv"),rows);
            writeManifest(root,p,hash,"FAILED",rows.size(),warmups.size(),seconds(started),input,output,protocolHash,active,error.toString());
            throw new IOException("Study FAILED at "+active+"; partial data is not a completed experiment",error);
        } finally {worker.shutdownNow();}
        try {
            require(rows.size()==p.totalMatrices(),"Missing matrix results");
            require(Files.size(input)+Files.size(output)<=p.maxArchiveBytes(),"Final archive size budget exceeded");
            writeTables(summary,p,rows,inputRows,warmups);
            PublishedReferences.verifyIntegrity(Path.of("experiments/reproduction"));
            writeManifest(root,p,hash,"COMPLETE",rows.size(),warmups.size(),seconds(started),input,output,protocolHash,null,null);
            return new Completion(rows.size(),warmups.size(),seconds(started),List.copyOf(rows));
        } catch(IOException|RuntimeException error) {
            writeManifest(root,p,hash,"FAILED",rows.size(),warmups.size(),seconds(started),input,output,protocolHash,"FINALIZATION",error.toString());throw error;
        }
    }

    static PairwiseComparisonMatrix generate(int n,long seed) {
        return new MatrixGenerator().generate(MatrixGenerator.Type.DISCRETE_EXPERT,n,seed,GENERATOR_PARAMETERS).matrix();
    }
    static KurennykhRunResult calculate(KurennykhExperimentPlan p,String hash,String id,int n,int sample,long seed,String inputHash,
                                        Timed timed,PerronWeights.Result before,PerronWeights.Result after) {
        var c=timed.result();var d=KurennykhMetrics.deviations(c.original(),c.corrected());
        var rank=KurennykhMetrics.rankChange(KurennykhMetrics.rank(before.weights()),KurennykhMetrics.rank(after.weights()));
        long touched=c.history().stream().map(x->Math.min(x.i(),x.k())+":"+Math.max(x.i(),x.k())).distinct().count();
        boolean acceptable=c.initialCi()<=p.targetCi(),target=c.finalCi()<=p.targetCi();
        require(target==(c.status()==CosineCorrectionResult.Status.TARGET_REACHED||c.status()==CosineCorrectionResult.Status.ALREADY_ACCEPTABLE),"Status and target disagree");
        require(d.changedComparisons()==c.changedComparisons(),"Changed comparison count disagrees");
        return new KurennykhRunResult(p.profile(),hash,id,n,sample,seed,inputHash,c.status().name(),c.detail(),timed.elapsedMillis(),
                c.initialCi(),c.finalCi(),c.initialK(),c.finalK(),c.initialCi()-c.finalCi(),c.iterations(),c.history().size(),(int)touched,
                d.changedComparisons(),d.eFull(),d.eUpper(),d.vUpper(),d.vChanged(),d.vOffdiagonal(),d.logDeviation(),acceptable,target,!acceptable,!acceptable&&target,
                rank.originalLeader(),rank.finalLeader(),rank.initialPosition(),rank.finalPosition(),rank.deltaPosition(),rank.leaderChanged(),rank.originalBestRankChanged(),
                rank.initialTiedLeaders(),rank.finalTiedLeaders(),p.targetCi(),p.maxCorrections(),p.minimumComparison(),p.maximumComparison());
    }
    private static Timed correct(ExecutorService worker,KurennykhExperimentPlan p,PairwiseComparisonMatrix original) throws IOException {
        var future=worker.submit(()-> {long start=System.nanoTime();var result=new CosineConsistencyCorrector(p.options()).correct(original);return new Timed(result,(System.nanoTime()-start)/1e6);});
        try{return future.get(p.runTimeoutSeconds(),TimeUnit.SECONDS);}
        catch(InterruptedException error){Thread.currentThread().interrupt();throw new IOException("Interrupted correction",error);}
        catch(ExecutionException|TimeoutException error){future.cancel(true);throw new IOException("Correction exception/timeout: "+error,error);}
    }
    static void validateMatrix(PairwiseComparisonMatrix matrix) {
        new PairwiseComparisonMatrix(matrix.toArray());
        for(double[] row:matrix.toArray())for(double value:row)require(MatrixGenerator.inRange(value),"Matrix outside common comparison range");
    }
    static void writeTables(Path summary,KurennykhExperimentPlan p,List<KurennykhRunResult> rows,List<InputRow> inputs,List<Warmup> warmups) throws IOException {
        KurennykhResultWriter.csv(summary.resolve("runs.csv"),rows);KurennykhResultWriter.csv(summary.resolve("inputs.csv"),inputs);
        ReproductionFiles.writeJson(summary.resolve("warmup.json"),Map.of("schemaVersion",1,"runs",warmups));
        var aggregates=KurennykhStatistics.aggregate(p,rows);var groups=KurennykhStatistics.summaries(p,rows);
        var comparison=KurennykhComparison.compare(p,aggregates,groups,KurennykhComparison.REFERENCE);
        for(var entry:Map.of("aggregated",aggregates,"groups",groups,"comparison_with_article",comparison).entrySet()) {
            KurennykhResultWriter.csv(summary.resolve(entry.getKey()+".csv"),entry.getValue());
            ReproductionFiles.writeJson(summary.resolve(entry.getKey()+".json"),Map.of("schemaVersion",1,"rows",entry.getValue()));
        }
    }
    private static void writeManifest(Path root,KurennykhExperimentPlan p,String hash,String state,int completed,int warmups,double wall,
            Path input,Path output,String protocol,String failureId,String reason) throws IOException {
        boolean complete=state.equals("COMPLETE");
        var manifest=new Manifest(1,p.profile(),state,hash,p.totalMatrices(),completed,warmups,wall,
                root.relativize(input).toString().replace('\\','/'),root.relativize(output).toString().replace('\\','/'),
                complete?ReproductionFiles.sha256(input):null,complete?ReproductionFiles.sha256(output):null,
                ReproductionFiles.sha256(KurennykhComparison.REFERENCE),protocol,failureId,reason);
        ReproductionFiles.writeJson(root.resolve("summary/"+p.profile()+"/manifest.json"),manifest);
    }
    private static void rejectLinks(Path path) throws IOException {
        if(Files.exists(path,java.nio.file.LinkOption.NOFOLLOW_LINKS))try(var files=Files.walk(path)) {
            for(Path node:files.toList())require(!Files.isSymbolicLink(node),"Study paths must not contain symbolic links: "+node);
        }
    }
    private static double seconds(long start){return(System.nanoTime()-start)/1e9;}
}
