package MCD.consistency.experiments;

import MCD.consistency.ConsistencyMetrics;
import MCD.consistency.PairwiseComparisonMatrix;
import MCD.consistency.cli.MatrixFileReader;
import MCD.consistency.cli.MatrixInput;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.StringJoiner;

import static MCD.consistency.experiments.ExperimentFiles.require;

/** A dataset is generated once, then loaded unchanged after checking its specification and hash. */
public final class InputRepository {
    public record Case(String id, MatrixGenerator.Type type, int size, int sample, long seed) {
    }

    public record InputInfo(String profile, String caseId, MatrixGenerator.Type type, int size, int sample,
                            long masterSeed, long generationSeed, String generatorVersion,
                            MatrixGenerator.Parameters parameters, String inputFile, String sha256,
                            double initialCi, double initialK, ResearchMetrics.InitialCategory initialCategory,
                            List<Double> latentWeights, List<MatrixGenerator.Perturbation> perturbations) {
    }

    public record Prepared(InputInfo info, MatrixInput input) {
    }

    public Prepared prepare(Path root, BenchmarkProfile profile, Case specification) throws IOException {
        Path file = root.resolve("inputs/" + profile.name() + "/" + specification.id() + ".csv");
        Path metadata = file.resolveSibling(specification.id() + ".json");
        if (Files.exists(file) || Files.exists(metadata)) {
            require(Files.isRegularFile(file) && Files.isRegularFile(metadata), "Incomplete cached input: " + file);
            InputInfo info = ExperimentFiles.JSON.fromJson(ExperimentFiles.read(metadata), InputInfo.class);
            require(info.profile().equals(profile.name()) && info.caseId().equals(specification.id())
                    && info.type() == specification.type() && info.size() == specification.size() && info.sample() == specification.sample()
                    && info.masterSeed() == profile.masterSeed() && info.generationSeed() == specification.seed()
                    && info.generatorVersion().equals(MatrixGenerator.VERSION) && info.parameters().equals(profile.generator()),
                    "Cached input specification changed; use a fresh root instead of silently replacing data: " + file);
            require(info.sha256().equals(ExperimentFiles.hash(file)), "Cached input SHA-256 mismatch: " + file);
            MatrixInput input = new MatrixFileReader().read(file);
            require(Double.compare(info.initialCi(), ConsistencyMetrics.saatyIndex(input.matrix())) == 0
                    && Double.compare(info.initialK(), ConsistencyMetrics.koczkodajIndex(input.matrix()).value()) == 0,
                    "Cached initial metrics mismatch: " + file);
            return new Prepared(info, input);
        }
        var generated = new MatrixGenerator().generate(specification.type(), specification.size(), specification.seed(), profile.generator());
        writeMatrix(file, generated.matrix());
        var input = new MatrixFileReader().read(file);
        double ci = ConsistencyMetrics.saatyIndex(input.matrix());
        double k = ConsistencyMetrics.koczkodajIndex(input.matrix()).value();
        var info = new InputInfo(profile.name(), specification.id(), specification.type(), specification.size(), specification.sample(),
                profile.masterSeed(), specification.seed(), MatrixGenerator.VERSION, profile.generator(),
                root.relativize(file).toString().replace('\\', '/'), ExperimentFiles.hash(file), ci, k, ResearchMetrics.category(ci, k),
                generated.latentWeights(), generated.perturbations());
        ExperimentFiles.write(metadata, info);
        return new Prepared(info, input);
    }

    private static void writeMatrix(Path file, PairwiseComparisonMatrix matrix) throws IOException {
        Files.createDirectories(file.getParent());
        StringBuilder csv = new StringBuilder();
        for (int i = 0; i < matrix.size(); i++) {
            StringJoiner row = new StringJoiner(",");
            for (int j = 0; j < matrix.size(); j++) {
                row.add(Double.toString(matrix.get(i, j)));
            }
            csv.append(row).append('\n');
        }
        Files.writeString(file, csv, StandardCharsets.UTF_8);
    }
}
