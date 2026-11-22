package MCD.consistency.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.NoSuchFileException;

/** Separate entry point: MCD.Main and the legacy console application remain unchanged. */
public final class ConsistencyCli {
    private ConsistencyCli() {
    }

    public static void main(String[] args) {
        int code = run(args, System.out, System.err);
        if (code != 0) {
            System.exit(code);
        }
    }

    /** Testable entry point without global stream replacement or System.exit. */
    public static int run(String[] args, PrintStream out, PrintStream err) {
        try {
            CliArguments arguments = CliArguments.parse(args);
            if (arguments.help()) {
                out.print(CliArguments.usage());
                return 0;
            }
            MatrixInput input = new MatrixFileReader().read(arguments.input());
            AnalysisResult analysis = new AnalysisRunner().analyze(input, arguments);
            new ResultWriter().write(analysis, arguments.outputDirectory());
            new ConsoleReport().print(analysis, arguments.outputDirectory(), out);
            return 0;
        } catch (NoSuchFileException error) {
            err.println("File not found: " + error.getFile());
            return 1;
        } catch (IOException error) {
            err.println("I/O error: " + error.getMessage());
            return 1;
        } catch (IllegalArgumentException error) {
            err.println("Error: " + error.getMessage());
            err.println("Use --help for command-line options.");
            return 2;
        } catch (ArithmeticException error) {
            err.println("Numerical error: " + error.getMessage());
            return 1;
        }
    }
}
