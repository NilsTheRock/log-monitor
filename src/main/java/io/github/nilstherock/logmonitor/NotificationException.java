package io.github.nilstherock.logmonitor;

/**
 * Thrown when a {@link Notifier} could not deliver a finding. Deliberately independent of the
 * underlying channel, so that notifiers which do not use file I/O can report failures as well.
 */
public class NotificationException extends Exception {

    /**
     * @param message what could not be delivered and why
     * @param cause   the underlying failure
     */
    public NotificationException(String message, Throwable cause) {
        super(message, cause);
    }

    /** @param message what could not be delivered and why */
    public NotificationException(String message) {
        super(message);
    }
}
