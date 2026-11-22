package MCD.consistency;

import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.*;

public class GeneticMutationModeTest {
    @Test
    public void lowerMutationChangesTheLowerValueBeforeTakingTheReciprocal() {
        var parent = GeneticChromosome.fromGenes(2, new double[] {4});
        var mutation = GeneticOperators.mutate(parent, options(GeneticMutationMode.BIDIRECTIONAL), scripted(true, 0.75));
        assertTrue(mutation.changed());
        assertEquals(0.5, mutation.chromosome().matrix().get(1, 0), 0); // 0.25 + 0.25
        assertEquals(2, mutation.chromosome().gene(0), 0);             // 1/0.5, NOT 4+0.25
        assertEquals(4, parent.gene(0), 0);
    }

    @Test
    public void bidirectionalUpperBranchStillAddsToTheUpperEntry() {
        var mutation = GeneticOperators.mutate(GeneticChromosome.fromGenes(2, new double[] {4}),
                options(GeneticMutationMode.BIDIRECTIONAL), scripted(false, 0.75));
        assertEquals(4.25, mutation.chromosome().gene(0), 0);
        assertEquals(1 / 4.25, mutation.chromosome().matrix().get(1, 0), 0);
    }

    @Test
    public void upperOnlyDoesNotConsumeAnOrientationRandomDraw() {
        Random random = new Random(0) {
            @Override public int nextInt(int bound) { return 0; }
            @Override public double nextDouble() { return 0.75; }
            @Override public boolean nextBoolean() { throw new AssertionError("Old mode must not draw an orientation"); }
        };
        var mutation = GeneticOperators.mutate(GeneticChromosome.fromGenes(2, new double[] {4}),
                options(GeneticMutationMode.UPPER_ONLY), random);
        assertEquals(4.25, mutation.chromosome().gene(0), 0);
    }

    @Test
    public void lowerMutationRejectsInvalidValuesAndNeverClips() {
        Random random = new Random(0) {
            private int call;
            @Override public int nextInt(int bound) { return 0; }
            @Override public boolean nextBoolean() { return true; }
            @Override public double nextDouble() { return call++ == 0 ? 0 : 0.75; }
        };
        var mutation = GeneticOperators.mutate(GeneticChromosome.fromGenes(2, new double[] {4}),
                options(GeneticMutationMode.BIDIRECTIONAL), random);
        assertEquals(2, mutation.attempts()); // 0.25 - 0.5 rejected, then 0.25 + 0.25 accepted
        assertEquals(2, mutation.chromosome().gene(0), 0);
    }

    @Test
    public void lowerOrientationCanChangeAGeneThatIsTooLargeForUpperAdditiveMutation() {
        var chromosome = GeneticChromosome.fromGenes(2, new double[] {1e20});
        var upper = new GeneticCorrectionOptions(20, 100, 0.3, 1e-30, 1e30, 0, 64, GeneticMutationMode.UPPER_ONLY);
        var both = new GeneticCorrectionOptions(20, 100, 0.3, 1e-30, 1e30, 0, 64, GeneticMutationMode.BIDIRECTIONAL);
        assertFalse(GeneticOperators.canMutate(chromosome, upper));
        assertTrue(GeneticOperators.canMutate(chromosome, both));
        assertEquals(4, GeneticOperators.mutate(chromosome, both, scripted(true, 0.75)).chromosome().gene(0), 1e-14);
    }

    @Test
    public void oldSeed42RegressionAndNewModeReproducibility() {
        var input = new PairwiseComparisonMatrix(ComparisonFixtures.paperOriginal());
        var old = new GeneticConsistencyCorrector(options(GeneticMutationMode.UPPER_ONLY)).correct(input);
        assertEquals(5, old.generations());
        assertEquals(0.23707268042400265, old.finalK(), 0);
        var corrector = new GeneticConsistencyCorrector(options(GeneticMutationMode.BIDIRECTIONAL));
        var first = corrector.correct(input);
        var second = corrector.correct(input);
        assertEquals(first.history(), second.history());
        assertEquals(first.finalK(), second.finalK(), 0);
        assertTrue(first.finalK() < 0.3);
        for (int i = 0; i < input.size(); i++) {
            assertArrayEquals(first.best().toArray()[i], second.best().toArray()[i], 0);
        }
        assertEquals(GeneticMutationMode.UPPER_ONLY, GeneticCorrectionOptions.defaults(42).mutationMode());
        assertThrows(IllegalArgumentException.class, () -> new GeneticCorrectionOptions(20, 100, 0.3, 1.0 / 9, 9, 42, 64, null));
    }

    private static GeneticCorrectionOptions options(GeneticMutationMode mode) {
        return new GeneticCorrectionOptions(1000, 100, 0.3, 1.0 / 9, 9, 42, 64, mode);
    }

    private static Random scripted(boolean lower, double value) {
        return new Random(0) {
            @Override public int nextInt(int bound) { return 0; }
            @Override public boolean nextBoolean() { return lower; }
            @Override public double nextDouble() { return value; }
        };
    }
}
