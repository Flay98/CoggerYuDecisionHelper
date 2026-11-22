package MCD.consistency.experiments;

import MCD.consistency.ConsistencyMetrics;
import MCD.consistency.cli.MatrixFileReader;
import MCD.consistency.reproduction.SavedResultVerifier;
import com.google.gson.JsonElement;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static MCD.consistency.experiments.ExperimentFiles.require;

/** Revalidate archived results using the Java core without executing any optimization. */
public final class BenchmarkVerifier {
    private BenchmarkVerifier() { }

    public static void main(String[] args) throws IOException {
        if (args.length > 2) throw new IllegalArgumentException("Usage: BenchmarkVerifier [profile] [comparison-root]");
        Path root = Path.of(args.length > 1 ? args[1] : "experiments/comparison");
        String profile = args.length > 0 ? args[0] : "REPORT";
        System.out.println(profile + ": reloaded and verified " + verify(root, profile) + " measured/warmup results");
    }

    public static int verify(Path root, String profile) throws IOException {
        Path summary = root.resolve("summary/" + profile);
        var manifest = ExperimentFiles.read(summary.resolve("manifest.json"));
        require(manifest.get("status").getAsString().equals("COMPLETE"), "Cannot verify an incomplete profile");
        String planHash = manifest.get("planSha256").getAsString();
        var plan = ExperimentFiles.JSON.fromJson(ExperimentFiles.read(summary.resolve("plan.json")), BenchmarkProfile.class);
        require(planHash.equals(ExperimentFiles.hash(ExperimentFiles.JSON.toJson(plan).getBytes(java.nio.charset.StandardCharsets.UTF_8))), "Archived plan hash mismatch");
        List<RunRecord> measured = readRows(summary.resolve("runs.json"));
        List<RunRecord> warmups = readRows(summary.resolve("warmup.json"));
        require(measured.size() == plan.runCount() && measured.size() == manifest.get("completedRuns").getAsInt(), "Missing saved runs");
        require(warmups.size() == plan.warmupRounds() * 3 && warmups.size() == manifest.get("warmupRuns").getAsInt(), "Missing saved warmups");
        var schedule = ExperimentPlan.schedule(plan);
        for (int i = 0; i < measured.size(); i++) {
            var expected = schedule.get(i); var row = measured.get(i);
            require(row.runId().equals(expected.runId()) && row.configuration() == expected.configuration()
                    && java.util.Objects.equals(row.seed(), expected.seed()) && row.caseId().equals(expected.input().id())
                    && row.phase().equals("MEASURED"), "Saved schedule mismatch");
        }
        for (RunRecord row : java.util.stream.Stream.concat(measured.stream(), warmups.stream()).toList()) {
            require(row.profile().equals(profile) && row.planSha256().equals(planHash), "Mixed saved profiles/plans");
            require(ExperimentFiles.hash(root.resolve(row.inputFile())).equals(row.inputSha256()), "Saved input SHA mismatch");
            var original = new MatrixFileReader().read(root.resolve(row.inputFile())).matrix();
            Path result = root.resolve(row.resultFile());
            SavedResultVerifier.verify(result, original);
            var exported = ExperimentFiles.read(result.resolveSibling("run.json"));
            require(ExperimentFiles.JSON.toJsonTree(row).equals(exported.get("run")), "run.json and summary disagree");
            boolean cosine = row.configuration() == ExperimentPlan.Configuration.COSINE;
            var corrected = new MatrixFileReader().read(result.resolveSibling(cosine ? "cosine_corrected.csv" : "genetic_corrected.csv")).matrix();
            require(row.initialCi() == ConsistencyMetrics.saatyIndex(original)
                    && row.initialK() == ConsistencyMetrics.koczkodajIndex(original).value()
                    && row.finalCi() == ConsistencyMetrics.saatyIndex(corrected)
                    && row.finalK() == ConsistencyMetrics.koczkodajIndex(corrected).value(), "Run metrics differ from saved matrices");
            require(row.logDeviation() == ResearchMetrics.logDeviation(original, corrected), "Saved deviation mismatch");
            boolean ciOk = ResearchMetrics.acceptableCi(row.finalCi()), kOk = ResearchMetrics.acceptableK(row.finalK());
            require(row.ciAcceptable() == ciOk && row.kAcceptable() == kOk && row.bothAcceptable() == (ciOk && kOk)
                    && row.ownSuccess() == (cosine ? ciOk : kOk), "Saved criterion flags mismatch");
            require(row.deltaCi() == row.initialCi() - row.finalCi() && row.deltaK() == row.initialK() - row.finalK()
                    && java.util.Objects.equals(row.relativeCi(), ResearchMetrics.relativeChange(row.initialCi(), row.finalCi()))
                    && java.util.Objects.equals(row.relativeK(), ResearchMetrics.relativeChange(row.initialK(), row.finalK())), "Saved improvements mismatch");
            require(row.minimumComparison() == 1.0/9 && row.maximumComparison() == 9 && row.targetCi() == .1 && row.targetK() == .3,
                    "Saved common comparison settings mismatch");
            if (cosine) require(row.maxIterations() == plan.maxIterations(), "Saved cosine limit mismatch");
            else require(row.population() == plan.population() && row.maxGenerations() == plan.maxGenerations()
                    && row.maxMutationAttempts() == 64 && row.mutationMode().equals(row.configuration().mutationMode().name()), "Saved GA settings mismatch");
            for (int i = 0; i < corrected.size(); i++) for (int j = 0; j < corrected.size(); j++) {
                require(MatrixGenerator.inRange(corrected.get(i, j)), "Saved result outside common scale");
            }
        }
        compare(summary, "per_matrix", BenchmarkStatistics.perMatrix(measured));
        compare(summary, "aggregated", BenchmarkStatistics.bySizeAndType(measured));
        compare(summary, "by_initial_category", BenchmarkStatistics.byInitialCategory(measured));
        compare(summary, "overall", BenchmarkStatistics.overall(measured));
        return measured.size() + warmups.size();
    }

    private static List<RunRecord> readRows(Path file) throws IOException {
        List<RunRecord> rows = new ArrayList<>();
        for (JsonElement row : ExperimentFiles.read(file).getAsJsonArray("runs")) rows.add(ExperimentFiles.JSON.fromJson(row, RunRecord.class));
        return rows;
    }

    private static void compare(Path summary, String name, List<BenchmarkStatistics.Aggregate> expected) throws IOException {
        require(ExperimentFiles.JSON.toJsonTree(expected).equals(ExperimentFiles.read(summary.resolve(name + ".json")).get("rows")), "Saved aggregate mismatch: " + name);
    }
}
