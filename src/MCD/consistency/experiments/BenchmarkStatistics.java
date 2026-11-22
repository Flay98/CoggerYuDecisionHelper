package MCD.consistency.experiments;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

/** Seeds are averaged inside each matrix before aggregating independent matrix units. */
public final class BenchmarkStatistics {
    private BenchmarkStatistics() {
    }

    public record Aggregate(String scope, String profile, int size, String type, String configuration, String initialCategory,
                            String caseId, int nMatrices, int nRuns, long successfulRuns, long allSeedsSuccessfulMatrices,
                            long anySeedSuccessfulMatrices, double ownSuccessRate, double ciAcceptableRate,
                            double kAcceptableRate, double bothAcceptableRate, double alreadyAcceptableRate, double noChangesRate,
                            double meanFinalCi, double medianFinalCi, double meanFinalK, double medianFinalK,
                            double meanDeltaCi, double medianDeltaCi, double meanDeltaK, double medianDeltaK,
                            double meanChangedComparisons, double meanLogDeviation, double meanElapsedMillis,
                            double medianElapsedMillis, double meanSteps, Double sampleSdFinalCi, Double sampleSdFinalK,
                            Double sampleSdElapsedMillis, Double meanRelativeCi, Double meanRelativeK,
                            int nRelativeCiMatrices, int nRelativeKMatrices,
                            int initiallyBothAcceptable, int initiallyCiOnlyViolation,
                            int initiallyKOnlyViolation, int initiallyBothViolated) {
    }

    public static List<Aggregate> perMatrix(List<RunRecord> rows) {
        return group(rows, "MATRIX", r -> r.caseId() + "/" + r.configuration(), true);
    }

    public static List<Aggregate> bySizeAndType(List<RunRecord> rows) {
        return group(rows, "SIZE_TYPE", r -> r.size() + "/" + r.type() + "/" + r.configuration(), false);
    }

    public static List<Aggregate> byInitialCategory(List<RunRecord> rows) {
        return group(rows, "INITIAL_CATEGORY", r -> r.size() + "/" + r.type() + "/" + r.configuration() + "/" + r.initialCategory(), false);
    }

    public static List<Aggregate> overall(List<RunRecord> rows) {
        return group(rows, "OVERALL", r -> r.configuration().name(), false);
    }

    private static List<Aggregate> group(List<RunRecord> rows, String scope,
                                         java.util.function.Function<RunRecord, String> key, boolean perMatrix) {
        Map<String, List<RunRecord>> groups = new LinkedHashMap<>();
        java.util.Set<String> ids = new java.util.HashSet<>();
        Map<String, RunRecord> inputs = new LinkedHashMap<>();
        for (RunRecord row : rows) {
            ExperimentFiles.require(row.phase().equals("MEASURED"), "Warmup must not enter measured statistics");
            ExperimentFiles.require(ids.add(row.runId()), "Duplicate measured run ID");
            ExperimentFiles.require(row.profile().equals(rows.get(0).profile())
                    && row.planSha256().equals(rows.get(0).planSha256()), "Mixed experimental plans");
            RunRecord original = inputs.putIfAbsent(row.caseId(), row);
            if (original != null) {
                ExperimentFiles.require(original.inputSha256().equals(row.inputSha256())
                        && original.type() == row.type() && original.size() == row.size()
                        && original.initialCategory() == row.initialCategory()
                        && original.initialCi() == row.initialCi() && original.initialK() == row.initialK(),
                        "Methods/seeds must share identical input data");
            }
            groups.computeIfAbsent(key.apply(row), ignored -> new ArrayList<>()).add(row);
        }
        List<Aggregate> result = new ArrayList<>();
        for (List<RunRecord> group : groups.values()) {
            Map<String, List<RunRecord>> matrices = new LinkedHashMap<>();
            for (RunRecord row : group) {
                matrices.computeIfAbsent(row.caseId(), ignored -> new ArrayList<>()).add(row);
            }
            List<List<RunRecord>> units = List.copyOf(matrices.values());
            RunRecord first = group.get(0);
            boolean overall = scope.equals("OVERALL");
            double[] ci = values(units, RunRecord::finalCi), k = values(units, RunRecord::finalK);
            double[] deltaCi = values(units, RunRecord::deltaCi), deltaK = values(units, RunRecord::deltaK);
            double[] elapsed = values(units, RunRecord::elapsedMillis);
            var relativeCi = relativeValues(units, true);
            var relativeK = relativeValues(units, false);
            result.add(new Aggregate(scope, first.profile(), overall ? 0 : first.size(), overall ? "ALL" : first.type().name(),
                    first.configuration().name(), scope.equals("INITIAL_CATEGORY") || perMatrix ? first.initialCategory().name() : "ALL",
                    perMatrix ? first.caseId() : "ALL", units.size(), group.size(), group.stream().filter(RunRecord::ownSuccess).count(),
                    units.stream().filter(unit -> unit.stream().allMatch(RunRecord::ownSuccess)).count(),
                    units.stream().filter(unit -> unit.stream().anyMatch(RunRecord::ownSuccess)).count(),
                    mean(values(units, row -> row.ownSuccess() ? 1 : 0)), mean(values(units, row -> row.ciAcceptable() ? 1 : 0)),
                    mean(values(units, row -> row.kAcceptable() ? 1 : 0)), mean(values(units, row -> row.bothAcceptable() ? 1 : 0)),
                    mean(values(units, row -> row.alreadyAcceptable() ? 1 : 0)), mean(values(units, row -> row.noChanges() ? 1 : 0)),
                    mean(ci), median(perMatrix ? raw(group, RunRecord::finalCi) : ci),
                    mean(k), median(perMatrix ? raw(group, RunRecord::finalK) : k),
                    mean(deltaCi), median(perMatrix ? raw(group, RunRecord::deltaCi) : deltaCi),
                    mean(deltaK), median(perMatrix ? raw(group, RunRecord::deltaK) : deltaK),
                    mean(values(units, RunRecord::changedComparisons)), mean(values(units, RunRecord::logDeviation)), mean(elapsed),
                    median(perMatrix ? raw(group, RunRecord::elapsedMillis) : elapsed), mean(values(units, RunRecord::steps)),
                    sampleSd(perMatrix ? raw(group, RunRecord::finalCi) : ci), sampleSd(perMatrix ? raw(group, RunRecord::finalK) : k),
                    sampleSd(perMatrix ? raw(group, RunRecord::elapsedMillis) : elapsed),
                    relativeCi.length == 0 ? null : mean(relativeCi), relativeK.length == 0 ? null : mean(relativeK),
                    relativeCi.length, relativeK.length, categoryCount(units, ResearchMetrics.InitialCategory.BOTH_ACCEPTABLE),
                    categoryCount(units, ResearchMetrics.InitialCategory.CI_ONLY_VIOLATION),
                    categoryCount(units, ResearchMetrics.InitialCategory.K_ONLY_VIOLATION),
                    categoryCount(units, ResearchMetrics.InitialCategory.BOTH_VIOLATED)));
        }
        return List.copyOf(result);
    }

    private static int categoryCount(List<List<RunRecord>> units, ResearchMetrics.InitialCategory category) {
        return (int) units.stream().filter(unit -> unit.get(0).initialCategory() == category).count();
    }

    private static double[] relativeValues(List<List<RunRecord>> units, boolean ci) {
        List<Double> values = new ArrayList<>();
        for (var unit : units) {
            var applicable = unit.stream().map(row -> ci ? row.relativeCi() : row.relativeK()).filter(java.util.Objects::nonNull).toList();
            if (!applicable.isEmpty()) {
                values.add(applicable.stream().mapToDouble(Double::doubleValue).average().orElseThrow());
            }
        }
        return values.stream().mapToDouble(Double::doubleValue).toArray();
    }

    private static double[] values(List<List<RunRecord>> units, ToDoubleFunction<RunRecord> function) {
        return units.stream().mapToDouble(unit -> mean(raw(unit, function))).toArray();
    }

    private static double[] raw(List<RunRecord> rows, ToDoubleFunction<RunRecord> function) {
        return rows.stream().mapToDouble(function).toArray();
    }

    public static double mean(double[] values) {
        return java.util.Arrays.stream(values).average().orElseThrow();
    }

    public static double median(double[] values) {
        if (values.length == 0) throw new IllegalArgumentException("Empty statistics sample");
        double[] sorted = values.clone();
        java.util.Arrays.sort(sorted);
        return sorted[(sorted.length - 1) / 2] / 2 + sorted[sorted.length / 2] / 2;
    }

    public static Double sampleSd(double[] values) {
        if (values.length < 2) return null;
        double mean = mean(values), squares = 0;
        for (double value : values) squares = Math.fma(value - mean, value - mean, squares);
        return Math.sqrt(squares / (values.length - 1));
    }
}
