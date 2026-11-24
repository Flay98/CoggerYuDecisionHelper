package MCD.consistency.reproduction.kurennykh;

import MCD.consistency.PairwiseComparisonMatrix;
import MCD.consistency.experiments.ResearchMetrics;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Post-hoc measures only. No metric here is used for selecting a correction. */
public final class KurennykhMetrics {
    private KurennykhMetrics() { }
    public record Deviations(double eFull,double eUpper,double vUpper,Double vChanged,double vOffdiagonal,
                             double logDeviation,int changedComparisons) { }
    public record Ranking(List<Integer> objectsByRank,List<Integer> ranksByObject,int leader,int tiedLeaders) {
        public Ranking { objectsByRank=List.copyOf(objectsByRank);ranksByObject=List.copyOf(ranksByObject); }
    }
    public record RankChange(int originalLeader,int finalLeader,int initialPosition,int finalPosition,int deltaPosition,
                             boolean leaderChanged,boolean originalBestRankChanged,int initialTiedLeaders,int finalTiedLeaders) { }

    public static Deviations deviations(PairwiseComparisonMatrix a,PairwiseComparisonMatrix b) {
        if(a.size()!=b.size())throw new IllegalArgumentException("Matrix sizes differ");
        double full=0,upper=0,relative=0,offdiagonal=0;int changed=0;
        for(int i=0;i<a.size();i++)for(int j=0;j<a.size();j++) {
            double difference=b.get(i,j)-a.get(i,j);full=Math.fma(difference,difference,full);
            if(i!=j)offdiagonal+=Math.abs(difference)/a.get(i,j);
            if(i<j) { upper=Math.fma(difference,difference,upper);relative+=Math.abs(difference)/a.get(i,j);
                if(Double.compare(a.get(i,j),b.get(i,j))!=0)changed++; }
        }
        double pairs=(double)a.size()*(a.size()-1)/2;
        if(!Double.isFinite(full)||!Double.isFinite(relative)||!Double.isFinite(offdiagonal))
            throw new ArithmeticException("Deviation is outside finite double precision");
        return new Deviations(full,upper,100*relative/pairs,changed==0?null:100*relative/changed,
                100*offdiagonal/(2*pairs),ResearchMetrics.logDeviation(a,b),changed);
    }

    public static Ranking rank(List<Double> weights) {
        if(weights==null||weights.isEmpty()||weights.stream().anyMatch(x->x==null||!Double.isFinite(x)||x<=0))
            throw new IllegalArgumentException("Ranking requires positive finite weights");
        var order=new ArrayList<Integer>();for(int i=0;i<weights.size();i++)order.add(i);
        order.sort(Comparator.<Integer>comparingDouble(weights::get).reversed().thenComparingInt(i->i));
        var ranks=new ArrayList<>(java.util.Collections.nCopies(weights.size(),0));
        for(int i=0;i<order.size();i++)ranks.set(order.get(i),i+1);
        int best=order.get(0);int ties=(int)weights.stream().filter(x->Double.compare(x,weights.get(best))==0).count();
        return new Ranking(order,ranks,best,ties);
    }

    public static RankChange rankChange(Ranking before,Ranking after) {
        if(before.ranksByObject().size()!=after.ranksByObject().size())throw new IllegalArgumentException("Ranking sizes differ");
        int initial=before.ranksByObject().get(before.leader()),last=after.ranksByObject().get(before.leader());
        return new RankChange(before.leader(),after.leader(),initial,last,last-initial,before.leader()!=after.leader(),
                initial!=last,before.tiedLeaders(),after.tiedLeaders());
    }
}
