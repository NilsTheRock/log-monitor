package io.github.nils897.logmonitor;

/**
 * Reports findings to the outside world. Implementations decide where a finding goes - console,
 * file, or any channel added later - which keeps the scanner independent of the alerting channel.
 */
public interface Notifier {

    /**
     * Reports a single finding.
     *
     * @param finding the matching log line
     * @throws NotificationException if the finding could not be reported; the scanner then keeps
     *                               its offset at this line and reports it again on the next scan
     */
    void notify(Finding finding) throws NotificationException;
}
