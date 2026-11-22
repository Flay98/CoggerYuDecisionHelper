package MCD.consistency;

import org.junit.Test;

import static MCD.consistency.GeneticCorrectionResult.Status.*;
import static MCD.consistency.GeneticOperatorsTest.*;
import static org.junit.Assert.*;

public class GeneticConsistencyCorrectorTest {
    @Test
    public void acceptableInputReturnsOriginalWithoutBuildingAPopulation() {
        PairwiseComparisonMatrix matrix = new PairwiseComparisonMatrix(ComparisonFixtures.consistent());
        GeneticCorrectionResult result = new GeneticConsistencyCorrector().correct(matrix);
        assertEquals(ALREADY_ACCEPTABLE, result.status());
        assertSame(matrix, result.original());
        assertSame(matrix, result.best());
        assertEquals(0, result.generations());
        assertEquals(1, result.evaluatedIndividuals());
        assertEquals(0, result.changedComparisons());
        assertEquals(1, result.history().size());
        assertEquals(1, result.history().get(0).populationSize());
        assertEquals(0, result.seed());
    }

    @Test
    public void targetIsStrictSoEqualityDoesNotMeanAlreadyAcceptable() {
        PairwiseComparisonMatrix matrix = new PairwiseComparisonMatrix(ComparisonFixtures.paperOriginal());
        double k = ConsistencyMetrics.koczkodajIndex(matrix).value();
        var options = new GeneticCorrectionOptions(20, 0, k, 1.0 / 9, 9, 0, 64);
        GeneticCorrectionResult result = new GeneticConsistencyCorrector(options).correct(matrix);
        assertNotEquals(ALREADY_ACCEPTABLE, result.status());
        assertEquals(20, result.evaluatedIndividuals());
    }

    @Test
    public void zeroGenerationLimitStillEvaluatesTheInitialPopulation() {
        var options = new GeneticCorrectionOptions(20, 0, 0, 1.0 / 9, 9, 0, 64);
        GeneticCorrectionResult result = new GeneticConsistencyCorrector(options)
                .correct(new PairwiseComparisonMatrix(ComparisonFixtures.paperOriginal()));
        assertEquals(MAX_GENERATIONS, result.status());
        assertEquals(0, result.generations());
        assertEquals(20, result.evaluatedIndividuals());
        assertEquals(1, result.history().size());
        assertTrue(result.finalK() <= result.initialK());
    }

    @Test
    public void targetCanBeReachedInGenerationZeroDuringInitialization() {
        var options = new GeneticCorrectionOptions(1000, 0, 0.5, 1.0 / 9, 9, 0, 64);
        GeneticCorrectionResult result = new GeneticConsistencyCorrector(options)
                .correct(new PairwiseComparisonMatrix(ComparisonFixtures.paperOriginal()));
        assertEquals(TARGET_REACHED, result.status());
        assertEquals(0, result.generations());
        assertEquals(1000, result.evaluatedIndividuals());
        assertTrue(result.finalK() < 0.5);
    }

    @Test
    public void generationLimitAndPopulationStatisticsHaveExactCounts() {
        var options = new GeneticCorrectionOptions(23, 4, 0, 1.0 / 9, 9, 42, 64);
        GeneticCorrectionResult result = new GeneticConsistencyCorrector(options)
                .correct(new PairwiseComparisonMatrix(ComparisonFixtures.paperOriginal()));
        assertEquals(MAX_GENERATIONS, result.status());
        assertEquals(4, result.generations());
        assertEquals(5, result.history().size());
        assertEquals(23 + 4L * options.offspringCount(), result.evaluatedIndividuals());
        checkHistory(result);
    }

    @Test
    public void fixedSeedsReachThePaperThresholdWithoutUsingThePublishedAnswer() {
        for (long seed : new long[] {0, 1, 42}) {
            PairwiseComparisonMatrix matrix = new PairwiseComparisonMatrix(ComparisonFixtures.paperOriginal());
            GeneticCorrectionResult result = new GeneticConsistencyCorrector(paperOptions(1000, seed)).correct(matrix);
            assertEquals("seed=" + seed, TARGET_REACHED, result.status());
            assertEquals(0.55, result.initialK(), 1e-14);
            assertEquals(0.0245317176445452, result.initialCi(), 1e-12);
            assertTrue(result.finalK() < 0.3);
            assertTrue(result.finalK() < result.initialK());
            assertTrue(result.generations() <= 100);
            assertEquals(ConsistencyMetrics.koczkodajIndex(result.best()).value(), result.finalK(), 0);
            assertEquals(ConsistencyMetrics.saatyIndex(result.best()), result.finalCi(), 1e-12);
            assertMatrixValid(result.best(), result.options());
            checkHistory(result);
        }
    }

    @Test
    public void seedAndAllRunStateAreResetOnEveryInvocation() {
        var options = paperOptions(1000, 42);
        GeneticConsistencyCorrector corrector = new GeneticConsistencyCorrector(options);
        PairwiseComparisonMatrix matrix = new PairwiseComparisonMatrix(ComparisonFixtures.paperOriginal());
        GeneticCorrectionResult first = corrector.correct(matrix);
        GeneticCorrectionResult second = corrector.correct(matrix);
        assertEquals(first.status(), second.status());
        assertEquals(first.seed(), second.seed());
        assertEquals(first.history(), second.history());
        assertEquals(first.finalK(), second.finalK(), 0);
        assertEquals(first.finalCi(), second.finalCi(), 0);
        assertEquals(first.evaluatedIndividuals(), second.evaluatedIndividuals());
        for (int i = 0; i < matrix.size(); i++) {
            assertArrayEquals(first.best().toArray()[i], second.best().toArray()[i], 0);
        }
        assertThrows(UnsupportedOperationException.class, first.history()::clear);
    }

    @Test
    public void preservesInputAndCountsOnlyChangedIndependentComparisons() {
        double[][] source = ComparisonFixtures.paperOriginal();
        PairwiseComparisonMatrix matrix = new PairwiseComparisonMatrix(source);
        GeneticCorrectionResult result = new GeneticConsistencyCorrector(paperOptions(1000, 0)).correct(matrix);
        assertSame(matrix, result.original());
        int count = 0;
        for (int i = 0; i < matrix.size(); i++) {
            assertArrayEquals(source[i], matrix.toArray()[i], 0);
            for (int j = i + 1; j < matrix.size(); j++) {
                if (matrix.get(i, j) != result.best().get(i, j)) {
                    count++;
                }
            }
        }
        assertEquals(count, result.changedComparisons());
        assertTrue(count > 0 && count <= 6);
    }

    @Test
    public void defaultScaleRejectsThePaperInputInsteadOfClippingEightAndNine() {
        PairwiseComparisonMatrix matrix = new PairwiseComparisonMatrix(ComparisonFixtures.paperOriginal());
        var error = assertThrows(IllegalArgumentException.class, () -> new GeneticConsistencyCorrector().correct(matrix));
        assertTrue(error.getMessage(), error.getMessage().contains("outside"));
        assertEquals(9, matrix.get(0, 3), 0);
        assertEquals(8, matrix.get(1, 3), 0);
    }

    @Test
    public void inputRoundingCannotHideAnOutOfRangeReconstructedReciprocal() {
        double minimum = Math.nextUp(1.0 / 7);
        PairwiseComparisonMatrix rounded = new PairwiseComparisonMatrix(new double[][] {{1, 7}, {minimum, 1}});
        var options = new GeneticCorrectionOptions(20, 100, 0.3, minimum, 7, 0, 64);
        var error = assertThrows(IllegalArgumentException.class, () -> new GeneticConsistencyCorrector(options).correct(rounded));
        assertTrue(error.getMessage(), error.getMessage().contains("reconstructed reciprocal"));
    }

    @Test
    public void lockedRangeReturnsNoAdmissibleChangesWithAFixedSizePopulation() {
        var options = new GeneticCorrectionOptions(20, 10, 0, 1, 1, 0, 4);
        GeneticCorrectionResult result = new GeneticConsistencyCorrector(options).correct(ComparisonFixtures.triad(1, 1, 1));
        assertEquals(NO_ADMISSIBLE_CHANGES, result.status());
        assertEquals(0, result.generations());
        assertEquals(20, result.evaluatedIndividuals());
        assertEquals(19, result.history().get(0).failedMutations());
        assertEquals(0, result.changedComparisons());
        checkHistory(result);
    }

    @Test
    public void finitePrecisionCanMakeTheMutationStepUnableToChangeAnyGene() {
        var options = new GeneticCorrectionOptions(10, 10, 0.3, 1e-30, 1e30, 0, 4);
        GeneticCorrectionResult result = new GeneticConsistencyCorrector(options).correct(ComparisonFixtures.triad(1e20, 1e20, 1e20));
        assertEquals(NO_ADMISSIBLE_CHANGES, result.status());
        assertEquals(0, result.changedComparisons());
        assertTrue(result.finalK() >= 0.3);
    }

    @Test
    public void retryExhaustionIsNotMisreportedAsProofThatNoChangesExist() {
        var options = new GeneticCorrectionOptions(2, 10, 0, Math.nextDown(1.0), Math.nextUp(1.0), 0, 1);
        PairwiseComparisonMatrix matrix = ComparisonFixtures.triad(1, 1, 1);
        assertTrue(GeneticOperators.canMutate(GeneticChromosome.fromMatrix(matrix), options));
        GeneticCorrectionResult result = new GeneticConsistencyCorrector(options).correct(matrix);
        assertEquals(MUTATION_ATTEMPTS_EXHAUSTED, result.status());
        assertEquals(2, result.evaluatedIndividuals());
        assertEquals(0, result.changedComparisons());
    }

    @Test
    public void smallOddPopulationsKeepTheirSizeAndAtLeastOneElite() {
        for (int size : new int[] {2, 3, 5, 11}) {
            var options = new GeneticCorrectionOptions(size, 2, 0, 1.0 / 9, 9, 7, 64);
            GeneticCorrectionResult result = new GeneticConsistencyCorrector(options)
                    .correct(new PairwiseComparisonMatrix(ComparisonFixtures.paperOriginal()));
            assertEquals(MAX_GENERATIONS, result.status());
            checkHistory(result);
        }
    }

    @Test
    public void orderTwoHasZeroKAndReturnsWithoutEvolution() {
        PairwiseComparisonMatrix matrix = new PairwiseComparisonMatrix(new double[][] {{1, 3}, {1.0 / 3, 1}});
        var result = new GeneticConsistencyCorrector().correct(matrix);
        assertEquals(ALREADY_ACCEPTABLE, result.status());
        assertEquals(0, result.finalK(), 0);
        assertEquals(0, result.finalCi(), 1e-12);
    }

    @Test
    public void rejectsInvalidOptionsAndNullInputs() {
        assertThrows(IllegalArgumentException.class, () -> new GeneticConsistencyCorrector(null));
        assertThrows(IllegalArgumentException.class, () -> new GeneticConsistencyCorrector().correct(null));
        assertThrows(IllegalArgumentException.class, () -> new GeneticCorrectionOptions(1, 100, 0.3, 0.1, 10, 0, 64));
        assertThrows(IllegalArgumentException.class, () -> new GeneticCorrectionOptions(20, -1, 0.3, 0.1, 10, 0, 64));
        for (double target : new double[] {-1, 1.1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> new GeneticCorrectionOptions(20, 100, target, 0.1, 10, 0, 64));
        }
        assertThrows(IllegalArgumentException.class, () -> new GeneticCorrectionOptions(20, 100, 0.3, 0, 10, 0, 64));
        assertThrows(IllegalArgumentException.class, () -> new GeneticCorrectionOptions(20, 100, 0.3, 2, 10, 0, 64));
        assertThrows(IllegalArgumentException.class, () -> new GeneticCorrectionOptions(20, 100, 0.3, 0.1, 0.5, 0, 64));
        assertThrows(IllegalArgumentException.class, () -> new GeneticCorrectionOptions(20, 100, 0.3, 0.1, Double.POSITIVE_INFINITY, 0, 64));
        assertThrows(IllegalArgumentException.class, () -> new GeneticCorrectionOptions(20, 100, 0.3, 0.1, 10, 0, 0));
    }

    private static void checkHistory(GeneticCorrectionResult result) {
        assertEquals(result.generations() + 1, result.history().size());
        double previous = result.initialK();
        for (int i = 0; i < result.history().size(); i++) {
            var summary = result.history().get(i);
            assertEquals(i, summary.generation());
            assertEquals(result.options().populationSize(), summary.populationSize());
            assertEquals(summary.populationSize(), summary.retained() + summary.unmutatedOffspring() + summary.mutationSlots());
            assertEquals(summary.mutationSlots(), summary.successfulMutations() + summary.failedMutations());
            assertTrue(summary.bestK() <= previous);
            previous = summary.bestK();
            assertEquals(result.options().populationSize() + (long) i * result.options().offspringCount(), summary.evaluatedIndividuals());
            if (i == 0) {
                assertEquals(1, summary.retained());
                assertEquals(0, summary.unmutatedOffspring());
                assertEquals(result.options().populationSize() - 1, summary.mutationSlots());
            } else {
                assertEquals(result.options().eliteCount(), summary.retained());
                assertEquals(result.options().unmutatedOffspringCount(), summary.unmutatedOffspring());
                assertEquals(result.options().mutatedOffspringCount(), summary.mutationSlots());
            }
        }
        var last = result.history().get(result.history().size() - 1);
        assertEquals(result.finalK(), last.bestK(), 0);
        assertEquals(result.evaluatedIndividuals(), last.evaluatedIndividuals());
    }
}
