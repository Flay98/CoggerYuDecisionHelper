package MCD.consistency.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Locale;

/** Dispatch by extension; both readers delegate mathematical validation to the core. */
public final class MatrixFileReader {
    public MatrixInput read(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            throw new IOException("Input must be a file, not a directory: " + path);
        }
        String name = path.getFileName().toString();
        String extension = name.toLowerCase(Locale.ROOT);
        try {
            if (extension.endsWith(".csv")) {
                return new MatrixInput(name, path, new CsvMatrixReader().read(path));
            }
            if (extension.endsWith(".json")) {
                return new JsonMatrixReader().read(path);
            }
            throw new IllegalArgumentException("Unknown input extension; expected .csv or .json");
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Invalid input " + path + ": " + error.getMessage(), error);
        }
    }
}
