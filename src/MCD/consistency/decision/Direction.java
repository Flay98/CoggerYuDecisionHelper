package MCD.consistency.decision;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public enum Direction {
    MAX, MIN;

    public static List<Direction> parse(String text) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("Directions are required: max,min,...");
        return Arrays.stream(text.split(",", -1)).map(value -> {
            try { return valueOf(value.trim().toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException error) { throw new IllegalArgumentException("Unknown direction '" + value + "'; use max or min", error); }
        }).toList();
    }
}
