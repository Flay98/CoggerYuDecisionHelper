package MCD.consistency.decision;

import MCD.ChoiceMethod;
import MCD.Ranker;
import org.junit.Test;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.regex.Pattern;
import static org.junit.Assert.*;

/** Exercise both untouched legacy pipelines on a small hand-checkable table. */
public class LegacyRankingCompatibilityTest {
    @Test public void originalSimpleModeKeepsItsScores() { check(ChoiceMethod.Simple,2d/3,1d/3); }
    @Test public void originalCoggerYuModeKeepsItsDifferentScores() { check(ChoiceMethod.CoggerYu,7d/11,4d/11); }
    private static void check(ChoiceMethod method,double first,double second) {
        PrintStream previous=System.out;var oldMethod=Ranker.method;var oldP=Ranker.P;var oldInfo=Ranker.AdditionalInfo;
        var buffer=new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(buffer,true,StandardCharsets.UTF_8));Ranker.method=method;Ranker.P=2d;Ranker.AdditionalInfo=false;
            Ranker.Survey(new Double[][]{{2d,4d},{1d,2d}},new String[]{"x","y"},new Scanner("5\n0\n0\n"));
        } finally {System.setOut(previous);Ranker.method=oldMethod;Ranker.P=oldP;Ranker.AdditionalInfo=oldInfo;}
        var matcher=Pattern.compile("g([12]) score: ([0-9.,]+)").matcher(buffer.toString(StandardCharsets.UTF_8));int count=0;
        while(matcher.find()) { double actual=Double.parseDouble(matcher.group(2).replace(',','.'));assertEquals(matcher.group(1).equals("1")?first:second,actual,5.1e-7);count++; }
        assertEquals(2,count);
    }
}
