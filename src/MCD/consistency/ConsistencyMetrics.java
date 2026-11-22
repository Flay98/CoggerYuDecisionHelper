package MCD.consistency;

import Jama.EigenvalueDecomposition;
import Jama.Matrix;

import java.util.Optional;

/** Numerical indices for quantitative multiplicative pairwise comparison matrices. */
public final class ConsistencyMetrics {
    private static final double EIGENVALUE_TOLERANCE = 1e-10;

    private ConsistencyMetrics() {
    }

    /**
     * Returns the positive Perron eigenvalue, not row sums from Ranker.EigenValues.
     * A positive matrix has a real, positive eigenvalue with greatest real part.
     * JAMA uses a nonsymmetric Hessenberg/real Schur decomposition in this case.
     *
     * Diagonal similarity B = D^-1 A D with log(D_ii) = mean_j(log(a_ij))
     * initially reduces differences in scale while preserving eigenvalues.
     * Further diagonal balancing equalizes maximum off-diagonal entries in each
     * row/column pair. A uniform
     * scaling then keeps entries <= 1 before calling JAMA. Neither step changes
     * the stored expert comparisons. This particularly helps consistent matrices
     * whose coefficients span hundreds of decimal orders of magnitude.
     *
     * @throws ArithmeticException if a finite, usable Perron root cannot be
     *                             obtained in double precision
     */
    public static double principalEigenvalue(PairwiseComparisonMatrix matrix) {
        double lambda = Math.exp(logPrincipalEigenvalue(matrix));
        if (!Double.isFinite(lambda) || lambda <= 0) {
            throw new ArithmeticException("Principal eigenvalue is outside the finite double range");
        }
        return lambda;
    }

    private static double logPrincipalEigenvalue(PairwiseComparisonMatrix matrix) {
        requireMatrix(matrix);
        int n = matrix.size();
        double[][] logs = new double[n][n];
        double[] means = new double[n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                logs[i][j] = Math.log(matrix.get(i, j));
                means[i] += logs[i][j] / n;
            }
        }
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                logs[i][j] = logs[i][j] - means[i] + means[j];
            }
        }
        balanceLogMatrix(logs);
        double shift = 0;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                shift = Math.max(shift, logs[i][j]);
            }
        }
        double[][] scaled = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                scaled[i][j] = Math.exp(logs[i][j] - shift);
            }
        }
        EigenvalueDecomposition decomposition = new EigenvalueDecomposition(new Matrix(scaled));
        double[] real = decomposition.getRealEigenvalues();
        double[] imaginary = decomposition.getImagEigenvalues();
        int principal = 0;
        for (int i = 0; i < n; i++) {
            if (!Double.isFinite(real[i]) || !Double.isFinite(imaginary[i])) {
                throw new ArithmeticException("JAMA returned non-finite eigenvalues");
            }
            if (real[i] > real[principal]) {
                principal = i;
            }
        }
        if (real[principal] <= 0 || Math.abs(imaginary[principal]) > EIGENVALUE_TOLERANCE * real[principal]) {
            throw new ArithmeticException("Cannot resolve a positive real Perron eigenvalue in double precision");
        }
        return Math.log(real[principal]) + shift;
    }

    /**
     * Numerical preconditioning only: subtracting d from row i and adding d to
     * column i in log space is diagonal similarity, not preference correction.
     * Balancing maxima prevents a large acyclic path from dwarfing every cycle
     * after uniform scaling (and destroying small scaled eigenvalues in JAMA).
     */
    private static void balanceLogMatrix(double[][] logs) {
        for (int sweep = 0; sweep < 1000; sweep++) {
            double maximumChange = 0;
            for (int i = 0; i < logs.length; i++) {
                double rowMaximum = Double.NEGATIVE_INFINITY;
                double columnMaximum = Double.NEGATIVE_INFINITY;
                for (int j = 0; j < logs.length; j++) {
                    if (i != j) {
                        rowMaximum = Math.max(rowMaximum, logs[i][j]);
                        columnMaximum = Math.max(columnMaximum, logs[j][i]);
                    }
                }
                double change = (rowMaximum - columnMaximum) / 2;
                maximumChange = Math.max(maximumChange, Math.abs(change));
                for (int j = 0; j < logs.length; j++) {
                    if (i != j) {
                        logs[i][j] -= change;
                        logs[j][i] += change;
                    }
                }
            }
            if (maximumChange <= 1e-12) {
                return;
            }
        }
        throw new ArithmeticException("Eigenvalue preconditioning did not converge after 1000 sweeps");
    }

    /**
     * Saaty's CI = (lambda_max - n)/(n - 1), NOT the consistency ratio CR.
     * Only a small negative rounding error is clamped to zero. A larger negative
     * result is reported explicitly instead of masking a numerical problem or
     * the effects of an excessively relaxed structural validation tolerance.
     */
    public static double saatyIndex(PairwiseComparisonMatrix matrix) {
        double logLambda = logPrincipalEigenvalue(matrix);
        int n = matrix.size();
        double lambda = Math.exp(logLambda);
        // CI can still be finite when lambda itself exceeds Double.MAX_VALUE.
        double index = Double.isFinite(lambda) ? (lambda - n) / (n - 1)
                : Math.exp(logLambda - Math.log(n - 1)) - (double) n / (n - 1);
        if (!Double.isFinite(index)) {
            throw new ArithmeticException("Saaty CI is outside the finite double range");
        }
        if (index < -EIGENVALUE_TOLERANCE) {
            throw new ArithmeticException("Significantly negative Saaty CI: " + index
                    + "; check rounding tolerance and numerical conditioning");
        }
        return Math.max(0, index);
    }

    /**
     * For r = a_ij*a_jk/a_ik > 0 the published expression
     * min(|1-r|, |1-1/r|) equals 1 - exp(-|log(r)|).
     * expm1 retains small defects; the logarithmic ratio avoids unsafe products.
     * Values extremely close to 1 can round to 1 in double precision.
     * Complexity: O(n^3) time, O(1) auxiliary space.
     */
    public static KoczkodajResult koczkodajIndex(PairwiseComparisonMatrix matrix) {
        requireMatrix(matrix);
        double maximum = 0;
        KoczkodajResult.Triad worst = null;
        for (int i = 0; i < matrix.size(); i++) {
            for (int j = i + 1; j < matrix.size(); j++) {
                for (int k = j + 1; k < matrix.size(); k++) {
                    double logRatio = NumericalSupport.logProductRatio(matrix.get(i, j), matrix.get(j, k), matrix.get(i, k));
                    double local = -Math.expm1(-Math.abs(logRatio));
                    if (worst == null || local > maximum) {
                        maximum = local;
                        worst = new KoczkodajResult.Triad(i, j, k, local);
                    }
                }
            }
        }
        return new KoczkodajResult(maximum, Optional.ofNullable(worst));
    }

    private static void requireMatrix(PairwiseComparisonMatrix matrix) {
        if (matrix == null) {
            throw new IllegalArgumentException("Pairwise comparison matrix must not be null");
        }
    }
}
