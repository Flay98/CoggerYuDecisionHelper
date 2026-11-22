package MCD.consistency.cli;

import MCD.consistency.PairwiseComparisonMatrix;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Header-free numeric CSV, not the legacy alternatives/criteria table format. */
public final class CsvMatrixReader {
    public PairwiseComparisonMatrix read(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("CSV file is empty: " + path);
        }
        double[][] data = new double[lines.size()][];
        for (int row = 0; row < lines.size(); row++) {
            String line = lines.get(row);
            if (row == 0 && line.startsWith("\uFEFF")) {
                line = line.substring(1);
            }
            if (line.isBlank()) {
                throw new IllegalArgumentException("Empty CSV row " + (row + 1) + " in " + path);
            }
            String[] cells = line.split(",", -1);
            data[row] = new double[cells.length];
            for (int column = 0; column < cells.length; column++) {
                String cell = cells[column].strip();
                if (cell.isEmpty()) {
                    throw new IllegalArgumentException("Empty CSV cell at row " + (row + 1) + ", column " + (column + 1));
                }
                try {
                    data[row][column] = Double.parseDouble(cell);
                } catch (NumberFormatException error) {
                    throw new IllegalArgumentException("Invalid CSV number at row " + (row + 1)
                            + ", column " + (column + 1) + ": " + cell, error);
                }
            }
        }
        // Shape, positivity, finiteness, diagonal and reciprocity have one authority.
        return new PairwiseComparisonMatrix(data);
    }
}
