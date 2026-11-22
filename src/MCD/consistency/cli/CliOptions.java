package MCD.consistency.cli;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Shared argument syntax only; mathematical defaults/validation stay in CliArguments. */
public final class CliOptions {
    private CliOptions() { }

    public record Parsed(Map<String, String> values, boolean help) {
        public Parsed { values = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(values)); }
    }

    public static Parsed parse(String[] args, Set<String> valueOptions) {
        Map<String, String> values = new LinkedHashMap<>();
        boolean help = args.length == 0;
        for (int i = 0; i < args.length; i++) {
            String token = args[i];
            if (token.equals("--help")) { help = true; continue; }
            int equals = token.indexOf('=');
            String key = equals < 0 ? token : token.substring(0, equals);
            if (!valueOptions.contains(key)) throw new IllegalArgumentException("Unknown argument: " + token);
            if (values.containsKey(key)) throw new IllegalArgumentException("Duplicate argument: " + key);
            String value;
            if (equals >= 0) value = token.substring(equals + 1);
            else {
                if (i + 1 >= args.length || args[i + 1].startsWith("--")) throw new IllegalArgumentException("Missing value for " + key);
                value = args[++i];
            }
            if (value.isBlank()) throw new IllegalArgumentException("Empty value for " + key);
            values.put(key, value);
        }
        return new Parsed(values, help);
    }
}
