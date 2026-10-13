package io.github.nils897.logmonitor;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * A single log line that matched the configured pattern.
 *
 * @param file       the log file the line was found in
 * @param lineNumber the 1-based number of the line within that file
 * @param line       the matching line, without its line separator
 * @param detectedAt the time the match was detected
 */
public record Finding(Path file, long lineNumber, String line, Instant detectedAt) {

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    /**
     * Formats this finding as one line. All notifiers use this method, so console output and
     * alert file contain exactly the same text.
     *
     * @return for example {@code [2026-10-12 14:03:11] Match in app.log (line 42): ... ERROR ...}
     */
    public String format() {
        return "[%s] Match in %s (line %d): %s"
                .formatted(TIMESTAMP.format(detectedAt), file.getFileName(), lineNumber, line);
    }
}
