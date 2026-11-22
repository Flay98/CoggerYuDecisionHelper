package MCD.consistency.decision;

import MCD.consistency.cli.CliArguments;
import MCD.consistency.cli.CliOptions;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/** Input-specific options plus delegation to the existing mathematical configuration parser. */
public record DecisionArguments(Path matrix, Path alternatives, Path outputDirectory, List<Direction> directions,
                                List<String> criterionOrder, CliArguments correction, boolean help) {
    public DecisionArguments { directions=List.copyOf(directions); criterionOrder=List.copyOf(criterionOrder); }

    public static DecisionArguments parse(String[] args) {
        var supported=new HashSet<>(CliArguments.correctionOptionNames());
        supported.addAll(List.of("--matrix","--alternatives","--directions","--criterion-order","--output-dir"));
        var parsed=CliOptions.parse(args,supported); var values=parsed.values();
        if(!parsed.help()) for(String required:List.of("--matrix","--alternatives","--directions"))
            if(!values.containsKey(required)) throw new IllegalArgumentException("Required argument: "+required);
        Path matrix=values.containsKey("--matrix")?Path.of(values.get("--matrix")):null;
        Path alternatives=values.containsKey("--alternatives")?Path.of(values.get("--alternatives")):null;
        Path output=Path.of(values.getOrDefault("--output-dir","build/results/decision"));
        var delegated=new ArrayList<String>();
        if(matrix!=null) { delegated.add("--input"); delegated.add(matrix.toString()); }
        delegated.add("--output-dir"); delegated.add(output.toString());
        for(var entry:values.entrySet()) if(CliArguments.correctionOptionNames().contains(entry.getKey())) {
            delegated.add(entry.getKey()); delegated.add(entry.getValue());
        }
        if(parsed.help()) delegated.add("--help");
        var correction=CliArguments.parse(delegated.toArray(String[]::new));
        var directions=values.containsKey("--directions")?Direction.parse(values.get("--directions")):List.<Direction>of();
        var order=values.containsKey("--criterion-order")?Arrays.stream(values.get("--criterion-order").split(",",-1)).map(String::trim).toList():List.<String>of();
        if(order.stream().anyMatch(String::isBlank)) throw new IllegalArgumentException("Empty criterion in --criterion-order");
        return new DecisionArguments(matrix,alternatives,output,directions,order,correction,parsed.help());
    }

    public static String usage() {
        return """
                Usage: ./gradlew runDecision --args="--matrix criteria.csv --alternatives table.csv --directions max,min,max [options]"
                  --matrix PATH            Criteria PCM (.csv or .json)
                  --alternatives PATH      UTF-8 unquoted CSV: alternative,criterion1,...
                  --directions LIST        max/min for every criterion, in header order
                  --criterion-order LIST   Optional assertion of exact criterion names/order; no reordering
                  --output-dir PATH        Default: build/results/decision
                  --seed INTEGER           Same seed for BOTH genetic modes (default: 0)
                  --min-comparison NUMBER  Override lower bound for all correction configurations
                  --max-comparison NUMBER  Override upper bound for all correction configurations
                  --cosine-target-ci NUMBER --genetic-target-k NUMBER
                  --max-iterations INTEGER --max-generations INTEGER --population INTEGER
                  --help                   Show help without reading or writing files
                Mathematical defaults and validation are shared with runConsistency:
                CI <= 0.1; K < 0.3; 100 iterations/generations; population 1000; 64 mutation attempts.
                Without explicit bounds: cosine has no extra scale; genetic uses [1/7,7].
                Always four independent scenarios: original, cosine, GA UPPER_ONLY, GA BIDIRECTIONAL.
                Criterion order is positional: row/column j of the PCM is header criterion j.
                Weights = principal RIGHT eigenvector; column normalization + weighted sum.
                This is a separate decision procedure, NOT the original Cogger-Yu algorithm.
                """;
    }
}
