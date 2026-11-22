package MCD.consistency.decision;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Collectors;

public final class DecisionConsoleReport {
    public void print(DecisionAnalysis analysis,Path output,PrintStream out) {
        out.println("DECISION RANKING COMPARISON");
        out.println("Procedure: right Perron weights + column normalization + weighted sum (separate from original Cogger-Yu)");
        out.println("Criteria (positional PCM order):");
        for(int j=0;j<analysis.table().criterionCount();j++) out.printf(Locale.ROOT,"%d. %s %s%n",j+1,analysis.table().criteria().get(j),analysis.table().directions().get(j));
        out.println("Alternatives: "+String.join(", ",analysis.table().alternatives()));
        out.println("\nCONSISTENCY AND CORRECTION STATUS");
        var original=analysis.scenarios().get(0);
        for(var s:analysis.scenarios()) out.printf(Locale.ROOT,"%s: %s; CI=%.12g; K=%.12g; deltaCI=%+.12g; deltaK=%+.12g; changed=%d%n",
                s.kind(),s.status(),s.ci(),s.k(),original.ci()-s.ci(),original.k()-s.k(),s.changedComparisons());
        for(var s:analysis.scenarios()) if(s.correction()!=null) {
            if(s.correction().cosine()!=null) out.println(s.kind()+": iterations="+s.correction().cosine().result().iterations());
            else out.println(s.kind()+": generations="+s.correction().genetic().result().generations()+"; seed="+s.correction().genetic().result().seed());
        }
        out.println("\nCRITERION WEIGHTS (delta = after - original)");
        for(var s:analysis.scenarios()) {
            out.print(s.kind()+": ");
            for(int j=0;j<analysis.table().criterionCount();j++) out.printf(Locale.ROOT,"%s=%.9f (%+.9f)  ",analysis.table().criteria().get(j),s.priorities().weights().get(j),s.weightChanges().get(j));
            out.println();
        }
        out.println("\nALTERNATIVE SCORES AND POSITIONS (deltaRank = after - original; negative means an improvement)");
        out.println("scenario | alternative | score | deltaScore | rank | deltaRank");
        for(var s:analysis.scenarios()) for(int i=0;i<analysis.table().alternativeCount();i++) out.printf(Locale.ROOT,"%s | %s | %.9f | %+.9f | %d | %+d%n",
                s.kind(),analysis.table().alternatives().get(i),s.ranking().scores().get(i),s.scoreChanges().get(i),s.ranking().ranks().get(i),s.positionChanges().get(i));
        out.println("\nRANKING AND WINNER COMPARISON");
        for(var s:analysis.scenarios()) out.println(s.kind()+": "+s.ranking().ranking().stream().map(e->e.alternative()+" (#"+e.rank()+")").collect(Collectors.joining(" > "))+"; winner="+s.ranking().winner());
        out.println("Exact ties retain input order. Lower inconsistency alone does not validate expert preferences.");
        out.println("Results saved to: "+output.toAbsolutePath().normalize());
    }
}
