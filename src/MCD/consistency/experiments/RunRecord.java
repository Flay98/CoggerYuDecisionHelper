package MCD.consistency.experiments;

/** Flat external run schema. Null seed/counters/parameters mean not applicable, not zero. */
public record RunRecord(String profile, String planSha256, String phase, String runId, String caseId, int size, MatrixGenerator.Type type,
                        ResearchMetrics.InitialCategory initialCategory, ExperimentPlan.Configuration configuration,
                        String mutationMode, Long seed, long generationSeed, String inputSha256, String inputFile,
                        String resultFile, String status, double elapsedMillis,
                        double initialCi, double finalCi, double initialK, double finalK,
                        double deltaCi, double deltaK, Double relativeCi, Double relativeK, double logDeviation,
                        int changedComparisons, int steps, Integer iterations, Integer generations, Long fitnessEvaluations,
                        boolean ownSuccess, boolean ciAcceptable, boolean kAcceptable, boolean bothAcceptable,
                        boolean alreadyAcceptable, boolean noChanges,
                        Integer population, Integer maxGenerations, Integer maxIterations,
                        double targetCi, double targetK, double minimumComparison, double maximumComparison, Integer maxMutationAttempts) {
}
