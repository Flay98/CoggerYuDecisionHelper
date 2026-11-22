package MCD.consistency.decision;

import MCD.consistency.ConsistencyMetrics;
import MCD.consistency.GeneticCorrectionOptions;
import MCD.consistency.GeneticMutationMode;
import MCD.consistency.PairwiseComparisonMatrix;
import MCD.consistency.cli.AnalysisResult;
import MCD.consistency.cli.AnalysisRunner;
import MCD.consistency.cli.CliArguments;
import MCD.consistency.cli.MatrixInput;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** One original PCM/table, three independent correction calls; never a hybrid pipeline. */
public final class DecisionRunner {
    public DecisionAnalysis analyze(MatrixInput input, Path alternativesFile, AlternativeTable table, DecisionArguments args) {
        if(input.matrix().size()!=table.criterionCount()) throw new IllegalArgumentException("PCM size must equal the criterion count");
        if(!args.criterionOrder().isEmpty() && !args.criterionOrder().equals(table.criteria()))
            throw new IllegalArgumentException("--criterion-order differs from alternatives header order; no automatic reordering is performed");
        var ranking=new WeightedRanking(); var normalized=ranking.normalize(table);
        var scenarios=new ArrayList<DecisionAnalysis.Scenario>();
        var original=evaluate(DecisionAnalysis.Kind.ORIGINAL,input.matrix(),table,normalized,null,null);
        scenarios.add(original);
        for(var kind:List.of(DecisionAnalysis.Kind.COSINE,DecisionAnalysis.Kind.GA_UPPER_ONLY,DecisionAnalysis.Kind.GA_BIDIRECTIONAL)) {
            CliArguments base=args.correction(); var g=base.geneticOptions();
            var genetic=new GeneticCorrectionOptions(g.populationSize(),g.maxGenerations(),g.targetK(),g.minimumComparison(),g.maximumComparison(),g.seed(),g.maxMutationAttempts(),
                    kind==DecisionAnalysis.Kind.GA_BIDIRECTIONAL?GeneticMutationMode.BIDIRECTIONAL:GeneticMutationMode.UPPER_ONLY);
            var selected=new CliArguments(input.path(),args.outputDirectory(),kind==DecisionAnalysis.Kind.COSINE?CliArguments.Method.COSINE:CliArguments.Method.GENETIC,
                    base.cosineOptions(),genetic,false);
            // The same immutable input is passed to every call. Previous corrected matrices are never inputs.
            AnalysisResult correction=new AnalysisRunner().analyze(input,selected);
            PairwiseComparisonMatrix corrected=kind==DecisionAnalysis.Kind.COSINE?correction.cosine().result().corrected():correction.genetic().result().best();
            scenarios.add(evaluate(kind,corrected,table,normalized,original,correction));
        }
        return new DecisionAnalysis(input,alternativesFile,table,normalized,scenarios);
    }

    private DecisionAnalysis.Scenario evaluate(DecisionAnalysis.Kind kind, PairwiseComparisonMatrix matrix, AlternativeTable table,
                                               List<List<Double>> normalized, DecisionAnalysis.Scenario original, AnalysisResult correction) {
        var weights=new PerronWeights().calculate(matrix);
        var ranking=new WeightedRanking().rank(table,normalized,weights.weights());
        List<Double> weightChanges=new ArrayList<>(),scoreChanges=new ArrayList<>(); List<Integer> positions=new ArrayList<>();
        for(int j=0;j<table.criterionCount();j++) weightChanges.add(original==null?0:weights.weights().get(j)-original.priorities().weights().get(j));
        for(int i=0;i<table.alternativeCount();i++) {
            scoreChanges.add(original==null?0:ranking.scores().get(i)-original.ranking().scores().get(i));
            positions.add(original==null?0:ranking.ranks().get(i)-original.ranking().ranks().get(i));
        }
        return new DecisionAnalysis.Scenario(kind,matrix,ConsistencyMetrics.saatyIndex(matrix),ConsistencyMetrics.koczkodajIndex(matrix).value(),
                weights,ranking,weightChanges,scoreChanges,positions,correction);
    }
}
