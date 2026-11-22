package MCD.consistency.experiments;

import java.util.HashSet;
import java.util.List;

/** Fixed, versioned plans live in experiments/comparison/config, never in scientific references. */
public record BenchmarkProfile(String name, List<Integer> sizes, int samplesPerType, List<Long> gaSeeds,
                               long masterSeed, int population, int maxGenerations, int maxIterations,
                               int warmupRounds, int traceSize, int maxWallSeconds, int runTimeoutSeconds,
                               MatrixGenerator.Parameters generator) {
    public BenchmarkProfile {
        if (name == null || !List.of("SMOKE", "PILOT", "REPORT", "FULL").contains(name)
                || sizes == null || sizes.isEmpty() || sizes.stream().anyMatch(n -> n == null || n < 2 || n > 100)
                || new HashSet<>(sizes).size() != sizes.size() || samplesPerType < 1 || samplesPerType > 65535
                || gaSeeds == null || gaSeeds.isEmpty() || gaSeeds.stream().anyMatch(java.util.Objects::isNull)
                || new HashSet<>(gaSeeds).size() != gaSeeds.size() || !gaSeeds.contains(42L)
                || !sizes.contains(traceSize) || population < 2 || maxGenerations < 0 || maxIterations < 0
                || warmupRounds < 0 || maxWallSeconds < 1 || runTimeoutSeconds < 1 || generator == null) {
            throw new IllegalArgumentException("Invalid benchmark profile");
        }
        sizes = List.copyOf(sizes);
        gaSeeds = List.copyOf(gaSeeds);
    }

    public int inputCount() {
        return sizes.size() * MatrixGenerator.Type.values().length * samplesPerType;
    }

    public int runCount() {
        return inputCount() * (1 + 2 * gaSeeds.size());
    }

    public String traceCaseId() {
        return MatrixGenerator.caseId(MatrixGenerator.Type.DISCRETE_EXPERT, traceSize, 0);
    }
}
