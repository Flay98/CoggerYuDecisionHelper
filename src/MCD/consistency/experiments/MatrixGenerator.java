package MCD.consistency.experiments;

import MCD.consistency.PairwiseComparisonMatrix;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/** Reproducible inputs only; this class never calls a correction algorithm or rejects by CI/K. */
public final class MatrixGenerator {
    public static final String VERSION = "matrix-generator-v1";
    private static final double[] SCALE = {1.0/9, 1.0/8, 1.0/7, 1.0/6, 1.0/5, 0.25, 1.0/3, 0.5,
            1, 2, 3, 4, 5, 6, 7, 8, 9};

    public enum Type { DISCRETE_EXPERT, PERTURBED_CONSISTENT }

    public record Parameters(double perturbationFraction, double weightRatioBound, double noiseLogBound, int proposalAttempts) {
        public Parameters {
            if (!Double.isFinite(perturbationFraction) || perturbationFraction <= 0 || perturbationFraction > 1
                    || !Double.isFinite(weightRatioBound) || weightRatioBound < 1
                    || !Double.isFinite(noiseLogBound) || noiseLogBound <= 0 || proposalAttempts < 1) {
                throw new IllegalArgumentException("Invalid matrix generation parameters");
            }
        }
    }

    public record Perturbation(int i, int j, double epsilon, double before, double after, int attempts) {
    }

    public record Generated(PairwiseComparisonMatrix matrix, List<Double> latentWeights, List<Perturbation> perturbations) {
        public Generated {
            latentWeights = List.copyOf(latentWeights);
            perturbations = List.copyOf(perturbations);
        }
    }

    public static String caseId(Type type, int size, int sample) {
        return String.format(Locale.ROOT, "%s_n%02d_m%03d", type == Type.DISCRETE_EXPERT ? "A" : "B", size, sample);
    }

    /** SplitMix64 finalizer applied to disjoint tuple bit fields; no shared mutable RNG. */
    public static long seed(long master, Type type, int size, int sample) {
        if (size < 2 || sample < 0 || sample >= 65536) {
            throw new IllegalArgumentException("Invalid generation case");
        }
        long value = master ^ ((long) size << 32) ^ ((long) type.ordinal() << 24) ^ sample;
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    public Generated generate(Type type, int size, long seed, Parameters parameters) {
        if (type == null || size < 2 || parameters == null) {
            throw new IllegalArgumentException("Invalid generation request");
        }
        Random random = new Random(seed);
        double[][] data = new double[size][size];
        List<Double> weights = new ArrayList<>();
        List<Perturbation> changes = new ArrayList<>();
        if (type == Type.PERTURBED_CONSISTENT) {
            for (int i = 0; i < size; i++) {
                weights.add(Math.exp(random.nextDouble() * Math.log(parameters.weightRatioBound())));
            }
        }
        List<int[]> pairs = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            data[i][i] = 1;
            for (int j = i + 1; j < size; j++) {
                double value = type == Type.DISCRETE_EXPERT ? SCALE[random.nextInt(SCALE.length)] : weights.get(i) / weights.get(j);
                if (!inRange(value)) {
                    throw new IllegalArgumentException("Latent weights exceed comparison bounds");
                }
                data[i][j] = value;
                data[j][i] = 1 / value;
                pairs.add(new int[] {i, j});
            }
        }
        if (type == Type.PERTURBED_CONSISTENT) {
            // Uniform subset without replacement, via Fisher-Yates shuffle.
            for (int i = pairs.size() - 1; i > 0; i--) {
                int j = random.nextInt(i + 1);
                int[] swap = pairs.get(i);
                pairs.set(i, pairs.get(j));
                pairs.set(j, swap);
            }
            int selected = (int) Math.ceil(parameters.perturbationFraction() * pairs.size());
            for (int index = 0; index < selected; index++) {
                int i = pairs.get(index)[0], j = pairs.get(index)[1];
                double before = data[i][j];
                boolean accepted = false;
                for (int attempt = 1; attempt <= parameters.proposalAttempts(); attempt++) {
                    double epsilon = (2 * random.nextDouble() - 1) * parameters.noiseLogBound();
                    double after = before * Math.exp(epsilon);
                    if (after != before && inRange(after)) {
                        data[i][j] = after;
                        data[j][i] = 1 / after;
                        changes.add(new Perturbation(i, j, epsilon, before, after, attempt));
                        accepted = true;
                        break;
                    }
                }
                if (!accepted) {
                    throw new IllegalStateException("Perturbation retries exhausted; no clipping or silent omission");
                }
            }
        }
        return new Generated(new PairwiseComparisonMatrix(data), weights, changes);
    }

    public static boolean inRange(double value) {
        return Double.isFinite(value) && value >= 1.0 / 9 && value <= 9
                && 1 / value >= 1.0 / 9 && 1 / value <= 9;
    }
}
