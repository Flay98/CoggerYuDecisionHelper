package MCD.consistency;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Row geometry used by Kurennykh et al. (2018), formula (9), derived from (7)-(8). */
public final class RowCosines {
    private RowCosines() {
    }

    /**
     * xi_ij = sum_k(a_ik*a_jk) / (||row_i|| * ||row_j||).
     * Each row is divided by its largest entry BEFORE computing its norm to
     * avoid overflow in squares, products and norms. Positive row scaling
     * preserves angles. The returned array is independent of the input matrix.
     */
    public static double[][] compute(PairwiseComparisonMatrix matrix) {
        if (matrix == null) {
            throw new IllegalArgumentException("Pairwise comparison matrix must not be null");
        }
        int n = matrix.size();
        double[][] unitRows = new double[n][n];
        for (int i = 0; i < n; i++) {
            double maximum = 0;
            for (int k = 0; k < n; k++) {
                maximum = Math.max(maximum, matrix.get(i, k));
            }
            double squaredNorm = 0;
            for (int k = 0; k < n; k++) {
                unitRows[i][k] = matrix.get(i, k) / maximum;
                squaredNorm = Math.fma(unitRows[i][k], unitRows[i][k], squaredNorm);
            }
            double norm = Math.sqrt(squaredNorm);
            for (int k = 0; k < n; k++) {
                unitRows[i][k] /= norm;
            }
        }
        double[][] result = new double[n][n];
        for (int i = 0; i < n; i++) {
            result[i][i] = 1;
            for (int j = i + 1; j < n; j++) {
                double dot = 0;
                double compensation = 0;
                for (int k = 0; k < n; k++) {
                    double term = unitRows[i][k] * unitRows[j][k] - compensation;
                    double sum = dot + term;
                    compensation = (sum - dot) - term;
                    dot = sum;
                }
                // Only remove floating-point overshoot in a cosine, never clip
                // or round an expert comparison to a preference scale.
                result[i][j] = result[j][i] = Math.max(0, Math.min(1, dot));
            }
        }
        return result;
    }

    /** Independent pairs i < k, ascending cosine, then ascending i and k. */
    public static List<Comparison> rankComparisons(PairwiseComparisonMatrix matrix) {
        return rank(compute(matrix));
    }

    static List<Comparison> rank(double[][] cosines) {
        List<Comparison> result = new ArrayList<>();
        for (int i = 0; i < cosines.length; i++) {
            for (int k = i + 1; k < cosines.length; k++) {
                result.add(new Comparison(i, k, cosines[i][k]));
            }
        }
        result.sort(Comparator.comparingDouble(Comparison::cosine)
                .thenComparingInt(Comparison::i).thenComparingInt(Comparison::k));
        return List.copyOf(result);
    }

    /** Zero-based indices of an independent comparison and its row cosine. */
    public record Comparison(int i, int k, double cosine) {
    }
}
