package MCD.consistency.experiments;

import MCD.consistency.PairwiseComparisonMatrix;

/** Post-hoc diagnostics only; these quantities never enter fitness or correction rules. */
public final class ResearchMetrics {
    public static final double CI_TARGET = 0.1;
    public static final double K_TARGET = 0.3;
    public static final double RELATIVE_ZERO_TOLERANCE = 1e-12;

    public enum InitialCategory { BOTH_ACCEPTABLE, CI_ONLY_VIOLATION, K_ONLY_VIOLATION, BOTH_VIOLATED }

    private ResearchMetrics() {
    }

    public static boolean acceptableCi(double ci) { return ci <= CI_TARGET; }
    public static boolean acceptableK(double k) { return k < K_TARGET; }

    public static InitialCategory category(double ci, double k) {
        if (acceptableCi(ci)) {
            return acceptableK(k) ? InitialCategory.BOTH_ACCEPTABLE : InitialCategory.K_ONLY_VIOLATION;
        }
        return acceptableK(k) ? InitialCategory.CI_ONLY_VIOLATION : InitialCategory.BOTH_VIOLATED;
    }

    /** Null is exported as JSON null / CSV NA, never as division by zero. */
    public static Double relativeChange(double before, double after) {
        return before <= RELATIVE_ZERO_TOLERANCE ? null : (before - after) / before;
    }

    public static double logDeviation(PairwiseComparisonMatrix original, PairwiseComparisonMatrix corrected) {
        if (original.size() != corrected.size()) {
            throw new IllegalArgumentException("Deviation requires equally sized matrices");
        }
        double norm = 0;
        for (int i = 0; i < original.size(); i++) {
            for (int j = i + 1; j < original.size(); j++) {
                double a = original.get(i, j), b = corrected.get(i, j);
                double difference = b - a;
                double log = Math.abs(difference) <= 0.5 * a
                        ? Math.log1p(difference / a) : Math.log(b) - Math.log(a);
                norm = Math.hypot(norm, log);
            }
        }
        double pairs = (double) original.size() * (original.size() - 1) / 2;
        return norm / Math.sqrt(pairs);
    }
}
