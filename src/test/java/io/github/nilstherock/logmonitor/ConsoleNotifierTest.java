package io.github.nilstherock.logmonitor;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConsoleNotifierTest {

    private final Finding finding = new Finding(Path.of("app.log"), 42, "ERROR first failure", Instant.EPOCH);

    @Test
    void colorsOnlyTheHeader() {
        assertEquals("\u001B[32m" + finding.header() + "\u001B[0m ERROR first failure",
                new ConsoleNotifier(true).format(finding));
    }

    @Test
    void matchesTheAlertFileWithoutColors() {
        assertEquals(finding.format(), new ConsoleNotifier(false).format(finding));
    }
}
