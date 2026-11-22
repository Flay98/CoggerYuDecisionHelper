package MCD.consistency.reproduction;

import MCD.consistency.ConsistencyMetrics;
import MCD.consistency.PairwiseComparisonMatrix;
import MCD.consistency.cli.MatrixFileReader;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static MCD.consistency.reproduction.ReproductionFiles.require;

/** Read-only transcriptions pinned independently of generated outputs. */
public final class PublishedReferences {
    private static final Map<String, String> HASHES = Map.of(
            ReproductionPlan.PUBLISHED_INPUT, "2a93af5acb9bc1971c87215ba84b88d8d04d2b2ca8b49e53dabb31b72f62e007",
            "reference/sayadi_2026_corrected.json", "35466b9a5bd6ca658d28a30f5d5a41266170df2566198bbea889bb82ab6edfd9",
            "reference/kurennykh_2018_table1.csv", "c291a1817a2c9ede3ce9b818f6b26c78cd1da7043f6da07dd8a8bc956fb0bfba",
            "reference/sources.json", "8f386545c28443cfa50c58ecc2018caa8bb309aabadec19e893a314367b0f799");

    private PublishedReferences() {
    }

    public static void verifyIntegrity(Path root) throws IOException {
        for (var entry : HASHES.entrySet()) {
            require(entry.getValue().equals(ReproductionFiles.sha256(root.resolve(entry.getKey()))),
                    "Published reference changed: " + entry.getKey() + "; outputs must never replace references");
        }
    }

    public static Map<String, String> pinnedHashes() {
        return HASHES;
    }

    public static List<MatrixCheck> verifyNumbers(Path root) throws IOException {
        verifyIntegrity(root);
        var original = new MatrixFileReader().read(root.resolve(ReproductionPlan.PUBLISHED_INPUT)).matrix();
        var reference = ReproductionFiles.readJson(root.resolve("reference/sayadi_2026_corrected.json"));
        var rows = reference.getAsJsonArray("matrix");
        double[][] data = new double[rows.size()][];
        for (int i = 0; i < data.length; i++) {
            data[i] = new double[rows.get(i).getAsJsonArray().size()];
            for (int j = 0; j < data[i].length; j++) {
                data[i][j] = rows.get(i).getAsJsonArray().get(j).getAsDouble();
            }
        }
        // Explicitly preserve printed 0.7714/0.4881, rather than silently repairing them.
        double tolerance = reference.get("validationTolerance").getAsDouble();
        var corrected = new PairwiseComparisonMatrix(data, tolerance);
        var checks = List.of(check("SJ2026_ORIGINAL", original, 0.55, 0, null),
                check("SJ2026_PUBLISHED_CORRECTION", corrected, reference.get("reportedK").getAsDouble(),
                        changed(original, corrected), reference.get("reportedGenerations").getAsInt()));
        require(checks.get(1).changedComparisonsFromMatrices() == 2, "Printed matrices should differ in two independent comparisons");
        return checks;
    }

    private static MatrixCheck check(String id, PairwiseComparisonMatrix matrix, double reportedK,
                                     int changed, Integer reportedGenerations) {
        double core = ConsistencyMetrics.koczkodajIndex(matrix).value();
        double independent = independentReferenceK(matrix);
        require(Math.abs(core - reportedK) < 1e-12 && Math.abs(independent - reportedK) < 1e-12,
                "Published K does not match independent/core verification: " + id);
        return new MatrixCheck(id, reportedK, core, independent, ConsistencyMetrics.saatyIndex(matrix),
                false, core - reportedK, matrix.validationTolerance(), changed, reportedGenerations);
    }

    /**
     * Reference-only independent check: direct published expression in decimal
     * arithmetic (34 significant digits), without the core's logarithms/expm1.
     * This is NOT a second fitness implementation used by the genetic algorithm.
     */
    static double independentReferenceK(PairwiseComparisonMatrix matrix) {
        MathContext precision = MathContext.DECIMAL128;
        BigDecimal maximum = BigDecimal.ZERO;
        for (int i = 0; i < matrix.size(); i++) {
            for (int j = i + 1; j < matrix.size(); j++) {
                for (int k = j + 1; k < matrix.size(); k++) {
                    BigDecimal product = BigDecimal.valueOf(matrix.get(i, j)).multiply(BigDecimal.valueOf(matrix.get(j, k)), precision);
                    BigDecimal direct = BigDecimal.valueOf(matrix.get(i, k));
                    BigDecimal local = BigDecimal.ONE.subtract(product.divide(direct, precision)).abs()
                            .min(BigDecimal.ONE.subtract(direct.divide(product, precision)).abs());
                    maximum = maximum.max(local);
                }
            }
        }
        return maximum.doubleValue();
    }

    private static int changed(PairwiseComparisonMatrix original, PairwiseComparisonMatrix corrected) {
        int count = 0;
        for (int i = 0; i < original.size(); i++) {
            for (int j = i + 1; j < original.size(); j++) {
                if (original.get(i, j) != corrected.get(i, j)) {
                    count++;
                }
            }
        }
        return count;
    }

    public record MatrixCheck(String id, double reportedK, double coreK, double independentDecimalK,
                              double computedCi, boolean ciReportedInArticle, double errorFromReportedK,
                              double validationTolerance, int changedComparisonsFromMatrices, Integer reportedGenerations) {
    }
}
