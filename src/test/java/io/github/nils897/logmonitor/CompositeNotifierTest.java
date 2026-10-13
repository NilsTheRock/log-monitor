package io.github.nils897.logmonitor;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompositeNotifierTest {

    @Test
    void forwardsEveryFindingToAllNotifiers() throws NotificationException {
        RecordingNotifier first = new RecordingNotifier();
        RecordingNotifier second = new RecordingNotifier();
        Finding finding = new Finding(Path.of("app.log"), 42, "ERROR first failure", Instant.EPOCH);

        new CompositeNotifier(first, second).notify(finding);

        assertEquals(List.of(finding), first.findings());
        assertEquals(List.of(finding), second.findings());
    }
}
