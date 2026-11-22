package MCD.consistency;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static MCD.consistency.CosineCorrectionResult.Status;

/**
 * Quantitative correction based on Kurennykh et al. (2018), pp. 8-11, (9)-(10).
 * Author rules: rank low row cosines and propose a_ik = a_ij*a_jk.
 * Implementation choices: refresh ranking after each edit, rank supporting
 * paths by min(xi_ij, xi_jk), and accept only a measured strict CI improvement.
 * This is a deterministic completion of an underspecified recommendation method,
 * not a claim to reproduce the authors' unavailable C# implementation exactly.
 */
public final class CosineConsistencyCorrector {
    private final CosineCorrectionOptions options;

    public CosineConsistencyCorrector() {
        this(CosineCorrectionOptions.defaults());
    }

    public CosineConsistencyCorrector(CosineCorrectionOptions options) {
        if (options == null) {
            throw new IllegalArgumentException("Correction options must not be null");
        }
        this.options = options;
    }

    /**
     * Automatically accepts admissible recommendations; no console interaction.
     * Invalid input scale and failure to evaluate the INITIAL indices throw an
     * exception. Failed candidate evaluations are counted and skipped, leaving
     * the last accepted matrix unchanged. No rejected trial enters the history.
     */
    public CosineCorrectionResult correct(PairwiseComparisonMatrix original) {
        if (original == null) {
            throw new IllegalArgumentException("Pairwise comparison matrix must not be null");
        }
        for (int i = 0; i < original.size(); i++) {
            for (int k = 0; k < original.size(); k++) {
                if (!options.contains(original.get(i, k))) {
                    throw new IllegalArgumentException("Input entry [" + i + "][" + k + "] is outside the configured comparison interval");
                }
            }
        }
        double initialCi = ConsistencyMetrics.saatyIndex(original);
        double initialK = ConsistencyMetrics.koczkodajIndex(original).value();
        PairwiseComparisonMatrix current = original;
        double ci = initialCi;
        double k = initialK;
        List<CosineCorrectionResult.Change> history = new ArrayList<>();
        if (ci <= options.targetCi()) {
            return result(original, current, initialCi, ci, initialK, k, history,
                    Status.ALREADY_ACCEPTABLE, "Initial CI is already at or below the target");
        }
        while (history.size() < options.maxIterations()) {
            double[][] cosines = RowCosines.compute(current);
            Rejections rejected = new Rejections();
            Candidate candidate = findCandidate(current, ci, cosines, rejected);
            if (candidate == null) {
                return result(original, current, initialCi, ci, initialK, k, history,
                        Status.NO_ADMISSIBLE_IMPROVEMENT, rejected.describe());
            }
            double afterK = ConsistencyMetrics.koczkodajIndex(candidate.matrix()).value();
            double old = current.get(candidate.i(), candidate.k());
            double updated = candidate.matrix().get(candidate.i(), candidate.k());
            history.add(new CosineCorrectionResult.Change(history.size() + 1,
                    candidate.i(), candidate.k(), candidate.via(), old, updated, updated - old,
                    cosines[candidate.i()][candidate.k()], candidate.support(), ci, candidate.ci(), k, afterK));
            current = candidate.matrix();
            ci = candidate.ci();
            k = afterK;
            if (ci <= options.targetCi()) {
                return result(original, current, initialCi, ci, initialK, k, history,
                        Status.TARGET_REACHED, "Corrected CI is at or below the target");
            }
        }
        return result(original, current, initialCi, ci, initialK, k, history,
                Status.MAX_ITERATIONS, "Maximum accepted corrections reached before the target CI");
    }

    private Candidate findCandidate(PairwiseComparisonMatrix current, double ci,
                                    double[][] cosines, Rejections rejected) {
        // Keep cosine priority: never choose the globally best trial CI instead.
        for (RowCosines.Comparison pair : RowCosines.rank(cosines)) {
            List<Support> supports = new ArrayList<>();
            for (int j = 0; j < current.size(); j++) {
                if (j != pair.i() && j != pair.k()) {
                    supports.add(new Support(j, Math.min(cosines[pair.i()][j], cosines[j][pair.k()])));
                }
            }
            supports.sort(Comparator.comparingDouble(Support::cosine).reversed().thenComparingInt(Support::via));
            for (Support support : supports) {
                if (support.cosine() < options.minimumSupportCosine()) {
                    rejected.support++;
                    continue;
                }
                // Formula (10): add Delta = a_ij*a_jk - a_ik, hence replace by
                // the product, without a scale projection or rounding step.
                double updated = current.get(pair.i(), support.via()) * current.get(support.via(), pair.k());
                double reciprocal = 1 / updated;
                if (!Double.isFinite(updated) || updated <= 0 || !Double.isFinite(reciprocal) || reciprocal <= 0) {
                    rejected.representation++;
                    continue;
                }
                if (!options.contains(updated) || !options.contains(reciprocal)) {
                    rejected.scale++;
                    continue;
                }
                if (updated == current.get(pair.i(), pair.k())) {
                    rejected.unchanged++;
                    continue;
                }
                double[][] data = current.toArray();
                data[pair.i()][pair.k()] = updated;
                data[pair.k()][pair.i()] = reciprocal;
                try {
                    PairwiseComparisonMatrix trial = new PairwiseComparisonMatrix(data, current.validationTolerance());
                    double after = ConsistencyMetrics.saatyIndex(trial);
                    if (ci - after > options.minimumCiDecrease()) {
                        return new Candidate(pair.i(), pair.k(), support.via(), support.cosine(), trial, after);
                    }
                    rejected.noDecrease++;
                } catch (ArithmeticException | IllegalArgumentException error) {
                    // Numeric impossibility is a rejected recommendation, not
                    // permission to silently alter its value or accept NaN CI.
                    rejected.numerical++;
                }
            }
        }
        return null;
    }

    private CosineCorrectionResult result(PairwiseComparisonMatrix original, PairwiseComparisonMatrix corrected,
                                          double initialCi, double finalCi, double initialK, double finalK,
                                          List<CosineCorrectionResult.Change> history, Status status, String detail) {
        return new CosineCorrectionResult(original, corrected, initialCi, finalCi, initialK, finalK,
                options, status, detail, history);
    }

    private record Support(int via, double cosine) {
    }

    private record Candidate(int i, int k, int via, double support, PairwiseComparisonMatrix matrix, double ci) {
    }

    private static final class Rejections {
        long support, scale, representation, unchanged, noDecrease, numerical;

        String describe() {
            return "No admissible improving correction in the current matrix: support=" + support
                    + ", scale=" + scale + ", non-representable=" + representation + ", unchanged=" + unchanged
                    + ", insufficient CI decrease=" + noDecrease + ", numerical failures=" + numerical;
        }
    }
}
