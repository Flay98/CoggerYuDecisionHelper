package MCD.consistency.decision;

import MCD.consistency.cli.CliArguments;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class DecisionArgumentsTest {
    static String[] basic() { return new String[]{"--matrix","examples/decision/criteria_3x3.csv","--alternatives","examples/decision/alternatives_3x3.csv","--directions","max,min,max"}; }
    static String[] args(String... extra) {
        var list=new java.util.ArrayList<>(List.of(basic()));list.addAll(List.of(extra));return list.toArray(String[]::new);
    }

    @Test public void sharesAllMathematicalDefaultsWithConsistencyCli() {
        var decision=DecisionArguments.parse(basic());var old=CliArguments.parse(new String[]{"--input","examples/decision/criteria_3x3.csv"});
        assertEquals(old.cosineOptions(),decision.correction().cosineOptions());assertEquals(old.geneticOptions(),decision.correction().geneticOptions());
        assertEquals(List.of(Direction.MAX,Direction.MIN,Direction.MAX),decision.directions());
    }

    @Test public void delegatesCustomNumericalOptionsAndFullWidthSeedExactly() {
        String[] extra={"--seed",Long.toString(Long.MAX_VALUE),"--population","18","--max-generations","3","--max-iterations","7",
                "--genetic-target-k","0.2","--cosine-target-ci","0.05","--min-comparison","0.1111111111111111","--max-comparison","9"};
        var a=DecisionArguments.parse(args(extra)); var delegated=new java.util.ArrayList<>(List.of("--input","matrix.csv"));delegated.addAll(List.of(extra));
        var b=CliArguments.parse(delegated.toArray(String[]::new));
        assertEquals(b.cosineOptions(),a.correction().cosineOptions());assertEquals(b.geneticOptions(),a.correction().geneticOptions());
        assertEquals(Long.MAX_VALUE,a.correction().geneticOptions().seed());
    }

    @Test public void rejectsUnknownDuplicateMissingAndInvalidOptions() {
        for(String[] extras:List.of(new String[]{"--method","both"},new String[]{"--seed","abc"},new String[]{"--seed","1","--seed","2"},new String[]{"--population","1"},new String[]{"--mutation-mode","BIDIRECTIONAL"},new String[]{"--max-generations"}))
            assertThrows(IllegalArgumentException.class,()->DecisionArguments.parse(args(extras)));
        assertThrows(IllegalArgumentException.class,()->DecisionArguments.parse(new String[]{"--matrix","file.csv"}));
    }

    @Test public void supportsHelpEqualsSyntaxAndExplicitOrderAssertion() {
        assertTrue(DecisionArguments.parse(new String[]{}).help()); assertTrue(DecisionArguments.parse(new String[]{"--help"}).help());
        var options=DecisionArguments.parse(args("--seed=42","--criterion-order=speed,cost,accuracy"));
        assertEquals(42,options.correction().geneticOptions().seed());assertEquals(List.of("speed","cost","accuracy"),options.criterionOrder());
    }
}
