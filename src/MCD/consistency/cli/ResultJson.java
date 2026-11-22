package MCD.consistency.cli;

import MCD.consistency.CosineCorrectionOptions;
import MCD.consistency.GeneticCorrectionOptions;
import MCD.consistency.PairwiseComparisonMatrix;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.Strictness;

/** Explicit schemaVersion=1 mapping; never reflectively serializes a core result or matrix. */
public final class ResultJson {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping()
            .setStrictness(Strictness.STRICT).create();

    public String format(AnalysisResult analysis) {
        JsonArray methods = new JsonArray();
        JsonObject results = new JsonObject();
        if (analysis.cosine() != null) {
            methods.add("cosine");
            var run = analysis.cosine();
            var c = run.result();
            JsonArray history = new JsonArray();
            for (var change : c.history()) {
                history.add(object("iteration", change.iteration(), "i", change.i(), "k", change.k(), "via", change.via(),
                        "oldValue", change.oldValue(), "newValue", change.newValue(), "delta", change.delta(),
                        "comparisonCosine", change.comparisonCosine(), "supportCosine", change.supportCosine(),
                        "ciBefore", change.ciBefore(), "ciAfter", change.ciAfter(),
                        "kBefore", change.kBefore(), "kAfter", change.kAfter()));
            }
            results.add("cosine", object("algorithm", "Kurennykh et al. (2018)", "status", c.status().name(), "detail", c.detail(),
                    "initialMetrics", metrics(c.initialCi(), c.initialK()), "finalMetrics", metrics(c.finalCi(), c.finalK()),
                    "parameters", cosineParameters(c.options()), "iterations", c.iterations(),
                    "changedComparisons", c.changedComparisons(), "elapsedMillis", run.elapsedMillis(),
                    "matrix", matrix(c.corrected()), "matrixCsv", "cosine_corrected.csv", "history", history));
        }
        if (analysis.genetic() != null) {
            methods.add("genetic");
            var run = analysis.genetic();
            var g = run.result();
            JsonArray history = new JsonArray();
            for (var generation : g.history()) {
                history.add(object("generation", generation.generation(), "bestK", generation.bestK(),
                        "populationSize", generation.populationSize(), "retained", generation.retained(),
                        "unmutatedOffspring", generation.unmutatedOffspring(), "mutationSlots", generation.mutationSlots(),
                        "successfulMutations", generation.successfulMutations(), "failedMutations", generation.failedMutations(),
                        "evaluatedIndividuals", generation.evaluatedIndividuals()));
            }
            results.add("genetic", object("algorithm", "Sayadi-Janicki (2026), Algorithm 1", "status", g.status().name(), "detail", g.detail(),
                    "initialMetrics", metrics(g.initialCi(), g.initialK()), "finalMetrics", metrics(g.finalCi(), g.finalK()),
                    "parameters", geneticParameters(g.options()), "seed", g.seed(), "generations", g.generations(),
                    "evaluatedIndividuals", g.evaluatedIndividuals(), "changedComparisons", g.changedComparisons(),
                    "elapsedMillis", run.elapsedMillis(), "matrix", matrix(g.best()),
                    "matrixCsv", "genetic_corrected.csv", "history", history));
        }
        var input = analysis.input();
        JsonObject root = object("schemaVersion", 1, "indexBase", 0, "name", input.name(), "size", input.matrix().size(),
                "inputFile", input.path().toAbsolutePath().normalize().toString(), "methods", methods,
                "matrix", matrix(input.matrix()), "originalMatrixCsv", "original_matrix.csv",
                "initialMetrics", metrics(analysis.initialMetrics().ci(), analysis.initialMetrics().k()), "results", results);
        return GSON.toJson(root) + "\n";
    }

    private static JsonObject metrics(double ci, double k) {
        return object("ci", ci, "k", k);
    }

    private static JsonObject cosineParameters(CosineCorrectionOptions c) {
        return object("targetCi", c.targetCi(), "maxIterations", c.maxIterations(),
                "minimumCiDecrease", c.minimumCiDecrease(), "minimumSupportCosine", c.minimumSupportCosine(),
                "minimumComparison", c.minimumComparison(), "maximumComparison", c.maximumComparison());
    }

    private static JsonObject geneticParameters(GeneticCorrectionOptions g) {
        return object("populationSize", g.populationSize(), "maxGenerations", g.maxGenerations(), "targetK", g.targetK(),
                "minimumComparison", g.minimumComparison(), "maximumComparison", g.maximumComparison(),
                "seed", g.seed(), "maxMutationAttempts", g.maxMutationAttempts(), "mutationMode", g.mutationMode().name());
    }

    private static JsonArray matrix(PairwiseComparisonMatrix matrix) {
        JsonArray rows = new JsonArray();
        for (int i = 0; i < matrix.size(); i++) {
            JsonArray row = new JsonArray();
            for (int j = 0; j < matrix.size(); j++) {
                row.add(matrix.get(i, j));
            }
            rows.add(row);
        }
        return rows;
    }

    /** Field names and their order above define the external schema, independent of Java internals. */
    private static JsonObject object(Object... fields) {
        JsonObject result = new JsonObject();
        for (int i = 0; i < fields.length; i += 2) {
            String key = (String) fields[i];
            Object value = fields[i + 1];
            if (value instanceof JsonElement json) {
                result.add(key, json);
            } else if (value instanceof Number number) {
                result.addProperty(key, number);
            } else if (value instanceof String string) {
                result.addProperty(key, string);
            } else {
                throw new IllegalArgumentException("Unsupported JSON schema value: " + key);
            }
        }
        return result;
    }
}
