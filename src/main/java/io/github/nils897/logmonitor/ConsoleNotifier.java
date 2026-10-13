package io.github.nils897.logmonitor;

/** Writes findings to standard output. */
public final class ConsoleNotifier implements Notifier {

    @Override
    public void notify(Finding finding) {
        System.out.println(finding.format());
    }
}
