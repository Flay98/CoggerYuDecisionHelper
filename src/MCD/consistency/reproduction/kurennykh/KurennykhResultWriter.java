package MCD.consistency.reproduction.kurennykh;

import MCD.consistency.CosineCorrectionResult;
import MCD.consistency.PairwiseComparisonMatrix;
import MCD.consistency.decision.PerronWeights;
import MCD.consistency.reproduction.ReproductionFiles;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.Strictness;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Compact typed JSONL archives, existing CSV/JSON utilities; no per-matrix filesystem explosion. */
public final class KurennykhResultWriter {
    static final Gson JSON=new GsonBuilder().serializeNulls().disableHtmlEscaping().setStrictness(Strictness.STRICT).create();
    private KurennykhResultWriter() { }
    public record Input(int schemaVersion,String id,int size,int sample,long generationSeed,String sha256,double[][] matrix) { }
    public record Output(int schemaVersion,KurennykhRunResult run,double[][] matrix,PerronWeights.Result initialPriorities,
                         PerronWeights.Result finalPriorities,KurennykhMetrics.Ranking initialRanking,
                         KurennykhMetrics.Ranking finalRanking,List<CosineCorrectionResult.Change> history) { }

    static BufferedWriter archive(Path file) throws IOException {
        Files.createDirectories(file.getParent());return new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(file)),StandardCharsets.UTF_8));
    }
    static BufferedReader readArchive(Path file) throws IOException {
        return new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(file)),StandardCharsets.UTF_8));
    }
    static void line(BufferedWriter writer,Object record) throws IOException { writer.write(JSON.toJson(record));writer.newLine(); }
    static String hash(byte[] data) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));}
        catch(NoSuchAlgorithmException error){throw new IllegalStateException(error);}
    }
    public static String matrixCsv(PairwiseComparisonMatrix matrix) {
        var text=new StringBuilder();for(double[] row:matrix.toArray()) {var cells=new StringJoiner(",");for(double value:row)cells.add(Double.toString(value));text.append(cells).append('\n');}return text.toString();
    }
    static String matrixHash(PairwiseComparisonMatrix matrix) { return hash(matrixCsv(matrix).getBytes(StandardCharsets.UTF_8)); }
    static void csv(Path file,List<?> records) throws IOException {
        if(records.isEmpty())throw new IllegalArgumentException("No CSV rows");
        var first=JSON.toJsonTree(records.get(0)).getAsJsonObject();var keys=first.keySet().stream().toList();
        List<List<String>> rows=new ArrayList<>();rows.add(keys);
        for(Object record:records) {JsonObject json=JSON.toJsonTree(record).getAsJsonObject();rows.add(keys.stream().map(k->{var v=json.get(k);return v==null||v.isJsonNull()?"NA":v.getAsString();}).toList());}
        ReproductionFiles.writeCsv(file,rows);
    }
    static String csvText(List<?> records) throws IOException {
        // Use the same CSV utility in memory via its documented escaping, for verification without writes.
        if(records.isEmpty())throw new IllegalArgumentException("No CSV rows");
        var keys=JSON.toJsonTree(records.get(0)).getAsJsonObject().keySet().stream().toList();
        var text=new StringBuilder(String.join(",",keys)).append('\n');
        for(Object record:records) {var json=JSON.toJsonTree(record).getAsJsonObject();var cells=new ArrayList<String>();
            for(String k:keys) {var v=json.get(k);String s=v==null||v.isJsonNull()?"NA":v.getAsString();cells.add(s.contains(",")||s.contains("\"")||s.contains("\n")?"\""+s.replace("\"","\"\"")+"\"":s);}text.append(String.join(",",cells)).append('\n');}
        return text.toString();
    }
}
