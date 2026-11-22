package MCD.consistency.experiments;

import MCD.consistency.CosineCorrectionOptions;
import MCD.consistency.GeneticCorrectionOptions;
import MCD.consistency.GeneticMutationMode;
import MCD.consistency.cli.AnalysisResult;
import MCD.consistency.cli.AnalysisRunner;
import MCD.consistency.cli.CliArguments;
import MCD.consistency.cli.ResultWriter;
import MCD.consistency.reproduction.SavedResultVerifier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static MCD.consistency.experiments.ExperimentFiles.require;
import static MCD.consistency.experiments.ExperimentPlan.Configuration;

/** Executes bounded plans using existing algorithms/exports; no optimizer is reimplemented here. */
public final class BenchmarkRunner {
    private static final long MAX_OUTPUT_BYTES = 512L * 1024 * 1024;

    public static void main(String[] args) throws IOException {
        if (args.length < 1 || args.length > 2) throw new IllegalArgumentException("Usage: BenchmarkRunner SMOKE|PILOT|REPORT|FULL [comparison-root]");
        var result = run(Path.of(args.length == 2 ? args[1] : "experiments/comparison"), args[0]);
        System.out.printf(java.util.Locale.ROOT, "%s COMPLETE: inputs=%d measured=%d warmups=%d wall=%.3f s%n",
                result.profile().name(), result.profile().inputCount(), result.runs().size(), result.warmups().size(), result.wallSeconds());
    }

    public record Completion(BenchmarkProfile profile, String planSha256, List<RunRecord> runs,
                              List<RunRecord> warmups, double wallSeconds) {
    }

    public static BenchmarkProfile loadProfile(Path root, String name) throws IOException {
        require(List.of("SMOKE", "PILOT", "REPORT", "FULL").contains(name), "Unknown benchmark profile: " + name);
        var profile = ExperimentFiles.JSON.fromJson(ExperimentFiles.read(root.resolve("config/" + name + ".json")), BenchmarkProfile.class);
        require(profile.name().equals(name), "Profile name and file disagree");
        return profile;
    }

    public static Completion run(Path directory, String name) throws IOException {
        Path root = directory.toRealPath();
        BenchmarkProfile profile = loadProfile(root, name);
        String planHash = ExperimentFiles.hash(ExperimentFiles.JSON.toJson(profile).getBytes(StandardCharsets.UTF_8));
        Path summary = root.resolve("summary/" + name);
        Path output = root.resolve("outputs/" + name + "/" + planHash.substring(0, 16));
        // Prevent writes through links into the independent reproduction data or elsewhere.
        for (String tree : List.of("config", "inputs", "outputs", "summary")) rejectLinks(root.resolve(tree));
        Files.createDirectories(summary);
        Files.createDirectories(output);
        var environment = BenchmarkEnvironment.capture();
        ExperimentFiles.write(summary.resolve("plan.json"), profile);
        ExperimentFiles.write(summary.resolve("plan." + planHash + ".json"), profile);
        ExperimentFiles.write(summary.resolve("environment.json"), environment);
        var schedule = ExperimentPlan.schedule(profile);
        require(schedule.size() == profile.runCount(), "Schedule size mismatch");
        require(schedule.stream().map(ExperimentPlan.Scheduled::runId).distinct().count() == schedule.size(), "Duplicate run IDs");
        ExperimentFiles.csv(summary.resolve("schedule.csv"), schedule.stream().map(s -> new ScheduleRow(s.order(), s.runId(), s.input().id(), s.configuration().name(), s.seed())).toList());
        long start = System.nanoTime();
        List<RunRecord> rows = new ArrayList<>();
        List<RunRecord> warmups = new ArrayList<>();
        ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "matrix-benchmark");
            thread.setDaemon(true); // a timed-out noninterruptible numerical call cannot hang JVM exit
            return thread;
        });
        String active = "PREPARING";
        try {
            manifest(summary, profile, planHash, environment, "RUNNING", rows.size(), warmups.size(), seconds(start), null);
            Map<String, InputRepository.Prepared> inputs = new LinkedHashMap<>();
            var repository = new InputRepository();
            for (var input : ExperimentPlan.inputs(profile)) inputs.put(input.id(), repository.prepare(root, profile, input));
            ExperimentFiles.csv(summary.resolve("inputs.csv"), inputs.values().stream().map(i -> new InputRow(i.info().profile(), i.info().caseId(),
                    i.info().type().name(), i.info().size(), i.info().sample(), i.info().generationSeed(), i.info().sha256(),
                    i.info().inputFile(), i.info().initialCi(), i.info().initialK(), i.info().initialCategory().name())).toList());
            ExperimentFiles.write(summary.resolve("inputs.json"), new InputData(1, inputs.values().stream().map(InputRepository.Prepared::info).toList()));
            if (profile.warmupRounds() > 0) {
                var type = MatrixGenerator.Type.DISCRETE_EXPERT;
                var warmup = repository.prepare(root, profile, new InputRepository.Case("WARMUP_n" + profile.traceSize(), type,
                        profile.traceSize(), 65535, MatrixGenerator.seed(profile.masterSeed() ^ 0x5741524dL, type, profile.traceSize(), 65535)));
                for (int round = 0; round < profile.warmupRounds(); round++) {
                    for (int i = 0; i < 3; i++) {
                        var config = Configuration.values()[(round + i) % 3];
                        active = name + "_WARMUP_" + round + "_" + config;
                        warmups.add(execute(root, output, profile, planHash, environment, warmup, config,
                                config == Configuration.COSINE ? null : 42L, active, "WARMUP", worker));
                    }
                }
            }
            ExperimentFiles.write(summary.resolve("warmup.json"), new Rows(1, warmups));
            for (var scheduled : schedule) {
                active = scheduled.runId();
                require(seconds(start) <= profile.maxWallSeconds(), "Predefined profile wall-time budget exceeded");
                var row = execute(root, output, profile, planHash, environment, inputs.get(scheduled.input().id()),
                        scheduled.configuration(), scheduled.seed(), scheduled.runId(), "MEASURED", worker);
                rows.add(row);
                // Recoverable checkpoint; incomplete data is never marked COMPLETE or plotted.
                if (rows.size() % 16 == 0 || rows.size() == schedule.size()) {
                    ExperimentFiles.csv(summary.resolve("runs.csv"), rows);
                    manifest(summary, profile, planHash, environment, "RUNNING", rows.size(), warmups.size(), seconds(start), null);
                    require(directoryBytes(output) <= MAX_OUTPUT_BYTES, "Predefined output size budget exceeded");
                    System.out.printf(java.util.Locale.ROOT, "%s %d/%d measured; elapsed %.1f s%n", name, rows.size(), schedule.size(), seconds(start));
                }
            }
            require(rows.size() == profile.runCount(), "Missing measured runs");
            writeTables(summary, rows);
            double elapsed = seconds(start);
            manifest(summary, profile, planHash, environment, "COMPLETE", rows.size(), warmups.size(), elapsed, null);
            if (name.equals("PILOT")) estimateReport(root, rows, elapsed, directoryBytes(output));
            return new Completion(profile, planHash, List.copyOf(rows), List.copyOf(warmups), elapsed);
        } catch (IOException | RuntimeException error) {
            var failure = new Failure(active, error.getClass().getName(), error.getMessage());
            ExperimentFiles.write(summary.resolve("failure.json"), failure);
            manifest(summary, profile, planHash, environment, "FAILED", rows.size(), warmups.size(), seconds(start), failure);
            throw new IllegalStateException("Benchmark failed at " + active + "; incomplete profile is NOT report data", error);
        } finally {
            worker.shutdownNow();
        }
    }

    private static RunRecord execute(Path root, Path output, BenchmarkProfile p, String hash, BenchmarkEnvironment environment,
                                      InputRepository.Prepared input, Configuration configuration, Long seed, String id, String phase,
                                      ExecutorService worker) throws IOException {
        require(input.info().sha256().equals(ExperimentFiles.hash(input.input().path())), "Input changed between methods");
        var cosine = new CosineCorrectionOptions(0.1, p.maxIterations(), 1e-12, 0, 1.0 / 9, 9);
        var genetic = new GeneticCorrectionOptions(p.population(), p.maxGenerations(), 0.3, 1.0 / 9, 9,
                seed == null ? 42 : seed, 64, configuration == Configuration.COSINE ? GeneticMutationMode.UPPER_ONLY : configuration.mutationMode());
        var arguments = new CliArguments(input.input().path(), output.resolve(id), configuration == Configuration.COSINE
                ? CliArguments.Method.COSINE : CliArguments.Method.GENETIC, cosine, genetic, false);
        var future = worker.submit(() -> new AnalysisRunner().analyze(input.input(), arguments));
        AnalysisResult analysis;
        try {
            analysis = future.get(p.runTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException error) {
            future.cancel(true);
            throw new IOException("Run timeout: " + id, error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException("Benchmark interrupted: " + id, error);
        } catch (ExecutionException error) {
            throw new IOException("Algorithm exception in " + id + ": " + error.getCause(), error.getCause());
        }
        Path destination = output.resolve(id);
        new ResultWriter().write(analysis, destination);
        SavedResultVerifier.verify(destination.resolve("result.json"), input.input().matrix());
        var finalMatrix = configuration == Configuration.COSINE ? analysis.cosine().result().corrected() : analysis.genetic().result().best();
        for (int i = 0; i < finalMatrix.size(); i++) for (int j = 0; j < finalMatrix.size(); j++) {
            require(MatrixGenerator.inRange(finalMatrix.get(i, j)), "Corrected comparison outside the common scale: " + id);
        }
        double ci = configuration == Configuration.COSINE ? analysis.cosine().result().finalCi() : analysis.genetic().result().finalCi();
        double k = configuration == Configuration.COSINE ? analysis.cosine().result().finalK() : analysis.genetic().result().finalK();
        double elapsed = configuration == Configuration.COSINE ? analysis.cosine().elapsedMillis() : analysis.genetic().elapsedMillis();
        String status = configuration == Configuration.COSINE ? analysis.cosine().result().status().name() : analysis.genetic().result().status().name();
        int changed = configuration == Configuration.COSINE ? analysis.cosine().result().changedComparisons() : analysis.genetic().result().changedComparisons();
        int steps = configuration == Configuration.COSINE ? analysis.cosine().result().iterations() : analysis.genetic().result().generations();
        boolean ciOk = ResearchMetrics.acceptableCi(ci), kOk = ResearchMetrics.acceptableK(k);
        boolean own = configuration == Configuration.COSINE ? ciOk : kOk;
        require(own == (status.equals("TARGET_REACHED") || status.equals("ALREADY_ACCEPTABLE")), "Status/own criterion mismatch");
        var record = new RunRecord(p.name(), hash, phase, id, input.info().caseId(), input.info().size(), input.info().type(),
                input.info().initialCategory(), configuration, configuration == Configuration.COSINE ? null : configuration.mutationMode().name(),
                seed, input.info().generationSeed(), input.info().sha256(), input.info().inputFile(),
                root.relativize(destination.resolve("result.json")).toString().replace('\\', '/'), status, elapsed,
                input.info().initialCi(), ci, input.info().initialK(), k, input.info().initialCi() - ci, input.info().initialK() - k,
                ResearchMetrics.relativeChange(input.info().initialCi(), ci), ResearchMetrics.relativeChange(input.info().initialK(), k),
                ResearchMetrics.logDeviation(input.input().matrix(), finalMatrix), changed, steps,
                configuration == Configuration.COSINE ? steps : null, configuration == Configuration.COSINE ? null : steps,
                configuration == Configuration.COSINE ? null : analysis.genetic().result().evaluatedIndividuals(),
                own, ciOk, kOk, ciOk && kOk, status.equals("ALREADY_ACCEPTABLE"), changed == 0,
                configuration == Configuration.COSINE ? null : p.population(), configuration == Configuration.COSINE ? null : p.maxGenerations(),
                configuration == Configuration.COSINE ? p.maxIterations() : null, 0.1, 0.3, 1.0 / 9, 9, configuration == Configuration.COSINE ? null : 64);
        ExperimentFiles.write(destination.resolve("run.json"), new RunExport(1, record, environment, p));
        return record;
    }

    private static void writeTables(Path summary, List<RunRecord> rows) throws IOException {
        ExperimentFiles.csv(summary.resolve("runs.csv"), rows);
        ExperimentFiles.write(summary.resolve("runs.json"), new Rows(1, rows));
        writeAggregate(summary, "per_matrix", BenchmarkStatistics.perMatrix(rows));
        writeAggregate(summary, "aggregated", BenchmarkStatistics.bySizeAndType(rows));
        writeAggregate(summary, "by_initial_category", BenchmarkStatistics.byInitialCategory(rows));
        writeAggregate(summary, "overall", BenchmarkStatistics.overall(rows));
    }

    private static void writeAggregate(Path summary, String name, List<BenchmarkStatistics.Aggregate> rows) throws IOException {
        ExperimentFiles.csv(summary.resolve(name + ".csv"), rows);
        ExperimentFiles.write(summary.resolve(name + ".json"), new Aggregates(1, rows));
    }

    private static void manifest(Path summary, BenchmarkProfile p, String hash, BenchmarkEnvironment environment, String status,
                                 int completed, int warmups, double wallSeconds, Failure failure) throws IOException {
        ExperimentFiles.write(summary.resolve("manifest.json"), new Manifest(1, status, p.name(), hash, p.inputCount(), p.runCount(),
                completed, warmups, wallSeconds, MAX_OUTPUT_BYTES, environment, failure));
    }

    private static void estimateReport(Path root, List<RunRecord> pilot, double pilotSeconds, long bytes) throws IOException {
        var report = loadProfile(root, "REPORT");
        Map<String, Double> maxima = new LinkedHashMap<>();
        for (Configuration configuration : Configuration.values()) {
            maxima.put(configuration.name(), pilot.stream().filter(r -> r.configuration() == configuration).mapToDouble(RunRecord::elapsedMillis).max().orElseThrow());
        }
        double algorithmMs = report.inputCount() * (maxima.get("COSINE")
                + report.gaSeeds().size() * (maxima.get("GA_UPPER_ONLY") + maxima.get("GA_BIDIRECTIONAL")));
        double conservativeSeconds = 2 * algorithmMs / 1000 + 2 * pilotSeconds * report.runCount() / pilot.size();
        long projectedBytes = 2 * bytes * report.runCount() / pilot.size();
        ExperimentFiles.write(root.resolve("summary/PILOT/resource_estimate.json"), new ResourceEstimate(1, pilotSeconds, pilot.size(),
                report.inputCount(), report.runCount(), maxima, conservativeSeconds, projectedBytes, report.maxWallSeconds(),
                ExperimentFiles.hash(root.resolve("config/REPORT.initial.json")), ExperimentFiles.hash(root.resolve("config/REPORT.json"))));
    }

    private static long directoryBytes(Path root) throws IOException {
        long bytes = 0;
        try (var paths = Files.walk(root)) {
            for (Path file : paths.filter(Files::isRegularFile).toList()) bytes += Files.size(file);
        }
        return bytes;
    }

    private static void rejectLinks(Path root) throws IOException {
        if (Files.exists(root, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.toList()) require(!Files.isSymbolicLink(path), "Benchmark trees must not contain symbolic links: " + path);
            }
        }
    }

    private static double seconds(long start) { return (System.nanoTime() - start) / 1e9; }

    private record ScheduleRow(int order, String runId, String caseId, String configuration, Long seed) { }
    private record InputRow(String profile, String caseId, String type, int size, int sample, long generationSeed,
                            String sha256, String inputFile, double initialCi, double initialK, String initialCategory) { }
    private record InputData(int schemaVersion, List<InputRepository.InputInfo> inputs) { }
    private record Rows(int schemaVersion, List<RunRecord> runs) { }
    private record Aggregates(int schemaVersion, List<BenchmarkStatistics.Aggregate> rows) { }
    private record RunExport(int schemaVersion, RunRecord run, BenchmarkEnvironment environment, BenchmarkProfile profile) { }
    private record Failure(String runId, String exceptionType, String reason) { }
    private record Manifest(int schemaVersion, String status, String profile, String planSha256, int expectedInputs, int expectedRuns,
                            int completedRuns, int warmupRuns, double wallSeconds, long maxOutputBytes,
                            BenchmarkEnvironment environment, Failure failure) { }
    private record ResourceEstimate(int schemaVersion, double pilotWallSeconds, int pilotRuns, int reportInputs, int reportRuns,
                                    Map<String, Double> maximumObservedMillis, double conservativeProjectedSeconds,
                                    long projectedOutputBytes, int reportBudgetSeconds, String initialReportFileSha256, String finalReportFileSha256) { }
}
