package MCD.consistency.experiments;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** Small file utilities for the explicit benchmark schemas, not a mathematical implementation. */
final class ExperimentFiles {
    static final Gson JSON = new GsonBuilder().setPrettyPrinting().serializeNulls().disableHtmlEscaping()
            .setStrictness(Strictness.STRICT).create();

    private ExperimentFiles() {
    }

    static JsonObject read(Path path) throws IOException {
        try (JsonReader reader = new JsonReader(Files.newBufferedReader(path, StandardCharsets.UTF_8))) {
            reader.setStrictness(Strictness.STRICT);
            var value = JsonParser.parseReader(reader);
            require(reader.peek() == JsonToken.END_DOCUMENT && value.isJsonObject(), "Expected one JSON object: " + path);
            return value.getAsJsonObject();
        }
    }

    static void write(Path path, Object value) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, JSON.toJson(value) + "\n", StandardCharsets.UTF_8);
    }

    static String hash(Path path) throws IOException {
        return hash(Files.readAllBytes(path));
    }

    static String hash(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    /** The benchmark's own records define a stable flat CSV schema with nullable numeric fields. */
    static void csv(Path path, List<?> records) throws IOException {
        require(!records.isEmpty(), "Cannot infer CSV schema from empty rows");
        var first = JSON.toJsonTree(records.get(0)).getAsJsonObject();
        List<String> keys = first.keySet().stream().toList();
        StringBuilder text = new StringBuilder(String.join(",", keys)).append('\n');
        for (Object record : records) {
            JsonObject row = JSON.toJsonTree(record).getAsJsonObject();
            List<String> cells = keys.stream().map(key -> cell(row.get(key))).toList();
            text.append(String.join(",", cells)).append('\n');
        }
        Files.createDirectories(path.getParent());
        Files.writeString(path, text, StandardCharsets.UTF_8);
    }

    private static String cell(JsonElement value) {
        String text = value == null || value.isJsonNull() ? "NA" : value.getAsString();
        return text.contains(",") || text.contains("\"") || text.contains("\n")
                ? "\"" + text.replace("\"", "\"\"") + "\"" : text;
    }

    static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
