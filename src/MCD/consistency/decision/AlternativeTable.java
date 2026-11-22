package MCD.consistency.decision;

import java.util.HashSet;
import java.util.List;

/** Immutable alternative/criterion data; column order is the matrix criterion order. */
public final class AlternativeTable {
    private final List<String> alternatives;
    private final List<String> criteria;
    private final List<Direction> directions;
    private final double[][] values;

    public AlternativeTable(List<String> alternatives, List<String> criteria, List<Direction> directions, double[][] data) {
        validateNames(alternatives, "alternative"); validateNames(criteria, "criterion");
        if (criteria.size() < 2) throw new IllegalArgumentException("At least two criteria are required");
        if (directions == null || directions.size() != criteria.size() || directions.stream().anyMatch(java.util.Objects::isNull))
            throw new IllegalArgumentException("Direction count must equal criterion count: " + criteria.size());
        if (data == null || data.length != alternatives.size()) throw new IllegalArgumentException("Alternative count and data rows differ");
        values = new double[data.length][];
        for (int i = 0; i < data.length; i++) {
            if (data[i] == null || data[i].length != criteria.size()) throw new IllegalArgumentException("Criterion count mismatch in data row " + (i+1));
            values[i] = data[i].clone();
            for (int j = 0; j < criteria.size(); j++) if (!Double.isFinite(values[i][j]) || values[i][j] <= 0)
                throw new IllegalArgumentException("Criterion value at data row " + (i+1) + ", column " + (j+1) + " must be finite and strictly positive");
        }
        this.alternatives = List.copyOf(alternatives); this.criteria = List.copyOf(criteria); this.directions = List.copyOf(directions);
    }

    private static void validateNames(List<String> names, String kind) {
        if (names == null || names.isEmpty()) throw new IllegalArgumentException("Empty " + kind + " names");
        var seen = new HashSet<String>();
        for (String name : names) {
            if (name == null || name.isBlank() || !name.equals(name.trim()) || name.chars().anyMatch(c -> c < 32 || c == ',' || c == '"'))
                throw new IllegalArgumentException("Invalid " + kind + " name (no blank names, quotes, commas or control characters)");
            if (!seen.add(name)) throw new IllegalArgumentException("Duplicate " + kind + " name: " + name);
        }
    }

    public int alternativeCount() { return values.length; }
    public int criterionCount() { return criteria.size(); }
    public List<String> alternatives() { return alternatives; }
    public List<String> criteria() { return criteria; }
    public List<Direction> directions() { return directions; }
    public double get(int i, int j) { return values[i][j]; }
    public double[][] toArray() { return java.util.Arrays.stream(values).map(double[]::clone).toArray(double[][]::new); }
}
