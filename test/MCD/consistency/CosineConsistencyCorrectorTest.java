package MCD.consistency;

import org.junit.Test;

import static MCD.consistency.CosineCorrectionResult.Status.*;
import static org.junit.Assert.*;

public class CosineConsistencyCorrectorTest {
    private final CosineConsistencyCorrector corrector = new CosineConsistencyCorrector();

    @Test
    public void idealMatrixIsReturnedWithoutEdits() {
        PairwiseComparisonMatrix original = new PairwiseComparisonMatrix(ComparisonFixtures.consistent());
        CosineCorrectionResult result = corrector.correct(original);
        assertEquals(ALREADY_ACCEPTABLE, result.status());
        assertSame(original, result.original());
        assertSame(original, result.corrected());
        assertEquals(0, result.changedComparisons());
        assertEquals(0, result.iterations());
        assertEquals(result.initialCi(), result.finalCi(), 0);
        assertEquals(result.initialK(), result.finalK(), 0);
        assertTrue(result.history().isEmpty());
    }

    @Test
    public void acceptableButNotIdealMatrixIsNotModified() {
        PairwiseComparisonMatrix original = new PairwiseComparisonMatrix(ComparisonFixtures.inconsistent());
        assertFalse(original.isMultiplicativelyConsistent(1e-10));
        CosineCorrectionResult result = corrector.correct(original);
        assertEquals(ALREADY_ACCEPTABLE, result.status());
        assertEquals(0.009147353644814, result.initialCi(), 1e-12);
        assertEquals(1.0 / 3, result.finalK(), 1e-14);
        assertSame(original, result.corrected());
        assertEquals(0, result.iterations());
    }

    @Test
    public void severeTriadFollowsCosinePriorityAndFormulaTen() {
        PairwiseComparisonMatrix original = ComparisonFixtures.triad(3, 2, 1);
        CosineCorrectionResult result = corrector.correct(original);
        assertTrue(result.initialCi() > 0.1);
        assertEquals(TARGET_REACHED, result.status());
        assertEquals(1, result.iterations());
        assertEquals(1, result.changedComparisons());
        CosineCorrectionResult.Change change = result.history().get(0);
        assertEquals(0, change.i());
        assertEquals(2, change.k());
        assertEquals(1, change.via());
        assertEquals(1, change.oldValue(), 0);
        assertEquals(6, change.newValue(), 0);
        assertEquals(5, change.delta(), 0);
        assertEquals(1.0 / 6, result.corrected().get(2, 0), 0);
        assertEquals(0, result.finalCi(), 1e-12);
        assertEquals(0, result.finalK(), 1e-15);
        assertTrue(result.corrected().isMultiplicativelyConsistent(1e-12));
        assertEquals(1, original.get(0, 2), 0);
        replayAndCheck(result);
    }

    @Test
    public void targetComparisonIsInclusive() {
        PairwiseComparisonMatrix matrix = ComparisonFixtures.triad(3, 2, 1);
        double target = ConsistencyMetrics.saatyIndex(matrix);
        CosineCorrectionResult result = configured(target, 100, 1e-12, 0, Double.MIN_VALUE, Double.MAX_VALUE).correct(matrix);
        assertEquals(ALREADY_ACCEPTABLE, result.status());
        assertEquals(0, result.iterations());
    }

    @Test
    public void zeroIterationBudgetDoesNotAttemptEdits() {
        CosineCorrectionResult result = configured(0.1, 0, 1e-12, 0, Double.MIN_VALUE, Double.MAX_VALUE)
                .correct(ComparisonFixtures.triad(3, 2, 1));
        assertEquals(MAX_ITERATIONS, result.status());
        assertSame(result.original(), result.corrected());
        assertEquals(0, result.iterations());
    }

    @Test
    public void positiveIterationLimitReturnsTheLastAcceptedMatrix() {
        CosineCorrectionResult result = configured(1e-10, 1, 1e-12, 0, Double.MIN_VALUE, Double.MAX_VALUE)
                .correct(new PairwiseComparisonMatrix(ComparisonFixtures.paperOriginal()));
        assertEquals(MAX_ITERATIONS, result.status());
        assertEquals(1, result.iterations());
        assertTrue(result.finalCi() < result.initialCi());
        assertTrue(result.finalCi() > 1e-10);
        replayAndCheck(result);
    }

    @Test
    public void choosesTheStrongerSupportingChainWhenThereAreSeveralIntermediates() {
        PairwiseComparisonMatrix matrix = new PairwiseComparisonMatrix(ComparisonFixtures.paperOriginal());
        CosineCorrectionResult result = configured(0.001, 1, 1e-12, 0, Double.MIN_VALUE, Double.MAX_VALUE).correct(matrix);
        CosineCorrectionResult.Change change = result.history().get(0);
        assertEquals(0, change.i());
        assertEquals(3, change.k());
        assertEquals(1, change.via());
        assertEquals(16, change.newValue(), 0);
        double[][] cosines = RowCosines.compute(matrix);
        assertTrue(Math.min(cosines[0][1], cosines[1][3]) > Math.min(cosines[0][2], cosines[2][3]));
        assertEquals(0.985855620892146, change.supportCosine(), 1e-14);
    }

    @Test
    public void reachingTargetOnLastAllowedIterationIsSuccess() {
        CosineCorrectionResult result = configured(0.1, 1, 1e-12, 0, Double.MIN_VALUE, Double.MAX_VALUE)
                .correct(ComparisonFixtures.triad(3, 2, 1));
        assertEquals(TARGET_REACHED, result.status());
        assertEquals(1, result.iterations());
    }

    @Test
    public void excessiveSupportRequirementProducesAnExplicitNoImprovementStatus() {
        CosineCorrectionResult result = configured(0.1, 100, 1e-12, 0.9, Double.MIN_VALUE, Double.MAX_VALUE)
                .correct(ComparisonFixtures.triad(3, 2, 1));
        assertEquals(NO_ADMISSIBLE_IMPROVEMENT, result.status());
        assertEquals(0, result.iterations());
        assertTrue(result.detail(), result.detail().contains("support=3"));
        assertSame(result.original(), result.corrected());
    }

    @Test
    public void minimumDecreaseRejectsInsignificantOrNonImprovingTrials() {
        CosineCorrectionResult result = configured(0.1, 100, 1, 0, Double.MIN_VALUE, Double.MAX_VALUE)
                .correct(ComparisonFixtures.triad(3, 2, 1));
        assertEquals(NO_ADMISSIBLE_IMPROVEMENT, result.status());
        assertEquals(0, result.iterations());
        assertTrue(result.detail(), result.detail().contains("insufficient CI decrease=3"));
    }

    @Test
    public void scaleRejectsRatherThanClipsAndTriesTheNextComparison() {
        CosineCorrectionResult result = configured(0.1, 100, 1e-12, 0, 1.0 / 3, 3)
                .correct(ComparisonFixtures.triad(3, 2, 1));
        assertEquals(TARGET_REACHED, result.status());
        CosineCorrectionResult.Change change = result.history().get(0);
        // Most suspect (0,2) would require 6. It must NOT be clipped to 3.
        assertEquals(0, change.i());
        assertEquals(1, change.k());
        assertEquals(2, change.via());
        assertEquals(0.5, change.newValue(), 0);
        assertEquals(1, result.corrected().get(0, 2), 0);
        replayAndCheck(result);
    }

    @Test
    public void impossibleScaleReturnsWithoutInventingAReplacement() {
        CosineCorrectionResult result = configured(0.1, 100, 1e-12, 0, 1.0 / 3, 3)
                .correct(ComparisonFixtures.triad(3, 3, 1.0 / 3));
        assertEquals(NO_ADMISSIBLE_IMPROVEMENT, result.status());
        assertEquals(0, result.iterations());
        assertTrue(result.detail(), result.detail().contains("scale=3"));
        assertSame(result.original(), result.corrected());
    }

    @Test
    public void scaleBoundsAreInclusiveAndApplyToTheReciprocalToo() {
        CosineCorrectionResult result = configured(0.1, 100, 1e-12, 0, 1.0 / 9, 9)
                .correct(ComparisonFixtures.triad(3, 3, 1.0 / 3));
        assertEquals(TARGET_REACHED, result.status());
        for (double[] row : result.corrected().toArray()) {
            for (double value : row) {
                assertTrue(value >= 1.0 / 9 && value <= 9);
            }
        }
        // With an asymmetric interval the reciprocal may be the excluded entry.
        result = configured(0.1, 100, 1e-12, 0, 1.0 / 3, 9)
                .correct(ComparisonFixtures.triad(3, 3, 1.0 / 3));
        assertEquals(NO_ADMISSIBLE_IMPROVEMENT, result.status());
    }

    @Test
    public void inputOutsideScaleIsReportedEvenIfAlreadyAcceptable() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> configured(0.1, 100, 1e-12, 0, 0.5, 2)
                        .correct(new PairwiseComparisonMatrix(ComparisonFixtures.consistent())));
        assertTrue(error.getMessage(), error.getMessage().contains("Input entry"));
    }

    @Test
    public void nonRepresentableProductsAreRejectedWithoutChangingTheMatrix() {
        CosineCorrectionResult result = corrector.correct(ComparisonFixtures.triad(1e200, 1e200, 1e-200));
        assertEquals(NO_ADMISSIBLE_IMPROVEMENT, result.status());
        assertTrue(result.detail(), result.detail().contains("non-representable=3"));
        assertEquals(0, result.iterations());
        assertSame(result.original(), result.corrected());
    }

    @Test
    public void multistepHistoryReconstructsEveryMatrixAndEveryIndex() {
        CosineCorrectionResult result = configured(1e-10, 30, 1e-12, 0, Double.MIN_VALUE, Double.MAX_VALUE)
                .correct(new PairwiseComparisonMatrix(ComparisonFixtures.paperOriginal()));
        assertTrue(result.iterations() >= 2);
        assertTrue(result.finalCi() < result.initialCi());
        replayAndCheck(result);
        assertThrows(UnsupportedOperationException.class, result.history()::clear);
    }

    @Test
    public void repeatedRunsAreDeterministicAndDoNotMutateInput() {
        PairwiseComparisonMatrix matrix = new PairwiseComparisonMatrix(ComparisonFixtures.paperOriginal());
        CosineConsistencyCorrector configured = configured(0.001, 30, 1e-12, 0, Double.MIN_VALUE, Double.MAX_VALUE);
        CosineCorrectionResult first = configured.correct(matrix);
        CosineCorrectionResult second = configured.correct(matrix);
        assertEquals(first.status(), second.status());
        assertEquals(first.history(), second.history());
        assertEquals(first.finalCi(), second.finalCi(), 0);
        assertMatricesEqual(first.corrected(), second.corrected());
        assertMatricesEqual(new PairwiseComparisonMatrix(ComparisonFixtures.paperOriginal()), matrix);
    }

    @Test
    public void retainsExplicitInputValidationTolerance() {
        double[][] data = ComparisonFixtures.triad(3, 2, 1).toArray();
        data[1][0] *= 1 + 1e-5;
        PairwiseComparisonMatrix matrix = new PairwiseComparisonMatrix(data, 1e-4);
        CosineCorrectionResult result = corrector.correct(matrix);
        assertEquals(TARGET_REACHED, result.status());
        assertEquals(1e-4, result.corrected().validationTolerance(), 0);
        replayAndCheck(result);
    }

    @Test
    public void orderTwoNeverRequiresCorrectionsAtTheArticleThreshold() {
        CosineCorrectionResult result = corrector.correct(new PairwiseComparisonMatrix(new double[][] {{1, 16}, {1.0 / 16, 1}}));
        assertEquals(ALREADY_ACCEPTABLE, result.status());
        assertEquals(0, result.changedComparisons());
    }

    @Test
    public void validatesOptionsAndNullInput() {
        assertThrows(IllegalArgumentException.class, () -> corrector.correct(null));
        assertThrows(IllegalArgumentException.class, () -> new CosineConsistencyCorrector(null));
        assertThrows(IllegalArgumentException.class, () -> configured(-1, 1, 0, 0, 0.1, 10));
        assertThrows(IllegalArgumentException.class, () -> configured(Double.NaN, 1, 0, 0, 0.1, 10));
        assertThrows(IllegalArgumentException.class, () -> configured(0.1, -1, 0, 0, 0.1, 10));
        assertThrows(IllegalArgumentException.class, () -> configured(0.1, 1, -1, 0, 0.1, 10));
        assertThrows(IllegalArgumentException.class, () -> configured(0.1, 1, Double.POSITIVE_INFINITY, 0, 0.1, 10));
        assertThrows(IllegalArgumentException.class, () -> configured(0.1, 1, 0, 1.1, 0.1, 10));
        assertThrows(IllegalArgumentException.class, () -> configured(0.1, 1, 0, Double.NaN, 0.1, 10));
        assertThrows(IllegalArgumentException.class, () -> configured(0.1, 1, 0, 0, 0, 10));
        assertThrows(IllegalArgumentException.class, () -> configured(0.1, 1, 0, 0, 2, 10));
        assertThrows(IllegalArgumentException.class, () -> configured(0.1, 1, 0, 0, 0.1, 0.5));
        assertThrows(IllegalArgumentException.class, () -> configured(0.1, 1, 0, 0, 0.1, Double.POSITIVE_INFINITY));
    }

    private static CosineConsistencyCorrector configured(double target, int limit, double decrease, double support,
                                                        double minimum, double maximum) {
        return new CosineConsistencyCorrector(new CosineCorrectionOptions(target, limit, decrease, support, minimum, maximum));
    }

    private static void replayAndCheck(CosineCorrectionResult result) {
        PairwiseComparisonMatrix current = result.original();
        assertEquals(ConsistencyMetrics.saatyIndex(current), result.initialCi(), 1e-12);
        assertEquals(ConsistencyMetrics.koczkodajIndex(current).value(), result.initialK(), 1e-14);
        int iteration = 0;
        for (CosineCorrectionResult.Change change : result.history()) {
            assertEquals(++iteration, change.iteration());
            assertTrue(change.i() < change.k());
            assertTrue(change.via() != change.i() && change.via() != change.k());
            assertEquals(current.get(change.i(), change.k()), change.oldValue(), 0);
            assertEquals(current.get(change.i(), change.via()) * current.get(change.via(), change.k()), change.newValue(), 0);
            assertEquals(change.newValue() - change.oldValue(), change.delta(), 0);
            assertEquals(ConsistencyMetrics.saatyIndex(current), change.ciBefore(), 1e-12);
            assertEquals(ConsistencyMetrics.koczkodajIndex(current).value(), change.kBefore(), 1e-14);
            double[][] cosines = RowCosines.compute(current);
            assertEquals(cosines[change.i()][change.k()], change.comparisonCosine(), 0);
            assertEquals(Math.min(cosines[change.i()][change.via()], cosines[change.via()][change.k()]), change.supportCosine(), 0);
            double[][] data = current.toArray();
            data[change.i()][change.k()] = change.newValue();
            data[change.k()][change.i()] = 1 / change.newValue();
            current = new PairwiseComparisonMatrix(data, current.validationTolerance());
            assertEquals(ConsistencyMetrics.saatyIndex(current), change.ciAfter(), 1e-12);
            assertEquals(ConsistencyMetrics.koczkodajIndex(current).value(), change.kAfter(), 1e-14);
            assertTrue(change.ciBefore() - change.ciAfter() > result.options().minimumCiDecrease());
            for (int i = 0; i < current.size(); i++) {
                assertEquals(1, current.get(i, i), 0);
                for (int j = 0; j < current.size(); j++) {
                    assertTrue(current.get(i, j) > 0 && Double.isFinite(current.get(i, j)));
                    assertEquals(1, current.get(i, j) * current.get(j, i), current.validationTolerance());
                }
            }
        }
        assertMatricesEqual(current, result.corrected());
        assertEquals(ConsistencyMetrics.saatyIndex(current), result.finalCi(), 1e-12);
        assertEquals(ConsistencyMetrics.koczkodajIndex(current).value(), result.finalK(), 1e-14);
        int differences = 0;
        for (int i = 0; i < current.size(); i++) {
            for (int j = i + 1; j < current.size(); j++) {
                if (current.get(i, j) != result.original().get(i, j)) {
                    differences++;
                }
            }
        }
        assertEquals(differences, result.changedComparisons());
        assertEquals(result.history().size(), result.iterations());
    }

    private static void assertMatricesEqual(PairwiseComparisonMatrix expected, PairwiseComparisonMatrix actual) {
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            assertArrayEquals(expected.toArray()[i], actual.toArray()[i], 0);
        }
    }
}
