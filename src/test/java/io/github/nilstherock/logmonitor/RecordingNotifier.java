package io.github.nilstherock.logmonitor;

import java.util.ArrayList;
import java.util.List;

/** Test double that records all findings and can be told to reject some of them. */
final class RecordingNotifier implements Notifier {

    private final List<Finding> findings = new ArrayList<>();
    private String rejectedFragment;

    /** Makes this notifier fail for every line containing the given text. */
    void rejectLinesContaining(String fragment) {
        this.rejectedFragment = fragment;
    }

    @Override
    public void notify(Finding finding) throws NotificationException {
        if (rejectedFragment != null && finding.line().contains(rejectedFragment)) {
            throw new NotificationException("Test notifier rejected: " + finding.line());
        }
        findings.add(finding);
    }

    List<Finding> findings() {
        return findings;
    }

    List<String> reportedLines() {
        return findings.stream().map(Finding::line).toList();
    }
}
