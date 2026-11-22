package MCD.consistency.cli;

import MCD.consistency.CosineCorrectionResult;
import MCD.consistency.GeneticCorrectionResult;

/** In-memory orchestration model; ResultJson explicitly maps it to a versioned external schema. */
public record AnalysisResult(MatrixInput input, CliArguments.Method method, Metrics initialMetrics,
                             CosineRun cosine, GeneticRun genetic) {
    public record Metrics(double ci, double k) {
    }

    public record CosineRun(CosineCorrectionResult result, double elapsedMillis) {
    }

    public record GeneticRun(GeneticCorrectionResult result, double elapsedMillis) {
    }
}
