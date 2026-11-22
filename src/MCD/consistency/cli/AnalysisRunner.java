package MCD.consistency.cli;

import MCD.consistency.ConsistencyMetrics;
import MCD.consistency.CosineConsistencyCorrector;
import MCD.consistency.GeneticConsistencyCorrector;

/** Coordinates existing algorithms; deliberately has no file access or CLI printing. */
public final class AnalysisRunner {
    public AnalysisResult analyze(MatrixInput input, CliArguments arguments) {
        var original = input.matrix();
        var initial = new AnalysisResult.Metrics(ConsistencyMetrics.saatyIndex(original),
                ConsistencyMetrics.koczkodajIndex(original).value());
        AnalysisResult.CosineRun cosine = null;
        AnalysisResult.GeneticRun genetic = null;
        if (arguments.method() != CliArguments.Method.GENETIC) {
            long start = System.nanoTime();
            var result = new CosineConsistencyCorrector(arguments.cosineOptions()).correct(original);
            cosine = new AnalysisResult.CosineRun(result, (System.nanoTime() - start) / 1e6);
        }
        if (arguments.method() != CliArguments.Method.COSINE) {
            long start = System.nanoTime();
            // Always ORIGINAL, never cosine.result().corrected().
            var result = new GeneticConsistencyCorrector(arguments.geneticOptions()).correct(original);
            genetic = new AnalysisResult.GeneticRun(result, (System.nanoTime() - start) / 1e6);
        }
        return new AnalysisResult(input, arguments.method(), initial, cosine, genetic);
    }
}
