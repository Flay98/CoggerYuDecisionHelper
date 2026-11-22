package MCD.consistency;

import java.util.List;

/** Immutable correction outcome; all indices in the history are zero-based. */
public record CosineCorrectionResult(
        PairwiseComparisonMatrix original,
        PairwiseComparisonMatrix corrected,
        double initialCi,
        double finalCi,
        double initialK,
        double finalK,
        CosineCorrectionOptions options,
        Status status,
        String detail,
        List<Change> history) {

    public CosineCorrectionResult {
        history = List.copyOf(history);
    }

    /** One iteration is one accepted reciprocal-pair edit, not a rejected trial. */
    public int iterations() {
        return history.size();
    }

    /** Number of upper-triangle values that differ from the original at termination. */
    public int changedComparisons() {
        int count = 0;
        for (int i = 0; i < original.size(); i++) {
            for (int k = i + 1; k < original.size(); k++) {
                if (Double.compare(original.get(i, k), corrected.get(i, k)) != 0) {
                    count++;
                }
            }
        }
        return count;
    }

    public enum Status {
        ALREADY_ACCEPTABLE,
        TARGET_REACHED,
        MAX_ITERATIONS,
        NO_ADMISSIBLE_IMPROVEMENT
    }

    /**
     * delta = newValue - oldValue for a_ik; the reciprocal a_ki is updated in
     * the same iteration. Cosines refer to the matrix BEFORE this change.
     */
    public record Change(
            int iteration, int i, int k, int via,
            double oldValue, double newValue, double delta,
            double comparisonCosine, double supportCosine,
            double ciBefore, double ciAfter, double kBefore, double kAfter) {
    }
}
