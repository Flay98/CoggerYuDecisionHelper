package MCD.consistency.experiments;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Stable environment description; transient free-memory samples are deliberately excluded. */
public record BenchmarkEnvironment(String javaVersion, String javaVendor, String vmName, String osName,
                                   String osVersion, String osArch, int availableProcessors, long maxHeapBytes,
                                   String cpuModel, String physicalMemory) {
    public static BenchmarkEnvironment capture() {
        return new BenchmarkEnvironment(System.getProperty("java.version"), System.getProperty("java.vendor"),
                System.getProperty("java.vm.name"), System.getProperty("os.name"), System.getProperty("os.version"),
                System.getProperty("os.arch"), Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().maxMemory(),
                linuxValue("/proc/cpuinfo", "model name"), linuxValue("/proc/meminfo", "MemTotal"));
    }

    private static String linuxValue(String path, String key) {
        try (var lines = Files.lines(Path.of(path))) {
            return lines.filter(line -> line.startsWith(key) && line.contains(":"))
                    .map(line -> line.substring(line.indexOf(':') + 1).strip()).findFirst().orElse("unknown");
        } catch (IOException error) {
            return "unavailable";
        }
    }
}
