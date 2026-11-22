package MCD.consistency.reproduction;

import MCD.consistency.GeneticMutationMode;
import MCD.consistency.cli.CliArguments;
import MCD.consistency.cli.ConsistencyCli;
import MCD.consistency.cli.MatrixFileReader;
import com.google.gson.JsonObject;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.*;

public class ArticleReproductionTest {
    @ClassRule public static TemporaryFolder temporary = new TemporaryFolder();
    private static Path root;
    private static ArticleReproduction.Report report;

    @BeforeClass
    public static void executeTheFixedPlanOnceForTheSuite() throws IOException {
        root = copyData("verification");
        report = ArticleReproduction.run(root);
    }

    @Test
    public void checksPublishedNumbersIndependentlyAndKeepsPrintedRoundoff() throws IOException {
        var checks = PublishedReferences.verifyNumbers(root);
        assertEquals(2, checks.size());
        assertEquals(0.55, checks.get(0).coreK(), 1e-14);
        assertEquals(0.55, checks.get(0).independentDecimalK(), 0);
        assertEquals(0.2317, checks.get(1).coreK(), 1e-14);
        assertEquals(0.2317, checks.get(1).independentDecimalK(), 0);
        assertEquals(2, checks.get(1).reportedGenerations().intValue());
        assertEquals(2, checks.get(1).changedComparisonsFromMatrices());
        assertFalse(checks.get(1).ciReportedInArticle());
        assertEquals(0.003539913871672, checks.get(1).computedCi(), 1e-12);
        assertEquals(1e-4, checks.get(1).validationTolerance(), 0);
        var source = ReproductionFiles.readJson(root.resolve("reference/sayadi_2026_corrected.json"));
        assertEquals(0.7714, source.getAsJsonArray("matrix").get(1).getAsJsonArray().get(0).getAsDouble(), 0);
    }

    @Test
    public void planIsFixedAndAllSeedsArePresentExactlyOncePerMode() {
        assertEquals(List.of(0L, 1L, 2L, 3L, 4L, 5L, 10L, 42L, 100L, 2026L), ReproductionPlan.SEEDS);
        assertEquals(20, report.geneticRuns().size());
        assertEquals(20, new HashSet<>(report.geneticRuns().stream().map(ReproductionSummary.GeneticRunRow::experimentId).toList()).size());
        for (GeneticMutationMode mode : GeneticMutationMode.values()) {
            var group = report.geneticRuns().stream().filter(row -> row.mutationMode() == mode).toList();
            assertEquals(ReproductionPlan.SEEDS, group.stream().map(ReproductionSummary.GeneticRunRow::seed).toList());
            for (var run : group) {
                assertEquals(1000, run.populationSize());
                assertEquals(100, run.maxGenerations());
                assertEquals(0.3, run.targetK(), 0);
                assertEquals(1.0 / 9, run.minimumComparison(), 0);
                assertEquals(9, run.maximumComparison(), 0);
                assertEquals(64, run.maxMutationAttempts());
                assertEquals("PUBLISHED_INPUT_2026", run.origin());
                assertEquals(ReproductionPlan.PUBLISHED_INPUT, run.inputFile());
                assertEquals(0.55, run.initialK(), 1e-14);
                assertTrue(run.finalK() <= run.initialK()); // elitism, not CI optimization
                assertEquals(run.finalK() - 0.2317, run.deltaK(), 0);
                assertEquals(run.generations() - 2, run.deltaGenerations());
                assertEquals(run.changedComparisons() - 2, run.deltaChangedComparisons());
                assertEquals(run.finalK() < 0.3, run.status().equals("TARGET_REACHED"));
            }
        }
    }

    @Test
    public void reopensEverySavedMatrixAndConfirmsTheSavedMode() throws IOException {
        var original = new MatrixFileReader().read(root.resolve(ReproductionPlan.PUBLISHED_INPUT)).matrix();
        for (var run : report.geneticRuns()) {
            Path path = root.resolve(run.resultFile());
            SavedResultVerifier.verify(path, original);
            var json = ReproductionFiles.readJson(path);
            var genetic = json.getAsJsonObject("results").getAsJsonObject("genetic");
            assertEquals(run.mutationMode().name(), genetic.getAsJsonObject("parameters").get("mutationMode").getAsString());
            assertEquals(run.seed(), genetic.get("seed").getAsLong());
            assertEquals(run.finalK(), genetic.getAsJsonObject("finalMetrics").get("k").getAsDouble(), 0);
            assertEquals(run.elapsedMillis(), genetic.get("elapsedMillis").getAsDouble(), 0);
            var metadata = ReproductionFiles.readJson(path.getParent().resolve("experiment.json"));
            assertEquals(run.experimentId(), metadata.get("experimentId").getAsString());
        }
        for (var run : report.ownCosineRuns()) {
            SavedResultVerifier.verify(root.resolve(run.resultFile()), new MatrixFileReader().read(root.resolve(run.inputFile())).matrix());
        }
    }

    @Test
    public void own2018ChecksAreNeverLabelledAsPublishedMatrices() throws IOException {
        assertEquals(3, report.ownCosineRuns().size());
        for (var row : report.ownCosineRuns()) {
            assertEquals("OWN_INDEPENDENT_CHECK_NOT_AUTHOR_DATA", row.origin());
        }
        var illustrative = report.ownCosineRuns().get(0);
        assertEquals("K2018_OWN_ILLUSTRATIVE_3", illustrative.experimentId());
        assertEquals(0.183720900491, illustrative.initialCi(), 1e-12);
        assertEquals(1, illustrative.iterations());
        assertEquals(0, illustrative.finalCi(), 1e-12);
        var result = ReproductionFiles.readJson(root.resolve(illustrative.resultFile()));
        var change = result.getAsJsonObject("results").getAsJsonObject("cosine").getAsJsonArray("history").get(0).getAsJsonObject();
        assertEquals(6, change.get("newValue").getAsDouble(), 0);
        var sources = ReproductionFiles.readJson(root.resolve("reference/sources.json"));
        assertFalse(sources.getAsJsonObject("kurennykh2018").get("originalMatricesProvided").getAsBoolean());
        assertFalse(sources.getAsJsonObject("kurennykh2018").get("illustrative3x3IsAnAuthorExample").getAsBoolean());
    }

    @Test
    public void publishedTableIsKeptAsDataAndNotPresentedAsOurMeasurements() throws IOException {
        var lines = Files.readAllLines(root.resolve("reference/kurennykh_2018_table1.csv"));
        assertEquals(9, lines.size());
        assertEquals("3,0.276,14.754,41.328,1.507,0.104,100", lines.get(1));
        assertEquals("10,53.328,448.334,127.737,38.434,0.728,283", lines.get(8));
        assertTrue(Files.readString(root.resolve("summary/report.md")).contains("Точное воспроизведение этой таблицы по публикации невозможно"));
        PublishedReferences.verifyIntegrity(root);
    }

    @Test
    public void rejectsReplacingAPublishedReferenceWithOneOfOurResultsBeforeAnyWrites() throws IOException {
        Path copy = copyData("reference-replacement");
        Files.copy(root.resolve(report.geneticRuns().get(0).resultFile()), copy.resolve("reference/sayadi_2026_corrected.json"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        var error = assertThrows(IllegalStateException.class, () -> ArticleReproduction.run(copy));
        assertTrue(error.getMessage().contains("Published reference changed"));
        assertFalse(Files.exists(copy.resolve("outputs")));
        assertFalse(Files.exists(copy.resolve("summary")));
        PublishedReferences.verifyIntegrity(root);
    }

    @Test
    public void rejectsOutputLinksToTheProtectedReferenceTree() throws IOException {
        Path copy = copyData("reference-symlink");
        Files.createSymbolicLink(copy.resolve("outputs"), copy.resolve("reference"));
        assertThrows(IllegalStateException.class, () -> ArticleReproduction.run(copy));
        PublishedReferences.verifyIntegrity(copy);
        Path hardLink = copyData("reference-hardlink");
        Files.createDirectories(hardLink.resolve("outputs"));
        Files.createLink(hardLink.resolve("outputs/not-a-generated-result.json"), hardLink.resolve("reference/sayadi_2026_corrected.json"));
        assertThrows(IllegalStateException.class, () -> ArticleReproduction.run(hardLink));
        PublishedReferences.verifyIntegrity(hardLink);
    }

    @Test
    public void catchesTamperedMetricsAndMatricesAfterExport() throws IOException {
        var run = report.geneticRuns().get(0);
        Path copy = temporary.newFolder("tampered-run").toPath();
        try (var paths = Files.list(root.resolve(run.resultFile()).getParent())) {
            for (Path path : paths.toList()) {
                Files.copy(path, copy.resolve(path.getFileName()));
            }
        }
        var original = new MatrixFileReader().read(root.resolve(ReproductionPlan.PUBLISHED_INPUT)).matrix();
        JsonObject json = ReproductionFiles.readJson(copy.resolve("result.json"));
        json.getAsJsonObject("results").getAsJsonObject("genetic").getAsJsonObject("finalMetrics").addProperty("k", 0.99);
        ReproductionFiles.writeJson(copy.resolve("result.json"), json);
        assertThrows(IllegalStateException.class, () -> SavedResultVerifier.verify(copy.resolve("result.json"), original));
        Files.copy(root.resolve(run.resultFile()), copy.resolve("result.json"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Files.copy(copy.resolve("original_matrix.csv"), copy.resolve("genetic_corrected.csv"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        assertThrows(IllegalStateException.class, () -> SavedResultVerifier.verify(copy.resolve("result.json"), original));
    }

    @Test
    public void tablesAndSummariesAgreeWithAllRunRows() throws IOException {
        var csvRuns = Files.readAllLines(root.resolve("summary/ga_runs.csv"));
        var csvModes = Files.readAllLines(root.resolve("summary/ga_mode_summary.csv"));
        assertEquals(21, csvRuns.size());
        assertEquals(3, csvModes.size());
        var runHeader = java.util.Arrays.asList(csvRuns.get(0).split(",", -1));
        for (int i = 0; i < report.geneticRuns().size(); i++) {
            var row = report.geneticRuns().get(i);
            String[] cells = csvRuns.get(i + 1).split(",", -1);
            assertEquals(runHeader.size(), cells.length);
            assertEquals(row.experimentId(), cells[runHeader.indexOf("experimentId")]);
            assertEquals(row.mutationMode().name(), cells[runHeader.indexOf("mutationMode")]);
            assertEquals(row.seed(), Long.parseLong(cells[runHeader.indexOf("seed")]));
            assertEquals(row.finalK(), Double.parseDouble(cells[runHeader.indexOf("finalK")]), 0);
            assertEquals(row.finalCi(), Double.parseDouble(cells[runHeader.indexOf("finalCi")]), 0);
            assertEquals(row.elapsedMillis(), Double.parseDouble(cells[runHeader.indexOf("elapsedMillis")]), 0);
            assertEquals(row.deltaGenerations(), Integer.parseInt(cells[runHeader.indexOf("deltaGenerations")]));
        }
        var modeHeader = java.util.Arrays.asList(csvModes.get(0).split(",", -1));
        for (int i = 0; i < report.summaries().size(); i++) {
            var summary = report.summaries().get(i);
            String[] cells = csvModes.get(i + 1).split(",", -1);
            assertEquals(modeHeader.size(), cells.length);
            assertEquals(summary.mutationMode().name(), cells[modeHeader.indexOf("mutationMode")]);
            assertEquals(summary.meanFinalK(), Double.parseDouble(cells[modeHeader.indexOf("meanFinalK")]), 0);
            assertEquals(summary.meanGenerations(), Double.parseDouble(cells[modeHeader.indexOf("meanGenerations")]), 0);
            assertEquals(summary.successes(), Long.parseLong(cells[modeHeader.indexOf("successes")]));
        }
        assertEquals(4, Files.readAllLines(root.resolve("summary/cosine_own_runs.csv")).size());
        var json = ReproductionFiles.readJson(root.resolve("summary/ga_runs.json"));
        assertEquals(20, json.getAsJsonArray("runs").size());
        for (int i = 0; i < 20; i++) {
            assertEquals(report.geneticRuns().get(i).finalK(), json.getAsJsonArray("runs").get(i).getAsJsonObject().get("finalK").getAsDouble(), 0);
        }
        for (var summary : report.summaries()) {
            var group = report.geneticRuns().stream().filter(row -> row.mutationMode() == summary.mutationMode()).toList();
            assertEquals(10, summary.runs());
            assertEquals(group.stream().filter(row -> row.finalK() < 0.3).count(), summary.successes());
            assertEquals(group.stream().mapToDouble(ReproductionSummary.GeneticRunRow::finalK).sum() / group.size(), summary.meanFinalK(), 1e-15);
            assertEquals(group.stream().mapToInt(ReproductionSummary.GeneticRunRow::generations).sum() / (double) group.size(), summary.meanGenerations(), 0);
            assertEquals(group.stream().mapToInt(ReproductionSummary.GeneticRunRow::changedComparisons).sum() / (double) group.size(), summary.meanChangedComparisons(), 0);
        }
        var manifest = ReproductionFiles.readJson(root.resolve("summary/run_manifest.json"));
        assertEquals(10, manifest.getAsJsonArray("seeds").size());
        assertEquals(20, manifest.getAsJsonArray("geneticExperimentIds").size());
        assertEquals(3, manifest.getAsJsonArray("ownExperimentIds").size());
    }

    @Test
    public void failedSeedsAreIncludedInSummaryDenominators() {
        var rows = new java.util.ArrayList<>(report.geneticRuns());
        var r = rows.stream().filter(row -> row.mutationMode() == GeneticMutationMode.UPPER_ONLY && row.seed() == 42).findFirst().orElseThrow();
        assertTrue(r.finalK() < 0.3);
        rows.set(rows.indexOf(r), new ReproductionSummary.GeneticRunRow(r.experimentId(), r.origin(), r.inputFile(), r.inputSha256(),
                r.mutationMode(), r.seed(), r.populationSize(), r.maxGenerations(), r.targetK(), r.minimumComparison(), r.maximumComparison(),
                r.maxMutationAttempts(), 100, r.initialK(), 0.55, r.initialCi(), r.initialCi(), "MAX_GENERATIONS", 0, 91000, r.elapsedMillis(),
                r.reportedK(), r.reportedGenerations(), r.referenceChangedComparisons(), 0.55 - 0.2317, 98, -2, r.resultFile()));
        var changed = ReproductionSummary.summarize(rows).get(0);
        assertEquals(10, changed.runs());
        assertEquals(report.summaries().get(0).successes() - 1, changed.successes());
        assertEquals(changed.successes() / 10.0, changed.successRate(), 0);
        assertEquals(100, changed.maxGenerations());
    }

    @Test
    public void mutationModeIsParsedAndRecordedByTheCli() throws IOException {
        var parsed = CliArguments.parse(new String[] {"--input", "matrix.csv", "--mutation-mode", "bidirectional"});
        assertEquals(GeneticMutationMode.BIDIRECTIONAL, parsed.geneticOptions().mutationMode());
        assertThrows(IllegalArgumentException.class, () -> CliArguments.parse(new String[] {"--input", "matrix.csv", "--mutation-mode", "other"}));
        Path output = temporary.newFolder("mode-cli").toPath();
        var stdout = new ByteArrayOutputStream();
        var stderr = new ByteArrayOutputStream();
        int code = ConsistencyCli.run(new String[] {"--input", root.resolve(ReproductionPlan.PUBLISHED_INPUT).toString(),
                "--method", "genetic", "--mutation-mode", "BIDIRECTIONAL", "--population", "20", "--max-generations", "0",
                "--genetic-target-k", "0", "--min-comparison", "0.1111111111111111", "--max-comparison", "9", "--output-dir", output.toString()},
                new PrintStream(stdout, true, StandardCharsets.UTF_8), new PrintStream(stderr, true, StandardCharsets.UTF_8));
        assertEquals(stderr.toString(StandardCharsets.UTF_8), 0, code);
        assertTrue(stdout.toString(StandardCharsets.UTF_8).contains("Mutation mode: BIDIRECTIONAL"));
        assertEquals("BIDIRECTIONAL", ReproductionFiles.readJson(output.resolve("result.json")).getAsJsonObject("results")
                .getAsJsonObject("genetic").getAsJsonObject("parameters").get("mutationMode").getAsString());
    }

    private static Path copyData(String name) throws IOException {
        Path target = temporary.newFolder(name).toPath();
        Path source = Path.of("experiments/reproduction");
        for (String tree : List.of("inputs", "reference")) {
            try (var paths = Files.walk(source.resolve(tree))) {
                for (Path file : paths.filter(Files::isRegularFile).toList()) {
                    Path output = target.resolve(source.relativize(file));
                    Files.createDirectories(output.getParent());
                    Files.copy(file, output);
                }
            }
        }
        return target;
    }
}
