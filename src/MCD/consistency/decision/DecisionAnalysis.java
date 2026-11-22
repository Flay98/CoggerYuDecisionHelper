package MCD.consistency.decision;

import MCD.consistency.PairwiseComparisonMatrix;
import MCD.consistency.cli.AnalysisResult;
import MCD.consistency.cli.MatrixInput;
import java.nio.file.Path;
import java.util.List;

/** Immutable in-memory results; the external JSON schema is defined separately. */
public record DecisionAnalysis(MatrixInput input, Path alternativesFile, AlternativeTable table,
                               List<List<Double>> normalized, List<Scenario> scenarios) {
    public DecisionAnalysis {
        normalized=normalized.stream().map(List::copyOf).toList(); scenarios=List.copyOf(scenarios);
    }

    public enum Kind { ORIGINAL, COSINE, GA_UPPER_ONLY, GA_BIDIRECTIONAL }
    public record Scenario(Kind kind, PairwiseComparisonMatrix matrix, double ci, double k,
                           PerronWeights.Result priorities, WeightedRanking.Result ranking,
                           List<Double> weightChanges, List<Double> scoreChanges, List<Integer> positionChanges,
                           AnalysisResult correction) {
        public Scenario { weightChanges=List.copyOf(weightChanges); scoreChanges=List.copyOf(scoreChanges); positionChanges=List.copyOf(positionChanges); }
        public String status() {
            if(correction==null) return "NOT_APPLIED";
            return correction.cosine()!=null?correction.cosine().result().status().name():correction.genetic().result().status().name();
        }
        public int changedComparisons() {
            if(correction==null) return 0;
            return correction.cosine()!=null?correction.cosine().result().changedComparisons():correction.genetic().result().changedComparisons();
        }
        public String matrixCsv() { return switch(kind) {
            case ORIGINAL -> "original_matrix.csv";
            case COSINE -> "cosine_corrected.csv";
            case GA_UPPER_ONLY -> "genetic_upper_corrected.csv";
            case GA_BIDIRECTIONAL -> "genetic_bidirectional_corrected.csv";
        }; }
        public String correctionDirectory() { return "corrections/"+kind.name().toLowerCase(java.util.Locale.ROOT); }
    }
}
