package MCD.consistency.cli;

import MCD.consistency.CosineCorrectionOptions;
import MCD.consistency.GeneticCorrectionOptions;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Parses CLI syntax; mathematical parameter validation belongs to the existing option classes. */
public record CliArguments(Path input, Path outputDirectory, Method method,
                           CosineCorrectionOptions cosineOptions, GeneticCorrectionOptions geneticOptions,
                           boolean help) {
    private static final Set<String> VALUE_OPTIONS = Set.of("--input", "--method", "--output-dir", "--seed",
            "--min-comparison", "--max-comparison", "--cosine-target-ci", "--genetic-target-k",
            "--max-iterations", "--max-generations", "--population");

    public enum Method { COSINE, GENETIC, BOTH }

    public static CliArguments parse(String[] args) {
        Map<String, String> values = new LinkedHashMap<>();
        boolean help = args.length == 0;
        for (int i = 0; i < args.length; i++) {
            String token = args[i];
            if (token.equals("--help")) {
                help = true;
                continue;
            }
            int equals = token.indexOf('=');
            String key = equals < 0 ? token : token.substring(0, equals);
            if (!VALUE_OPTIONS.contains(key)) {
                throw new IllegalArgumentException("Unknown argument: " + token);
            }
            if (values.containsKey(key)) {
                throw new IllegalArgumentException("Duplicate argument: " + key);
            }
            String value;
            if (equals >= 0) {
                value = token.substring(equals + 1);
            } else {
                if (i + 1 >= args.length || args[i + 1].startsWith("--")) {
                    throw new IllegalArgumentException("Missing value for " + key);
                }
                value = args[++i];
            }
            if (value.isBlank()) {
                throw new IllegalArgumentException("Empty value for " + key);
            }
            values.put(key, value);
        }
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
        // An absent bound keeps EACH method's own default; there is no hidden common scale.
        c = new CosineCorrectionOptions(number(values, "--cosine-target-ci", c.targetCi()),
                integer(values, "--max-iterations", c.maxIterations()), c.minimumCiDecrease(), c.minimumSupportCosine(),
                number(values, "--min-comparison", c.minimumComparison()), number(values, "--max-comparison", c.maximumComparison()));
        g = new GeneticCorrectionOptions(integer(values, "--population", g.populationSize()),
                integer(values, "--max-generations", g.maxGenerations()), number(values, "--genetic-target-k", g.targetK()),
                number(values, "--min-comparison", g.minimumComparison()), number(values, "--max-comparison", g.maximumComparison()),
                seed, g.maxMutationAttempts());
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
