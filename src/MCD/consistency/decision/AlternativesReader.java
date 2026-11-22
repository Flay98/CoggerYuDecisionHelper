package MCD.consistency.decision;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Small unquoted UTF-8 CSV dialect; no silent coercion or legacy CSVReader use. */
public final class AlternativesReader {
    public AlternativeTable read(Path file, List<Direction> directions, int expectedCriteria) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        if (lines.size() < 2) throw new IllegalArgumentException("Alternatives CSV must contain a header and at least one data row");
        String[] header = cells(lines.get(0), 1);
        if (!header[0].equals("alternative")) throw new IllegalArgumentException("First CSV header must be 'alternative'");
        if (header.length - 1 != expectedCriteria) throw new IllegalArgumentException("Alternatives criterion count " + (header.length-1)
                + " differs from matrix size " + expectedCriteria);
        List<String> names = new ArrayList<>();
        double[][] data = new double[lines.size()-1][expectedCriteria];
        for (int i = 1; i < lines.size(); i++) {
            String[] cells = cells(lines.get(i), i+1);
            if (cells.length != header.length) throw new IllegalArgumentException("Alternatives CSV row " + (i+1) + ": expected " + header.length + " columns");
            names.add(cells[0]);
            for (int j = 0; j < expectedCriteria; j++) {
                try { data[i-1][j] = Double.parseDouble(cells[j+1]); }
                catch (NumberFormatException error) { throw new IllegalArgumentException("Invalid number in alternatives CSV row " + (i+1) + ", column " + (j+2), error); }
            }
        }
        return new AlternativeTable(names, Arrays.asList(header).subList(1, header.length), directions, data);
    }

    private static String[] cells(String line, int row) {
        if (line.isBlank()) throw new IllegalArgumentException("Empty alternatives CSV row " + row);
        if (line.indexOf('"') >= 0) throw new IllegalArgumentException("Quoted/multiline cells are unsupported in alternatives CSV, row " + row);
        return Arrays.stream(line.split(",", -1)).map(String::trim).toArray(String[]::new);
    }
}
