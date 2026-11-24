package MCD.consistency.reproduction.kurennykh;

import MCD.consistency.PairwiseComparisonMatrix;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class KurennykhMetricsTest {
    private static PairwiseComparisonMatrix pair(double a) {return new PairwiseComparisonMatrix(new double[][]{{1,a},{1/a,1}});}
    @Test public void fullUpperAndPercentageMetricsMatchHandCalculation() {
        var d=KurennykhMetrics.deviations(pair(2),pair(4));
        assertEquals(4.0625,d.eFull(),0);assertEquals(4,d.eUpper(),0);assertEquals(100,d.vUpper(),0);
        assertEquals(100,d.vChanged(),0);assertEquals(75,d.vOffdiagonal(),0);assertEquals(Math.log(2),d.logDeviation(),1e-15);assertEquals(1,d.changedComparisons());
    }
    @Test public void singleChangedPairInThreeByThreeUsesAllIndependentPairsAsPrimaryDenominator() {
        var a=new PairwiseComparisonMatrix(new double[][]{{1,1,1},{1,1,1},{1,1,1}});
        var b=new PairwiseComparisonMatrix(new double[][]{{1,2,1},{.5,1,1},{1,1,1}});
        var d=KurennykhMetrics.deviations(a,b);
        assertEquals(1.25,d.eFull(),0);assertEquals(1,d.eUpper(),0);assertEquals(100d/3,d.vUpper(),1e-14);
        assertEquals(100,d.vChanged(),0);assertEquals(25,d.vOffdiagonal(),0);assertEquals(Math.log(2)/Math.sqrt(3),d.logDeviation(),1e-15);
    }
    @Test public void unchangedMatrixHasZeroDeviationsAndUndefinedChangedOnlyVariation() {
        var a=pair(3);var d=KurennykhMetrics.deviations(a,a);
        assertEquals(0,d.eFull(),0);assertEquals(0,d.eUpper(),0);assertEquals(0,d.vUpper(),0);assertEquals(0,d.vOffdiagonal(),0);
        assertEquals(0,d.logDeviation(),0);assertEquals(0,d.changedComparisons());assertNull(d.vChanged());
    }
    @Test public void fullErrorIsOrientationInvariantButUpperDiagnosticsNeedNotBe() {
        var upper=KurennykhMetrics.deviations(pair(2),pair(4));var reverse=KurennykhMetrics.deviations(pair(.5),pair(.25));
        assertEquals(upper.eFull(),reverse.eFull(),0);assertEquals(.0625,reverse.eUpper(),0);assertEquals(50,reverse.vUpper(),0);
    }
    @Test public void rankOfTheOriginalBestObjectIsTrackedRatherThanJustItsScore() {
        var change=KurennykhMetrics.rankChange(KurennykhMetrics.rank(List.of(.5,.3,.2)),KurennykhMetrics.rank(List.of(.2,.5,.3)));
        assertEquals(0,change.originalLeader());assertEquals(1,change.finalLeader());assertEquals(1,change.initialPosition());assertEquals(3,change.finalPosition());
        assertEquals(2,change.deltaPosition());assertTrue(change.leaderChanged());assertTrue(change.originalBestRankChanged());
    }
    @Test public void unchangedRankAndExactTiesFollowPredeclaredIndexOrder() {
        var ties=KurennykhMetrics.rank(List.of(.5,.5));assertEquals(List.of(0,1),ties.objectsByRank());assertEquals(List.of(1,2),ties.ranksByObject());assertEquals(2,ties.tiedLeaders());
        var c=KurennykhMetrics.rankChange(ties,KurennykhMetrics.rank(List.of(.6,.4)));
        assertFalse(c.leaderChanged());assertFalse(c.originalBestRankChanged());assertEquals(0,c.deltaPosition());
    }
    @Test public void rejectsInvalidWeightsAndMismatchedSizes() {
        assertThrows(IllegalArgumentException.class,()->KurennykhMetrics.rank(List.of(.5,0d)));
        assertThrows(IllegalArgumentException.class,()->KurennykhMetrics.rank(List.of(Double.NaN,1d)));
        assertThrows(IllegalArgumentException.class,()->KurennykhMetrics.deviations(pair(2),new PairwiseComparisonMatrix(new double[][]{{1,1,1},{1,1,1},{1,1,1}})));
    }
}
