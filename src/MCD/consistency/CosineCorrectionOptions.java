package MCD.consistency;

/**
 * Explicit deterministic implementation settings, not numerical constants
 * established by the article (except its default target CI of 0.1).
 * The comparison scale is a CONTINUOUS inclusive interval; both a_ik and its
 * reciprocal must belong to it. Out-of-range candidates are rejected as-is.
 */
public record CosineCorrectionOptions(
        double targetCi,
        int maxIterations,
        double minimumCiDecrease,
        double minimumSupportCosine,
        double minimumComparison,
        double maximumComparison) {

    public CosineCorrectionOptions {
        if (!Double.isFinite(targetCi) || targetCi < 0) {
            throw new IllegalArgumentException("Target CI must be finite and non-negative");
        }
        if (maxIterations < 0) {
            throw new IllegalArgumentException("Maximum iterations must be non-negative");
        }
        if (!Double.isFinite(minimumCiDecrease) || minimumCiDecrease < 0) {
            throw new IllegalArgumentException("Minimum CI decrease must be finite and non-negative");
        }
        if (!Double.isFinite(minimumSupportCosine) || minimumSupportCosine < 0 || minimumSupportCosine > 1) {
            throw new IllegalArgumentException("Minimum support cosine must be finite and in [0, 1]");
        }
        if (!Double.isFinite(minimumComparison) || minimumComparison <= 0 || minimumComparison > 1
                || !Double.isFinite(maximumComparison) || maximumComparison < 1) {
            throw new IllegalArgumentException("Comparison interval must be finite, positive and contain 1");
        }
    }

    /** No imposed expert scale; strongest available supports without an absolute cosine cutoff. */
    public static CosineCorrectionOptions defaults() {
        return new CosineCorrectionOptions(0.1, 100, 1e-12, 0, Double.MIN_VALUE, Double.MAX_VALUE);
    }

    boolean contains(double value) {
        return Double.isFinite(value) && value >= minimumComparison && value <= maximumComparison;
    }
}
