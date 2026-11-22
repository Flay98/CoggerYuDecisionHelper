package MCD.consistency.cli;

import MCD.Main;
import MCD.consistency.ConsistencyMetrics;
import MCD.consistency.PairwiseComparisonMatrix;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static MCD.consistency.cli.MatrixReadersTest.assertMatricesEqual;
import static org.junit.Assert.*;

public class ConsistencyCliTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void helpAndEmptyCommandPrintUsageWithoutFiles() {
        for (String[] arguments : new String[][] {{"--help"}, {}}) {
            Command result = run(arguments);
            assertEquals(0, result.code());
            assertTrue(result.out().contains("runConsistency"));
            assertTrue(result.out().contains("independently"));
            assertEquals("", result.err());
        }
    }

    @Test
    public void reportsInvalidArgumentsInputsAndOutputDirectoriesWithNonzeroCodes() throws IOException {
        assertEquals(2, run("--unknown").code());
        assertEquals(2, run("--input").code());
        assertEquals(2, run("--method", "cosine").code());
        Command missing = run("--input", temporary.getRoot().toPath().resolve("missing.csv").toString());
        assertEquals(1, missing.code());
        assertTrue(missing.err().contains("File not found"));
        Path bad = temporary.newFile("bad.csv").toPath();
        Files.writeString(bad, "1,bad\n0.5,1");
        Command invalid = run("--input", bad.toString(), "--method", "cosine");
        assertEquals(2, invalid.code());
        assertTrue(invalid.err().contains("row 1, column 2"));
        Path outputFile = temporary.newFile("not-a-directory").toPath();
        assertEquals(1, run("--input", example("consistent_3x3.csv"), "--method", "cosine",
                "--output-dir", outputFile.toString()).code());
    }

    @Test
    public void cosineOnlyProducesOnlyItsOwnResultAndRoundTripsEveryMatrix() throws IOException {
        Path directory = temporary.newFolder("cosine").toPath();
        Command command = run("--input", example("inconsistent_3x3.csv"), "--method", "cosine", "--output-dir", directory.toString());
        assertEquals(command.err(), 0, command.code());
        assertTrue(command.out().contains("COSINE CORRECTION"));
        assertFalse(command.out().contains("GENETIC ALGORITHM"));
        assertTrue(command.out().contains("TARGET_REACHED"));
        assertTrue(Files.isRegularFile(directory.resolve("original_matrix.csv")));
        assertTrue(Files.isRegularFile(directory.resolve("cosine_corrected.csv")));
        assertFalse(Files.exists(directory.resolve("genetic_corrected.csv")));
        JsonObject root = json(directory);
        assertEquals(1, root.get("schemaVersion").getAsInt());
        assertEquals(0, root.get("indexBase").getAsInt());
        assertEquals(3, root.get("size").getAsInt());
        assertEquals("cosine", root.getAsJsonArray("methods").get(0).getAsString());
        assertFalse(root.getAsJsonObject("results").has("genetic"));
        JsonObject cosine = root.getAsJsonObject("results").getAsJsonObject("cosine");
        assertEquals(1, cosine.get("iterations").getAsInt());
        assertEquals(1, cosine.get("changedComparisons").getAsInt());
        JsonObject change = cosine.getAsJsonArray("history").get(0).getAsJsonObject();
        assertEquals(0, change.get("i").getAsInt());
        assertEquals(2, change.get("k").getAsInt());
        assertEquals(1, change.get("via").getAsInt());
        assertEquals(6, change.get("newValue").getAsDouble(), 0);
        assertEquals(5, change.get("delta").getAsDouble(), 0);
        checkSavedMatricesAndMetrics(directory, root);
    }

    @Test
    public void paperGeneticExampleReachesTheTargetThroughTheActualCli() throws IOException {
        Path directory = temporary.newFolder("genetic").toPath();
        Command command = run("--input", example("sayadi_4x4.json"), "--method", "genetic", "--output-dir", directory.toString(),
                "--seed", "42", "--min-comparison", "0.1111111111111111", "--max-comparison", "9");
        assertEquals(command.err(), 0, command.code());
        assertFalse(command.out().contains("COSINE CORRECTION"));
        assertTrue(command.out().contains("GENETIC ALGORITHM"));
        assertTrue(command.out().contains("Seed: 42"));
        assertFalse(Files.exists(directory.resolve("cosine_corrected.csv")));
        JsonObject root = json(directory);
        assertEquals("Sayadi-Janicki 2026: quantitative example", root.get("name").getAsString());
        JsonObject genetic = root.getAsJsonObject("results").getAsJsonObject("genetic");
        assertEquals("TARGET_REACHED", genetic.get("status").getAsString());
        assertEquals(0.55, root.getAsJsonObject("initialMetrics").get("k").getAsDouble(), 1e-14);
        assertTrue(genetic.getAsJsonObject("finalMetrics").get("k").getAsDouble() < 0.3);
        assertEquals(42, genetic.get("seed").getAsLong());
        assertEquals(1000, genetic.getAsJsonObject("parameters").get("populationSize").getAsInt());
        assertEquals(genetic.get("generations").getAsInt() + 1, genetic.getAsJsonArray("history").size());
        assertEquals(1000 + 900L * genetic.get("generations").getAsInt(), genetic.get("evaluatedIndividuals").getAsLong());
        checkSavedMatricesAndMetrics(directory, root);
    }

    @Test
    public void bothAlgorithmsRunIndependentlyAndMatchSeparateSeededRuns() throws IOException {
        MatrixInput input = new MatrixFileReader().read(Path.of(example("inconsistent_3x3.csv")));
        CliArguments bothArgs = arguments("both", "inconsistent_3x3.csv");
        AnalysisResult both = new AnalysisRunner().analyze(input, bothArgs);
        AnalysisResult genetic = new AnalysisRunner().analyze(input, arguments("genetic", "inconsistent_3x3.csv"));
        AnalysisResult cosine = new AnalysisRunner().analyze(input, arguments("cosine", "inconsistent_3x3.csv"));
        assertSame(input.matrix(), both.cosine().result().original());
        assertSame(input.matrix(), both.genetic().result().original());
        assertEquals(5.0 / 6, both.genetic().result().initialK(), 1e-14);
        assertEquals(0, both.cosine().result().finalK(), 1e-14);
        assertMatricesEqual(genetic.genetic().result().best(), both.genetic().result().best());
        assertEquals(genetic.genetic().result().history(), both.genetic().result().history());
        assertMatricesEqual(cosine.cosine().result().corrected(), both.cosine().result().corrected());
        assertEquals(cosine.cosine().result().history(), both.cosine().result().history());
        Path directory = temporary.newFolder("both").toPath();
        new ResultWriter().write(both, directory);
        assertEquals(2, json(directory).getAsJsonObject("results").size());
        checkSavedMatricesAndMetrics(directory, json(directory));
    }

    @Test
    public void cosineCorrectlyLeavesThePaperExampleUnchangedAtDefaultCi() throws IOException {
        MatrixInput input = new MatrixFileReader().read(Path.of(example("sayadi_4x4.csv")));
        AnalysisResult result = new AnalysisRunner().analyze(input, arguments("both", "sayadi_4x4.csv"));
        assertEquals("ALREADY_ACCEPTABLE", result.cosine().result().status().name());
        assertEquals(0, result.cosine().result().iterations());
        assertEquals(0, result.cosine().result().changedComparisons());
        assertSame(input.matrix(), result.cosine().result().corrected());
        assertEquals(0.55, result.genetic().result().initialK(), 1e-14);
    }

    @Test
    public void repeatedCliRunsAreIdenticalExceptForElapsedTimes() throws IOException {
        Path directory = temporary.newFolder("repeat").toPath();
        String[] args = {"--input", example("inconsistent_3x3.csv"), "--method", "both", "--output-dir", directory.toString(),
                "--seed", "42", "--population", "40", "--max-generations", "4"};
        assertEquals(0, run(args).code());
        JsonObject first = json(directory);
        String firstCsv = Files.readString(directory.resolve("genetic_corrected.csv"));
        assertEquals(0, run(args).code());
        JsonObject second = json(directory);
        for (String method : new String[] {"cosine", "genetic"}) {
            first.getAsJsonObject("results").getAsJsonObject(method).remove("elapsedMillis");
            second.getAsJsonObject("results").getAsJsonObject(method).remove("elapsedMillis");
        }
        assertEquals(first, second);
        assertEquals(firstCsv, Files.readString(directory.resolve("genetic_corrected.csv")));
    }

    @Test
    public void csvRoundTripPreservesFullDoublePrecisionAndIsLocaleIndependent() throws IOException {
        Path path = temporary.newFile("precise.csv").toPath();
        double ratio = Math.nextUp(1.2345678901234567);
        Files.writeString(path, "1," + ratio + "\n" + (1 / ratio) + ",1\n");
        Path directory = temporary.newFolder("precision").toPath();
        // Avoid global locale mutation: serialization itself is asserted to contain decimal dots.
        Command command = run("--input", path.toString(), "--method", "cosine", "--output-dir", directory.toString());
        assertEquals(command.err(), 0, command.code());
        PairwiseComparisonMatrix reloaded = new MatrixFileReader().read(directory.resolve("original_matrix.csv")).matrix();
        assertEquals(Double.doubleToLongBits(ratio), Double.doubleToLongBits(reloaded.get(0, 1)));
        assertTrue(Files.readString(directory.resolve("original_matrix.csv")).contains(Double.toString(ratio)));
        checkSavedMatricesAndMetrics(directory, json(directory));
    }

    @Test
    public void fullLongSeedSurvivesJsonWithoutDoubleRounding() throws IOException {
        Path directory = temporary.newFolder("long-seed").toPath();
        Command command = run("--input", example("consistent_3x3.csv"), "--method", "genetic", "--seed", Long.toString(Long.MAX_VALUE),
                "--output-dir", directory.toString());
        assertEquals(command.err(), 0, command.code());
        assertEquals(Long.MAX_VALUE, json(directory).getAsJsonObject("results").getAsJsonObject("genetic").get("seed").getAsLong());
    }

    @Test
    public void refusesToOverwriteInputIncludingThroughASymlink() throws IOException {
        Path directory = temporary.newFolder("input-protection").toPath();
        Path input = directory.resolve("original_matrix.csv");
        Files.copy(Path.of(example("consistent_3x3.csv")), input);
        String original = Files.readString(input);
        Command command = run("--input", input.toString(), "--method", "cosine", "--output-dir", directory.toString());
        assertEquals(2, command.code());
        assertTrue(command.err().contains("overwrite the input"));
        assertEquals(original, Files.readString(input));
        Path linkedDirectory = temporary.newFolder("symlink-protection").toPath();
        Files.createSymbolicLink(linkedDirectory.resolve("original_matrix.csv"), input);
        assertEquals(2, run("--input", input.toString(), "--method", "cosine", "--output-dir", linkedDirectory.toString()).code());
        assertEquals(original, Files.readString(input));
    }

    @Test
    public void legacyMainStillRunsItsOriginalConsoleInASeparateProcess() throws Exception {
        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        String classes = Path.of(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        ProcessBuilder builder = new ProcessBuilder(java.toString(), "-Dfile.encoding=UTF-8", "-cp", classes, "MCD.Main")
                .redirectErrorStream(true);
        // Child encoding is explicit, independently of the parent/system locale.
        builder.environment().put("LC_ALL", "C.UTF-8");
        builder.environment().put("LANG", "C.UTF-8");
        Process process = builder.start();
        try {
            process.getOutputStream().write("1\n".getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().close();
            assertTrue("Original console did not exit", process.waitFor(10, TimeUnit.SECONDS));
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(output, 0, process.exitValue());
            assertTrue(output, output.contains("1 - Выход"));
            assertFalse(output.contains("PAIRWISE COMPARISON MATRIX ANALYSIS"));
        } finally {
            process.destroyForcibly();
        }
    }

    private CliArguments arguments(String method, String file) {
        return CliArguments.parse(new String[] {"--input", example(file), "--method", method, "--seed", "42",
                "--min-comparison", "0.1111111111111111", "--max-comparison", "9", "--population", "40", "--max-generations", "4"});
    }

    private static void checkSavedMatricesAndMetrics(Path directory, JsonObject root) throws IOException {
        PairwiseComparisonMatrix original = new MatrixFileReader().read(directory.resolve("original_matrix.csv")).matrix();
        // result.json itself can be opened as JSON matrix input (root matrix is the original).
        assertMatricesEqual(original, new MatrixFileReader().read(directory.resolve("result.json")).matrix());
        checkJsonMatrix(root.getAsJsonArray("matrix"), original);
        assertEquals(ConsistencyMetrics.saatyIndex(original), root.getAsJsonObject("initialMetrics").get("ci").getAsDouble(), 0);
        assertEquals(ConsistencyMetrics.koczkodajIndex(original).value(), root.getAsJsonObject("initialMetrics").get("k").getAsDouble(), 0);
        for (String method : root.getAsJsonObject("results").keySet()) {
            JsonObject result = root.getAsJsonObject("results").getAsJsonObject(method);
            PairwiseComparisonMatrix corrected = new MatrixFileReader().read(directory.resolve(result.get("matrixCsv").getAsString())).matrix();
            checkJsonMatrix(result.getAsJsonArray("matrix"), corrected);
            assertEquals(ConsistencyMetrics.saatyIndex(corrected), result.getAsJsonObject("finalMetrics").get("ci").getAsDouble(), 0);
            assertEquals(ConsistencyMetrics.koczkodajIndex(corrected).value(), result.getAsJsonObject("finalMetrics").get("k").getAsDouble(), 0);
            assertEquals(root.getAsJsonObject("initialMetrics"), result.getAsJsonObject("initialMetrics"));
            assertTrue(result.get("elapsedMillis").getAsDouble() >= 0);
        }
    }

    private static void checkJsonMatrix(JsonArray rows, PairwiseComparisonMatrix matrix) {
        assertEquals(matrix.size(), rows.size());
        for (int i = 0; i < matrix.size(); i++) {
            assertEquals(matrix.size(), rows.get(i).getAsJsonArray().size());
            for (int j = 0; j < matrix.size(); j++) {
                assertEquals(matrix.get(i, j), rows.get(i).getAsJsonArray().get(j).getAsDouble(), 0);
            }
        }
    }

    private static String example(String name) {
        return Path.of("examples", "matrices", name).toString();
    }

    private static JsonObject json(Path directory) throws IOException {
        return JsonParser.parseString(Files.readString(directory.resolve("result.json"))).getAsJsonObject();
    }

    private static Command run(String... args) {
        var output = new ByteArrayOutputStream();
        var errors = new ByteArrayOutputStream();
        int code = ConsistencyCli.run(args, new PrintStream(output, true, StandardCharsets.UTF_8),
                new PrintStream(errors, true, StandardCharsets.UTF_8));
        return new Command(code, output.toString(StandardCharsets.UTF_8), errors.toString(StandardCharsets.UTF_8));
    }

    private record Command(int code, String out, String err) {
    }
}
