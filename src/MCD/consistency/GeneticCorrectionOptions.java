package MCD.consistency;

/** Algorithm 1 settings; selection/elitism/crossover fractions are fixed by the paper. */
public record GeneticCorrectionOptions(
        int populationSize, int maxGenerations, double targetK,
        double minimumComparison, double maximumComparison,
        long seed, int maxMutationAttempts, GeneticMutationMode mutationMode) {

    /** Source-compatible configuration of the original upper-only operator. */
    public GeneticCorrectionOptions(int populationSize, int maxGenerations, double targetK,
                                    double minimumComparison, double maximumComparison,
                                    long seed, int maxMutationAttempts) {
        this(populationSize, maxGenerations, targetK, minimumComparison, maximumComparison,
                seed, maxMutationAttempts, GeneticMutationMode.UPPER_ONLY);
    }

    public GeneticCorrectionOptions {
        if (mutationMode == null) {
            throw new IllegalArgumentException("Mutation mode must not be null");
        }
        if (populationSize < 2) {
            throw new IllegalArgumentException("Population size must be at least 2");
        }
        if (maxGenerations < 0) {
            throw new IllegalArgumentException("Maximum generations must be non-negative");
        }
        if (!Double.isFinite(targetK) || targetK < 0 || targetK > 1) {
            throw new IllegalArgumentException("Target K must be finite and in [0, 1]");
        }
        if (!Double.isFinite(minimumComparison) || minimumComparison <= 0 || minimumComparison > 1
                || !Double.isFinite(maximumComparison) || maximumComparison < 1) {
            throw new IllegalArgumentException("Comparison interval must be finite, positive and contain 1");
        }
        if (maxMutationAttempts < 1) {
            throw new IllegalArgumentException("Maximum mutation attempts must be positive");
        }
    }

    public static GeneticCorrectionOptions defaults(long seed) {
        return new GeneticCorrectionOptions(1000, 100, 0.3, 1.0 / 7, 7, seed, 64);
    }

    /** Round selection down for odd P. At least one parent is available for P >= 2. */
    public int selectedCount() {
        return populationSize / 2;
    }

    /** 20% of the selected half, rounded down, with one elite guaranteed at small P. */
    public int eliteCount() {
        return Math.max(1, selectedCount() / 5);
    }

    /** Fill the complement exactly; at P = 1000 this is 90% of the population. */
    public int offspringCount() {
        return populationSize - eliteCount();
    }

    public int unmutatedOffspringCount() {
        return offspringCount() / 10;
    }

    public int mutatedOffspringCount() {
        return offspringCount() - unmutatedOffspringCount();
    }

    boolean contains(double value) {
        return Double.isFinite(value) && value >= minimumComparison && value <= maximumComparison;
    }

    boolean acceptsGene(double value) {
        return value > 0 && contains(value) && contains(1 / value);
    }
}
