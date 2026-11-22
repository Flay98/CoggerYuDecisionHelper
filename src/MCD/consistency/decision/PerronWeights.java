package MCD.consistency.decision;

import MCD.consistency.PairwiseComparisonMatrix;
import java.util.Arrays;
import java.util.List;

/** Principal RIGHT eigenvector via positive power iteration in log coordinates. Not Cogger-Yu. */
public final class PerronWeights {
    public static final double DEFAULT_TOLERANCE = 1e-12;
    public static final int DEFAULT_MAX_ITERATIONS = 10000;
    private final double tolerance;
    private final int maxIterations;

    public PerronWeights() { this(DEFAULT_TOLERANCE, DEFAULT_MAX_ITERATIONS); }
    public PerronWeights(double tolerance, int maxIterations) {
        if (!Double.isFinite(tolerance) || tolerance <= 0 || maxIterations < 1)
            throw new IllegalArgumentException("Positive finite weight tolerance and iteration limit are required");
        this.tolerance = tolerance; this.maxIterations = maxIterations;
    }

    public record Result(List<Double> weights, double eigenvalue, int iterations, double maxRelativeResidual,
                         double tolerance, int maxIterations) {
        public Result { weights = List.copyOf(weights); }
    }

    public Result calculate(PairwiseComparisonMatrix matrix) {
        if (matrix == null) throw new IllegalArgumentException("Criteria matrix is required");
        int n = matrix.size();
        double[][] logs = new double[n][n];
        for (int i=0; i<n; i++) for (int j=0; j<n; j++) logs[i][j] = Math.log(matrix.get(i,j));
        double[] weights = new double[n]; Arrays.fill(weights, -Math.log(n));
        for (int iteration=1; iteration<=maxIterations; iteration++) {
            double[] product = multiply(logs, weights);
            double low = Double.POSITIVE_INFINITY, high = Double.NEGATIVE_INFINITY;
            for (int i=0; i<n; i++) { double ratio = product[i]-weights[i]; low=Math.min(low,ratio); high=Math.max(high,ratio); }
            // Collatz ratios (Aw)_i / w_i must agree; this checks every component relatively,
            // including tiny priorities, unlike an absolute vector-change test alone.
            if (high-low <= tolerance) return finish(logs, weights, iteration);
            double normalizer = logSumExp(product);
            for (int i=0; i<n; i++) weights[i] = product[i]-normalizer;
        }
        throw new ArithmeticException("Principal right eigenvector did not converge after " + maxIterations + " iterations");
    }

    private Result finish(double[][] matrix, double[] logWeights, int iteration) {
        double[] weights = Arrays.stream(logWeights).map(Math::exp).toArray();
        double sum = sum(weights);
        for (int i=0; i<weights.length; i++) {
            weights[i] /= sum;
            if (!Double.isFinite(weights[i]) || weights[i] <= 0) throw new ArithmeticException("Positive criterion weight cannot be represented in double precision");
            logWeights[i] = Math.log(weights[i]);
        }
        double[] product = multiply(matrix, logWeights);
        double low = Double.POSITIVE_INFINITY, high = Double.NEGATIVE_INFINITY;
        for (int i=0; i<weights.length; i++) { low=Math.min(low,product[i]-logWeights[i]); high=Math.max(high,product[i]-logWeights[i]); }
        double logLambda = (low+high)/2;
        double residual = Math.expm1((high-low)/2);
        double lambda = Math.exp(logLambda);
        if (!Double.isFinite(lambda) || lambda <= 0 || !Double.isFinite(residual) || residual > 4*tolerance
                || Math.abs(sum(weights)-1) > 1e-12) throw new ArithmeticException("Cannot verify finite normalized Perron priorities and residual");
        return new Result(Arrays.stream(weights).boxed().toList(), lambda, iteration, residual, tolerance, maxIterations);
    }

    private static double[] multiply(double[][] matrix, double[] vector) {
        double[] result=new double[vector.length], terms=new double[vector.length];
        for (int i=0; i<result.length; i++) {
            for (int j=0; j<terms.length; j++) terms[j]=matrix[i][j]+vector[j];
            result[i]=logSumExp(terms);
        }
        return result;
    }

    private static double logSumExp(double[] terms) {
        double maximum=Arrays.stream(terms).max().orElseThrow();
        double[] scaled=Arrays.stream(terms).map(x->Math.exp(x-maximum)).toArray();
        return maximum+Math.log(sum(scaled));
    }

    /** Compensated summation also serves normalized columns and weighted scores. */
    static double sum(double[] values) {
        double total=0, compensation=0;
        for(double value:values) { double corrected=value-compensation; double next=total+corrected; compensation=(next-total)-corrected; total=next; }
        return total;
    }
}
