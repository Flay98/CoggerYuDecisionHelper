package MCD.consistency.experiments;

import MCD.consistency.ConsistencyMetrics;
import MCD.consistency.PairwiseComparisonMatrix;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

public class MatrixGeneratorTest {
    static final MatrixGenerator.Parameters PARAMETERS = new MatrixGenerator.Parameters(.4, 3, Math.log(3), 64);

    @Test public void allSizesAndTypesAreValidBoundedReciprocalMatrices() {
        for (int n = 3; n <= 10; n++) for (var type : MatrixGenerator.Type.values()) {
            var matrix = new MatrixGenerator().generate(type, n, MatrixGenerator.seed(20261123, type, n, 0), PARAMETERS).matrix();
            assertEquals(n, matrix.size());
            new PairwiseComparisonMatrix(matrix.toArray());
            for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) {
                assertTrue(Double.isFinite(matrix.get(i,j)));
                assertTrue(MatrixGenerator.inRange(matrix.get(i,j)));
                if (i == j) assertEquals(1, matrix.get(i,j), 0);
                assertEquals(1, matrix.get(i,j) * matrix.get(j,i), 2e-16);
            }
        }
    }

    @Test public void discreteUpperGenesAreExactlyFromTheDeclaredSeventeenValues() {
        Set<Double> scale = new HashSet<>(List.of(1d));
        for (int value = 2; value <= 9; value++) { scale.add((double)value); scale.add(1d/value); }
        Set<Double> observed = new HashSet<>();
        for (int seed = 0; seed < 10; seed++) {
            var matrix = new MatrixGenerator().generate(MatrixGenerator.Type.DISCRETE_EXPERT, 10, seed, PARAMETERS).matrix();
            for (int i = 0; i < 10; i++) for (int j = i+1; j < 10; j++) {
                assertTrue(scale.contains(matrix.get(i,j))); observed.add(matrix.get(i,j));
            }
        }
        assertEquals(scale, observed);
    }

    @Test public void sameSeedReproducesMatrixAndPerturbationWitnessExactly() {
        for (var type : MatrixGenerator.Type.values()) {
            var generator = new MatrixGenerator();
            var a = generator.generate(type, 7, 1234, PARAMETERS);
            var b = generator.generate(type, 7, 1234, PARAMETERS);
            assertArrayEquals(a.matrix().toArray(), b.matrix().toArray());
            assertEquals(a.latentWeights(), b.latentWeights()); assertEquals(a.perturbations(), b.perturbations());
        }
    }

    @Test public void perturbedInputsCanBeReconstructedFromConsistentLatentWeights() {
        int n = 8;
        var generated = new MatrixGenerator().generate(MatrixGenerator.Type.PERTURBED_CONSISTENT, n, 55, PARAMETERS);
        double[][] baseline = new double[n][n];
        for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) baseline[i][j] = generated.latentWeights().get(i)/generated.latentWeights().get(j);
        assertEquals(0, ConsistencyMetrics.saatyIndex(new PairwiseComparisonMatrix(baseline)), 1e-12);
        assertEquals(0, ConsistencyMetrics.koczkodajIndex(new PairwiseComparisonMatrix(baseline)).value(), 1e-12);
        assertEquals((int)Math.ceil(.4*n*(n-1)/2), generated.perturbations().size());
        Set<String> selected = new HashSet<>();
        for (var change : generated.perturbations()) {
            assertTrue(selected.add(change.i()+":"+change.j()));
            assertTrue(change.i()<change.j());
            assertEquals(baseline[change.i()][change.j()], change.before(), 0);
            assertTrue(Math.abs(change.epsilon()) <= Math.log(3));
            assertEquals(change.before()*Math.exp(change.epsilon()), change.after(), 0);
            assertTrue(change.attempts()>=1 && change.attempts()<=64);
            baseline[change.i()][change.j()] = change.after(); baseline[change.j()][change.i()] = 1/change.after();
        }
        // Reconstruct unaffected lower elements by the same reciprocal operation.
        for (int i=0;i<n;i++) for (int j=i+1;j<n;j++) baseline[j][i]=1/baseline[i][j];
        assertArrayEquals(baseline, generated.matrix().toArray());
    }

    @Test public void tupleSeedsAndIdsAreUniqueAcrossTheReportPlan() throws Exception {
        var profile = BenchmarkRunner.loadProfile(java.nio.file.Path.of("experiments/comparison"), "REPORT");
        var cases = ExperimentPlan.inputs(profile);
        assertEquals(64, cases.size()); assertEquals(448, profile.runCount());
        assertEquals(cases.size(), cases.stream().map(InputRepository.Case::seed).distinct().count());
        assertEquals(cases.size(), cases.stream().map(InputRepository.Case::id).distinct().count());
        var schedule = ExperimentPlan.schedule(profile);
        assertEquals(profile.runCount(), schedule.size());
        assertEquals(schedule.size(), schedule.stream().map(ExperimentPlan.Scheduled::runId).distinct().count());
        for (var input : cases) {
            var jobs = schedule.stream().filter(s -> s.input().id().equals(input.id())).toList();
            assertEquals(7, jobs.size());
            assertTrue(jobs.stream().allMatch(s -> s.input().equals(input)));
            for (var config : ExperimentPlan.Configuration.values()) {
                var seeds = jobs.stream().filter(s -> s.configuration()==config).map(ExperimentPlan.Scheduled::seed).toList();
                if (config==ExperimentPlan.Configuration.COSINE) assertEquals(1, seeds.size());
                else assertEquals(new HashSet<>(profile.gaSeeds()), new HashSet<>(seeds));
            }
        }
    }

    @Test public void profilesRemainSeparateAndInitialReportPlanIsPreserved() throws Exception {
        var root = java.nio.file.Path.of("experiments/comparison");
        assertEquals(java.nio.file.Files.readString(root.resolve("config/REPORT.initial.json")), java.nio.file.Files.readString(root.resolve("config/REPORT.json")));
        assertEquals(12, BenchmarkRunner.loadProfile(root,"SMOKE").runCount());
        assertEquals(18, BenchmarkRunner.loadProfile(root,"PILOT").runCount());
        assertEquals(1760, BenchmarkRunner.loadProfile(root,"FULL").runCount());
        assertThrows(IllegalArgumentException.class, () -> new BenchmarkProfile("SMOKE", List.of(3,3), 1, List.of(42L), 1, 10,1,1,0,3,10,10,PARAMETERS));
    }

    @Test public void invalidGeneratorRequestsAndImpossibleLatentBoundsFailExplicitly() {
        assertThrows(IllegalArgumentException.class, () -> new MatrixGenerator.Parameters(.4,3,Double.NaN,64));
        assertThrows(IllegalArgumentException.class, () -> new MatrixGenerator().generate(MatrixGenerator.Type.DISCRETE_EXPERT,1,0,PARAMETERS));
        assertThrows(IllegalArgumentException.class, () -> MatrixGenerator.seed(1,MatrixGenerator.Type.DISCRETE_EXPERT,3,-1));
        assertThrows(IllegalArgumentException.class, () -> new MatrixGenerator().generate(MatrixGenerator.Type.PERTURBED_CONSISTENT,10,0,new MatrixGenerator.Parameters(.4,1e200,1,64)));
    }
}
