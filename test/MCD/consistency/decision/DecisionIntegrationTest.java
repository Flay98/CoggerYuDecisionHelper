package MCD.consistency.decision;

import MCD.consistency.CosineConsistencyCorrector;
import MCD.consistency.GeneticConsistencyCorrector;
import MCD.consistency.GeneticCorrectionOptions;
import MCD.consistency.GeneticMutationMode;
import MCD.consistency.PairwiseComparisonMatrix;
import MCD.consistency.cli.ConsistencyCli;
import MCD.consistency.cli.MatrixFileReader;
import MCD.consistency.reproduction.SavedResultVerifier;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class DecisionIntegrationTest {
    @ClassRule public static final TemporaryFolder TEMP=new TemporaryFolder();
    private static DecisionArguments options;
    private static DecisionAnalysis analysis;
    private static Path directory;

    @BeforeClass public static void prepareRealDemo() throws Exception {
        directory=TEMP.newFolder("demo").toPath(); options=arguments(directory);
        var input=new MatrixFileReader().read(options.matrix());
        var table=new AlternativesReader().read(options.alternatives(),options.directions(),input.matrix().size());
        analysis=new DecisionRunner().analyze(input,options.alternatives(),table,options);
        new DecisionWriter().write(analysis,directory);
    }

    @Test public void realCosineCorrectionChangesLeaderCtoAAndRecoversKnownWeights() {
        var original=analysis.scenarios().get(0);var cosine=analysis.scenarios().get(1);
        assertEquals(.183720900491,original.ci(),1e-12); assertEquals(5d/6,original.k(),1e-14);
        assertEquals("C",original.ranking().winner()); assertEquals("A",cosine.ranking().winner());
        assertEquals(6,cosine.matrix().get(0,2),0); assertEquals(1d/6,cosine.matrix().get(2,0),0);
        assertEquals(1,cosine.correction().cosine().result().iterations()); assertEquals(1,cosine.changedComparisons());
        assertEquals(6,cosine.correction().cosine().result().history().get(0).newValue(),0);
        assertArrayEquals(new double[]{2d/3,2d/9,1d/9},cosine.priorities().weights().stream().mapToDouble(Double::doubleValue).toArray(),1e-13);
        assertEquals(List.of(-2,0,2),cosine.positionChanges()); assertEquals("TARGET_REACHED",cosine.status());
    }

    @Test public void geneticModesSaveActualSeededOutcomesWithoutRequiringLeaderChange() {
        var upper=analysis.scenarios().get(2);var bi=analysis.scenarios().get(3);
        assertTrue(upper.k()<.3);assertTrue(bi.k()<.3);
        assertEquals(42,upper.correction().genetic().result().seed());assertEquals(42,bi.correction().genetic().result().seed());
        assertEquals(GeneticMutationMode.UPPER_ONLY,upper.correction().genetic().result().options().mutationMode());
        assertEquals(GeneticMutationMode.BIDIRECTIONAL,bi.correction().genetic().result().options().mutationMode());
        assertEquals("A",upper.ranking().winner()); assertEquals("C",bi.ranking().winner());
        assertEquals(List.of(0,0,0),bi.positionChanges());
    }

    @Test public void eachCorrectorReceivesTheSameOriginalObjectAndMatchesAnIndependentDirectCall() {
        var original=analysis.input().matrix();
        assertSame(original,analysis.scenarios().get(1).correction().cosine().result().original());
        assertArrayEquals(new CosineConsistencyCorrector(options.correction().cosineOptions()).correct(original).corrected().toArray(),analysis.scenarios().get(1).matrix().toArray());
        for(int index=2;index<4;index++) {
            var scenario=analysis.scenarios().get(index);var g=scenario.correction().genetic().result();
            assertSame(original,g.original());
            assertArrayEquals(new GeneticConsistencyCorrector(g.options()).correct(original).best().toArray(),scenario.matrix().toArray());
            assertEquals(5d/6,g.initialK(),1e-14);
        }
    }

    @Test public void inputsRemainUnchangedAfterCorrectionRankingAndExport() throws Exception {
        assertArrayEquals(DecisionMathTest.inconsistent().toArray(),analysis.input().matrix().toArray());
        assertArrayEquals(DecisionMathTest.table().toArray(),analysis.table().toArray());
        assertArrayEquals(new MatrixFileReader().read(options.matrix()).matrix().toArray(),analysis.input().matrix().toArray());
        assertArrayEquals(new AlternativesReader().read(options.alternatives(),options.directions(),3).toArray(),analysis.table().toArray());
        assertThrows(UnsupportedOperationException.class,()->analysis.scenarios().clear());
        assertThrows(UnsupportedOperationException.class,()->analysis.scenarios().get(1).weightChanges().set(0,0d));
    }

    @Test public void deltasDescribeNumericChangesAndRankChangesSeparately() {
        var original=analysis.scenarios().get(0);
        for(var scenario:analysis.scenarios()) {
            for(int j=0;j<3;j++) assertEquals(scenario.priorities().weights().get(j)-original.priorities().weights().get(j),scenario.weightChanges().get(j),0);
            for(int i=0;i<3;i++) {
                assertEquals(scenario.ranking().scores().get(i)-original.ranking().scores().get(i),scenario.scoreChanges().get(i),0);
                assertEquals(Integer.valueOf(scenario.ranking().ranks().get(i)-original.ranking().ranks().get(i)),scenario.positionChanges().get(i));
            }
        }
        assertTrue(analysis.scenarios().get(3).scoreChanges().stream().anyMatch(x->x!=0));
        assertEquals(List.of(0,0,0),analysis.scenarios().get(3).positionChanges());
    }

    @Test public void alreadyAcceptableMatrixHasIdenticalWeightsScoresAndRanksInAllScenarios() throws Exception {
        Path input=TEMP.newFile("consistent.csv").toPath();Files.copy(Path.of("examples/matrices/consistent_3x3.csv"),input,java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        var args=arguments(TEMP.newFolder("acceptable").toPath(),"--matrix",input.toString());
        var result=new DecisionRunner().analyze(new MatrixFileReader().read(input),args.alternatives(),DecisionMathTest.table(),args);
        for(var scenario:result.scenarios().subList(1,4)) {
            assertEquals("ALREADY_ACCEPTABLE",scenario.status());assertEquals(0,scenario.changedComparisons());
            assertEquals(result.scenarios().get(0).priorities(),scenario.priorities());assertEquals(result.scenarios().get(0).ranking(),scenario.ranking());
            assertEquals(List.of(0,0,0),scenario.positionChanges());
        }
    }

    @Test public void seedReproducesAllAlgorithmicJsonValuesExcludingElapsedTime() throws Exception {
        var again=new DecisionRunner().analyze(analysis.input(),analysis.alternativesFile(),analysis.table(),options);
        var a=new DecisionJson().create(analysis);var b=new DecisionJson().create(again);
        for(var root:List.of(a,b)) for(var s:root.getAsJsonArray("scenarios")) {
            var correction=s.getAsJsonObject().get("correction");if(!correction.isJsonNull()) correction.getAsJsonObject().remove("elapsedMillis");
        }
        assertEquals(a,b);
    }

    @Test public void exportedJsonCanReconstructWeightsNormalizationsScoresAndRanking() throws Exception {
        JsonObject root=json();assertEquals(1,root.get("schemaVersion").getAsInt());
        assertEquals(3,root.getAsJsonArray("criteria").size());assertEquals("cost",root.getAsJsonArray("criteria").get(1).getAsString());
        assertEquals("MIN",root.getAsJsonArray("directions").get(1).getAsString());
        assertArrayEquals(analysis.input().matrix().toArray(),new MatrixFileReader().read(directory.resolve("result.json")).matrix().toArray());
        var table=new AlternativeTable(strings(root.getAsJsonArray("alternatives")),strings(root.getAsJsonArray("criteria")),
                strings(root.getAsJsonArray("directions")).stream().map(Direction::valueOf).toList(),matrix(root.getAsJsonArray("alternativeValues")));
        var normalized=new WeightedRanking().normalize(table);
        for(int i=0;i<3;i++) for(int j=0;j<3;j++) assertEquals(normalized.get(i).get(j),root.getAsJsonArray("normalizedAlternatives").get(i).getAsJsonArray().get(j).getAsDouble(),0);
        for(var item:root.getAsJsonArray("scenarios")) {
            var s=item.getAsJsonObject();var pcm=new PairwiseComparisonMatrix(matrix(s.getAsJsonArray("matrix")));
            var weights=new PerronWeights().calculate(pcm).weights();var result=new WeightedRanking().rank(table,normalized,weights);
            for(int i=0;i<3;i++) { assertEquals(weights.get(i),s.getAsJsonArray("weights").get(i).getAsDouble(),0);
                assertEquals(result.scores().get(i),s.getAsJsonArray("scores").get(i).getAsDouble(),0);
                assertEquals(result.ranks().get(i).intValue(),s.getAsJsonArray("ranksByAlternative").get(i).getAsInt()); }
            assertEquals(result.winner(),s.get("winner").getAsString());
            double previous=Double.POSITIVE_INFINITY;
            for(var entry:s.getAsJsonArray("ranking")) { double score=entry.getAsJsonObject().get("score").getAsDouble();assertTrue(previous>=score);previous=score; }
        }
    }

    @Test public void exportedCsvValuesAndPositionsMatchJsonWithoutPrecisionLoss() throws Exception {
        var weights=Files.readAllLines(directory.resolve("criterion_weights.csv"));var scores=Files.readAllLines(directory.resolve("alternative_scores.csv"));var ranks=Files.readAllLines(directory.resolve("ranking_comparison.csv"));
        assertEquals(13,weights.size());assertEquals(13,scores.size());assertEquals(13,ranks.size());
        assertEquals("scenario,criterion,direction,weight,delta_weight",weights.get(0));
        for(int s=0;s<4;s++) for(int i=0;i<3;i++) {
            var scenario=analysis.scenarios().get(s);String[] w=weights.get(1+s*3+i).split(",");String[] score=scores.get(1+s*3+i).split(",");String[] rank=ranks.get(1+s*3+i).split(",");
            assertEquals(scenario.kind().name(),w[0]);assertEquals(analysis.table().criteria().get(i),w[1]);
            assertEquals(scenario.priorities().weights().get(i),Double.parseDouble(w[3]),0);assertEquals(scenario.weightChanges().get(i),Double.parseDouble(w[4]),0);
            assertEquals(scenario.ranking().scores().get(i),Double.parseDouble(score[2]),0);assertEquals(scenario.scoreChanges().get(i),Double.parseDouble(score[3]),0);
            assertEquals(scenario.ranking().ranks().get(i).intValue(),Integer.parseInt(rank[3]));assertEquals(scenario.positionChanges().get(i).intValue(),Integer.parseInt(rank[4]));
        }
    }

    @Test public void allSavedMatricesMetricsAndCorrectionHistoriesCanBeReloaded() throws Exception {
        JsonObject root=json();
        for(int i=0;i<4;i++) {
            var scenario=analysis.scenarios().get(i);var saved=root.getAsJsonArray("scenarios").get(i).getAsJsonObject();
            var reloaded=new MatrixFileReader().read(directory.resolve(saved.get("matrixCsv").getAsString())).matrix();
            assertArrayEquals(scenario.matrix().toArray(),reloaded.toArray());
            if(i==0) { assertTrue(saved.get("correction").isJsonNull());continue; }
            var correction=saved.getAsJsonObject("correction");assertFalse(correction.has("history"));
            String[] reference=correction.get("historyFile").getAsString().split("#");
            SavedResultVerifier.verify(directory.resolve(reference[0]),analysis.input().matrix());
            var nested=JsonParser.parseString(Files.readString(directory.resolve(reference[0]))).getAsJsonObject();
            String method=i==1?"cosine":"genetic";
            assertEquals(saved.get("ci").getAsDouble(),nested.getAsJsonObject("results").getAsJsonObject(method).getAsJsonObject("finalMetrics").get("ci").getAsDouble(),0);
            assertTrue(nested.getAsJsonObject("results").getAsJsonObject(method).getAsJsonArray("history").size()>0);
        }
    }

    @Test public void explicitCriterionOrderIsCheckedWithoutReordering() throws Exception {
        var good=arguments(directory,"--criterion-order","speed,cost,accuracy");
        assertEquals(analysis.table().criteria(),new DecisionRunner().analyze(analysis.input(),analysis.alternativesFile(),analysis.table(),good).table().criteria());
        var bad=arguments(directory,"--criterion-order","cost,speed,accuracy");
        assertThrows(IllegalArgumentException.class,()->new DecisionRunner().analyze(analysis.input(),analysis.alternativesFile(),analysis.table(),bad));
        assertThrows(IllegalArgumentException.class,()->new DecisionRunner().analyze(analysis.input(),analysis.alternativesFile(),
                new AlternativeTable(List.of("A"),List.of("x","y"),Direction.parse("max,min"),new double[][]{{1,2}}),options));
    }

    @Test public void decisionCliRunsTheFullRealPipelineAndHelpDoesNotAccessFiles() throws Exception {
        Path output=TEMP.newFolder("cli-demo").toPath();var command=command(arguments(output));
        assertEquals(command.error,0,command.code);var saved=JsonParser.parseString(Files.readString(output.resolve("result.json"))).getAsJsonObject();
        assertEquals("C",saved.getAsJsonArray("scenarios").get(0).getAsJsonObject().get("winner").getAsString());
        assertEquals("A",saved.getAsJsonArray("scenarios").get(1).getAsJsonObject().get("winner").getAsString());
        Path absent=TEMP.getRoot().toPath().resolve("help-output");var outputStream=new ByteArrayOutputStream();
        int code=DecisionCli.run(new String[]{"--help","--matrix","missing.csv","--output-dir",absent.toString()},new PrintStream(outputStream),System.err);
        assertEquals(0,code);assertFalse(Files.exists(absent));assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("runDecision"));
    }

    @Test public void invalidArgumentsAndMissingFilesReturnClearNonzeroCodes() {
        var sink=new PrintStream(new ByteArrayOutputStream());
        assertEquals(2,DecisionCli.run(new String[]{"--directions","max,min"},sink,sink));
        assertEquals(1,DecisionCli.run(new String[]{"--matrix","does-not-exist.csv","--alternatives","missing.csv","--directions","max,min"},sink,sink));
        assertEquals(2,DecisionCli.run(new String[]{"--matrix",options.matrix().toString(),"--alternatives",options.alternatives().toString(),"--directions","max"},sink,sink));
    }

    @Test public void writerProtectsBothInputFilesIncludingSymlinkAliases() throws Exception {
        Path output=TEMP.newFolder("protected").toPath();
        Files.createSymbolicLink(output.resolve("criterion_weights.csv"),analysis.alternativesFile().toAbsolutePath());
        String before=Files.readString(analysis.alternativesFile());
        assertThrows(IllegalArgumentException.class,()->new DecisionWriter().write(analysis,output));assertEquals(before,Files.readString(analysis.alternativesFile()));
        Path output2=TEMP.newFolder("protected-matrix").toPath();Files.createSymbolicLink(output2.resolve("cosine_corrected.csv"),analysis.input().path().toAbsolutePath());
        assertThrows(IllegalArgumentException.class,()->new DecisionWriter().write(analysis,output2));
        assertFalse(Files.exists(output2.resolve("original_matrix.csv")));
    }

    @Test public void originalConsistencyCliKeepsItsSchemaAndSeededCorrectionResults() throws Exception {
        Path output=TEMP.newFolder("old-cli").toPath();var sink=new PrintStream(new ByteArrayOutputStream());
        int code=ConsistencyCli.run(new String[]{"--input",options.matrix().toString(),"--method","both","--seed","42","--min-comparison","0.1111111111111111","--max-comparison","9","--output-dir",output.toString()},sink,sink);
        assertEquals(0,code);SavedResultVerifier.verify(output.resolve("result.json"),analysis.input().matrix());
        assertArrayEquals(analysis.scenarios().get(1).matrix().toArray(),new MatrixFileReader().read(output.resolve("cosine_corrected.csv")).matrix().toArray());
        assertArrayEquals(analysis.scenarios().get(2).matrix().toArray(),new MatrixFileReader().read(output.resolve("genetic_corrected.csv")).matrix().toArray());
    }

    private static DecisionArguments arguments(Path output,String... replacements) {
        var values=new java.util.LinkedHashMap<String,String>();String[] base=DecisionArgumentsTest.args("--seed","42","--min-comparison","0.1111111111111111","--max-comparison","9","--output-dir",output.toString());
        for(int i=0;i<base.length;i+=2) values.put(base[i],base[i+1]);for(int i=0;i<replacements.length;i+=2) values.put(replacements[i],replacements[i+1]);
        var args=new ArrayList<String>();values.forEach((k,v)->{args.add(k);args.add(v);});return DecisionArguments.parse(args.toArray(String[]::new));
    }
    private static Command command(DecisionArguments arguments) {
        var output=new ByteArrayOutputStream();var error=new ByteArrayOutputStream();
        var args=new ArrayList<>(List.of("--matrix",arguments.matrix().toString(),"--alternatives",arguments.alternatives().toString(),"--directions","max,min,max","--seed","42",
                "--min-comparison","0.1111111111111111","--max-comparison","9","--output-dir",arguments.outputDirectory().toString()));
        int code=DecisionCli.run(args.toArray(String[]::new),new PrintStream(output,true,StandardCharsets.UTF_8),new PrintStream(error,true,StandardCharsets.UTF_8));
        return new Command(code,output.toString(StandardCharsets.UTF_8),error.toString(StandardCharsets.UTF_8));
    }
    private static JsonObject json() throws Exception { return JsonParser.parseString(Files.readString(directory.resolve("result.json"))).getAsJsonObject(); }
    private static List<String> strings(JsonArray list) { var result=new ArrayList<String>();for(var value:list) result.add(value.getAsString());return result; }
    private static double[][] matrix(JsonArray rows) { double[][] data=new double[rows.size()][];for(int i=0;i<data.length;i++) {var row=rows.get(i).getAsJsonArray();data[i]=new double[row.size()];for(int j=0;j<row.size();j++) data[i][j]=row.get(j).getAsDouble();}return data; }
    private record Command(int code,String output,String error) { }
}
