package MCD.consistency;

/**
 * Immutable positive multiplicative pairwise comparison matrix of order n >= 2.
 * Structural validity does not imply multiplicative consistency or an acceptable CI/K.
 * No preference scale is imposed and accepted entries are never corrected or rounded.
 */
public final class PairwiseComparisonMatrix {
    public static final double DEFAULT_VALIDATION_TOLERANCE = 1e-10;

    private final double[][] values;
    private final double validationTolerance;

    public PairwiseComparisonMatrix(double[][] values) {
        this(values, DEFAULT_VALIDATION_TOLERANCE);
    }

    /**
     * @param tolerance finite value in [0, 1): |a_ii - 1| <= tolerance and
     *                  |a_ij * a_ji - 1| <= tolerance. The latter is dimensionless
     *                  and does not accidentally accept arbitrary tiny entries.
     * @throws IllegalArgumentException if the data or tolerance are invalid
     */
    public PairwiseComparisonMatrix(double[][] values, double tolerance) {
        if (!Double.isFinite(tolerance) || tolerance < 0 || tolerance >= 1) {
            throw new IllegalArgumentException("Validation tolerance must be finite and in [0, 1)");
        }
        this.validationTolerance = tolerance;
        if (values == null || values.length < 2) {
            throw new IllegalArgumentException("Matrix must contain at least two rows (n >= 2)");
        }
        int n = values.length;
        this.values = new double[n][];
        for (int i = 0; i < n; i++) {
            if (values[i] == null || values[i].length != n) {
                throw new IllegalArgumentException("Matrix must be square: row " + i + " must have " + n + " entries");
            }
            this.values[i] = values[i].clone();
            for (int j = 0; j < n; j++) {
                double value = this.values[i][j];
                if (!Double.isFinite(value) || value <= 0) {
                    throw new IllegalArgumentException("Entry [" + i + "][" + j + "] must be finite and strictly positive");
                }
            }
            if (Math.abs(this.values[i][i] - 1) > tolerance) {
                throw new IllegalArgumentException("Diagonal entry [" + i + "][" + i + "] must equal 1 within tolerance");
            }
        }
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                // FMA evaluates the product defect with one rounding. Overflow
                // indicates an invalid reciprocal pair, not a valid extreme ratio.
                double defect = Math.abs(Math.fma(this.values[i][j], this.values[j][i], -1));
                if (defect > tolerance) {
                    throw new IllegalArgumentException("Entries [" + i + "][" + j + "] and [" + j + "][" + i
                            + "] must be reciprocal: |a_ij * a_ji - 1| = " + defect);
                }
            }
        }
    }

    public int size() {
        return values.length;
    }

    /** Structural tolerance used at construction; retained when a correction makes a new matrix. */
    public double validationTolerance() {
        return validationTolerance;
    }

    /** Row and column indices are zero-based. */
    public double get(int row, int column) {
        return values[row][column];
    }

    /** Returns a deep copy; neither construction nor access exposes internal arrays. */
    public double[][] toArray() {
        double[][] copy = new double[size()][];
        for (int i = 0; i < size(); i++) {
            copy[i] = values[i].clone();
        }
        return copy;
    }

    /**
     * Checks all ordered triples, including repeated indices. For each triple,
     * 1/(1+tolerance) <= a_ij*a_jk/a_ik <= 1+tolerance must hold.
     * Checking all orientations also respects the rounding in the lower triangle
     * and diagonal of matrices accepted with an explicitly relaxed validation tolerance.
     * This is approximate IDEAL consistency, not an article's acceptability threshold.
     */
    public boolean isMultiplicativelyConsistent(double tolerance) {
        if (!Double.isFinite(tolerance) || tolerance < 0) {
            throw new IllegalArgumentException("Consistency tolerance must be finite and non-negative");
        }
        double bound = Math.log1p(tolerance);
        for (int i = 0; i < size(); i++) {
            for (int j = 0; j < size(); j++) {
                for (int k = 0; k < size(); k++) {
                    double defect = NumericalSupport.logProductRatio(values[i][j], values[j][k], values[i][k]);
                    if (Math.abs(defect) > bound) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
