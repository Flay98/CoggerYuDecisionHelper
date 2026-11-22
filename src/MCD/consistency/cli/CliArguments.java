package MCD.consistency.cli;

import MCD.consistency.CosineCorrectionOptions;
import MCD.consistency.GeneticCorrectionOptions;
import MCD.consistency.GeneticMutationMode;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.Locale;

/** Parses CLI syntax; mathematical parameter validation belongs to the existing option classes. */
public record CliArguments(Path input, Path outputDirectory, Method method,
                           CosineCorrectionOptions cosineOptions, GeneticCorrectionOptions geneticOptions,
                           boolean help) {
    private static final Set<String> VALUE_OPTIONS = Set.of("--input", "--method", "--output-dir", "--seed",
            "--min-comparison", "--max-comparison", "--cosine-target-ci", "--genetic-target-k",
            "--max-iterations", "--max-generations", "--population", "--mutation-mode");

    public enum Method { COSINE, GENETIC, BOTH }

    /** Names delegated by integration CLIs; this remains the single mathematical option parser. */
    public static Set<String> correctionOptionNames() {
        return VALUE_OPTIONS.stream().filter(key -> !Set.of("--input", "--method", "--output-dir", "--mutation-mode").contains(key))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public static CliArguments parse(String[] args) {
        var parsed = CliOptions.parse(args, VALUE_OPTIONS);
        Map<String, String> values = parsed.values();
        boolean help = parsed.help();
        if (!help && !values.containsKey("--input")) {
            throw new IllegalArgumentException("Required argument: --input <matrix.csv|matrix.json>");
        }
        Method method = switch (values.getOrDefault("--method", "both")) {
            case "cosine" -> Method.COSINE;
            case "genetic" -> Method.GENETIC;
            case "both" -> Method.BOTH;
            default -> throw new IllegalArgumentException("--method must be cosine, genetic or both");
        };
        long seed = longValue(values, "--seed", 0);
        CosineCorrectionOptions c = CosineCorrectionOptions.defaults();
        GeneticCorrectionOptions g = GeneticCorrectionOptions.defaults(seed);
        GeneticMutationMode mutationMode;
        try {
            mutationMode = GeneticMutationMode.valueOf(values.getOrDefault("--mutation-mode", "UPPER_ONLY").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("--mutation-mode must be UPPER_ONLY or BIDIRECTIONAL", error);
        }
        // An absent bound keeps EACH method's own default; there is no hidden common scale.
        c = new CosineCorrectionOptions(number(values, "--cosine-target-ci", c.targetCi()),
                integer(values, "--max-iterations", c.maxIterations()), c.minimumCiDecrease(), c.minimumSupportCosine(),
                number(values, "--min-comparison", c.minimumComparison()), number(values, "--max-comparison", c.maximumComparison()));
        g = new GeneticCorrectionOptions(integer(values, "--population", g.populationSize()),
                integer(values, "--max-generations", g.maxGenerations()), number(values, "--genetic-target-k", g.targetK()),
                number(values, "--min-comparison", g.minimumComparison()), number(values, "--max-comparison", g.maximumComparison()),
                seed, g.maxMutationAttempts(), mutationMode);
        return new CliArguments(values.containsKey("--input") ? Path.of(values.get("--input")) : null,
                Path.of(values.getOrDefault("--output-dir", "build/results/consistency")), method, c, g, help);
    }

    private static double number(Map<String, String> values, String key, double fallback) {
        try {
            return values.containsKey(key) ? Double.parseDouble(values.get(key)) : fallback;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(key + " requires a number", error);
        }
    }

    private static int integer(Map<String, String> values, String key, int fallback) {
        long value = longValue(values, key, fallback);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(key + " is outside the integer range");
        }
        return (int) value;
    }

    private static long longValue(Map<String, String> values, String key, long fallback) {
        try {
            return values.containsKey(key) ? Long.parseLong(values.get(key)) : fallback;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(key + " requires an integer", error);
        }
    }

    public static String usage() {
        return """
                Usage: ./gradlew runConsistency --args="--input matrix.csv [options]"
                  --input PATH             UTF-8 numeric matrix: .csv or .json (required)
                  --method METHOD          cosine | genetic | both (default: both)
                  --output-dir PATH        Default: build/results/consistency
                  --seed INTEGER           Genetic seed (default: 0)
                  --mutation-mode MODE     UPPER_ONLY (default) | BIDIRECTIONAL
                  --min-comparison NUMBER   Override the lower range bound for both methods
                  --max-comparison NUMBER   Override the upper range bound for both methods
                  --cosine-target-ci NUMBER Default: 0.1; stop at CI <= target
                  --genetic-target-k NUMBER Default: 0.3; stop at K < target
                  --max-iterations INTEGER Cosine limit (default: 100)
                  --max-generations INTEGER Genetic limit (default: 100)
                  --population INTEGER     Genetic population (default: 1000)
                  --help                   Show this help; no arguments also show help
                Without explicit bounds: cosine has no extra scale, genetic uses [1/7, 7].
                Both methods run independently on the ORIGINAL matrix.
                For the Sayadi example, supply --min-comparison 0.1111111111111111 --max-comparison 9.
                """;
    }
}
