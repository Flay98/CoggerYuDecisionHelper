package MCD.consistency.reproduction;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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

/** Files and fixed-schema reproduction DTOs; does not implement any correction method. */
final class ReproductionFiles {
    private static final Gson GSON = new GsonBuilder().setStrictness(Strictness.STRICT)
            .setPrettyPrinting().serializeNulls().disableHtmlEscaping().create();

    private ReproductionFiles() {
    }

    static JsonObject readJson(Path path) throws IOException {
        try (JsonReader reader = new JsonReader(Files.newBufferedReader(path, StandardCharsets.UTF_8))) {
            reader.setStrictness(Strictness.STRICT);
            var json = JsonParser.parseReader(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT || !json.isJsonObject()) {
                throw new IOException("Expected one JSON object: " + path);
            }
            return json.getAsJsonObject();
        }
    }

    static void writeJson(Path path, Object fixedSchemaData) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, GSON.toJson(fixedSchemaData) + "\n", StandardCharsets.UTF_8);
    }

    static String sha256(Path path) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }

    static void writeCsv(Path path, List<List<String>> rows) throws IOException {
        StringBuilder text = new StringBuilder();
        for (List<String> row : rows) {
            text.append(String.join(",", row.stream().map(ReproductionFiles::csvCell).toList())).append('\n');
        }
        Files.createDirectories(path.getParent());
        Files.writeString(path, text, StandardCharsets.UTF_8);
    }

    private static String csvCell(String value) {
        return value.contains(",") || value.contains("\"") || value.contains("\n")
                ? "\"" + value.replace("\"", "\"\"") + "\"" : value;
    }

    static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
