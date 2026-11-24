package MCD.consistency.reproduction.kurennykh;

import MCD.consistency.reproduction.PublishedReferences;
import MCD.consistency.reproduction.ReproductionFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** The fixed external reference is read-only, independently hash pinned and visually checked against the PDF. */
public final class KurennykhComparison {
    private KurennykhComparison() { }
    public static final Path REFERENCE=Path.of("experiments/reproduction/reference/kurennykh_2018_table1.csv");
    public record Published(int size,double elapsedMillis,double eFull,double undefinedVariation,double iterations,double deltaCi,int rankChanges) { }
    public record Comparison(String profile,int size,String metric,String authorDefinition,String ourDefinition,
            double authorValue,double ourValue,Double differenceOurMinusAuthor,Double absoluteDifference,Double relativeDifference,
            int ourMatrices,Integer authorMatricesPerSize,String comparability,String limitations) { }

    /** Rebuild comparison exports only, without regenerating matrices or running the corrector. */
    public static void main(String[] args) throws IOException {
        if(args.length>2)throw new IllegalArgumentException("Usage: KurennykhComparison [REPORT|SMOKE] [root]");
        String profile=args.length>0?args[0]:"REPORT";Path root=Path.of(args.length>1?args[1]:"experiments/kurennykh2018");
        Path summary=root.resolve("summary/"+profile);
        ReproductionFiles.require(ReproductionFiles.readJson(summary.resolve("manifest.json")).get("status").getAsString().equals("COMPLETE"),"Comparison requires completed data");
        var p=KurennykhResultWriter.JSON.fromJson(ReproductionFiles.readJson(summary.resolve("plan.json")),KurennykhExperimentPlan.class);
        var aggregates=new ArrayList<KurennykhStatistics.Aggregate>();var groups=new ArrayList<KurennykhStatistics.GroupSummary>();
        for(var row:ReproductionFiles.readJson(summary.resolve("aggregated.json")).getAsJsonArray("rows"))aggregates.add(KurennykhResultWriter.JSON.fromJson(row,KurennykhStatistics.Aggregate.class));
        for(var row:ReproductionFiles.readJson(summary.resolve("groups.json")).getAsJsonArray("rows"))groups.add(KurennykhResultWriter.JSON.fromJson(row,KurennykhStatistics.GroupSummary.class));
        var rows=compare(p,aggregates,groups,REFERENCE);
        KurennykhResultWriter.csv(summary.resolve("comparison_with_article.csv"),rows);
        ReproductionFiles.writeJson(summary.resolve("comparison_with_article.json"),java.util.Map.of("schemaVersion",1,"rows",rows));
        System.out.println(profile+": rebuilt "+rows.size()+" comparison rows from saved aggregates; no optimization");
    }

    public static List<Published> published(Path file) throws IOException {
        String expected=PublishedReferences.pinnedHashes().get("reference/kurennykh_2018_table1.csv");
        ReproductionFiles.require(ReproductionFiles.sha256(file).equals(expected),"Published Table 1 changed; no result may replace the reference");
        var rows=Files.readAllLines(file);var result=new ArrayList<Published>();
        for(String line:rows.subList(1,rows.size())) {String[] v=line.split(",",-1);
            result.add(new Published(Integer.parseInt(v[0]),Double.parseDouble(v[1]),Double.parseDouble(v[2]),Double.parseDouble(v[3]),Double.parseDouble(v[4]),Double.parseDouble(v[5]),Integer.parseInt(v[6])));}
        return List.copyOf(result);
    }

    public static List<Comparison> compare(KurennykhExperimentPlan plan,List<KurennykhStatistics.Aggregate> aggregates,
                                           List<KurennykhStatistics.GroupSummary> groups,Path reference) throws IOException {
        var result=new ArrayList<Comparison>();
        for(var p:published(reference))if(plan.sizes().contains(p.size())) {
            for(var metric:List.of("elapsedMillis","eFull","vUpper","iterations","deltaCi")) {
                var our=aggregates.stream().filter(a->a.size()==p.size()&&a.group().equals("ALL")&&a.metric().equals(metric)).findFirst().orElseThrow();
                double value=switch(metric) {case "elapsedMillis"->p.elapsedMillis();case "eFull"->p.eFull();case "vUpper"->p.undefinedVariation();case "iterations"->p.iterations();default->p.deltaCi();};
                String definition=switch(metric) {case "eFull"->"Sum over all entries, formula (11)";case "vUpper"->"Average variation percent; denominator not stated";
                    case "iterations"->"Varied elements; repeated/reciprocal counting not specified";case "elapsedMillis"->"C# 7 / .NET Core 2, computing time";default->"Mean CI reduction";};
                String restriction=switch(metric) {case "vUpper"->"Operational V_upper chosen before runs; author's formula unknown. Not a direct numerical reproduction.";
                    case "iterations"->"Our iteration is one accepted reciprocal pair edit, including repeats; author's counting not fully specified.";
                    case "elapsedMillis"->"Different language/runtime/hardware/JIT and internal index calculations; descriptive only.";
                    default->"Same mathematical measure; original generator, inputs, size-specific N and averaging population unknown.";};
                result.add(new Comparison(plan.profile(),p.size(),metric,definition,metric+" averaged over ALL matrices",value,our.mean(),our.mean()-value,
                        Math.abs(our.mean()-value),value==0?null:(our.mean()-value)/Math.abs(value),our.nMatrices(),null,
                        metric.equals("vUpper")?"DEFINITION_UNKNOWN_DESCRIPTIVE_ONLY":metric.equals("elapsedMillis")?"ENVIRONMENT_DEPENDENT":
                        metric.equals("iterations")?"COUNTING_PARTLY_UNSPECIFIED":"SAME_FORMULA_DIFFERENT_SAMPLE",restriction));
            }
            var group=groups.stream().filter(g->g.size()==p.size()&&g.group().equals("ALL")).findFirst().orElseThrow();
            result.add(new Comparison(plan.profile(),p.size(),"originalBestRankChanges","Count of original best object rank changes; weights/ties unspecified",
                    "Perron right priorities, exact ties by index; ordinal position change",p.rankChanges(),group.originalBestRankChanges(),null,null,null,group.nMatrices(),null,
                    "AUTHOR_N_PER_SIZE_UNKNOWN","Raw counts shown separately; neither author percentages nor differences of frequencies/counts are inferred."));
        }
        return List.copyOf(result);
    }
}
