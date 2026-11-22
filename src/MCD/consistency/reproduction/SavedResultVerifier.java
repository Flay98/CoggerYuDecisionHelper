package MCD.consistency.reproduction;

import MCD.consistency.ConsistencyMetrics;
import MCD.consistency.PairwiseComparisonMatrix;
import MCD.consistency.cli.MatrixFileReader;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Path;

import static MCD.consistency.reproduction.ReproductionFiles.require;

/** Reopens exported data; never assumes that successful writing proves numerical correctness. */
public final class SavedResultVerifier {
    private SavedResultVerifier() {
    }

    public static void verify(Path resultFile, PairwiseComparisonMatrix expectedOriginal) throws IOException {
        var root = ReproductionFiles.readJson(resultFile);
        require(root.get("schemaVersion").getAsInt() == 1, "Unknown result schema");
        var reader = new MatrixFileReader();
        var original = reader.read(resultFile.getParent().resolve(root.get("originalMatrixCsv").getAsString())).matrix();
        same(original, expectedOriginal);
        same(reader.read(resultFile).matrix(), expectedOriginal);
        verifyMatrixJson(root.getAsJsonArray("matrix"), original);
        verifyMetrics(root.getAsJsonObject("initialMetrics"), original);
        for (var entry : root.getAsJsonObject("results").entrySet()) {
            var method = entry.getValue().getAsJsonObject();
            var matrix = reader.read(resultFile.getParent().resolve(method.get("matrixCsv").getAsString())).matrix();
            // Constructor checks positive/finite entries, unit diagonal and reciprocity.
            new PairwiseComparisonMatrix(matrix.toArray());
            verifyMatrixJson(method.getAsJsonArray("matrix"), matrix);
            verifyMetrics(method.getAsJsonObject("initialMetrics"), original);
            verifyMetrics(method.getAsJsonObject("finalMetrics"), matrix);
            int changed = 0;
            for (int i = 0; i < matrix.size(); i++) {
                for (int j = i + 1; j < matrix.size(); j++) {
                    if (matrix.get(i, j) != original.get(i, j)) {
                        changed++;
                    }
                }
            }
            require(changed == method.get("changedComparisons").getAsInt(), "Changed-comparison count mismatch");
            if (entry.getKey().equals("genetic")) {
                require(method.getAsJsonArray("history").size() == method.get("generations").getAsInt() + 1,
                        "Genetic history length mismatch");
                var last = method.getAsJsonArray("history").get(method.getAsJsonArray("history").size() - 1).getAsJsonObject();
                equal(last.get("bestK").getAsDouble(), method.getAsJsonObject("finalMetrics").get("k").getAsDouble(), "Last genetic K");
            } else if (entry.getKey().equals("cosine")) {
                require(method.getAsJsonArray("history").size() == method.get("iterations").getAsInt(), "Cosine history length mismatch");
            }
            // No cross-objective assertion: GA need not decrease CI; cosine need not decrease K.
        }
    }

    private static void verifyMetrics(JsonObject metrics, PairwiseComparisonMatrix matrix) {
        equal(metrics.get("ci").getAsDouble(), ConsistencyMetrics.saatyIndex(matrix), "Saved CI");
        equal(metrics.get("k").getAsDouble(), ConsistencyMetrics.koczkodajIndex(matrix).value(), "Saved K");
    }

    private static void verifyMatrixJson(JsonArray rows, PairwiseComparisonMatrix matrix) {
        require(rows.size() == matrix.size(), "JSON matrix size mismatch");
        for (int i = 0; i < matrix.size(); i++) {
            require(rows.get(i).getAsJsonArray().size() == matrix.size(), "JSON matrix row mismatch");
            for (int j = 0; j < matrix.size(); j++) {
                equal(rows.get(i).getAsJsonArray().get(j).getAsDouble(), matrix.get(i, j), "JSON/CSV element");
            }
        }
    }

    private static void same(PairwiseComparisonMatrix actual, PairwiseComparisonMatrix expected) {
        require(actual.size() == expected.size(), "Wrong original matrix dimension");
        for (int i = 0; i < expected.size(); i++) {
            for (int j = 0; j < expected.size(); j++) {
                equal(actual.get(i, j), expected.get(i, j), "Original matrix element");
            }
        }
    }

    private static void equal(double actual, double expected, String label) {
        require(Double.isFinite(actual) && Double.compare(actual, expected) == 0,
                label + " mismatch: " + actual + " != " + expected);
    }
}
