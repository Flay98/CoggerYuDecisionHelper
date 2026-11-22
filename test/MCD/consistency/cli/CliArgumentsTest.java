package MCD.consistency.cli;

import MCD.consistency.CosineCorrectionOptions;
import MCD.consistency.GeneticCorrectionOptions;
import org.junit.Test;

import java.nio.file.Path;

import static org.junit.Assert.*;

public class CliArgumentsTest {
    @Test
    public void preservesBothMethodsDefaultsWithoutInventingACommonRange() {
        var arguments = CliArguments.parse(new String[] {"--input", "matrix.csv"});
        assertEquals(CliArguments.Method.BOTH, arguments.method());
        assertEquals(CosineCorrectionOptions.defaults(), arguments.cosineOptions());
        assertEquals(GeneticCorrectionOptions.defaults(0), arguments.geneticOptions());
        assertEquals(Path.of("build/results/consistency"), arguments.outputDirectory());
        assertFalse(arguments.help());
    }

    @Test
    public void forwardsEveryExposedOptionIncludingLongSeeds() {
        var a = CliArguments.parse(new String[] {"--input=data.json", "--method", "genetic", "--output-dir", "a directory",
                "--seed", "9223372036854775807", "--min-comparison", "0.1111111111111111", "--max-comparison", "9",
                "--cosine-target-ci", "0.01", "--genetic-target-k", "0.2", "--max-iterations", "7",
                "--max-generations", "8", "--population", "21"});
        assertEquals(Path.of("data.json"), a.input());
        assertEquals(Path.of("a directory"), a.outputDirectory());
        assertEquals(CliArguments.Method.GENETIC, a.method());
        assertEquals(Long.MAX_VALUE, a.geneticOptions().seed());
        assertEquals(1.0 / 9, a.cosineOptions().minimumComparison(), 0);
        assertEquals(9, a.geneticOptions().maximumComparison(), 0);
        assertEquals(0.01, a.cosineOptions().targetCi(), 0);
        assertEquals(0.2, a.geneticOptions().targetK(), 0);
        assertEquals(7, a.cosineOptions().maxIterations());
        assertEquals(8, a.geneticOptions().maxGenerations());
        assertEquals(21, a.geneticOptions().populationSize());
    }

    @Test
    public void noArgumentsAndHelpNeedNoInputFile() {
        assertTrue(CliArguments.parse(new String[0]).help());
        assertTrue(CliArguments.parse(new String[] {"--help"}).help());
        assertTrue(CliArguments.usage().contains("--genetic-target-k"));
    }

    @Test
    public void rejectsMissingUnknownDuplicateAndInvalidArguments() {
        for (String[] args : new String[][] {
                {"--method", "cosine"}, {"--input"}, {"--input", "a.csv", "--unknown", "1"},
                {"--input", "a.csv", "--input", "b.csv"}, {"--input", "a.csv", "--method", "other"},
                {"--input", "a.csv", "--seed", "1.5"}, {"--input", "a.csv", "--population", "1"},
                {"--input", "a.csv", "--population", "2147483648"}, {"--input", "a.csv", "--max-iterations", "-1"},
                {"--input", "a.csv", "--genetic-target-k", "NaN"}, {"--input", "a.csv", "--cosine-target-ci", "Infinity"},
                {"--input", "a.csv", "--max-comparison", "0.5"}, {"--input="}
        }) {
            var error = assertThrows(IllegalArgumentException.class, () -> CliArguments.parse(args));
            assertFalse(error.getMessage().isBlank());
        }
    }
}
