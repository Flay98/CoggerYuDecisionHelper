package MCD.consistency.experiments;

import MCD.consistency.ConsistencyMetrics;
import MCD.consistency.cli.MatrixFileReader;
import MCD.consistency.reproduction.PublishedReferences;
import MCD.consistency.reproduction.SavedResultVerifier;
import com.google.gson.JsonObject;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/** Tiny real plan: two inputs, one cosine and two seeds per GA. Never runs REPORT in JUnit. */
public class BenchmarkInfrastructureTest {
    @ClassRule public static final TemporaryFolder TEMP = new TemporaryFolder();
    private static Path root;
    private static BenchmarkProfile profile;
    private static BenchmarkRunner.Completion completion;

    @BeforeClass public static void runTinyPlan() throws Exception {
        root=TEMP.newFolder("benchmark").toPath();
        profile=new BenchmarkProfile("SMOKE",List.of(3),1,List.of(0L,42L),20261123,10,1,1,0,3,30,10,MatrixGeneratorTest.PARAMETERS);
        ExperimentFiles.write(root.resolve("config/SMOKE.json"),profile);
        PublishedReferences.verifyIntegrity(Path.of("experiments/reproduction"));
        completion=BenchmarkRunner.run(root,"SMOKE");
    }

    @Test public void everyMethodAndSeedReceiveTheSameSavedOriginal() throws Exception {
        assertEquals(10,completion.runs().size()); assertTrue(completion.warmups().isEmpty());
        for(var input:ExperimentPlan.inputs(profile)) {
            var rows=completion.runs().stream().filter(r->r.caseId().equals(input.id())).toList();
            assertEquals(5,rows.size()); assertEquals(1,rows.stream().map(RunRecord::inputSha256).distinct().count());
            assertEquals(1,rows.stream().map(RunRecord::initialCi).distinct().count());
            assertEquals(1,rows.stream().map(RunRecord::initialK).distinct().count());
            for(var row:rows) {
                Path original=root.resolve(row.inputFile());
                Path exported=root.resolve(row.resultFile()).resolveSibling("original_matrix.csv");
                assertEquals(Files.readString(original),Files.readString(exported));
            }
        }
    }

    @Test public void allMatricesAndSavedMetricsCanBeReloadedAndRecomputed() throws Exception {
        for(var row:completion.runs()) {
            var original=new MatrixFileReader().read(root.resolve(row.inputFile())).matrix();
            SavedResultVerifier.verify(root.resolve(row.resultFile()),original);
            String name=row.configuration()==ExperimentPlan.Configuration.COSINE?"cosine_corrected.csv":"genetic_corrected.csv";
            var corrected=new MatrixFileReader().read(root.resolve(row.resultFile()).resolveSibling(name)).matrix();
            assertEquals(row.finalCi(),ConsistencyMetrics.saatyIndex(corrected),0);
            assertEquals(row.finalK(),ConsistencyMetrics.koczkodajIndex(corrected).value(),0);
            assertEquals(row.logDeviation(),ResearchMetrics.logDeviation(original,corrected),0);
            assertEquals(row.deltaK(),row.initialK()-row.finalK(),0);
            assertEquals(row.bothAcceptable(),row.ciAcceptable()&&row.kAcceptable());
        }
    }

    @Test public void rawAndHierarchicalSummariesHaveCompleteCountsAndMatchingNumbers() throws Exception {
        var summary=root.resolve("summary/SMOKE");
        var manifest=ExperimentFiles.read(summary.resolve("manifest.json"));
        assertEquals("COMPLETE",manifest.get("status").getAsString()); assertEquals(10,manifest.get("completedRuns").getAsInt());
        assertEquals(11,Files.readAllLines(summary.resolve("runs.csv")).size());
        var saved=ExperimentFiles.read(summary.resolve("runs.json")).getAsJsonArray("runs");
        assertEquals(10,saved.size());
        for(int i=0;i<10;i++) assertEquals(ExperimentFiles.JSON.toJsonTree(completion.runs().get(i)),saved.get(i));
        var aggregates=BenchmarkStatistics.bySizeAndType(completion.runs());
        assertEquals(6,aggregates.size());
        assertEquals(6,ExperimentFiles.read(summary.resolve("aggregated.json")).getAsJsonArray("rows").size());
        assertEquals(10,aggregates.stream().mapToInt(BenchmarkStatistics.Aggregate::nRuns).sum());
        for(var a:aggregates) assertEquals(1,a.nMatrices());
    }

    @Test public void perRunExportIncludesActualParametersSeedsEnvironmentAndHash() throws Exception {
        for(var row:completion.runs()) {
            var exported=ExperimentFiles.read(root.resolve(row.resultFile()).resolveSibling("run.json"));
            assertEquals(1,exported.get("schemaVersion").getAsInt());
            assertEquals(completion.planSha256(),exported.getAsJsonObject("run").get("planSha256").getAsString());
            assertTrue(exported.getAsJsonObject("environment").get("javaVersion").getAsString().startsWith("17"));
            assertEquals(1d/9,row.minimumComparison(),0); assertEquals(9,row.maximumComparison(),0);
            if(row.configuration()!=ExperimentPlan.Configuration.COSINE) {
                assertEquals(Integer.valueOf(10),row.population()); assertEquals(Integer.valueOf(64),row.maxMutationAttempts());
                assertEquals(row.configuration().mutationMode().name(),row.mutationMode()); assertNotNull(row.seed());
            }
        }
    }

    @Test public void cachedInputIsLoadedWithoutRegenerationOrRewriting() throws Exception {
        var repository=new InputRepository(); var spec=ExperimentPlan.inputs(profile).get(0);
        var first=repository.prepare(root,profile,spec);
        var modified=Files.getLastModifiedTime(first.input().path());
        var again=repository.prepare(root,profile,spec);
        assertEquals(first.info(),again.info()); assertEquals(modified,Files.getLastModifiedTime(again.input().path()));
        assertArrayEquals(first.input().matrix().toArray(),again.input().matrix().toArray());
    }

    @Test public void tamperedCachedInputCausesExplicitFailureInsteadOfSilentReplacement() throws Exception {
        var isolated=TEMP.newFolder("tamper").toPath();
        ExperimentFiles.write(isolated.resolve("config/SMOKE.json"),profile);
        var input=new InputRepository().prepare(isolated,profile,ExperimentPlan.inputs(profile).get(0));
        Files.writeString(input.input().path(),"1,1,1\n1,1,1\n1,1,1\n");
        assertThrows(IllegalStateException.class,()->BenchmarkRunner.run(isolated,"SMOKE"));
        var failure=ExperimentFiles.read(isolated.resolve("summary/SMOKE/failure.json"));
        assertTrue(failure.get("reason").getAsString().contains("SHA-256"));
        assertEquals("FAILED",ExperimentFiles.read(isolated.resolve("summary/SMOKE/manifest.json")).get("status").getAsString());
    }

    @Test public void fixedSeedReproducesAllMathematicalResultsWithoutComparingTime() throws Exception {
        var second=TEMP.newFolder("repeat").toPath();
        ExperimentFiles.write(second.resolve("config/SMOKE.json"),profile);
        var repeated=BenchmarkRunner.run(second,"SMOKE");
        assertEquals(completion.planSha256(),repeated.planSha256());
        for(int i=0;i<completion.runs().size();i++) {
            JsonObject a=ExperimentFiles.JSON.toJsonTree(completion.runs().get(i)).getAsJsonObject();
            JsonObject b=ExperimentFiles.JSON.toJsonTree(repeated.runs().get(i)).getAsJsonObject();
            a.remove("elapsedMillis"); b.remove("elapsedMillis"); assertEquals(a,b);
        }
    }

    @Test public void seedsAreAveragedWithinMatricesBeforePopulationAggregation() {
        var rows=List.of(fake("matrix1","r1",.1,true),fake("matrix1","r2",.3,false),fake("matrix2","r3",.8,false));
        var stats=BenchmarkStatistics.overall(rows).get(0);
        assertEquals(2,stats.nMatrices()); assertEquals(3,stats.nRuns()); assertEquals(1,stats.successfulRuns());
        assertEquals(.5,stats.meanFinalK(),1e-15); assertEquals(.5,stats.medianFinalK(),1e-15);
        assertEquals(.25,stats.ownSuccessRate(),0); assertEquals(1,stats.anySeedSuccessfulMatrices());
        assertEquals(0,stats.allSeedsSuccessfulMatrices());
        assertEquals(Math.sqrt(.18),stats.sampleSdFinalK(),1e-15);
        assertEquals(2,stats.initiallyBothViolated());
    }

    @Test public void unsuccessfulRunsRemainInMeansAndSuccessDenominators() {
        var rows=List.of(fake("matrix1","r1",.2,true),fake("matrix2","r2",.9,false));
        var stats=BenchmarkStatistics.bySizeAndType(rows).get(0);
        assertEquals(.55,stats.meanFinalK(),1e-15); assertEquals(.5,stats.ownSuccessRate(),0);
        assertEquals(2,stats.nRuns()); assertEquals(1,stats.successfulRuns());
    }

    @Test public void warmupsDuplicateRunsAndMixedPlansCannotEnterStatistics() {
        var row=fake("matrix","run",.2,true);
        assertThrows(IllegalStateException.class,()->BenchmarkStatistics.overall(List.of(row,row)));
        var json=ExperimentFiles.JSON.toJsonTree(row).getAsJsonObject(); json.addProperty("phase","WARMUP");
        var warmup=ExperimentFiles.JSON.fromJson(json,RunRecord.class);
        assertThrows(IllegalStateException.class,()->BenchmarkStatistics.overall(List.of(warmup)));
        json=ExperimentFiles.JSON.toJsonTree(row).getAsJsonObject();json.addProperty("runId","different");json.addProperty("planSha256","other");
        var other=ExperimentFiles.JSON.fromJson(json,RunRecord.class);
        assertThrows(IllegalStateException.class,()->BenchmarkStatistics.overall(List.of(row,other)));
    }

    @Test public void mismatchedInputHashesAreRejectedAcrossMethods() {
        var row=fake("matrix","r1",.2,true);
        var json=ExperimentFiles.JSON.toJsonTree(row).getAsJsonObject();json.addProperty("runId","r2");json.addProperty("inputSha256","altered");
        var other=ExperimentFiles.JSON.fromJson(json,RunRecord.class);
        assertThrows(IllegalStateException.class,()->BenchmarkStatistics.overall(List.of(row,other)));
    }

    @Test public void undefinedRelativeMetricsHaveExplicitMissingCountsAndCsvNa() throws Exception {
        var json=ExperimentFiles.JSON.toJsonTree(fake("m","r",.2,true)).getAsJsonObject();json.add("relativeCi",com.google.gson.JsonNull.INSTANCE);
        var row=ExperimentFiles.JSON.fromJson(json,RunRecord.class);
        var stats=BenchmarkStatistics.overall(List.of(row)).get(0);
        assertNull(stats.meanRelativeCi());assertEquals(0,stats.nRelativeCiMatrices());assertNull(stats.sampleSdFinalK());
        Path csv=TEMP.newFile("nulls.csv").toPath();ExperimentFiles.csv(csv,List.of(row));
        assertTrue(Files.readString(csv).contains(",NA,"));
    }

    @Test public void mediansAndSampleStandardDeviationHaveKnownValues() {
        double[] values={9,1,3,7}; assertEquals(5,BenchmarkStatistics.median(values),0);
        assertArrayEquals(new double[]{9,1,3,7},values,0); // median must not mutate data
        assertEquals(2,BenchmarkStatistics.sampleSd(new double[]{1,3,5}),0);
        assertNull(BenchmarkStatistics.sampleSd(new double[]{1}));
    }

    @Test public void reproductionReferencesAndOutputsAreNotWrittenByComparison() throws Exception {
        PublishedReferences.verifyIntegrity(Path.of("experiments/reproduction"));
        try(var files=Files.walk(root)) {
            assertFalse(files.anyMatch(p->p.toString().contains("reproduction")));
        }
    }

    @Test public void archivedResultsCanBeAuditedWithoutRerunningOptimizers() throws Exception {
        assertEquals(10,BenchmarkVerifier.verify(root,"SMOKE"));
    }

    @Test public void archivedAuditDetectsTamperingInRunMetadata() throws Exception {
        Path copy=TEMP.newFolder("audit-tamper").toPath();
        try(var paths=Files.walk(root)) {
            for(Path source:paths.toList()) {
                Path destination=copy.resolve(root.relativize(source));
                if(Files.isDirectory(source)) Files.createDirectories(destination);
                else Files.copy(source,destination);
            }
        }
        Path file=copy.resolve(completion.runs().get(0).resultFile()).resolveSibling("run.json");
        var json=ExperimentFiles.read(file);json.getAsJsonObject("run").addProperty("finalCi",999);
        ExperimentFiles.write(file,json);
        assertThrows(IllegalStateException.class,()->BenchmarkVerifier.verify(copy,"SMOKE"));
    }

    private static RunRecord fake(String matrix,String run,double k,boolean success) {
        // Explicitly synthetic summary fixtures, never saved as experimental results.
        var template=completion.runs().stream().filter(r->r.configuration()==ExperimentPlan.Configuration.GA_UPPER_ONLY).findFirst().orElseThrow();
        var json=ExperimentFiles.JSON.toJsonTree(template).getAsJsonObject();
        json.addProperty("caseId",matrix); json.addProperty("runId",run); json.addProperty("initialCategory","BOTH_VIOLATED");
        json.addProperty("finalK",k);json.addProperty("ownSuccess",success);json.addProperty("kAcceptable",success);
        json.addProperty("bothAcceptable",false);json.addProperty("alreadyAcceptable",false);
        json.addProperty("status",success?"TARGET_REACHED":"MAX_GENERATIONS");
        return ExperimentFiles.JSON.fromJson(json,RunRecord.class);
    }
}
