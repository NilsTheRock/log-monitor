import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Brings the demo back to its starting point: removes the watched directory, the alert file and
 * the state file, then copies the sample log files into the watched directory.
 *
 * <p>Single file program, so it runs on Windows, Linux and macOS without a shell script. Run it
 * from the repository root:
 *
 * <pre>java demo/ResetDemo.java</pre>
 */
public class ResetDemo {

    public static void main(String[] args) throws IOException {
        Path demo = Path.of("demo");
        Path samples = demo.resolve("samples");
        if (Files.notExists(samples)) {
            System.err.println("Cannot find " + samples.toAbsolutePath()
                    + ", please run this from the repository root: java demo/ResetDemo.java");
            return;
        }

        Path watch = demo.resolve("watch");
        deleteRecursively(watch);
        Files.deleteIfExists(demo.resolve("alerts.log"));
        Files.deleteIfExists(demo.resolve("state.properties"));

        Files.createDirectories(watch);
        try (Stream<Path> files = Files.list(samples)) {
            for (Path sample : files.sorted().toList()) {
                Files.copy(sample, watch.resolve(sample.getFileName()));
                System.out.println("copied " + sample.getFileName() + " to " + watch);
            }
        }
        System.out.println("Demo reset. Alert file and state file are gone, "
                + "so every sample finding is reported again.");
    }

    private static void deleteRecursively(Path directory) throws IOException {
        if (Files.notExists(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}
