package MCD.consistency.reproduction;

import MCD.consistency.GeneticCorrectionOptions;
import MCD.consistency.GeneticMutationMode;

import java.util.List;

/** Fixed before observing results. This is a 20-run verification, not a seed search. */
public final class ReproductionPlan {
    public static final List<Long> SEEDS = List.of(0L, 1L, 2L, 3L, 4L, 5L, 10L, 42L, 100L, 2026L);
    public static final String PUBLISHED_INPUT = "inputs/published/sayadi_4x4.csv";
    public static final List<OwnCase> OWN_CASES = List.of(
            new OwnCase("K2018_OWN_ILLUSTRATIVE_3", "inputs/own/illustrative_3x3.csv"),
            new OwnCase("K2018_OWN_CYCLIC_3", "inputs/own/cyclic_3x3.csv"),
            new OwnCase("K2018_OWN_PERTURBED_4", "inputs/own/perturbed_4x4.csv"));

    private ReproductionPlan() {
    }

    public static GeneticCorrectionOptions geneticOptions(long seed, GeneticMutationMode mode) {
        return new GeneticCorrectionOptions(1000, 100, 0.3, 1.0 / 9, 9, seed, 64, mode);
    }

    public static String geneticId(long seed, GeneticMutationMode mode) {
        return "SJ2026_" + mode.name() + "_SEED_" + seed;
    }

    public record OwnCase(String id, String input) {
    }
}
