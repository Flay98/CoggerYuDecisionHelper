package MCD.consistency.decision;

import MCD.consistency.cli.MatrixFileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.NoSuchFileException;

/** Independent entry point. Neither MCD.Main nor its Simple/CoggerYu modes are modified. */
public final class DecisionCli {
    private DecisionCli() { }
    public static void main(String[] args) {
        int code=run(args,System.out,System.err);
        if(code!=0) System.exit(code);
    }
    public static int run(String[] args,PrintStream out,PrintStream err) {
        try {
            var options=DecisionArguments.parse(args);
            if(options.help()) { out.print(DecisionArguments.usage()); return 0; }
            var input=new MatrixFileReader().read(options.matrix());
            var table=new AlternativesReader().read(options.alternatives(),options.directions(),input.matrix().size());
            var analysis=new DecisionRunner().analyze(input,options.alternatives(),table,options);
            new DecisionWriter().write(analysis,options.outputDirectory());
            new DecisionConsoleReport().print(analysis,options.outputDirectory(),out);
            return 0;
        } catch(NoSuchFileException error) { err.println("File not found: "+error.getFile()); return 1; }
        catch(IOException error) { err.println("I/O error: "+error.getMessage()); return 1; }
        catch(IllegalArgumentException error) { err.println("Error: "+error.getMessage()+"; use --help"); return 2; }
        catch(ArithmeticException error) { err.println("Numerical error: "+error.getMessage()); return 1; }
    }
}
