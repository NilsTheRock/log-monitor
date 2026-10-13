package io.github.nilstherock.logmonitor;

import java.util.List;

/**
 * Forwards every finding to all contained notifiers. Fails fast: if one notifier throws, the
 * remaining ones are not called and the scanner reports the line again on the next scan, so a
 * notifier that already succeeded may see that line twice (at-least-once delivery).
 */
public final class CompositeNotifier implements Notifier {

    private final List<Notifier> notifiers;

    /** @param notifiers the notifiers to forward to, in order */
    public CompositeNotifier(Notifier... notifiers) {
        this.notifiers = List.of(notifiers);
    }

    @Override
    public void notify(Finding finding) throws NotificationException {
        for (Notifier notifier : notifiers) {
            notifier.notify(finding);
        }
    }
}
