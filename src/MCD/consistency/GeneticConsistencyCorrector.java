package MCD.consistency;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static MCD.consistency.GeneticCorrectionResult.Status;

/** Sayadi-Janicki (2026), section 4.1, Algorithm 1. Minimizes K only. */
public final class GeneticConsistencyCorrector {
    private final GeneticCorrectionOptions options;

    /** Deterministic default seed 0, not a seed based on time or global randomness. */
    public GeneticConsistencyCorrector() {
        this(GeneticCorrectionOptions.defaults(0));
    }

    public GeneticConsistencyCorrector(GeneticCorrectionOptions options) {
        if (options == null) {
            throw new IllegalArgumentException("Genetic options must not be null");
        }
        this.options = options;
    }

    public GeneticCorrectionResult correct(PairwiseComparisonMatrix original) {
        GeneticChromosome chromosome = GeneticChromosome.fromMatrix(original);
        for (int i = 0; i < original.size(); i++) {
            for (int j = 0; j < original.size(); j++) {
                if (!options.contains(original.get(i, j))) {
                    throw new IllegalArgumentException("Input entry [" + i + "][" + j + "] is outside the configured genetic comparison interval");
                }
                if (i < j && !options.acceptsGene(original.get(i, j))) {
                    throw new IllegalArgumentException("Upper entry [" + i + "][" + j
                            + "] has a reconstructed reciprocal outside the configured genetic comparison interval");
                }
            }
        }
        // All run-local state (including RNG) is recreated on EACH call.
        return new Run(original, chromosome).execute();
    }

    private final class Run {
        final PairwiseComparisonMatrix original;
        final GeneticChromosome originalChromosome;
        final Random random = new Random(options.seed());
        final List<GeneticCorrectionResult.GenerationSummary> history = new ArrayList<>();
        long evaluations;
        double initialK;
        double initialCi;

        Run(PairwiseComparisonMatrix original, GeneticChromosome originalChromosome) {
            this.original = original;
            this.originalChromosome = originalChromosome;
        }

        GeneticOperators.Individual evaluate(GeneticChromosome chromosome) {
            GeneticOperators.Individual individual = GeneticOperators.evaluate(chromosome);
            evaluations++;
            return individual;
        }

        GeneticCorrectionResult execute() {
            GeneticOperators.Individual input = evaluate(originalChromosome);
            initialK = input.fitness();
            initialCi = ConsistencyMetrics.saatyIndex(original);
            if (initialK < options.targetK()) {
                history.add(new GeneticCorrectionResult.GenerationSummary(0, initialK, 1, 1, 0, 0, 0, 0, evaluations));
                return result(input, 0, Status.ALREADY_ACCEPTABLE, "Input K is strictly below the target");
            }
            GeneticOperators.Batch initial = GeneticOperators.initialPopulation(originalChromosome, options, random);
            List<GeneticOperators.Individual> population = new ArrayList<>();
            population.add(input); // Do not evaluate the same input twice.
            for (int i = 1; i < initial.chromosomes().size(); i++) {
                population.add(evaluate(initial.chromosomes().get(i)));
            }
            GeneticOperators.Individual best = GeneticOperators.selectBest(population, 1).get(0);
            history.add(new GeneticCorrectionResult.GenerationSummary(0, best.fitness(), population.size(),
                    1, 0, options.populationSize() - 1, initial.successfulMutations(), initial.failedMutations(), evaluations));
            if (best.fitness() < options.targetK()) {
                return result(best, 0, Status.TARGET_REACHED, "Initial population already contains a solution");
            }
            if (!GeneticOperators.canMutate(originalChromosome, options)) {
                return result(best, 0, Status.NO_ADMISSIBLE_CHANGES, "No representable gene change is possible within the range and mutation step");
            }
            if (initial.successfulMutations() == 0) {
                return result(best, 0, Status.MUTATION_ATTEMPTS_EXHAUSTED,
                        "All initial mutation retries failed; this does not prove that the range has no admissible changes");
            }
            for (int completed = 0; completed < options.maxGenerations(); completed++) {
                int generation = completed + 1;
                List<GeneticOperators.Individual> selected = GeneticOperators.selectBest(population, options.selectedCount());
                List<GeneticOperators.Individual> next = new ArrayList<>(selected.subList(0, options.eliteCount()));
                GeneticOperators.Batch children = GeneticOperators.offspring(selected, options, random);
                for (GeneticChromosome child : children.chromosomes()) {
                    next.add(evaluate(child));
                }
                population = next;
                best = GeneticOperators.selectBest(population, 1).get(0);
                history.add(new GeneticCorrectionResult.GenerationSummary(generation, best.fitness(), population.size(),
                        options.eliteCount(), options.unmutatedOffspringCount(), options.mutatedOffspringCount(),
                        children.successfulMutations(), children.failedMutations(), evaluations));
                if (best.fitness() < options.targetK()) {
                    return result(best, generation, Status.TARGET_REACHED, "Best K is strictly below the target");
                }
            }
            return result(best, options.maxGenerations(), Status.MAX_GENERATIONS, "Generation limit reached before K fell below the target");
        }

        GeneticCorrectionResult result(GeneticOperators.Individual best, int generations, Status status, String detail) {
            PairwiseComparisonMatrix matrix = best.chromosome().matrix();
            double finalCi = matrix == original ? initialCi : ConsistencyMetrics.saatyIndex(matrix);
            return new GeneticCorrectionResult(original, matrix, initialK, best.fitness(), initialCi, finalCi,
                    generations, evaluations, options, status, detail, history);
        }
    }
}
