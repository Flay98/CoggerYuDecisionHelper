package MCD.consistency;

import java.util.List;

/** Immutable quantitative GA outcome, including generation zero (initial population). */
public record GeneticCorrectionResult(
        PairwiseComparisonMatrix original, PairwiseComparisonMatrix best,
        double initialK, double finalK, double initialCi, double finalCi,
        int generations, long evaluatedIndividuals, GeneticCorrectionOptions options,
        Status status, String detail, List<GenerationSummary> history) {

    public GeneticCorrectionResult {
        history = List.copyOf(history);
    }

    public long seed() {
        return options.seed();
    }

    /** Final independent upper-triangle differences; reciprocal entries are not counted twice. */
    public int changedComparisons() {
        int count = 0;
        for (int i = 0; i < original.size(); i++) {
            for (int j = i + 1; j < original.size(); j++) {
                if (Double.compare(original.get(i, j), best.get(i, j)) != 0) {
                    count++;
                }
            }
        }
        return count;
    }

    public enum Status {
        ALREADY_ACCEPTABLE,
        TARGET_REACHED,
        MAX_GENERATIONS,
        NO_ADMISSIBLE_CHANGES,
        MUTATION_ATTEMPTS_EXHAUSTED
    }

    /**
     * retained + unmutatedOffspring + mutationSlots = populationSize.
     * successfulMutations + failedMutations = mutationSlots.
     * At generation zero, retained=1 (the actual input) and mutationSlots=P-1.
     * For an already acceptable input only that individual is evaluated (size=1).
     * evaluatedIndividuals is cumulative; carried elites reuse cached fitness.
     */
    public record GenerationSummary(
            int generation, double bestK, int populationSize,
            int retained, int unmutatedOffspring, int mutationSlots,
            int successfulMutations, int failedMutations, long evaluatedIndividuals) {
    }
}
