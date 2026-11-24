package MCD.consistency.reproduction.kurennykh;

import MCD.consistency.experiments.BenchmarkStatistics;
import java.util.ArrayList;
import java.util.List;

/** ALL is primary; failures are retained. Undefined metrics retain their own explicit denominator. */
public final class KurennykhStatistics {
    public static final double Z95=1.959963984540054;
    private KurennykhStatistics() { }
    public enum Group { ALL, NEEDS_CORRECTION, SUCCESSFUL_REQUIRED }
    public enum Metric {
        elapsedMillis,eFull,eUpper,vUpper,vChanged,vOffdiagonal,iterations,acceptedCorrections,
        uniqueTouchedComparisons,changedComparisons,deltaCi,initialCi,finalCi,initialK,finalK,logDeviation,deltaPosition;
        Double value(KurennykhRunResult r) { return switch(this) {
            case elapsedMillis->r.elapsedMillis();case eFull->r.eFull();case eUpper->r.eUpper();case vUpper->r.vUpper();
            case vChanged->r.vChanged();case vOffdiagonal->r.vOffdiagonal();case iterations->(double)r.iterations();
            case acceptedCorrections->(double)r.acceptedCorrections();case uniqueTouchedComparisons->(double)r.uniqueTouchedComparisons();
            case changedComparisons->(double)r.changedComparisons();case deltaCi->r.deltaCi();case initialCi->r.initialCi();
            case finalCi->r.finalCi();case initialK->r.initialK();case finalK->r.finalK();case logDeviation->r.logDeviation();case deltaPosition->(double)r.deltaPosition();
        }; }
    }
    public record Moments(int nDefined,Double mean,Double median,Double sampleSd,Double minimum,Double maximum,Double ci95Low,Double ci95High) { }
    public record Aggregate(String profile,int size,String group,String metric,int nMatrices,int nDefined,
                            Double mean,Double median,Double sampleSd,Double minimum,Double maximum,Double ci95Low,Double ci95High,
                            String ciMethod,boolean smallSample) { }
    public record GroupSummary(String profile,int size,String group,int nMatrices,long initiallyAcceptable,long correctionRequired,
            long successfullyCorrected,long failedCorrections,long targetReached,Double targetRate,Double requiredCorrectionSuccessRate,
            long leaderChanges,long originalBestRankChanges,Double bestRankChangeRate) { }

    public static Moments moments(double[] data) {
        if(data.length==0)return new Moments(0,null,null,null,null,null,null,null);
        for(double value:data)if(!Double.isFinite(value))throw new IllegalArgumentException("Non-finite statistic");
        double mean=BenchmarkStatistics.mean(data);Double sd=BenchmarkStatistics.sampleSd(data);
        double min=java.util.Arrays.stream(data).min().orElseThrow(),max=java.util.Arrays.stream(data).max().orElseThrow();
        Double error=sd==null?null:Z95*sd/Math.sqrt(data.length);
        return new Moments(data.length,mean,BenchmarkStatistics.median(data),sd,min,max,error==null?null:mean-error,error==null?null:mean+error);
    }

    private static List<KurennykhRunResult> select(List<KurennykhRunResult> runs,int size,Group group) {
        return runs.stream().filter(r->r.size()==size).filter(r->switch(group) {
            case ALL->true;case NEEDS_CORRECTION->r.correctionRequired();case SUCCESSFUL_REQUIRED->r.successfullyCorrected();
        }).toList();
    }

    public static List<Aggregate> aggregate(KurennykhExperimentPlan plan,List<KurennykhRunResult> runs) {
        validate(plan,runs);var rows=new ArrayList<Aggregate>();
        for(int n:plan.sizes())for(Group group:Group.values()) {
            var selected=select(runs,n,group);
            for(Metric metric:Metric.values()) {
                var m=moments(selected.stream().map(metric::value).filter(java.util.Objects::nonNull).mapToDouble(Double::doubleValue).toArray());
                rows.add(new Aggregate(plan.profile(),n,group.name(),metric.name(),selected.size(),m.nDefined(),m.mean(),m.median(),m.sampleSd(),m.minimum(),m.maximum(),m.ci95Low(),m.ci95High(),
                        "NORMAL_CLT_95_Z_1.959963984540054",m.nDefined()<30));
            }
        }
        return List.copyOf(rows);
    }
    public static List<GroupSummary> summaries(KurennykhExperimentPlan plan,List<KurennykhRunResult> runs) {
        validate(plan,runs);var result=new ArrayList<GroupSummary>();
        for(int n:plan.sizes())for(Group group:Group.values()) {
            var rows=select(runs,n,group);long already=rows.stream().filter(KurennykhRunResult::initiallyAcceptable).count();
            long required=rows.stream().filter(KurennykhRunResult::correctionRequired).count(),success=rows.stream().filter(KurennykhRunResult::successfullyCorrected).count();
            long target=rows.stream().filter(KurennykhRunResult::targetReached).count(),rank=rows.stream().filter(KurennykhRunResult::originalBestRankChanged).count();
            result.add(new GroupSummary(plan.profile(),n,group.name(),rows.size(),already,required,success,required-success,target,
                    rows.isEmpty()?null:(double)target/rows.size(),required==0?null:(double)success/required,
                    rows.stream().filter(KurennykhRunResult::leaderChanged).count(),rank,rows.isEmpty()?null:(double)rank/rows.size()));
        }
        return List.copyOf(result);
    }
    private static void validate(KurennykhExperimentPlan plan,List<KurennykhRunResult> rows) {
        if(rows.stream().anyMatch(r->!r.profile().equals(plan.profile())||!plan.sizes().contains(r.size()))
                ||rows.stream().map(KurennykhRunResult::id).distinct().count()!=rows.size()
                ||rows.stream().map(KurennykhRunResult::planSha256).distinct().count()>1)
            throw new IllegalArgumentException("Mixed plans/profiles or duplicate matrix IDs");
    }
}
