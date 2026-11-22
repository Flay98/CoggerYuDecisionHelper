package MCD.consistency;

import org.junit.Test;

import static org.junit.Assert.*;

public class PairwiseComparisonMatrixTest {
    @Test
    public void acceptsArbitraryOrderAndCoefficientsOutsideSaatyScale() {
        double[] weights = {1e150, 1e50, 1e-50, 1e-150, 1};
        double[][] data = new double[weights.length][weights.length];
        for (int i = 0; i < weights.length; i++) {
            for (int j = 0; j < weights.length; j++) {
                data[i][j] = weights[i] / weights[j];
            }
        }
        PairwiseComparisonMatrix matrix = new PairwiseComparisonMatrix(data);
        assertEquals(5, matrix.size());
        assertEquals(data[0][3], matrix.get(0, 3), 0);
        assertTrue(matrix.isMultiplicativelyConsistent(1e-12));
    }

    @Test
    public void constructorAndArrayAccessUseDeepCopies() {
        double[][] input = ComparisonFixtures.consistent();
        PairwiseComparisonMatrix matrix = new PairwiseComparisonMatrix(input);
        input[0][1] = 100;
        input[1] = new double[3];
        double[][] copy = matrix.toArray();
        copy[0][1] = 200;
        copy[1] = new double[3];
        assertEquals(3, matrix.get(0, 1), 0);
        assertEquals(1.0 / 3, matrix.get(1, 0), 0);
        assertNotSame(copy, matrix.toArray());
        assertNotSame(copy[0], matrix.toArray()[0]);
    }

    @Test
    public void rejectsNullEmptyAndSingleElementData() {
        assertInvalid(null, "at least two");
        assertInvalid(new double[0][], "at least two");
        assertInvalid(new double[][] {{1}}, "at least two");
    }

    @Test
    public void rejectsRectangularAndRaggedData() {
        assertInvalid(new double[][] {{1, 2, 3}, {0.5, 1, 4}}, "square");
        assertInvalid(new double[][] {{1, 2}, {0.5}}, "row 1");
    }

    @Test
    public void rejectsNullRows() {
        assertInvalid(new double[][] {{1, 2}, null}, "row 1");
    }

    @Test
    public void rejectsNonPositiveEntries() {
        for (double value : new double[] {-1, 0, -0.0}) {
            assertInvalid(new double[][] {{1, value}, {0.5, 1}}, "strictly positive");
        }
    }

    @Test
    public void rejectsNonFiniteEntries() {
        for (double value : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertInvalid(new double[][] {{1, value}, {0.5, 1}}, "finite");
        }
    }

    @Test
    public void rejectsNonUnitDiagonal() {
        assertInvalid(new double[][] {{2, 2}, {0.5, 1}}, "Diagonal");
    }

    @Test
    public void rejectsBrokenReciprocityAtOrdinaryAndExtremeScales() {
        assertInvalid(new double[][] {{1, 2}, {0.4, 1}}, "reciprocal");
        assertInvalid(new double[][] {{1, 1e-200}, {1e-200, 1}}, "reciprocal");
        assertInvalid(new double[][] {{1, 1e200}, {1e200, 1}}, "reciprocal");
        assertInvalid(new double[][] {{1, Double.MIN_VALUE}, {Double.MAX_VALUE, 1}}, "reciprocal");
    }

    @Test
    public void validatesToleranceArguments() {
        for (double tolerance : new double[] {-1, 1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new PairwiseComparisonMatrix(ComparisonFixtures.consistent(), tolerance));
        }
    }

    @Test
    public void permitsRoundingWithinExplicitToleranceWithoutChangingEntries() {
        assertInvalid(ComparisonFixtures.paperRounded(), "reciprocal");
        PairwiseComparisonMatrix matrix = new PairwiseComparisonMatrix(ComparisonFixtures.paperRounded(), 1e-4);
        assertEquals(0.7714, matrix.get(1, 0), 0);
        assertEquals(0.4881, matrix.get(3, 2), 0);
        assertFalse(matrix.isMultiplicativelyConsistent(1e-10));
    }

    @Test
    public void structuralToleranceHasAnInclusiveBoundary() {
        double[][] data = {{1, 2}, {0.5 + Math.scalb(1, -20), 1}};
        double defect = Math.scalb(1, -19);
        new PairwiseComparisonMatrix(data, defect);
        assertThrows(IllegalArgumentException.class,
                () -> new PairwiseComparisonMatrix(data, Math.nextDown(defect)));
    }

    @Test
    public void recognizesIdealConsistencyAndInconsistency() {
        assertTrue(new PairwiseComparisonMatrix(ComparisonFixtures.consistent()).isMultiplicativelyConsistent(1e-14));
        assertFalse(new PairwiseComparisonMatrix(ComparisonFixtures.inconsistent()).isMultiplicativelyConsistent(1e-12));
        assertTrue(new PairwiseComparisonMatrix(new double[][] {{1, 2}, {0.5, 1}}).isMultiplicativelyConsistent(0));
    }

    @Test
    public void consistencyUsesTheRequestedRelativeTolerance() {
        PairwiseComparisonMatrix matrix = ComparisonFixtures.triad(3, 2, 6 * (1 + 1e-6));
        assertTrue(matrix.isMultiplicativelyConsistent(2e-6));
        assertFalse(matrix.isMultiplicativelyConsistent(5e-7));
    }

    @Test
    public void consistencyAlsoChecksRoundedLowerTriangleAndDiagonal() {
        double[][] data = ComparisonFixtures.consistent();
        data[1][0] *= 1 + 1e-5;
        PairwiseComparisonMatrix lowerRounded = new PairwiseComparisonMatrix(data, 1e-4);
        assertFalse(lowerRounded.isMultiplicativelyConsistent(1e-6));
        data = ComparisonFixtures.consistent();
        data[0][0] += 1e-5;
        assertFalse(new PairwiseComparisonMatrix(data, 1e-4).isMultiplicativelyConsistent(1e-6));
    }

    @Test
    public void consistencyRejectsInvalidTolerances() {
        PairwiseComparisonMatrix matrix = new PairwiseComparisonMatrix(ComparisonFixtures.consistent());
        for (double tolerance : new double[] {-1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> matrix.isMultiplicativelyConsistent(tolerance));
        }
    }

    private static void assertInvalid(double[][] data, String messagePart) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new PairwiseComparisonMatrix(data));
        assertTrue(error.getMessage(), error.getMessage().contains(messagePart));
    }
}
