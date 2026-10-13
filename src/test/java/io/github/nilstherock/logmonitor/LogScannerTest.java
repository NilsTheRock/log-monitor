package io.github.nilstherock.logmonitor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogScannerTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-10-12T12:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path directory;

    private RecordingNotifier notifier;
    private StateStore stateStore;
    private LogScanner scanner;

    @BeforeEach
    void setUp() {
        notifier = new RecordingNotifier();
        stateStore = loadedStateStore();
        scanner = scannerWith(stateStore, notifier);
    }

    @Test
    void reportsOnlyLinesAddedSinceTheLastScan() throws IOException {
        write("app.log", "INFO started\n", "ERROR first failure\n");

        scanner.scanAll();
        assertEquals(List.of("ERROR first failure"), notifier.reportedLines());

        append("app.log", "INFO still running\n", "ERROR second failure\n");

        scanner.scanAll();
        assertEquals(List.of("ERROR first failure", "ERROR second failure"), notifier.reportedLines());
    }

    @Test
    void reportsAnIncompleteLastLineOnlyOnceItIsComplete() throws IOException {
        write("app.log", "ERROR complete line\n", "ERROR incomplete");

        scanner.scanAll();
        assertEquals(List.of("ERROR complete line"), notifier.reportedLines());

        append("app.log", " line\n");

        scanner.scanAll();
        assertEquals(List.of("ERROR complete line", "ERROR incomplete line"), notifier.reportedLines());
    }

    @Test
    void scansFromTheBeginningAfterTheFileGotShorter() throws IOException {
        write("app.log", "ERROR before rotation\n");
        scanner.scanAll();

        write("app.log", "ERROR after rotation\n");

        scanner.scanAll();
        assertEquals(List.of("ERROR before rotation", "ERROR after rotation"), notifier.reportedLines());
    }

    @Test
    void doesNotReportKnownLinesAgainAfterRestart() throws IOException {
        write("app.log", "ERROR first failure\n");
        scanner.scanAll();

        RecordingNotifier afterRestart = new RecordingNotifier();
        scannerWith(loadedStateStore(), afterRestart).scanAll();

        assertEquals(List.of(), afterRestart.reportedLines());
    }

    @Test
    void handlesAnEmptyFile() throws IOException {
        write("app.log");

        scanner.scanAll();

        assertTrue(notifier.findings().isEmpty());
    }

    @Test
    void forgetsTheStateOfADeletedFile() throws IOException {
        write("app.log", "ERROR first failure\n");
        scanner.scanAll();
        Path file = directory.resolve("app.log");
        assertEquals(20, stateStore.get(file).byteOffset());

        Files.delete(file);
        scanner.scanFile(file);

        assertEquals(FileState.INITIAL, stateStore.get(file));
    }

    @Test
    void reportsALineAgainWhenItsNotificationFailed() throws IOException {
        notifier.rejectLinesContaining("second");
        write("app.log", "ERROR first failure\n", "ERROR second failure\n", "ERROR third failure\n");

        scanner.scanAll();
        assertEquals(List.of("ERROR first failure"), notifier.reportedLines());
        assertEquals(20, stateStore.get(directory.resolve("app.log")).byteOffset());

        RecordingNotifier workingNotifier = new RecordingNotifier();
        scannerWith(stateStore, workingNotifier).scanAll();

        assertEquals(List.of("ERROR second failure", "ERROR third failure"), workingNotifier.reportedLines());
    }

    @Test
    void handlesCrlfLineEndings() throws IOException {
        write("app.log", "INFO started\r\n", "ERROR first failure\r\n");

        scanner.scanAll();

        assertEquals(List.of("ERROR first failure"), notifier.reportedLines());
    }

    private StateStore loadedStateStore() {
        StateStore store = new StateStore(directory.resolve("state.properties"));
        store.load();
        return store;
    }

    private LogScanner scannerWith(StateStore store, Notifier target) {
        return new LogScanner(directory, "*.log", new LineMatcher("\\bERROR\\b"),
                store, target, FIXED_CLOCK);
    }

    private void write(String fileName, String... lines) throws IOException {
        Files.writeString(directory.resolve(fileName), String.join("", lines), StandardCharsets.UTF_8);
    }

    private void append(String fileName, String... lines) throws IOException {
        Files.writeString(directory.resolve(fileName), String.join("", lines), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
}
