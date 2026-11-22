package MCD.consistency;

import java.util.Optional;

/**
 * K is the greatest local inconsistency over i < j < k. At n = 2 the convention
 * is K = 0 and an empty worstTriad. At n >= 3 a triad is returned even if K = 0.
 * Equal computed maxima are resolved by the first lexicographic (i, j, k).
 */
public record KoczkodajResult(double value, Optional<Triad> worstTriad) {
    public KoczkodajResult {
        if (!Double.isFinite(value) || value < 0 || value > 1 || worstTriad == null) {
            throw new IllegalArgumentException("K must be finite and in [0, 1], and worstTriad must not be null");
        }
        if (worstTriad.isEmpty() ? value != 0 : worstTriad.get().localInconsistency() != value) {
            throw new IllegalArgumentException("K must equal the worst triad's local inconsistency (or zero without a triad)");
        }
    }

    /** Object indices are zero-based: (0, 1, 2) denotes objects 1, 2 and 3. */
    public record Triad(int i, int j, int k, double localInconsistency) {
        public Triad {
            if (i < 0 || i >= j || j >= k || !Double.isFinite(localInconsistency)
                    || localInconsistency < 0 || localInconsistency > 1) {
                throw new IllegalArgumentException("Triad requires 0 <= i < j < k and local inconsistency in [0, 1]");
            }
        }
    }
}
