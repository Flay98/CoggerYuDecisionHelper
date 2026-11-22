package MCD.consistency.reproduction;

import MCD.consistency.CosineCorrectionOptions;
import MCD.consistency.GeneticMutationMode;
import MCD.consistency.cli.AnalysisRunner;
import MCD.consistency.cli.CliArguments;
import MCD.consistency.cli.MatrixFileReader;
import MCD.consistency.cli.ResultWriter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static MCD.consistency.reproduction.ReproductionFiles.require;
import static MCD.consistency.reproduction.ReproductionSummary.*;

/** Bounded article verification: one published GA input, two modes, ten seeds, three own cosine inputs. */
public final class ArticleReproduction {
    public static void main(String[] args) throws IOException {
        if (args.length > 1) {
            throw new IllegalArgumentException("Usage: reproduceArticles [experiments/reproduction]");
        }
        Path directory = Path.of(args.length == 0 ? "experiments/reproduction" : args[0]);
        var report = run(directory);
        System.out.println("Article reproduction: " + directory.toAbsolutePath().normalize());
        for (var summary : report.summaries()) {
            System.out.printf(Locale.ROOT, "%s: %d/%d reached target, mean generations=%.3f, mean K=%.12g%n",
                    summary.mutationMode(), summary.successes(), summary.runs(), summary.meanGenerations(), summary.meanFinalK());
        }
        System.out.println("Own cosine checks: " + report.ownCosineRuns().size());
        System.out.println("All exported matrices and metrics reloaded and verified; see summary/report.md");
    }

    public static Report run(Path directory) throws IOException {
        Path root = directory.toRealPath();
        var reference = PublishedReferences.verifyNumbers(root);
        protectReferences(root);
        var reader = new MatrixFileReader();
        var original = reader.read(root.resolve(ReproductionPlan.PUBLISHED_INPUT));
        // Validate all own inputs before starting any write; none are called author data.
        for (var own : ReproductionPlan.OWN_CASES) {
            reader.read(root.resolve(own.input()));
        }
        var runner = new AnalysisRunner();
        var writer = new ResultWriter();
        List<GeneticRunRow> gaRows = new ArrayList<>();
        List<CosineRunRow> ownRows = new ArrayList<>();
        var publishedCorrection = reference.get(1);
        for (GeneticMutationMode mode : GeneticMutationMode.values()) {
            for (long seed : ReproductionPlan.SEEDS) {
                String id = ReproductionPlan.geneticId(seed, mode);
                Path output = root.resolve("outputs/2026/" + mode.name() + "/seed_" + seed);
                var options = ReproductionPlan.geneticOptions(seed, mode);
                var arguments = new CliArguments(original.path(), output, CliArguments.Method.GENETIC,
                        CosineCorrectionOptions.defaults(), options, false);
                // Only published INPUT goes to the algorithm, never the corrected reference.
                var analysis = runner.analyze(original, arguments);
                writer.write(analysis, output);
                SavedResultVerifier.verify(output.resolve("result.json"), original.matrix());
                metadata(root, output, id, "PUBLISHED_INPUT_2026", ReproductionPlan.PUBLISHED_INPUT);
                var g = analysis.genetic().result();
                gaRows.add(new GeneticRunRow(id, "PUBLISHED_INPUT_2026", ReproductionPlan.PUBLISHED_INPUT,
                        ReproductionFiles.sha256(original.path()), mode, seed, options.populationSize(), options.maxGenerations(),
                        options.targetK(), options.minimumComparison(), options.maximumComparison(), options.maxMutationAttempts(),
                        g.generations(), g.initialK(), g.finalK(), g.initialCi(), g.finalCi(), g.status().name(),
                        g.changedComparisons(), g.evaluatedIndividuals(), analysis.genetic().elapsedMillis(),
                        publishedCorrection.reportedK(), publishedCorrection.reportedGenerations(), publishedCorrection.changedComparisonsFromMatrices(),
                        g.finalK() - publishedCorrection.reportedK(), g.generations() - publishedCorrection.reportedGenerations(),
                        g.changedComparisons() - publishedCorrection.changedComparisonsFromMatrices(), relative(root, output.resolve("result.json"))));
            }
        }
        for (var own : ReproductionPlan.OWN_CASES) {
            var input = reader.read(root.resolve(own.input()));
            Path output = root.resolve("outputs/2018_own/" + own.id());
            var arguments = new CliArguments(input.path(), output, CliArguments.Method.COSINE,
                    CosineCorrectionOptions.defaults(), ReproductionPlan.geneticOptions(0, GeneticMutationMode.UPPER_ONLY), false);
            var analysis = runner.analyze(input, arguments);
            writer.write(analysis, output);
            SavedResultVerifier.verify(output.resolve("result.json"), input.matrix());
            metadata(root, output, own.id(), "OWN_INDEPENDENT_CHECK_NOT_AUTHOR_DATA", own.input());
            var c = analysis.cosine().result();
            if (own.id().equals("K2018_OWN_ILLUSTRATIVE_3")) {
                require(Math.abs(c.initialCi() - 0.183720900491) < 1e-12 && c.iterations() == 1
                        && c.corrected().get(0, 2) == 6 && c.finalCi() < 1e-12, "Own illustrative control failed");
            }
            ownRows.add(new CosineRunRow(own.id(), "OWN_INDEPENDENT_CHECK_NOT_AUTHOR_DATA", own.input(),
                    ReproductionFiles.sha256(input.path()), c.initialCi(), c.finalCi(), c.initialK(), c.finalK(),
                    c.iterations(), c.changedComparisons(), c.status().name(), analysis.cosine().elapsedMillis(),
                    relative(root, output.resolve("result.json"))));
        }
        PublishedReferences.verifyIntegrity(root);
        ReproductionSummary.write(root.resolve("summary"), gaRows, ownRows, reference);
        writeManifest(root, gaRows, ownRows);
        return new Report(List.copyOf(gaRows), List.copyOf(ownRows), summarize(gaRows));
    }

    private static void metadata(Path root, Path output, String id, String origin, String input) throws IOException {
        ReproductionFiles.writeJson(output.resolve("experiment.json"), new ExperimentMetadata(1, id, origin,
                input, ReproductionFiles.sha256(root.resolve(input)), relative(root, output.resolve("result.json"))));
    }

    private static void writeManifest(Path root, List<GeneticRunRow> ga, List<CosineRunRow> own) throws IOException {
        Map<String, String> hashes = new LinkedHashMap<>();
        PublishedReferences.pinnedHashes().keySet().stream().sorted().forEach(path -> hashes.put(path, PublishedReferences.pinnedHashes().get(path)));
        for (var entry : ReproductionPlan.OWN_CASES) {
            hashes.put(entry.input(), ReproductionFiles.sha256(root.resolve(entry.input())));
        }
        ReproductionFiles.writeJson(root.resolve("summary/run_manifest.json"), new Manifest(1, "reproduction-v1",
                ReproductionPlan.SEEDS, List.of(GeneticMutationMode.values()), 1000, 100, 0.3, 1.0 / 9, 9, 64,
                hashes, ga.stream().map(GeneticRunRow::experimentId).toList(), own.stream().map(CosineRunRow::experimentId).toList(),
                System.getProperty("java.version"), System.getProperty("os.name"), System.getProperty("os.arch")));
    }

    /** No writes through symlinks or hard links into either protected data tree. */
    private static void protectReferences(Path root) throws IOException {
        List<Path> protectedFiles = new ArrayList<>();
        for (String name : List.of("inputs", "reference")) {
            try (var paths = Files.walk(root.resolve(name))) {
                protectedFiles.addAll(paths.filter(Files::isRegularFile).toList());
            }
        }
        for (String name : List.of("outputs", "summary")) {
            Path path = root.resolve(name);
            if (!Files.exists(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            try (var paths = Files.walk(path)) {
                for (Path node : paths.toList()) {
                    require(!Files.isSymbolicLink(node), "Generated output tree must not contain symbolic links: " + node);
                    if (Files.isRegularFile(node)) {
                        for (Path source : protectedFiles) {
                            require(!Files.isSameFile(node, source), "Output is a hard link to protected input/reference: " + node);
                        }
                    }
                }
            }
        }
    }

    private static String relative(Path root, Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }

    public record Report(List<GeneticRunRow> geneticRuns, List<CosineRunRow> ownCosineRuns, List<ModeSummary> summaries) {
    }

    private record ExperimentMetadata(int schemaVersion, String experimentId, String origin,
                                       String inputFile, String inputSha256, String resultFile) {
    }

    private record Manifest(int schemaVersion, String planVersion, List<Long> seeds, List<GeneticMutationMode> mutationModes,
                             int populationSize, int maxGenerations, double targetK, double minimumComparison, double maximumComparison,
                             int maxMutationAttempts, Map<String, String> protectedDataSha256, List<String> geneticExperimentIds,
                             List<String> ownExperimentIds, String javaVersion, String osName, String osArch) {
    }
}
