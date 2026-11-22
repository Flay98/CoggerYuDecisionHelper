package MCD.consistency.decision;

import MCD.consistency.cli.ResultWriter;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/** Separate decision export; correction histories reuse the existing unmodified CLI schema. */
public final class DecisionWriter {
    public void write(DecisionAnalysis analysis, Path output) throws IOException {
        // Preflight ALL destinations against BOTH input files, including links, before writing anything.
        List<String> files=new ArrayList<>(List.of("result.json","criterion_weights.csv","alternative_scores.csv","ranking_comparison.csv"));
        for(var s:analysis.scenarios()) {
            files.add(s.matrixCsv());
            if(s.correction()!=null) for(String file:List.of("result.json","original_matrix.csv",s.correction().cosine()!=null?"cosine_corrected.csv":"genetic_corrected.csv"))
                files.add(s.correctionDirectory()+"/"+file);
        }
        for(String name:files) for(Path input:List.of(analysis.input().path(),analysis.alternativesFile())) {
            Path target=output.resolve(name);
            if(target.toAbsolutePath().normalize().equals(input.toAbsolutePath().normalize()) || (Files.exists(target)&&Files.isSameFile(target,input)))
                throw new IllegalArgumentException("Decision output would overwrite an input file: "+target);
        }
        var json=new DecisionJson().create(analysis);
        Files.createDirectories(output);
        StringBuilder weights=new StringBuilder("scenario,criterion,direction,weight,delta_weight\n");
        StringBuilder scores=new StringBuilder("scenario,alternative,score,delta_score\n");
        StringBuilder ranks=new StringBuilder("scenario,alternative,original_rank,rank,delta_rank,winner\n");
        for(var s:analysis.scenarios()) {
            for(int j=0;j<analysis.table().criterionCount();j++) weights.append(s.kind()).append(',').append(analysis.table().criteria().get(j)).append(',')
                    .append(analysis.table().directions().get(j)).append(',').append(s.priorities().weights().get(j)).append(',').append(s.weightChanges().get(j)).append('\n');
            for(int i=0;i<analysis.table().alternativeCount();i++) {
                String name=analysis.table().alternatives().get(i);
                scores.append(s.kind()).append(',').append(name).append(',').append(s.ranking().scores().get(i)).append(',').append(s.scoreChanges().get(i)).append('\n');
                ranks.append(s.kind()).append(',').append(name).append(',').append(analysis.scenarios().get(0).ranking().ranks().get(i)).append(',')
                        .append(s.ranking().ranks().get(i)).append(',').append(s.positionChanges().get(i)).append(',').append(s.ranking().winner().equals(name)).append('\n');
            }
            write(output.resolve(s.matrixCsv()),matrixCsv(s.matrix().toArray()));
            if(s.correction()!=null) new ResultWriter().write(s.correction(),output.resolve(s.correctionDirectory()));
        }
        write(output.resolve("criterion_weights.csv"),weights.toString()); write(output.resolve("alternative_scores.csv"),scores.toString());
        write(output.resolve("ranking_comparison.csv"),ranks.toString());
        write(output.resolve("result.json"),new GsonBuilder().setPrettyPrinting().serializeNulls().disableHtmlEscaping().create().toJson(json)+"\n");
    }

    private static String matrixCsv(double[][] matrix) {
        StringBuilder text=new StringBuilder();
        for(double[] row:matrix) { var line=new StringJoiner(","); for(double value:row) line.add(Double.toString(value)); text.append(line).append('\n'); }
        return text.toString();
    }
    private static void write(Path file,String text) throws IOException { Files.writeString(file,text,StandardCharsets.UTF_8); }
}
