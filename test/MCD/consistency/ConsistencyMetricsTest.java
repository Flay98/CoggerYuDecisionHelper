package MCD.consistency;

import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.*;

public class ConsistencyMetricsTest {
    @Test
    public void consistentControlMatrixHasZeroIndices() {
        PairwiseComparisonMatrix matrix = new PairwiseComparisonMatrix(ComparisonFixtures.consistent());
        assertEquals(3, ConsistencyMetrics.principalEigenvalue(matrix), 1e-12);
        assertEquals(0, ConsistencyMetrics.saatyIndex(matrix), 1e-12);
        KoczkodajResult result = ConsistencyMetrics.koczkodajIndex(matrix);
        assertEquals(0, result.value(), 1e-15);
        assertTriad(result, 0, 1, 2);
    }

    @Test
    public void inconsistentControlMatrixMatchesAnalyticalEigenvalue() {
        PairwiseComparisonMatrix matrix = new PairwiseComparisonMatrix(ComparisonFixtures.inconsistent());
        // For this reciprocal 3x3 matrix: lambda = 1 + cbrt(r) + cbrt(1/r), r = 3*2/4.
        double lambda = 1 + Math.cbrt(1.5) + Math.cbrt(1 / 1.5);
        assertEquals(lambda, ConsistencyMetrics.principalEigenvalue(matrix), 1e-12);
        assertEquals(0.009147, ConsistencyMetrics.saatyIndex(matrix), 1e-6);
        assertEquals((lambda - 3) / 2, ConsistencyMetrics.saatyIndex(matrix), 1e-12);
        KoczkodajResult result = ConsistencyMetrics.koczkodajIndex(matrix);
        assertEquals(1.0 / 3, result.value(), 1e-14);
        assertTriad(result, 0, 1, 2);
    }

    @Test
    public void originalSayadiJanickiExampleMatchesPublishedK() {
        KoczkodajResult result = ConsistencyMetrics.koczkodajIndex(
                new PairwiseComparisonMatrix(ComparisonFixtures.paperOriginal()));
        assertEquals(0.55, result.value(), 1e-14);
        assertTriad(result, 0, 2, 3);
    }

    @Test
    public void roundedPublishedCorrectionMatchesPublishedK() {
        PairwiseComparisonMatrix matrix = new PairwiseComparisonMatrix(ComparisonFixtures.paperRounded(), 1e-4);
        KoczkodajResult result = ConsistencyMetrics.koczkodajIndex(matrix);
        assertEquals(0.2317, result.value(), 1e-12);
        assertTriad(result, 1, 2, 3);
        assertTrue(ConsistencyMetrics.saatyIndex(matrix) > 0);
    }

    @Test
    public void reciprocalReconstructionOfPublishedUpperTriangleHasSameK() {
        double[][] data = ComparisonFixtures.paperRounded();
        for (int i = 0; i < data.length; i++) {
            for (int j = i + 1; j < data.length; j++) {
                data[j][i] = 1 / data[i][j];
            }
        }
        assertEquals(0.2317, ConsistencyMetrics.koczkodajIndex(new PairwiseComparisonMatrix(data)).value(), 1e-12);
    }

    @Test
    public void orderTwoHasZeroKAndNoTriad() {
        for (double ratio : new double[] {1, 0.25, 16, 1e308, 1e-308}) {
            PairwiseComparisonMatrix matrix = new PairwiseComparisonMatrix(new double[][] {{1, ratio}, {1 / ratio, 1}});
            KoczkodajResult result = ConsistencyMetrics.koczkodajIndex(matrix);
            assertEquals(0, result.value(), 0);
            assertTrue(result.worstTriad().isEmpty());
            assertEquals(2, ConsistencyMetrics.principalEigenvalue(matrix), 1e-12);
            assertEquals(0, ConsistencyMetrics.saatyIndex(matrix), 1e-12);
        }
    }

    @Test
    public void tiesReturnFirstLexicographicTriadEvenForZeroK() {
        double[][] data = new double[5][5];
        for (double[] row : data) {
            java.util.Arrays.fill(row, 1);
        }
        KoczkodajResult result = ConsistencyMetrics.koczkodajIndex(new PairwiseComparisonMatrix(data));
        assertEquals(0, result.value(), 0);
        assertTriad(result, 0, 1, 2);
    }

    @Test
    public void koczkodajHandlesAnOverflowingIntermediateProduct() {
        // 1e200 * 2e108 overflows, but division by 1e308 would give approximately 2.
        PairwiseComparisonMatrix matrix = ComparisonFixtures.triad(1e200, 2e108, 1e308);
        assertEquals(0.5, ConsistencyMetrics.koczkodajIndex(matrix).value(), 1e-14);
        assertFalse(matrix.isMultiplicativelyConsistent(1e-10));
    }

    @Test
    public void koczkodajHandlesUnderflowAndRatiosWithSubnormalEntries() {
        assertEquals(1, ConsistencyMetrics.koczkodajIndex(
                ComparisonFixtures.triad(1e-200, 1e-200, 1e-308)).value(), 0);
        assertEquals(0.5, ConsistencyMetrics.koczkodajIndex(
                ComparisonFixtures.triad(1e-308, 2, 1e-308)).value(), 1e-14);
    }

    @Test
    public void koczkodajRetainsSmallDefectsAtExtremeScales() {
        double a = 1e200;
        double c = 2e200 * (1 + 1e-12);
        double expected = Math.abs(Math.fma(a, 2, -c) / c);
        double actual = ConsistencyMetrics.koczkodajIndex(ComparisonFixtures.triad(a, 2, c)).value();
        assertTrue(actual > 0);
        assertEquals(expected, actual, 1e-25);
    }

    @Test
    public void koczkodajMatchesDirectFormulaForSeededModerateMatrices() {
        Random random = new Random(2026);
        for (int n = 3; n <= 9; n++) {
            double[][] data = new double[n][n];
            for (int i = 0; i < n; i++) {
                data[i][i] = 1;
                for (int j = i + 1; j < n; j++) {
                    data[i][j] = Math.exp(8 * random.nextDouble() - 4);
                    data[j][i] = 1 / data[i][j];
                }
            }
            double expected = 0;
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    for (int k = j + 1; k < n; k++) {
                        double ratio = data[i][j] * data[j][k] / data[i][k];
                        expected = Math.max(expected, Math.min(Math.abs(1 - ratio), Math.abs(1 - 1 / ratio)));
                    }
                }
            }
            assertEquals(expected, ConsistencyMetrics.koczkodajIndex(new PairwiseComparisonMatrix(data)).value(), 1e-14);
        }
    }

    @Test
    public void consistentMatricesOfSeveralOrdersHavePerronRootN() {
        for (int n = 2; n <= 10; n++) {
            double[][] data = new double[n][n];
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    data[i][j] = (i + 1.0) / (j + 1);
                }
            }
            PairwiseComparisonMatrix matrix = new PairwiseComparisonMatrix(data);
            assertEquals(n, ConsistencyMetrics.principalEigenvalue(matrix), 1e-11);
            assertEquals(0, ConsistencyMetrics.saatyIndex(matrix), 1e-12);
        }
    }

    @Test
    public void extremeConsistentMatrixDoesNotConfuseScaleWithInconsistency() {
        double[] weights = {1e150, 1e50, 1e-50, 1e-150};
        double[][] data = new double[4][4];
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                data[i][j] = weights[i] / weights[j];
            }
        }
        PairwiseComparisonMatrix matrix = new PairwiseComparisonMatrix(data);
        assertEquals(4, ConsistencyMetrics.principalEigenvalue(matrix), 1e-10);
        assertEquals(0, ConsistencyMetrics.saatyIndex(matrix), 1e-11);
        assertEquals(0, ConsistencyMetrics.koczkodajIndex(matrix).value(), 1e-14);
    }

    @Test
    public void selectsPerronRootInThePresenceOfComplexEigenvalues() {
        PairwiseComparisonMatrix matrix = ComparisonFixtures.circulant(2, 3);
        // Constant row sums imply that the all-ones vector is a Perron eigenvector.
        double lambda = 1 + 2 + 3 + 1.0 / 3 + 0.5;
        assertEquals(lambda, ConsistencyMetrics.principalEigenvalue(matrix), 1e-12);
        assertEquals((lambda - 5) / 4, ConsistencyMetrics.saatyIndex(matrix), 1e-12);
    }

    @Test
    public void handlesLargeFinitePerronRoots() {
        PairwiseComparisonMatrix matrix = ComparisonFixtures.triad(1e200, 1e200, 1e-200);
        assertEquals(1, ConsistencyMetrics.principalEigenvalue(matrix) / 1e200, 1e-12);
        assertEquals(1, ConsistencyMetrics.saatyIndex(matrix) / 5e199, 1e-12);
    }

    @Test
    public void handlesExtremeAcyclicPathsWithoutLosingTheDominantCycle() {
        double large = Math.scalb(1.0, 1000);
        PairwiseComparisonMatrix matrix = new PairwiseComparisonMatrix(new double[][] {
                {1, large, 1, 1}, {1 / large, 1, large, 1},
                {1, 1 / large, 1, large}, {1, 1, 1 / large, 1}
        });
        // The 0->1->2->3->0 cycle dominates. Every other cycle contributes
        // less than the precision of double to its Perron root 2^750.
        assertEquals(1, ConsistencyMetrics.principalEigenvalue(matrix) / Math.scalb(1.0, 750), 1e-12);
    }

    @Test
    public void computesFiniteCIEvenIfThePerronRootOverflowsDouble() {
        PairwiseComparisonMatrix matrix = ComparisonFixtures.circulant(1e308, 1e308);
        assertThrows(ArithmeticException.class, () -> ConsistencyMetrics.principalEigenvalue(matrix));
        assertEquals(1, ConsistencyMetrics.saatyIndex(matrix) / 5e307, 1e-12);
    }

    @Test
    public void reportsSignificantlyNegativeCIInsteadOfSilentlyClampingIt() {
        PairwiseComparisonMatrix rounded = new PairwiseComparisonMatrix(new double[][] {{1, 1}, {0.9, 1}}, 0.11);
        assertThrows(ArithmeticException.class, () -> ConsistencyMetrics.saatyIndex(rounded));
    }

    @Test
    public void clampsOnlyTinyNegativeCIFromRounding() {
        PairwiseComparisonMatrix rounded = new PairwiseComparisonMatrix(new double[][] {{1, 1}, {1 - 1e-12, 1}});
        assertEquals(0, ConsistencyMetrics.saatyIndex(rounded), 0);
    }

    @Test
    public void rejectsNullMatricesWithClearErrors() {
        assertThrows(IllegalArgumentException.class, () -> ConsistencyMetrics.principalEigenvalue(null));
        assertThrows(IllegalArgumentException.class, () -> ConsistencyMetrics.saatyIndex(null));
        assertThrows(IllegalArgumentException.class, () -> ConsistencyMetrics.koczkodajIndex(null));
    }

    private static void assertTriad(KoczkodajResult result, int i, int j, int k) {
        assertTrue(result.worstTriad().isPresent());
        KoczkodajResult.Triad triad = result.worstTriad().get();
        assertEquals(i, triad.i());
        assertEquals(j, triad.j());
        assertEquals(k, triad.k());
        assertEquals(result.value(), triad.localInconsistency(), 0);
    }
}
