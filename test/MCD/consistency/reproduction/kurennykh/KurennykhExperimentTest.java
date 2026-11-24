package MCD.consistency.reproduction.kurennykh;

import MCD.consistency.ConsistencyMetrics;
import MCD.consistency.PairwiseComparisonMatrix;
import MCD.consistency.reproduction.PublishedReferences;
import MCD.consistency.reproduction.ReproductionFiles;
import com.google.gson.JsonArray;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import static org.junit.Assert.*;

/** Tiny temporary plans only: REPORT's 8000 optimizations never run as part of JUnit. */
public class KurennykhExperimentTest {
    @ClassRule public static TemporaryFolder TEMP=new TemporaryFolder();
    private static Path root;
    private static KurennykhExperimentPlan mini;
    private static KurennykhReproduction.Completion result;
    @BeforeClass public static void runSmallPlan() throws Exception {
        root=TEMP.newFolder("study").toPath();Files.copy(Path.of("experiments/kurennykh2018/PROTOCOL.md"),root.resolve("PROTOCOL.md"));
        mini=smallPlan("SMOKE",2,0);result=KurennykhReproduction.run(root,mini);
    }
    static KurennykhExperimentPlan smallPlan(String profile,int samples,int limit) throws Exception {
        var json=ReproductionFiles.readJson(Path.of("experiments/kurennykh2018/config/SMOKE.json"));
        json.addProperty("profile",profile);json.addProperty("matricesPerSize",samples);json.addProperty("maxCorrections",limit);json.addProperty("warmupPerSize",0);
        var sizes=new JsonArray();sizes.add(3);sizes.add(4);json.add("sizes",sizes);
        return KurennykhResultWriter.JSON.fromJson(json,KurennykhExperimentPlan.class);
    }
    @Test public void officialProfilesHaveExactlyFortyAndEightThousandInputs() throws Exception {
        Path root=Path.of("experiments/kurennykh2018");
        assertEquals(40,KurennykhExperimentPlan.load(root,"SMOKE").totalMatrices());assertEquals(8000,KurennykhExperimentPlan.load(root,"REPORT").totalMatrices());
        assertEquals(4,result.matrices());assertEquals(0,result.warmups());
    }
    @Test public void seedsIdsAndFirstFiveMatricesMatchAcrossProfilesExactly() throws Exception {
        Path root=Path.of("experiments/kurennykh2018");var smoke=KurennykhExperimentPlan.load(root,"SMOKE");var report=KurennykhExperimentPlan.load(root,"REPORT");
        var ids=new HashSet<String>();var seeds=new HashSet<Long>();
        for(int n:report.sizes())for(int sample=0;sample<1000;sample++) {
            assertTrue(ids.add(KurennykhExperimentPlan.id(n,sample)));assertTrue(seeds.add(report.seed(n,sample)));
            if(sample<5) {assertEquals(smoke.seed(n,sample),report.seed(n,sample));
                assertArrayEquals(KurennykhReproduction.generate(n,smoke.seed(n,sample)).toArray(),KurennykhReproduction.generate(n,report.seed(n,sample)).toArray());}
        }
    }
    @Test public void generatedInputsAreBoundedReciprocalAndDoNotShareMutableArrays() {
        for(int n=3;n<=10;n++) {
            var a=KurennykhReproduction.generate(n,mini.seed(n,0));var b=KurennykhReproduction.generate(n,mini.seed(n,1));
            double before=b.get(0,1);a.toArray()[0][1]=123;
            assertEquals(before,b.get(0,1),0);KurennykhReproduction.validateMatrix(a);KurennykhReproduction.validateMatrix(b);
            for(int i=0;i<n;i++) {assertEquals(1,a.get(i,i),0);for(int j=0;j<n;j++)assertEquals(1,a.get(i,j)*a.get(j,i),2e-16);}
        }
    }
    @Test public void momentsHaveKnownMeanMedianSdAndNominalNormalInterval() {
        var m=KurennykhStatistics.moments(new double[]{1,3,5});assertEquals(3,m.mean(),0);assertEquals(3,m.median(),0);assertEquals(2,m.sampleSd(),0);
        assertEquals(1,m.minimum(),0);assertEquals(5,m.maximum(),0);assertEquals(3-KurennykhStatistics.Z95*2/Math.sqrt(3),m.ci95Low(),0);
        assertEquals(3+KurennykhStatistics.Z95*2/Math.sqrt(3),m.ci95High(),0);
        assertTrue(KurennykhStatistics.moments(new double[]{0,0,4}).ci95Low()<0);
    }
    @Test public void emptyAndSingletonGroupsDoNotInventUncertaintyOrDivideByZero() {
        assertNull(KurennykhStatistics.moments(new double[]{}).mean());assertNull(KurennykhStatistics.moments(new double[]{2}).ci95Low());
        assertNull(KurennykhStatistics.moments(new double[]{2}).sampleSd());
        assertThrows(IllegalArgumentException.class,()->KurennykhStatistics.moments(new double[]{Double.NaN}));
    }
    @Test public void failedAndInitiallyAcceptableRunsRemainInCorrectDenominators() {
        var rows=List.of(fixture("a",.05,.05,true,true,0),fixture("b",.4,.08,false,true,2),fixture("c",.8,.3,false,false,100));
        var groups=KurennykhStatistics.summaries(mini,rows);var all=groups.stream().filter(g->g.size()==3&&g.group().equals("ALL")).findFirst().orElseThrow();
        assertEquals(3,all.nMatrices());assertEquals(1,all.initiallyAcceptable());assertEquals(1,all.successfullyCorrected());assertEquals(1,all.failedCorrections());
        assertEquals(2d/3,all.targetRate(),0);assertEquals(.5,all.requiredCorrectionSuccessRate(),0);
        var agg=KurennykhStatistics.aggregate(mini,rows);
        assertEquals((0+.32+.5)/3,mean(agg,"ALL","deltaCi"),1e-15);assertEquals(.41,mean(agg,"NEEDS_CORRECTION","deltaCi"),1e-15);
        assertEquals(.32,mean(agg,"SUCCESSFUL_REQUIRED","deltaCi"),1e-15);assertEquals(34,mean(agg,"ALL","iterations"),0);
    }
    @Test public void savedSummaryMatchesRunRowsAndEveryMatrixCanBeRevalidated() throws Exception {
        assertEquals(4,KurennykhVerifier.verify(root,"SMOKE"));
        var agg=KurennykhStatistics.aggregate(mini,result.runs());
        assertEquals(2*3*KurennykhStatistics.Metric.values().length,agg.size());
        for(int n:mini.sizes()) {
            var a=agg.stream().filter(x->x.size()==n&&x.group().equals("ALL")&&x.metric().equals("initialCi")).findFirst().orElseThrow();
            assertEquals(2,a.nMatrices());assertEquals(result.runs().stream().filter(r->r.size()==n).mapToDouble(KurennykhRunResult::initialCi).average().orElseThrow(),a.mean(),0);
        }
    }
    @Test public void archiveReloadRecomputesTheActualInitialAndFinalCi() throws Exception {
        try(var input=KurennykhResultWriter.readArchive(root.resolve("inputs/SMOKE/matrices.jsonl.gz"));var output=KurennykhResultWriter.readArchive(root.resolve("outputs/SMOKE/results.jsonl.gz"))) {
            for(int index=0;index<4;index++) {
                var a=KurennykhResultWriter.JSON.fromJson(input.readLine(),KurennykhResultWriter.Input.class);var b=KurennykhResultWriter.JSON.fromJson(output.readLine(),KurennykhResultWriter.Output.class);
                assertEquals(a.id(),b.run().id());assertEquals(ConsistencyMetrics.saatyIndex(new PairwiseComparisonMatrix(a.matrix())),b.run().initialCi(),0);
                assertEquals(ConsistencyMetrics.saatyIndex(new PairwiseComparisonMatrix(b.matrix())),b.run().finalCi(),0);
                assertEquals(b.run().iterations(),b.history().size());
            }
            assertNull(input.readLine());assertNull(output.readLine());
        }
    }
    @Test public void publishedTableIsIntactAndUnknownAuthorDenominatorsStayNull() throws Exception {
        PublishedReferences.verifyIntegrity(Path.of("experiments/reproduction"));var published=KurennykhComparison.published(KurennykhComparison.REFERENCE);
        assertEquals(8,published.size());assertEquals(14.754,published.get(0).eFull(),0);assertEquals(67.7913,published.get(2).eFull(),0);
        assertEquals(375.0551,published.get(6).eFull(),0);assertEquals(283,published.get(7).rankChanges());
        var comparisons=KurennykhComparison.compare(mini,KurennykhStatistics.aggregate(mini,result.runs()),KurennykhStatistics.summaries(mini,result.runs()),KurennykhComparison.REFERENCE);
        for(var row:comparisons) {assertNull(row.authorMatricesPerSize());if(row.metric().equals("originalBestRankChanges")){
            assertNull(row.relativeDifference());assertNull(row.differenceOurMinusAuthor());assertNull(row.absoluteDifference());
        }else assertEquals(Math.abs(row.ourValue()-row.authorValue()),row.absoluteDifference(),0);}
    }
    @Test public void changedReferenceOrSavedStatisticsAreDetectedWithoutReplacingThem() throws Exception {
        Path reference=TEMP.newFile("reference.csv").toPath();Files.writeString(reference,Files.readString(KurennykhComparison.REFERENCE).replace("14.754","14.755"));
        assertThrows(IllegalStateException.class,()->KurennykhComparison.published(reference));
        Path copy=copyRoot("tampered");Path csv=copy.resolve("summary/SMOKE/runs.csv");Files.writeString(csv,Files.readString(csv)+"fake\n");
        assertThrows(IllegalStateException.class,()->KurennykhVerifier.verify(copy,"SMOKE"));
    }
    @Test public void truncatedOrAlteredArchivesCannotBeAcceptedAsCompletedResults() throws Exception {
        Path copy=copyRoot("truncated");Files.writeString(copy.resolve("outputs/SMOKE/results.jsonl.gz"),"not gzip");
        assertThrows(IllegalStateException.class,()->KurennykhVerifier.verify(copy,"SMOKE"));
    }
    @Test public void duplicateRunIdsCannotSilentlyInflateStatistics() {
        var row=result.runs().get(0);assertThrows(IllegalArgumentException.class,()->KurennykhStatistics.aggregate(mini,List.of(row,row)));
    }
    @Test public void alreadyAcceptableControlKeepsWeightsRanksAndAllChangesAtZero() {
        var matrix=new PairwiseComparisonMatrix(new double[][]{{1,1,1},{1,1,1},{1,1,1}});
        var c=new MCD.consistency.CosineConsistencyCorrector(mini.options()).correct(matrix);
        var weights=new MCD.consistency.decision.PerronWeights().calculate(matrix);
        var row=KurennykhReproduction.calculate(mini,"control","control",3,0,0,KurennykhResultWriter.matrixHash(matrix),new KurennykhReproduction.Timed(c,0),weights,weights);
        assertTrue(row.initiallyAcceptable());assertTrue(row.targetReached());assertFalse(row.correctionRequired());assertFalse(row.successfullyCorrected());
        assertEquals(0,row.iterations());assertEquals(0,row.acceptedCorrections());assertEquals(0,row.changedComparisons());assertEquals(0,row.uniqueTouchedComparisons());
        assertEquals(0,row.eFull(),0);assertEquals(0,row.vUpper(),0);assertNull(row.vChanged());assertEquals(0,row.deltaPosition());assertEquals(3,row.initialTiedLeaders());
    }
    @Test public void bothProfilesHaveTheSameActualArchivedInputPrefix() throws Exception {
        Path copy=copyRoot("cross-profile");var report=smallPlan("REPORT",2,0);
        assertEquals(4,KurennykhReproduction.run(copy,report).matrices());assertEquals(4,KurennykhVerifier.verify(copy,"REPORT"));
        try(var smoke=KurennykhResultWriter.readArchive(copy.resolve("inputs/SMOKE/matrices.jsonl.gz"));
            var full=KurennykhResultWriter.readArchive(copy.resolve("inputs/REPORT/matrices.jsonl.gz"))) {
            for(int i=0;i<4;i++)assertEquals(smoke.readLine(),full.readLine());
        }
    }
    @Test public void positiveCorrectionHistoriesRoundTripAndReplayThroughTheRealAudit() throws Exception {
        Path copy=TEMP.newFolder("accepted-history").toPath();Files.copy(root.resolve("PROTOCOL.md"),copy.resolve("PROTOCOL.md"));
        var plan=smallPlan("SMOKE",5,100);var completed=KurennykhReproduction.run(copy,plan);
        assertTrue(completed.runs().stream().anyMatch(r->r.acceptedCorrections()>0));
        assertEquals(10,KurennykhVerifier.verify(copy,"SMOKE"));
        for(var r:completed.runs()) {assertTrue(r.acceptedCorrections()>=r.uniqueTouchedComparisons());assertTrue(r.uniqueTouchedComparisons()>=r.changedComparisons());}
    }
    private static double mean(List<KurennykhStatistics.Aggregate> rows,String group,String metric) {return rows.stream().filter(r->r.size()==3&&r.group().equals(group)&&r.metric().equals(metric)).findFirst().orElseThrow().mean();}
    private static KurennykhRunResult fixture(String id,double initial,double last,boolean acceptable,boolean target,int iterations) {
        var json=KurennykhResultWriter.JSON.toJsonTree(result.runs().get(0)).getAsJsonObject();json.addProperty("id",id);json.addProperty("size",3);
        json.addProperty("initialCi",initial);json.addProperty("finalCi",last);json.addProperty("deltaCi",initial-last);json.addProperty("initiallyAcceptable",acceptable);
        json.addProperty("targetReached",target);json.addProperty("correctionRequired",!acceptable);json.addProperty("successfullyCorrected",!acceptable&&target);json.addProperty("iterations",iterations);
        return KurennykhResultWriter.JSON.fromJson(json,KurennykhRunResult.class);
    }
    private static Path copyRoot(String name) throws Exception {
        Path copy=TEMP.newFolder(name).toPath();try(var paths=Files.walk(root)){for(Path source:paths.toList()){Path target=copy.resolve(root.relativize(source));if(Files.isDirectory(source))Files.createDirectories(target);else Files.copy(source,target);}}
        return copy;
    }
}
