package io.github.nilstherock.logmonitor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Scans log files for matching lines and hands every match to the notifier.
 *
 * <p>Each file is only read from the offset stored for it, so an append-only log is never scanned
 * twice. The scanner is not thread-safe on purpose: all scans are executed by a single thread, so
 * the state can never be modified concurrently.
 */
public final class LogScanner {

    private static final Logger LOG = Logger.getLogger(LogScanner.class.getName());
    private static final int BUFFER_SIZE = 8192;
    private static final byte LINE_FEED = '\n';

    private final Path directory;
    private final PathMatcher filePattern;
    private final LineMatcher lineMatcher;
    private final StateStore stateStore;
    private final Notifier notifier;
    private final Clock clock;

    /**
     * @param directory   the directory containing the log files; subdirectories are ignored
     * @param filePattern glob pattern the file name has to match, for example {@code *.log}
     * @param lineMatcher decides which lines are reported
     * @param stateStore  holds and persists the scan progress
     * @param notifier    receives every finding
     * @param clock       source of the detection timestamps
     */
    public LogScanner(Path directory, String filePattern, LineMatcher lineMatcher,
                      StateStore stateStore, Notifier notifier, Clock clock) {
        this.directory = directory;
        this.filePattern = FileSystems.getDefault().getPathMatcher("glob:" + filePattern);
        this.lineMatcher = lineMatcher;
        this.stateStore = stateStore;
        this.notifier = notifier;
        this.clock = clock;
    }

    /** Scans every file in the watched directory whose name matches the file pattern. */
    public void scanAll() {
        try (Stream<Path> files = Files.list(directory)) {
            files.filter(this::isLogFile).sorted().forEach(this::scanFile);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Cannot list directory " + directory, e);
        }
    }

    /**
     * Scans a single file, starting behind the last line that was already processed.
     *
     * @param file the file to scan; it does not have to exist
     */
    public void scanFile(Path file) {
        FileState state = stateStore.get(file);
        try {
            if (Files.notExists(file)) {
                stateStore.remove(file);
                stateStore.save();
                return;
            }
            long size = Files.size(file);
            if (size == state.byteOffset()) {
                return;
            }
            if (size < state.byteOffset()) {
                LOG.info("File " + file + " got shorter, scanning it from the beginning");
                state = FileState.INITIAL;
            }
            stateStore.put(file, reportNewLines(file, state));
            stateStore.save();
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Cannot scan " + file + ", keeping offset " + state.byteOffset()
                    + " and retrying on the next scan", e);
        }
    }

    /**
     * Reads the file from its stored offset and reports every complete line that matches. The file
     * is streamed in chunks, so even a large log file is never held in memory as a whole.
     *
     * @return the state to persist: behind the last processed line, or - if a notification failed -
     *         at the start of the line that could not be reported
     */
    private FileState reportNewLines(Path file, FileState state) throws IOException {
        long offset = state.byteOffset();
        long lineCount = state.lineCount();
        ByteArrayOutputStream lineBytes = new ByteArrayOutputStream();
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            channel.position(offset);
            ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
            while (channel.read(buffer) != -1) {
                buffer.flip();
                while (buffer.hasRemaining()) {
                    byte value = buffer.get();
                    if (value != LINE_FEED) {
                        lineBytes.write(value);
                        continue;
                    }
                    long lineStart = offset;
                    offset += lineBytes.size() + 1;
                    lineCount++;
                    if (!report(file, lineCount, decodeLine(lineBytes))) {
                        return new FileState(lineStart, lineCount - 1);
                    }
                    lineBytes.reset();
                }
                buffer.clear();
            }
        }
        // Whatever is left in lineBytes is a line without a line separator: the writer is not done
        // with it yet, so the offset stays in front of it and it is processed by a later scan.
        return new FileState(offset, lineCount);
    }

    /**
     * Reports the line if it matches.
     *
     * @return {@code false} if the notification failed and the scan of this file has to stop
     */
    private boolean report(Path file, long lineNumber, String line) {
        if (!lineMatcher.matches(line)) {
            return true;
        }
        try {
            notifier.notify(new Finding(file, lineNumber, line, clock.instant()));
            return true;
        } catch (NotificationException e) {
            LOG.log(Level.SEVERE, "Cannot report " + file + " line " + lineNumber
                    + ", reporting it again on the next scan", e);
            return false;
        }
    }

    /** Decodes a complete line and drops the carriage return of CRLF line endings. */
    private static String decodeLine(ByteArrayOutputStream lineBytes) {
        String line = lineBytes.toString(StandardCharsets.UTF_8);
        return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
    }

    private boolean isLogFile(Path file) {
        return Files.isRegularFile(file) && filePattern.matches(file.getFileName());
    }
}
