package MCD.consistency.decision;

import MCD.consistency.cli.ResultJson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Explicit stable decision schema; optimizer histories live only in the existing correction export. */
public final class DecisionJson {
    public JsonObject create(DecisionAnalysis analysis) throws IOException {
        var gson=new GsonBuilder().serializeNulls().create();
        var root=new JsonObject(); root.addProperty("schemaVersion",1); root.addProperty("indexBase",0);
        root.addProperty("procedure","PERRON_RIGHT_EIGENVECTOR_COLUMN_NORMALIZATION_WEIGHTED_SUM");
        root.addProperty("criterionOrder","Positional: matrix row/column j equals alternatives header criterion j; no reordering");
        root.addProperty("tieRule","Exact equal double scores keep original alternatives CSV order; ordinal ranks start at 1");
        root.addProperty("matrixFile",analysis.input().path().toAbsolutePath().normalize().toString());
        root.addProperty("alternativesFile",analysis.alternativesFile().toAbsolutePath().normalize().toString());
        root.addProperty("matrixFileSha256",hash(analysis.input().path())); root.addProperty("alternativesFileSha256",hash(analysis.alternativesFile()));
        root.add("criteria",gson.toJsonTree(analysis.table().criteria())); root.add("alternatives",gson.toJsonTree(analysis.table().alternatives()));
        root.add("directions",gson.toJsonTree(analysis.table().directions())); root.add("alternativeValues",gson.toJsonTree(analysis.table().toArray()));
        root.add("normalizedAlternatives",gson.toJsonTree(analysis.normalized())); root.add("matrix",gson.toJsonTree(analysis.input().matrix().toArray()));
        var scenarios=new JsonArray();
        for(var scenario:analysis.scenarios()) {
            var s=new JsonObject(); s.addProperty("id",scenario.kind().name()); s.addProperty("status",scenario.status());
            s.addProperty("changedComparisons",scenario.changedComparisons()); s.addProperty("matrixCsv",scenario.matrixCsv());
            s.add("matrix",gson.toJsonTree(scenario.matrix().toArray())); s.addProperty("ci",scenario.ci()); s.addProperty("k",scenario.k());
            s.add("weights",gson.toJsonTree(scenario.priorities().weights())); s.add("weightChanges",gson.toJsonTree(scenario.weightChanges()));
            var computation=gson.toJsonTree(scenario.priorities()).getAsJsonObject(); computation.remove("weights");
            computation.addProperty("method","LOG_DOMAIN_POWER_ITERATION_RIGHT_PERRON_VECTOR"); s.add("weightComputation",computation);
            s.addProperty("normalizedAlternativesRef","#/normalizedAlternatives");
            s.add("scores",gson.toJsonTree(scenario.ranking().scores())); s.add("scoreChanges",gson.toJsonTree(scenario.scoreChanges()));
            s.add("ranksByAlternative",gson.toJsonTree(scenario.ranking().ranks())); s.add("positionChanges",gson.toJsonTree(scenario.positionChanges()));
            s.add("ranking",gson.toJsonTree(scenario.ranking().ranking())); s.addProperty("winner",scenario.ranking().winner());
            if(scenario.correction()==null) s.add("correction",com.google.gson.JsonNull.INSTANCE);
            else {
                String method=scenario.correction().cosine()!=null?"cosine":"genetic";
                var correction=JsonParser.parseString(new ResultJson().format(scenario.correction())).getAsJsonObject()
                        .getAsJsonObject("results").getAsJsonObject(method);
                correction.remove("history"); correction.remove("matrix"); correction.remove("matrixCsv");
                correction.addProperty("historyFile",scenario.correctionDirectory()+"/result.json#/results/"+method+"/history");
                s.add("correction",correction);
            }
            scenarios.add(s);
        }
        root.add("scenarios",scenarios); return root;
    }

    private static String hash(Path path) throws IOException {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))); }
        catch(NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
}
