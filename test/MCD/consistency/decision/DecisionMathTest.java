package MCD.consistency.decision;

import MCD.consistency.ConsistencyMetrics;
import MCD.consistency.PairwiseComparisonMatrix;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class DecisionMathTest {
    static PairwiseComparisonMatrix inconsistent() { return new PairwiseComparisonMatrix(new double[][]{{1,3,1},{1d/3,1,2},{1,.5,1}}); }
    static PairwiseComparisonMatrix consistent() { return new PairwiseComparisonMatrix(new double[][]{{1,3,6},{1d/3,1,2},{1d/6,.5,1}}); }
    static AlternativeTable table() { return new AlternativeTable(List.of("A","B","C"),List.of("speed","cost","accuracy"),Direction.parse("max,min,max"),
            new double[][]{{90,120,.82},{70,75,.90},{60,60,.95}}); }

    @Test public void consistentPcmRecoversKnownNormalizedPriorities() {
        var result=new PerronWeights().calculate(consistent());
        assertArrayEquals(new double[]{2d/3,2d/9,1d/9},array(result.weights()),1e-13);
        assertEquals(3,result.eigenvalue(),1e-12); assertTrue(result.maxRelativeResidual()<=1e-12);
    }

    @Test public void inconsistentPcmWeightsSatisfyTheRightEigenvectorEquation() {
        var matrix=inconsistent(); var result=new PerronWeights().calculate(matrix);
        double[] geometric={Math.cbrt(3),Math.cbrt(2d/3),Math.cbrt(.5)};
        double total=java.util.Arrays.stream(geometric).sum();
        for(int i=0;i<3;i++) {
            assertEquals(geometric[i]/total,result.weights().get(i),1e-12);
            double product=0; for(int j=0;j<3;j++) product+=matrix.get(i,j)*result.weights().get(j);
            assertEquals(result.eigenvalue()*result.weights().get(i),product,1e-12);
        }
        assertEquals(ConsistencyMetrics.principalEigenvalue(matrix),result.eigenvalue(),1e-12);
    }

    @Test public void weightsArePositiveFiniteNormalizedAndDeterministicAcrossSizes() {
        for(int n=2;n<=10;n++) {
            double[][] data=new double[n][n]; double total=n*(n+1d)/2;
            for(int i=0;i<n;i++) for(int j=0;j<n;j++) data[i][j]=(i+1d)/(j+1d);
            var matrix=new PairwiseComparisonMatrix(data); var a=new PerronWeights().calculate(matrix); var b=new PerronWeights().calculate(matrix);
            assertEquals(a,b); assertEquals(1,a.weights().stream().mapToDouble(Double::doubleValue).sum(),1e-14);
            for(int i=0;i<n;i++) { assertTrue(a.weights().get(i)>0); assertEquals((i+1d)/total,a.weights().get(i),1e-13); }
        }
    }

    @Test public void logIterationHandlesLargeScaleDifferencesWithoutZeroingRepresentableWeights() {
        var result=new PerronWeights().calculate(new PairwiseComparisonMatrix(new double[][]{{1,1e300},{1e-300,1}}));
        assertEquals(1,result.weights().get(0),1e-14); assertTrue(result.weights().get(1)>0);
        assertEquals(1, result.weights().get(1)/1e-300,1e-12); assertEquals(2,result.eigenvalue(),1e-12);
    }

    @Test public void iterationLimitAndInvalidSolverSettingsFailExplicitly() {
        assertThrows(ArithmeticException.class,()->new PerronWeights(1e-12,1).calculate(inconsistent()));
        assertThrows(IllegalArgumentException.class,()->new PerronWeights(Double.NaN,10));
        assertThrows(IllegalArgumentException.class,()->new PerronWeights(1e-12,0));
        assertThrows(IllegalArgumentException.class,()->new PerronWeights().calculate(null));
    }

    @Test public void solverDoesNotMutateMatrixOrExposeMutablePriorities() {
        var matrix=inconsistent(); double[][] before=matrix.toArray(); var priorities=new PerronWeights().calculate(matrix);
        assertArrayEquals(before,matrix.toArray());
        assertThrows(UnsupportedOperationException.class,()->priorities.weights().set(0,0d));
    }

    @Test public void maxColumnNormalizationMatchesExactFractions() {
        var normalized=new WeightedRanking().normalize(table());
        assertEquals(9d/22,normalized.get(0).get(0),1e-16); assertEquals(7d/22,normalized.get(1).get(0),1e-16); assertEquals(6d/22,normalized.get(2).get(0),1e-16);
        assertEquals(82d/267,normalized.get(0).get(2),1e-16); assertEquals(95d/267,normalized.get(2).get(2),1e-16);
    }

    @Test public void inverseMinColumnNormalizationMatchesExactFractions() {
        var normalized=new WeightedRanking().normalize(table());
        assertEquals(5d/23,normalized.get(0).get(1),1e-16); assertEquals(8d/23,normalized.get(1).get(1),1e-16); assertEquals(10d/23,normalized.get(2).get(1),1e-16);
    }

    @Test public void normalizationAvoidsOverflowOfColumnSumsAndInverseValues() {
        var data=new AlternativeTable(List.of("low","high"),List.of("max","min"),Direction.parse("max,min"),new double[][]{{1e-300,1e-300},{1e300,1e300}});
        var u=new WeightedRanking().normalize(data);
        assertEquals(List.of(0d,1d),u.get(0));assertEquals(List.of(1d,0d),u.get(1));
        var result=new WeightedRanking().rank(data,u,List.of(.5,.5));assertEquals(List.of(.5,.5),result.scores());
    }

    @Test public void normalizedColumnsAndAlternativeScoresSumToOne() {
        var ranker=new WeightedRanking(); var u=ranker.normalize(table());
        for(int j=0;j<3;j++) { double sum=0;for(var row:u) sum+=row.get(j);assertEquals(1,sum,1e-15); }
        var result=ranker.rank(table(),u,new PerronWeights().calculate(inconsistent()).weights());
        assertEquals(1,result.scores().stream().mapToDouble(Double::doubleValue).sum(),1e-14);
    }

    @Test public void cosineCorrectionPrioritiesProduceKnownScoresAndLeaderChange() {
        var ranker=new WeightedRanking(); var table=table(); var u=ranker.normalize(table);
        var original=ranker.rank(table,u,new PerronWeights().calculate(inconsistent()).weights());
        var corrected=ranker.rank(table,u,new PerronWeights().calculate(consistent()).weights());
        assertEquals("C",original.winner()); assertEquals(List.of(3,2,1),original.ranks());
        assertEquals("A",corrected.winner()); assertEquals(List.of(1,2,3),corrected.ranks());
        for(int i=0;i<3;i++) assertEquals((2d/3)*u.get(i).get(0)+(2d/9)*u.get(i).get(1)+(1d/9)*u.get(i).get(2),corrected.scores().get(i),1e-13);
        assertTrue(corrected.scores().get(0)>corrected.scores().get(1)); assertTrue(corrected.scores().get(1)>corrected.scores().get(2));
    }

    @Test public void exactScoreTiesRetainOriginalOrderAndHaveOrdinalPositions() {
        var table=new AlternativeTable(List.of("Z","A","M"),List.of("x","y"),Direction.parse("max,min"),new double[][]{{2,3},{2,3},{2,3}});
        var ranker=new WeightedRanking(); var result=ranker.rank(table,ranker.normalize(table),List.of(.5,.5));
        assertEquals(List.of("Z","A","M"),result.ranking().stream().map(WeightedRanking.Entry::alternative).toList());
        assertEquals(List.of(1,2,3),result.ranks()); assertEquals("Z",result.winner());
    }

    @Test public void singleAlternativeIsAcceptedAndInvalidNormalizedDataAreRejected() {
        var table=new AlternativeTable(List.of("only"),List.of("x","y"),Direction.parse("max,min"),new double[][]{{2,3}});
        var ranker=new WeightedRanking(); assertEquals(List.of(1d),ranker.rank(table,ranker.normalize(table),List.of(.2,.8)).scores());
        assertThrows(IllegalArgumentException.class,()->ranker.rank(table,List.of(List.of(.1,.3)),List.of(.2,.8)));
        assertThrows(IllegalArgumentException.class,()->ranker.rank(table,ranker.normalize(table),List.of(.2,.9)));
    }

    @Test public void alternativeTableDefensivelyCopiesAndFreezesAllData() {
        double[][] data={{2,4},{1,2}}; var table=new AlternativeTable(List.of("A","B"),List.of("x","y"),Direction.parse("max,min"),data);
        data[0][0]=999;table.toArray()[0][0]=999; assertEquals(2,table.get(0,0),0);
        assertThrows(UnsupportedOperationException.class,()->table.criteria().set(0,"other"));
        var u=new WeightedRanking().normalize(table); assertThrows(UnsupportedOperationException.class,()->u.get(0).set(0,0d));
    }

    private static double[] array(List<Double> list) { return list.stream().mapToDouble(Double::doubleValue).toArray(); }
}
