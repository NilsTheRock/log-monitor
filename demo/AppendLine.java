import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Appends one line to demo/watch/app.log. Without arguments it appends an ERROR line with the
 * current timestamp, which is what the running log-monitor should report immediately.
 *
 * <p>Single file program, so it runs on Windows, Linux and macOS without a shell script. Run it
 * from the repository root:
 *
 * <pre>
 * java demo/AppendLine.java
 * java demo/AppendLine.java 2026-10-12 15:00:00 INFO  [demo] this line is not reported
 * </pre>
 */
public class AppendLine {

    public static void main(String[] args) throws IOException {
        Path file = Path.of("demo", "watch", "app.log");
        if (Files.notExists(file)) {
            System.err.println("Cannot find " + file.toAbsolutePath()
                    + ", please run java demo/ResetDemo.java first (from the repository root).");
            return;
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String line = args.length == 0
                ? timestamp + " ERROR [payment-gateway] payment 4711 rejected by the bank"
                : String.join(" ", args);

        Files.writeString(file, line + "\n", StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        System.out.println("appended to " + file + ": " + line);
    }
}
