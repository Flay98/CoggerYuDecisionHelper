package MCD.consistency;

import org.junit.Test;

import java.util.List;
import java.util.Random;

import static org.junit.Assert.*;

public class RowCosinesTest {
    @Test
    public void consistentRowsHaveUnitCosines() {
        double[][] cosines = RowCosines.compute(new PairwiseComparisonMatrix(ComparisonFixtures.consistent()));
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                assertEquals(1, cosines[i][j], 1e-15);
            }
        }
    }

    @Test
    public void severeTriadMatchesAnalyticalDotProductsAndOrder() {
        PairwiseComparisonMatrix matrix = ComparisonFixtures.triad(3, 2, 1);
        double[][] cosines = RowCosines.compute(matrix);
        assertEquals(16 / Math.sqrt(506), cosines[0][1], 1e-15);
        assertEquals(7 / (3 * Math.sqrt(11)), cosines[0][2], 1e-15);
        assertEquals(17 / (3 * Math.sqrt(46)), cosines[1][2], 1e-15);
        List<RowCosines.Comparison> ranked = RowCosines.rankComparisons(matrix);
        assertEquals(List.of(2, 1, 2), ranked.stream().map(RowCosines.Comparison::k).toList());
        assertEquals(List.of(0, 0, 1), ranked.stream().map(RowCosines.Comparison::i).toList());
        assertEquals(cosines[0][2], ranked.get(0).cosine(), 0);
    }

    @Test
    public void matchesDirectFormulaForModerateCoefficients() {
        Random random = new Random(2018);
        int n = 6;
        double[][] data = new double[n][n];
        for (int i = 0; i < n; i++) {
            data[i][i] = 1;
            for (int j = i + 1; j < n; j++) {
                data[i][j] = Math.exp(6 * random.nextDouble() - 3);
                data[j][i] = 1 / data[i][j];
            }
        }
        double[][] cosines = RowCosines.compute(new PairwiseComparisonMatrix(data));
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                double dot = 0, normI = 0, normJ = 0;
                for (int k = 0; k < n; k++) {
                    dot += data[i][k] * data[j][k];
                    normI += data[i][k] * data[i][k];
                    normJ += data[j][k] * data[j][k];
                }
                assertEquals(dot / (Math.sqrt(normI) * Math.sqrt(normJ)), cosines[i][j], 1e-15);
                assertEquals(cosines[i][j], cosines[j][i], 0);
            }
        }
    }

    @Test
    public void scaledNormsHandleExtremeCoefficientsWithoutOverflow() {
        PairwiseComparisonMatrix consistent = new PairwiseComparisonMatrix(new double[][] {
                {1, 1e308}, {1e-308, 1}
        });
        assertEquals(1, RowCosines.compute(consistent)[0][1], 1e-15);
        double[][] cosines = RowCosines.compute(ComparisonFixtures.triad(1e308, 1e308, 1e-308));
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                assertTrue(Double.isFinite(cosines[i][j]));
                assertTrue(cosines[i][j] >= 0 && cosines[i][j] <= 1);
            }
        }
        assertEquals(1e-308, cosines[0][1], 1e-322);
    }

    @Test
    public void tiesAreLexicographicAndResultsCannotModifyTheInput() {
        PairwiseComparisonMatrix matrix = ComparisonFixtures.triad(1, 1, 1);
        List<RowCosines.Comparison> ranked = RowCosines.rankComparisons(matrix);
        assertEquals(new RowCosines.Comparison(0, 1, 1), ranked.get(0));
        assertEquals(new RowCosines.Comparison(0, 2, 1), ranked.get(1));
        assertEquals(new RowCosines.Comparison(1, 2, 1), ranked.get(2));
        assertThrows(UnsupportedOperationException.class, ranked::clear);
        double[][] cosines = RowCosines.compute(matrix);
        cosines[0][1] = 0;
        assertEquals(1, RowCosines.compute(matrix)[0][1], 0);
        assertEquals(1, matrix.get(0, 1), 0);
    }

    @Test
    public void rejectsNullInput() {
        assertThrows(IllegalArgumentException.class, () -> RowCosines.compute(null));
        assertThrows(IllegalArgumentException.class, () -> RowCosines.rankComparisons(null));
    }
}
