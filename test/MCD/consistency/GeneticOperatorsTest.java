package MCD.consistency;

import org.junit.Test;

import java.util.List;
import java.util.Random;

import static org.junit.Assert.*;

public class GeneticOperatorsTest {
    @Test
    public void chromosomeStoresOnlyUpperTriangleAndReconstructsReciprocals() {
        double[] genes = {2, 5, 9, 3, 8, 4};
        GeneticChromosome chromosome = GeneticChromosome.fromGenes(4, genes);
        assertEquals(6, chromosome.length());
        assertEquals(4, chromosome.size());
        assertArrayEquals(genes, chromosome.genes(), 0);
        assertMatrixValid(chromosome.matrix(), paperOptions(40, 0));
        assertEquals(2, chromosome.matrix().get(0, 1), 0);
        assertEquals(4, chromosome.matrix().get(2, 3), 0);
        assertEquals(1.0 / 9, chromosome.matrix().get(3, 0), 0);
        genes[0] = 100;
        chromosome.genes()[0] = 200;
        assertEquals(2, chromosome.gene(0), 0);
    }

    @Test
    public void preservesActualInputAsTheOriginalPhenotype() {
        PairwiseComparisonMatrix original = new PairwiseComparisonMatrix(ComparisonFixtures.paperRounded(), 1e-4);
        assertSame(original, GeneticChromosome.fromMatrix(original).matrix());
        assertEquals(0.7714, GeneticChromosome.fromMatrix(original).matrix().get(1, 0), 0);
    }

    @Test
    public void rejectsInvalidGenesAndNonUnitDiagonalsWithoutNormalizingInput() {
        assertThrows(IllegalArgumentException.class, () -> GeneticChromosome.fromGenes(3, new double[] {1, 2}));
        for (double value : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY, Double.MIN_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> GeneticChromosome.fromGenes(2, new double[] {value}));
        }
        PairwiseComparisonMatrix diagonal = new PairwiseComparisonMatrix(new double[][] {{1 + 1e-12, 1}, {1, 1}});
        assertThrows(IllegalArgumentException.class, () -> GeneticChromosome.fromMatrix(diagonal));
    }

    @Test
    public void initialPopulationRetainsInputAndMutatesExactlyOneGenePerOtherMember() {
        GeneticCorrectionOptions options = paperOptions(40, 17);
        GeneticChromosome input = GeneticChromosome.fromMatrix(new PairwiseComparisonMatrix(ComparisonFixtures.paperOriginal()));
        GeneticOperators.Batch initial = GeneticOperators.initialPopulation(input, options, new Random(options.seed()));
        assertEquals(40, initial.chromosomes().size());
        assertSame(input, initial.chromosomes().get(0));
        assertEquals(39, initial.successfulMutations());
        assertEquals(0, initial.failedMutations());
        for (int i = 1; i < initial.chromosomes().size(); i++) {
            GeneticChromosome mutant = initial.chromosomes().get(i);
            assertEquals(1, differences(input, mutant));
            assertMatrixValid(mutant.matrix(), options);
            for (int gene = 0; gene < input.length(); gene++) {
                assertTrue(Math.abs(input.gene(gene) - mutant.gene(gene)) <= 0.5);
            }
        }
    }

    @Test
    public void fitnessIsExactlyTheExistingKoczkodajIndex() {
        GeneticChromosome chromosome = GeneticChromosome.fromMatrix(new PairwiseComparisonMatrix(ComparisonFixtures.paperOriginal()));
        double fitness = GeneticOperators.evaluate(chromosome).fitness();
        assertEquals(0.55, fitness, 1e-14);
        assertEquals(ConsistencyMetrics.koczkodajIndex(chromosome.matrix()).value(), fitness, 0);
        assertNotEquals(ConsistencyMetrics.saatyIndex(chromosome.matrix()), fitness, 1e-3);
    }

    @Test
    public void selectionKeepsTheLowestFitnessAndPreservesTieOrder() {
        var good = GeneticOperators.evaluate(GeneticChromosome.fromMatrix(new PairwiseComparisonMatrix(ComparisonFixtures.consistent())));
        var medium = GeneticOperators.evaluate(GeneticChromosome.fromMatrix(new PairwiseComparisonMatrix(ComparisonFixtures.inconsistent())));
        var bad = GeneticOperators.evaluate(GeneticChromosome.fromMatrix(ComparisonFixtures.triad(3, 2, 1)));
        var tie = GeneticOperators.evaluate(GeneticChromosome.fromMatrix(ComparisonFixtures.triad(1, 1, 1)));
        List<GeneticOperators.Individual> population = List.of(bad, good, medium, tie);
        List<GeneticOperators.Individual> selected = GeneticOperators.selectBest(population, 3);
        assertEquals(List.of(good, tie, medium), selected);
        assertSame(bad, population.get(0));
        assertThrows(UnsupportedOperationException.class, selected::clear);
    }

    @Test
    public void uniformCrossoverInheritsWholeGenesAndCannotModifyParents() {
        GeneticChromosome first = GeneticChromosome.fromGenes(3, new double[] {2, 3, 4});
        GeneticChromosome second = GeneticChromosome.fromGenes(3, new double[] {5, 6, 7});
        Random alternating = new Random(0) {
            private boolean takeFirst;
            @Override public boolean nextBoolean() {
                takeFirst = !takeFirst;
                return takeFirst;
            }
        };
        GeneticChromosome child = GeneticOperators.crossover(first, second, alternating);
        assertArrayEquals(new double[] {2, 6, 4}, child.genes(), 0);
        assertArrayEquals(new double[] {2, 3, 4}, first.genes(), 0);
        assertArrayEquals(new double[] {5, 6, 7}, second.genes(), 0);
        assertMatrixValid(child.matrix(), GeneticCorrectionOptions.defaults(0));
    }

    @Test
    public void mutationAddsDeltaToOneGeneAndRestoresItsReciprocal() {
        GeneticChromosome parent = GeneticChromosome.fromGenes(3, new double[] {2, 3, 4});
        var result = GeneticOperators.mutate(parent, paperOptions(20, 0), scripted(0.75));
        assertTrue(result.changed());
        assertEquals(1, result.attempts());
        assertArrayEquals(new double[] {2.25, 3, 4}, result.chromosome().genes(), 0);
        assertEquals(1 / 2.25, result.chromosome().matrix().get(1, 0), 0);
        assertEquals(1, differences(parent, result.chromosome()));
    }

    @Test
    public void rejectsInvalidMutationAndResamplesWithoutClipping() {
        GeneticChromosome parent = GeneticChromosome.fromGenes(2, new double[] {0.2});
        var result = GeneticOperators.mutate(parent, GeneticCorrectionOptions.defaults(0), scripted(0, 0.75));
        assertTrue(result.changed());
        assertEquals(2, result.attempts());
        assertEquals(0.45, result.chromosome().gene(0), 0);
        parent = GeneticChromosome.fromGenes(2, new double[] {7});
        result = GeneticOperators.mutate(parent, GeneticCorrectionOptions.defaults(0), scripted(0.75, 0.25));
        assertEquals(6.75, result.chromosome().gene(0), 0);
        assertEquals(2, result.attempts());
    }

    @Test
    public void mutationChecksTheReciprocalAgainstAsymmetricBounds() {
        GeneticCorrectionOptions options = new GeneticCorrectionOptions(20, 1, 0.3, 0.01, 2, 0, 4);
        GeneticChromosome parent = GeneticChromosome.fromGenes(2, new double[] {0.5});
        // 0.25 is inside the numeric interval, but its reciprocal 4 is outside.
        var result = GeneticOperators.mutate(parent, options, scripted(0.25, 0.75));
        assertEquals(2, result.attempts());
        assertEquals(0.75, result.chromosome().gene(0), 0);
        assertMatrixValid(result.chromosome().matrix(), options);
    }

    @Test
    public void boundedRetriesKeepAnUnchangedParentAndReportFailureHonestly() {
        GeneticChromosome parent = GeneticChromosome.fromGenes(2, new double[] {2});
        GeneticCorrectionOptions options = new GeneticCorrectionOptions(20, 1, 0.3, 1.0 / 7, 7, 0, 3);
        var result = GeneticOperators.mutate(parent, options, scripted(0.5, 0.5, 0.5));
        assertFalse(result.changed());
        assertEquals(3, result.attempts());
        assertSame(parent, result.chromosome());
    }

    @Test
    public void detectsLockedRangesAndChangesLostToDoublePrecision() {
        GeneticChromosome ones = GeneticChromosome.fromGenes(3, new double[] {1, 1, 1});
        GeneticCorrectionOptions locked = new GeneticCorrectionOptions(20, 1, 0, 1, 1, 0, 3);
        assertFalse(GeneticOperators.canMutate(ones, locked));
        var mutation = GeneticOperators.mutate(ones, locked, new Random(0));
        assertFalse(mutation.changed());
        assertEquals(0, mutation.attempts());
        GeneticChromosome huge = GeneticChromosome.fromGenes(3, new double[] {1e20, 1e20, 1e20});
        assertFalse(GeneticOperators.canMutate(huge, new GeneticCorrectionOptions(20, 1, 0.3, 1e-30, 1e30, 0, 3)));
    }

    @Test
    public void paperFractionsAndRoundingAlwaysProduceAnExactPopulationSize() {
        GeneticCorrectionOptions normal = GeneticCorrectionOptions.defaults(0);
        assertEquals(500, normal.selectedCount());
        assertEquals(100, normal.eliteCount());
        assertEquals(900, normal.offspringCount());
        assertEquals(90, normal.unmutatedOffspringCount());
        assertEquals(810, normal.mutatedOffspringCount());
        for (int size : new int[] {2, 3, 5, 11, 23, 101, 1000}) {
            GeneticCorrectionOptions options = paperOptions(size, 0);
            assertEquals(size / 2, options.selectedCount());
            assertTrue(options.eliteCount() >= 1);
            assertTrue(options.eliteCount() <= options.selectedCount());
            assertEquals(size, options.eliteCount() + options.unmutatedOffspringCount() + options.mutatedOffspringCount());
        }
    }

    @Test
    public void offspringBatchHasNinetyUnmutatedAndEightHundredTenMutatedChildrenAtDefaultSize() {
        GeneticChromosome parent = GeneticChromosome.fromGenes(3, new double[] {1, 1, 1});
        GeneticOperators.Batch batch = GeneticOperators.offspring(List.of(GeneticOperators.evaluate(parent)),
                GeneticCorrectionOptions.defaults(0), new Random(0));
        assertEquals(900, batch.chromosomes().size());
        assertEquals(810, batch.successfulMutations());
        assertEquals(0, batch.failedMutations());
        for (int i = 0; i < 900; i++) {
            assertEquals(i < 90 ? 0 : 1, differences(parent, batch.chromosomes().get(i)));
            assertMatrixValid(batch.chromosomes().get(i).matrix(), GeneticCorrectionOptions.defaults(0));
        }
    }

    static GeneticCorrectionOptions paperOptions(int populationSize, long seed) {
        return new GeneticCorrectionOptions(populationSize, 100, 0.3, 1.0 / 9, 9, seed, 64);
    }

    static void assertMatrixValid(PairwiseComparisonMatrix matrix, GeneticCorrectionOptions options) {
        for (int i = 0; i < matrix.size(); i++) {
            assertEquals(1, matrix.get(i, i), 0);
            for (int j = 0; j < matrix.size(); j++) {
                assertTrue(Double.isFinite(matrix.get(i, j)) && matrix.get(i, j) > 0);
                assertTrue(options.contains(matrix.get(i, j)));
                assertEquals(1, matrix.get(i, j) * matrix.get(j, i), 1e-14);
            }
        }
        new PairwiseComparisonMatrix(matrix.toArray());
    }

    private static int differences(GeneticChromosome first, GeneticChromosome second) {
        int differences = 0;
        for (int i = 0; i < first.length(); i++) {
            if (first.gene(i) != second.gene(i)) {
                differences++;
            }
        }
        return differences;
    }

    private static Random scripted(double... values) {
        return new Random(0) {
            private int index;
            @Override public int nextInt(int bound) { return 0; }
            @Override public double nextDouble() { return values[index++]; }
        };
    }
}
