package MCD.consistency.experiments;

import MCD.consistency.GeneticMutationMode;

import java.util.ArrayList;
import java.util.List;

/** Fixed schedule: rotate configurations between inputs and rotate seed order between inputs. */
public final class ExperimentPlan {
    public enum Configuration {
        COSINE, GA_UPPER_ONLY, GA_BIDIRECTIONAL;

        public GeneticMutationMode mutationMode() {
            return this == COSINE ? null : this == GA_UPPER_ONLY ? GeneticMutationMode.UPPER_ONLY : GeneticMutationMode.BIDIRECTIONAL;
        }
    }

    public record Scheduled(int order, String runId, InputRepository.Case input, Configuration configuration, Long seed) {
    }

    private ExperimentPlan() {
    }

    public static List<InputRepository.Case> inputs(BenchmarkProfile profile) {
        List<InputRepository.Case> inputs = new ArrayList<>();
        for (int n : profile.sizes()) {
            for (MatrixGenerator.Type type : MatrixGenerator.Type.values()) {
                for (int sample = 0; sample < profile.samplesPerType(); sample++) {
                    inputs.add(new InputRepository.Case(MatrixGenerator.caseId(type, n, sample), type, n, sample,
                            MatrixGenerator.seed(profile.masterSeed(), type, n, sample)));
                }
            }
        }
        return List.copyOf(inputs);
    }

    public static List<Scheduled> schedule(BenchmarkProfile profile) {
        List<Scheduled> result = new ArrayList<>();
        var inputs = inputs(profile);
        for (int index = 0; index < inputs.size(); index++) {
            var input = inputs.get(index);
            var configurations = Configuration.values();
            for (int c = 0; c < configurations.length; c++) {
                Configuration configuration = configurations[(c + index) % configurations.length];
                if (configuration == Configuration.COSINE) {
                    result.add(new Scheduled(result.size(), profile.name() + "_" + input.id() + "_COSINE", input, configuration, null));
                } else {
                    for (int s = 0; s < profile.gaSeeds().size(); s++) {
                        long seed = profile.gaSeeds().get((s + index) % profile.gaSeeds().size());
                        result.add(new Scheduled(result.size(), profile.name() + "_" + input.id() + "_" + configuration + "_seed" + seed,
                                input, configuration, seed));
                    }
                }
            }
        }
        return List.copyOf(result);
    }
}
