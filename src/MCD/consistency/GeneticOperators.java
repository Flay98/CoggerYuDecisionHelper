package MCD.consistency;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/** Testable package-private operators for the quantitative Algorithm 1 only. */
final class GeneticOperators {
    private GeneticOperators() {
    }

    static Individual evaluate(GeneticChromosome chromosome) {
        // Saaty's CI and judgment-change counts NEVER participate in selection.
        return new Individual(chromosome, ConsistencyMetrics.koczkodajIndex(chromosome.matrix()).value());
    }

    static List<Individual> selectBest(List<Individual> population, int count) {
        if (count < 1 || count > population.size()) {
            throw new IllegalArgumentException("Invalid selected population size");
        }
        List<Individual> sorted = new ArrayList<>(population);
        // Java's stable sort preserves insertion order for equal K; no secondary objective.
        sorted.sort(Comparator.comparingDouble(Individual::fitness));
        return List.copyOf(sorted.subList(0, count));
    }

    static GeneticChromosome crossover(GeneticChromosome first, GeneticChromosome second, Random random) {
        if (first.size() != second.size()) {
            throw new IllegalArgumentException("Parents must have the same dimension");
        }
        double[] genes = new double[first.length()];
        for (int gene = 0; gene < genes.length; gene++) {
            genes[gene] = random.nextBoolean() ? first.gene(gene) : second.gene(gene);
        }
        return GeneticChromosome.fromGenes(first.size(), genes);
    }

    /** One accepted mutation changes exactly one independently selected upper entry. */
    static Mutation mutate(GeneticChromosome parent, GeneticCorrectionOptions options, Random random) {
        if (!canMutate(parent, options)) {
            return new Mutation(parent, false, 0);
        }
        for (int attempt = 1; attempt <= options.maxMutationAttempts(); attempt++) {
            int gene = random.nextInt(parent.length());
            // No extra RNG draw in UPPER_ONLY: old seeded trajectories are preserved.
            // Uniform independent pair + fair orientation = uniform ordered i != j.
            boolean lower = options.mutationMode() == GeneticMutationMode.BIDIRECTIONAL && random.nextBoolean();
            double old = lower ? parent.lowerGene(gene) : parent.gene(gene);
            // Java's half-open uniform interval [-0.5, 0.5); no uncontrolled randomness.
            double delta = random.nextDouble() - 0.5;
            double oriented = old + delta;
            // Mutate the LOWER value itself, then recover the stored upper gene.
            double updated = lower ? 1 / oriented : oriented;
            if (oriented != old && oriented > 0 && options.contains(oriented)
                    && updated != parent.gene(gene) && options.acceptsGene(updated)) {
                double[] genes = parent.genes();
                genes[gene] = updated;
                return new Mutation(GeneticChromosome.fromGenes(parent.size(), genes), true, attempt);
            }
        }
        // Bounded rejection sampling: retain the unmodified parent in this slot.
        // This is a failed attempt, not a successfully mutated individual.
        return new Mutation(parent, false, options.maxMutationAttempts());
    }

    /** Distinguishes an actually locked floating-point domain from unlucky rejection sampling. */
    static boolean canMutate(GeneticChromosome chromosome, GeneticCorrectionOptions options) {
        for (int gene = 0; gene < chromosome.length(); gene++) {
            double value = chromosome.gene(gene);
            if (value - 0.5 < value && options.acceptsGene(Math.nextDown(value))) {
                return true;
            }
            if (value + (Math.nextDown(1.0) - 0.5) > value && options.acceptsGene(Math.nextUp(value))) {
                return true;
            }
            if (options.mutationMode() == GeneticMutationMode.BIDIRECTIONAL
                    && lowerMutationPossible(chromosome.lowerGene(gene), value, options)) {
                return true;
            }
        }
        return false;
    }

    private static boolean lowerMutationPossible(double lower, double upper, GeneticCorrectionOptions options) {
        // Feasibility witnesses only, NOT clipping a mutation proposal. Reciprocal
        // bounds and floating-point rounding can make adjacent lower values map
        // to the same upper value, so inspect both ends of the reachable interval.
        double low = Math.max(lower - 0.5, Math.max(options.minimumComparison(), 1 / options.maximumComparison()));
        double high = Math.min(lower + (Math.nextDown(1.0) - 0.5),
                Math.min(options.maximumComparison(), 1 / options.minimumComparison()));
        for (double candidate : new double[] {low, Math.nextUp(low), high, Math.nextDown(high)}) {
            if (candidate >= low && candidate <= high && candidate != lower && candidate > 0
                    && options.contains(candidate) && 1 / candidate != upper && options.acceptsGene(1 / candidate)) {
                return true;
            }
        }
        return false;
    }

    static Batch initialPopulation(GeneticChromosome original, GeneticCorrectionOptions options, Random random) {
        List<GeneticChromosome> population = new ArrayList<>();
        population.add(original);
        int successes = 0;
        for (int i = 1; i < options.populationSize(); i++) {
            Mutation mutation = mutate(original, options, random);
            population.add(mutation.chromosome());
            if (mutation.changed()) {
                successes++;
            }
        }
        return new Batch(population, successes, options.populationSize() - 1 - successes);
    }

    static Batch offspring(List<Individual> selected, GeneticCorrectionOptions options, Random random) {
        List<GeneticChromosome> children = new ArrayList<>();
        int successes = 0;
        for (int i = 0; i < options.offspringCount(); i++) {
            // Independent, uniform sampling with replacement from the selected half.
            GeneticChromosome first = selected.get(random.nextInt(selected.size())).chromosome();
            GeneticChromosome second = selected.get(random.nextInt(selected.size())).chromosome();
            GeneticChromosome child = crossover(first, second, random);
            if (i >= options.unmutatedOffspringCount()) {
                Mutation mutation = mutate(child, options, random);
                child = mutation.chromosome();
                if (mutation.changed()) {
                    successes++;
                }
            }
            children.add(child);
        }
        return new Batch(children, successes, options.mutatedOffspringCount() - successes);
    }

    record Individual(GeneticChromosome chromosome, double fitness) {
    }

    record Mutation(GeneticChromosome chromosome, boolean changed, int attempts) {
    }

    record Batch(List<GeneticChromosome> chromosomes, int successfulMutations, int failedMutations) {
        Batch {
            chromosomes = List.copyOf(chromosomes);
        }
    }
}
