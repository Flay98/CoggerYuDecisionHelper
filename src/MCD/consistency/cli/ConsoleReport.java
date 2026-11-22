package MCD.consistency.cli;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Locale;

/** Short demonstration report; full precision and histories are in result.json/CSV. */
public final class ConsoleReport {
    public void print(AnalysisResult analysis, Path outputDirectory, PrintStream out) {
        out.println("PAIRWISE COMPARISON MATRIX ANALYSIS");
        out.println("Input: " + analysis.input().path());
        out.println("Name: " + analysis.input().name());
        out.println("Matrix size: " + analysis.input().matrix().size());
        out.println("\nINITIAL METRICS");
        metrics(out, analysis.initialMetrics().ci(), analysis.initialMetrics().k());
        if (analysis.cosine() != null) {
            var run = analysis.cosine();
            var c = run.result();
            out.println("\nMETHOD: COSINE CORRECTION (2018)");
            common(out, c.status().name(), c.detail(), c.initialCi(), c.finalCi(), c.initialK(), c.finalK());
            out.println("Iterations: " + c.iterations());
            out.println("Changed comparisons: " + c.changedComparisons());
            out.printf(Locale.ROOT, "Elapsed time: %.3f ms%n", run.elapsedMillis());
        }
        if (analysis.genetic() != null) {
            var run = analysis.genetic();
            var g = run.result();
            out.println("\nMETHOD: GENETIC ALGORITHM (2026)");
            common(out, g.status().name(), g.detail(), g.initialCi(), g.finalCi(), g.initialK(), g.finalK());
            out.println("Generations: " + g.generations());
            out.println("Evaluated individuals: " + g.evaluatedIndividuals());
            out.println("Changed comparisons: " + g.changedComparisons());
            out.println("Seed: " + g.seed());
            out.println("Mutation mode: " + g.options().mutationMode());
            out.printf(Locale.ROOT, "Elapsed time: %.3f ms%n", run.elapsedMillis());
        }
        out.println("\nResults saved to: " + outputDirectory.toAbsolutePath().normalize());
    }

    private static void metrics(PrintStream out, double ci, double k) {
        out.printf(Locale.ROOT, "CI: %.12g%nK:  %.12g%n", ci, k);
    }

    private static void common(PrintStream out, String status, String detail,
                               double initialCi, double finalCi, double initialK, double finalK) {
        out.println("Status: " + status);
        out.println("Detail: " + detail);
        out.printf(Locale.ROOT, "Initial CI: %.12g%nFinal CI: %.12g%nInitial K: %.12g%nFinal K: %.12g%n",
                initialCi, finalCi, initialK, finalK);
    }
}
