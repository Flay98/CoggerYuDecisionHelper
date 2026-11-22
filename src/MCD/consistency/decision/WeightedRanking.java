package MCD.consistency.decision;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Column normalization and weighted sum, separate from the legacy Simple/CoggerYu procedures. */
public final class WeightedRanking {
    public record Entry(int rank, int alternativeIndex, String alternative, double score) { }
    public record Result(List<Double> scores, List<Integer> ranks, List<Entry> ranking, String winner) {
        public Result { scores=List.copyOf(scores); ranks=List.copyOf(ranks); ranking=List.copyOf(ranking); }
    }

    public List<List<Double>> normalize(AlternativeTable table) {
        double[][] normalized=new double[table.alternativeCount()][table.criterionCount()];
        for(int j=0;j<table.criterionCount();j++) {
            boolean maximize=table.directions().get(j)==Direction.MAX;
            double scale=maximize?0:Double.POSITIVE_INFINITY;
            for(int i=0;i<table.alternativeCount();i++) scale=maximize?Math.max(scale,table.get(i,j)):Math.min(scale,table.get(i,j));
            double[] column=new double[table.alternativeCount()];
            // x/max(x), or min(x)/x, avoids overflow of sums and reciprocals.
            // The common scale cancels exactly in the mathematical normalization.
            for(int i=0;i<column.length;i++) column[i]=maximize?table.get(i,j)/scale:scale/table.get(i,j);
            double total=PerronWeights.sum(column);
            for(int i=0;i<column.length;i++) normalized[i][j]=column[i]/total;
        }
        return java.util.Arrays.stream(normalized).map(row->java.util.Arrays.stream(row).boxed().toList()).toList();
    }

    public Result rank(AlternativeTable table, List<List<Double>> normalized, List<Double> weights) {
        if(weights.size()!=table.criterionCount() || normalized.size()!=table.alternativeCount()) throw new IllegalArgumentException("Ranking dimensions differ");
        double[] checkedWeights=weights.stream().mapToDouble(Double::doubleValue).toArray();
        for(double value:checkedWeights) if(!Double.isFinite(value)||value<=0) throw new IllegalArgumentException("Weights must be positive and finite");
        if(Math.abs(PerronWeights.sum(checkedWeights)-1)>1e-12) throw new IllegalArgumentException("Weights must sum to one");
        double[] scores=new double[table.alternativeCount()];
        for(int i=0;i<scores.length;i++) {
            if(normalized.get(i).size()!=weights.size()) throw new IllegalArgumentException("Normalized criterion count differs");
            double[] terms=new double[weights.size()];
            for(int j=0;j<terms.length;j++) {
                double value=normalized.get(i).get(j);
                if(!Double.isFinite(value)||value<0||value>1) throw new IllegalArgumentException("Invalid normalized value");
                terms[j]=value*weights.get(j);
            }
            scores[i]=PerronWeights.sum(terms);
        }
        for(int j=0;j<weights.size();j++) {
            double[] column=new double[scores.length]; for(int i=0;i<scores.length;i++) column[i]=normalized.get(i).get(j);
            if(Math.abs(PerronWeights.sum(column)-1)>1e-12) throw new IllegalArgumentException("Normalized columns must sum to one");
        }
        if(Math.abs(PerronWeights.sum(scores)-1)>1e-12) throw new ArithmeticException("Alternative scores must sum to one");
        var indices=new ArrayList<Integer>(); for(int i=0;i<scores.length;i++) indices.add(i);
        // Exact equal doubles retain the CSV order; no tolerance-based nontransitive comparator.
        indices.sort(Comparator.<Integer>comparingDouble(i->scores[i]).reversed().thenComparingInt(i->i));
        var ranking=new ArrayList<Entry>(); int[] ranks=new int[scores.length];
        for(int i=0;i<indices.size();i++) { int index=indices.get(i); ranks[index]=i+1; ranking.add(new Entry(i+1,index,table.alternatives().get(index),scores[index])); }
        return new Result(java.util.Arrays.stream(scores).boxed().toList(), java.util.Arrays.stream(ranks).boxed().toList(), ranking, ranking.get(0).alternative());
    }
}
