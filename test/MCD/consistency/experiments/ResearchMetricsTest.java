package MCD.consistency.experiments;

import MCD.consistency.PairwiseComparisonMatrix;
import org.junit.Test;
import static org.junit.Assert.*;

public class ResearchMetricsTest {
    private static PairwiseComparisonMatrix pair(double upper) { return new PairwiseComparisonMatrix(new double[][]{{1,upper},{1/upper,1}}); }

    @Test public void logarithmicDeviationHasKnownValueAndZeroForIdenticalInputs() {
        var a = new PairwiseComparisonMatrix(new double[][]{{1,1,1},{1,1,1},{1,1,1}});
        var b = new PairwiseComparisonMatrix(new double[][]{{1,2,1},{.5,1,1},{1,1,1}});
        assertEquals(Math.log(2)/Math.sqrt(3),ResearchMetrics.logDeviation(a,b),1e-16);
        assertEquals(0,ResearchMetrics.logDeviation(a,a),0);
    }

    @Test public void deviationIsSymmetricAndDoesNotDependOnComparisonOrientation() {
        assertEquals(ResearchMetrics.logDeviation(pair(2),pair(6)),ResearchMetrics.logDeviation(pair(.5),pair(1d/6)),2e-16);
        assertEquals(ResearchMetrics.logDeviation(pair(2),pair(6)),ResearchMetrics.logDeviation(pair(6),pair(2)),0);
    }

    @Test public void deviationAvoidsOverflowAndPreservesSmallChanges() {
        assertEquals(2*Math.log(1e300),ResearchMetrics.logDeviation(pair(1e-300),pair(1e300)),1e-12);
        double next=Math.nextUp(1d);
        assertEquals(Math.log1p(next-1),ResearchMetrics.logDeviation(pair(1),pair(next)),0);
    }

    @Test public void relativeChangeMarksZeroDenominatorsAndKeepsNegativeResults() {
        assertNull(ResearchMetrics.relativeChange(0,0)); assertNull(ResearchMetrics.relativeChange(1e-14,.2));
        assertEquals(.5,ResearchMetrics.relativeChange(.4,.2),0);
        assertEquals(-1,ResearchMetrics.relativeChange(.2,.4),0);
    }

    @Test public void thresholdsAndFourInitialCategoriesRemainDistinct() {
        assertEquals(ResearchMetrics.InitialCategory.BOTH_ACCEPTABLE, ResearchMetrics.category(.1,.299));
        assertEquals(ResearchMetrics.InitialCategory.K_ONLY_VIOLATION, ResearchMetrics.category(.1,.3));
        assertEquals(ResearchMetrics.InitialCategory.CI_ONLY_VIOLATION, ResearchMetrics.category(.101,.2));
        assertEquals(ResearchMetrics.InitialCategory.BOTH_VIOLATED, ResearchMetrics.category(.101,.3));
    }
}
