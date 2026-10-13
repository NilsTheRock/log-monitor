package io.github.nilstherock.logmonitor;

/** Writes findings to standard output. */
public final class ConsoleNotifier implements Notifier {

    @Override
    public void notify(Finding finding) {
        System.out.println(finding.format());
    }
}
