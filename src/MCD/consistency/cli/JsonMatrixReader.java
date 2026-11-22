package MCD.consistency.cli;

import MCD.consistency.PairwiseComparisonMatrix;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.MalformedJsonException;

import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/** Gson parses JSON syntax; explicit checks prevent coercion of strings and booleans to numbers. */
public final class JsonMatrixReader {
    public MatrixInput read(Path path) throws IOException {
        String name = path.getFileName().toString();
        JsonElement matrix = null;
        try (JsonReader reader = new JsonReader(Files.newBufferedReader(path, StandardCharsets.UTF_8))) {
            reader.setStrictness(Strictness.STRICT);
            if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                throw new IllegalArgumentException("JSON input must be an object containing matrix");
            }
            reader.beginObject();
            Set<String> seen = new HashSet<>();
            while (reader.hasNext()) {
                String key = reader.nextName();
                if (!seen.add(key)) {
                    throw new IllegalArgumentException("Duplicate JSON member: " + key);
                }
                JsonElement value = JsonParser.parseReader(reader);
                if (key.equals("matrix")) {
                    matrix = value;
                } else if (key.equals("name")) {
                    if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                        throw new IllegalArgumentException("JSON name must be a string when present");
                    }
                    name = value.getAsString();
                }
                // Other metadata is ignored; our result.json can also be reloaded as input.
            }
            reader.endObject();
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new IllegalArgumentException("JSON input contains trailing data");
            }
        } catch (JsonParseException | IllegalStateException | MalformedJsonException | EOFException error) {
            throw new IllegalArgumentException("Invalid JSON in " + path + ": " + error.getMessage(), error);
        }
        if (matrix == null || !matrix.isJsonArray()) {
            throw new IllegalArgumentException("JSON matrix is required and must be an array of numeric rows");
        }
        double[][] data = new double[matrix.getAsJsonArray().size()][];
        for (int i = 0; i < data.length; i++) {
            JsonElement row = matrix.getAsJsonArray().get(i);
            if (!row.isJsonArray()) {
                throw new IllegalArgumentException("JSON matrix row " + (i + 1) + " must be an array");
            }
            data[i] = new double[row.getAsJsonArray().size()];
            for (int j = 0; j < data[i].length; j++) {
                JsonElement cell = row.getAsJsonArray().get(j);
                if (!cell.isJsonPrimitive() || !cell.getAsJsonPrimitive().isNumber()) {
                    throw new IllegalArgumentException("JSON matrix element [" + i + "][" + j + "] must be a number, not a string or null");
                }
                data[i][j] = cell.getAsDouble();
            }
        }
        return new MatrixInput(name, path, new PairwiseComparisonMatrix(data));
    }
}
