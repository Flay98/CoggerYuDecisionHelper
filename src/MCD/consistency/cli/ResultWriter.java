package MCD.consistency.cli;

import MCD.consistency.PairwiseComparisonMatrix;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/** Saves only the selected methods; round-trip CSV precision uses Double.toString. */
public final class ResultWriter {
    public void write(AnalysisResult analysis, Path directory) throws IOException {
        List<String> files = new ArrayList<>(List.of("original_matrix.csv", "result.json"));
        if (analysis.cosine() != null) {
            files.add("cosine_corrected.csv");
        }
        if (analysis.genetic() != null) {
            files.add("genetic_corrected.csv");
        }
        // Protect the input even if the output directory or file is a symlink.
        for (String file : files) {
            Path target = directory.resolve(file);
            if (target.toAbsolutePath().normalize().equals(analysis.input().path().toAbsolutePath().normalize())
                    || (Files.exists(target) && Files.isSameFile(target, analysis.input().path()))) {
                throw new IllegalArgumentException("Output would overwrite the input file: " + target + "; choose another --output-dir");
            }
        }
        Files.createDirectories(directory);
        writeCsv(analysis.input().matrix(), directory.resolve("original_matrix.csv"));
        if (analysis.cosine() != null) {
            writeCsv(analysis.cosine().result().corrected(), directory.resolve("cosine_corrected.csv"));
        }
        if (analysis.genetic() != null) {
            writeCsv(analysis.genetic().result().best(), directory.resolve("genetic_corrected.csv"));
        }
        Files.writeString(directory.resolve("result.json"), new ResultJson().format(analysis), StandardCharsets.UTF_8);
    }

    private static void writeCsv(PairwiseComparisonMatrix matrix, Path target) throws IOException {
        StringBuilder csv = new StringBuilder();
        for (int i = 0; i < matrix.size(); i++) {
            StringJoiner row = new StringJoiner(",");
            for (int j = 0; j < matrix.size(); j++) {
                row.add(Double.toString(matrix.get(i, j)));
            }
            csv.append(row).append('\n');
        }
        Files.writeString(target, csv, StandardCharsets.UTF_8);
    }
}
