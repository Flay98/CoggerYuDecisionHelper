package MCD.consistency.reproduction;

import MCD.consistency.GeneticMutationMode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Explicit tabular schemas for article data, our runs and discrepancies. */
public final class ReproductionSummary {
    private ReproductionSummary() {
    }

    public static List<ModeSummary> summarize(List<GeneticRunRow> rows) {
        List<ModeSummary> summaries = new ArrayList<>();
        for (GeneticMutationMode mode : GeneticMutationMode.values()) {
            var group = rows.stream().filter(row -> row.mutationMode() == mode).toList();
            if (group.isEmpty()) {
                continue;
            }
            int[] generations = group.stream().mapToInt(GeneticRunRow::generations).sorted().toArray();
            int n = generations.length;
            double median = (generations[(n - 1) / 2] + (double) generations[n / 2]) / 2;
            long success = group.stream().filter(row -> row.finalK() < row.targetK()).count();
            summaries.add(new ModeSummary(mode, n, success, (double) success / n,
                    generations[0], median, generations[n - 1],
                    group.stream().mapToInt(GeneticRunRow::generations).average().orElseThrow(),
                    group.stream().mapToDouble(GeneticRunRow::finalK).min().orElseThrow(),
                    group.stream().mapToDouble(GeneticRunRow::finalK).average().orElseThrow(),
                    group.stream().mapToDouble(GeneticRunRow::finalK).max().orElseThrow(),
                    group.stream().mapToDouble(GeneticRunRow::finalCi).average().orElseThrow(),
                    group.stream().mapToInt(GeneticRunRow::changedComparisons).average().orElseThrow(),
                    group.stream().mapToDouble(GeneticRunRow::elapsedMillis).average().orElseThrow()));
        }
        return List.copyOf(summaries);
    }

    static void write(Path directory, List<GeneticRunRow> ga, List<CosineRunRow> own,
                      List<PublishedReferences.MatrixCheck> reference) throws IOException {
        Files.createDirectories(directory);
        var summaries = summarize(ga);
        ReproductionFiles.writeJson(directory.resolve("ga_runs.json"), new GeneticRows(1, ga));
        ReproductionFiles.writeJson(directory.resolve("ga_mode_summary.json"), new Summaries(1, summaries));
        ReproductionFiles.writeJson(directory.resolve("cosine_own_runs.json"), new OwnRows(1, own));
        ReproductionFiles.writeJson(directory.resolve("reference_checks.json"), new ReferenceChecks(1, reference));
        List<List<String>> csv = new ArrayList<>();
        csv.add(List.of("experimentId", "origin", "inputFile", "inputSha256", "mutationMode", "seed", "populationSize",
                "maxGenerations", "targetK", "minimumComparison", "maximumComparison", "maxMutationAttempts",
                "generations", "initialK", "finalK", "initialCi", "finalCi", "status", "changedComparisons",
                "evaluatedIndividuals", "elapsedMillis", "reportedK", "reportedGenerations", "referenceChangedComparisons",
                "deltaK", "deltaGenerations", "deltaChangedComparisons", "resultFile"));
        for (var row : ga) {
            csv.add(List.of(row.experimentId(), row.origin(), row.inputFile(), row.inputSha256(), row.mutationMode().name(),
                    Long.toString(row.seed()), Integer.toString(row.populationSize()), Integer.toString(row.maxGenerations()),
                    d(row.targetK()), d(row.minimumComparison()), d(row.maximumComparison()), Integer.toString(row.maxMutationAttempts()),
                    Integer.toString(row.generations()), d(row.initialK()), d(row.finalK()), d(row.initialCi()), d(row.finalCi()),
                    row.status(), Integer.toString(row.changedComparisons()), Long.toString(row.evaluatedIndividuals()), d(row.elapsedMillis()),
                    d(row.reportedK()), Integer.toString(row.reportedGenerations()), Integer.toString(row.referenceChangedComparisons()),
                    d(row.deltaK()), Integer.toString(row.deltaGenerations()), Integer.toString(row.deltaChangedComparisons()), row.resultFile()));
        }
        ReproductionFiles.writeCsv(directory.resolve("ga_runs.csv"), csv);
        csv = new ArrayList<>();
        csv.add(List.of("mutationMode", "runs", "successes", "successRate", "minGenerations", "medianGenerations", "maxGenerations",
                "meanGenerations", "minFinalK", "meanFinalK", "maxFinalK", "meanFinalCi", "meanChangedComparisons", "meanElapsedMillis"));
        for (var s : summaries) {
            csv.add(List.of(s.mutationMode().name(), Integer.toString(s.runs()), Long.toString(s.successes()), d(s.successRate()),
                    Integer.toString(s.minGenerations()), d(s.medianGenerations()), Integer.toString(s.maxGenerations()), d(s.meanGenerations()),
                    d(s.minFinalK()), d(s.meanFinalK()), d(s.maxFinalK()), d(s.meanFinalCi()), d(s.meanChangedComparisons()), d(s.meanElapsedMillis())));
        }
        ReproductionFiles.writeCsv(directory.resolve("ga_mode_summary.csv"), csv);
        csv = new ArrayList<>();
        csv.add(List.of("experimentId", "origin", "inputFile", "inputSha256", "initialCi", "finalCi", "initialK", "finalK",
                "iterations", "changedComparisons", "status", "elapsedMillis", "resultFile"));
        for (var row : own) {
            csv.add(List.of(row.experimentId(), row.origin(), row.inputFile(), row.inputSha256(), d(row.initialCi()), d(row.finalCi()),
                    d(row.initialK()), d(row.finalK()), Integer.toString(row.iterations()), Integer.toString(row.changedComparisons()),
                    row.status(), d(row.elapsedMillis()), row.resultFile()));
        }
        ReproductionFiles.writeCsv(directory.resolve("cosine_own_runs.csv"), csv);
        writeMarkdown(directory.resolve("report.md"), ga, own, summaries, reference);
    }

    private static void writeMarkdown(Path target, List<GeneticRunRow> ga, List<CosineRunRow> own,
                                      List<ModeSummary> summaries, List<PublishedReferences.MatrixCheck> reference) throws IOException {
        StringBuilder text = new StringBuilder("# Проверка воспроизведения статей\n\n");
        text.append("## Опубликованные матрицы 2026 года\n\n")
                .append("CI ниже вычислен нами: авторы не приводят CI этих матриц. Число 2 изменений получено сравнением опубликованных верхних треугольников.\n\n")
                .append("| Матрица | K статьи | K ядра | Независимый K (DECIMAL128) | Вычисленный CI |\n|---|---:|---:|---:|---:|\n");
        for (var r : reference) {
            text.append(String.format(Locale.ROOT, "| %s | %.12g | %.12g | %.12g | %.12g |%n", r.id(), r.reportedK(), r.coreK(), r.independentDecimalK(), r.computedCi()));
        }
        text.append("\n## Все заранее заданные запуски GA\n\nP=1000, K<0.3, максимум 100 новых поколений, диапазон [1/9,9], 64 попытки мутации. Поколение 0 — начальная популяция.\n\n")
                .append("Авторский ориентир: K=0.2317, 2 поколения, 2 изменённых независимых сравнения. Совпадение порога не доказывает эквивалентность алгоритмов.\n\n")
                .append("| Режим | Seed | Поколения | K | CI | Изменено | ΔK к статье | Δ поколений | Статус | мс |\n|---|---:|---:|---:|---:|---:|---:|---:|---|---:|\n");
        for (var r : ga) {
            text.append(String.format(Locale.ROOT, "| %s | %d | %d | %.12g | %.12g | %d | %+.9g | %+d | %s | %.3f |%n",
                    r.mutationMode(), r.seed(), r.generations(), r.finalK(), r.finalCi(), r.changedComparisons(), r.deltaK(), r.deltaGenerations(), r.status(), r.elapsedMillis()));
        }
        text.append("\n## Сводка GA (все запуски, без отбора удачных seed)\n\n")
                .append("| Режим | Успех / всего | Поколения min / median / max | Средние поколения | K min / mean / max | Среднее число изменений |\n|---|---:|---|---:|---|---:|\n");
        for (var s : summaries) {
            text.append(String.format(Locale.ROOT, "| %s | %d / %d | %d / %.1f / %d | %.3f | %.9g / %.9g / %.9g | %.3f |%n",
                    s.mutationMode(), s.successes(), s.runs(), s.minGenerations(), s.medianGenerations(), s.maxGenerations(),
                    s.meanGenerations(), s.minFinalK(), s.meanFinalK(), s.maxFinalK(), s.meanChangedComparisons()));
        }
        text.append("\n## Статья 2018 года: независимая проверка, не авторские данные\n\n")
                .append("В статье есть агрегированная таблица 1, но нет исходных матриц и seed. Точное воспроизведение этой таблицы по публикации невозможно. Её ручная транскрипция сохранена отдельно в reference/kurennykh_2018_table1.csv.\n\n")
                .append("| Собственная матрица | CI до | CI после | K до | K после | Правки | Изменено | Статус |\n|---|---:|---:|---:|---:|---:|---:|---|\n");
        for (var r : own) {
            text.append(String.format(Locale.ROOT, "| %s | %.12g | %.12g | %.12g | %.12g | %d | %d | %s |%n",
                    r.experimentId(), r.initialCi(), r.finalCi(), r.initialK(), r.finalK(), r.iterations(), r.changedComparisons(), r.status()));
        }
        text.append("\nОриентация мутации — установленное отличие операторов; влияние на конкретное авторское решение нельзя установить без их seed и трассы. Оператор скрещивания, обработка границ и счёт поколений также не заданы полностью. Все возможные причины обсуждаются в ../README.md; это не доказательство тождественности реализаций.\n\n")
                .append("Время зависит от JIT и среды, не сравнивается с авторами и не входит в критерий воспроизводимости. Все сохранённые матрицы повторно загружены, свойства и оба индекса проверены.\n");
        Files.writeString(target, text, StandardCharsets.UTF_8);
    }

    private static String d(double value) {
        return Double.toString(value);
    }

    public record GeneticRunRow(String experimentId, String origin, String inputFile, String inputSha256,
                                GeneticMutationMode mutationMode, long seed, int populationSize, int maxGenerations,
                                double targetK, double minimumComparison, double maximumComparison, int maxMutationAttempts,
                                int generations, double initialK, double finalK, double initialCi, double finalCi,
                                String status, int changedComparisons, long evaluatedIndividuals, double elapsedMillis,
                                double reportedK, int reportedGenerations, int referenceChangedComparisons,
                                double deltaK, int deltaGenerations, int deltaChangedComparisons, String resultFile) {
    }

    public record CosineRunRow(String experimentId, String origin, String inputFile, String inputSha256,
                               double initialCi, double finalCi, double initialK, double finalK,
                               int iterations, int changedComparisons, String status, double elapsedMillis, String resultFile) {
    }

    public record ModeSummary(GeneticMutationMode mutationMode, int runs, long successes, double successRate,
                              int minGenerations, double medianGenerations, int maxGenerations, double meanGenerations,
                              double minFinalK, double meanFinalK, double maxFinalK, double meanFinalCi,
                              double meanChangedComparisons, double meanElapsedMillis) {
    }

    private record GeneticRows(int schemaVersion, List<GeneticRunRow> runs) {
    }
    private record OwnRows(int schemaVersion, List<CosineRunRow> runs) {
    }
    private record Summaries(int schemaVersion, List<ModeSummary> summaries) {
    }
    private record ReferenceChecks(int schemaVersion, List<PublishedReferences.MatrixCheck> checks) {
    }
}
