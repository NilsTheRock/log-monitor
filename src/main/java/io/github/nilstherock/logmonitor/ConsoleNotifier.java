package io.github.nilstherock.logmonitor;

/**
 * Writes findings to standard output. On a terminal the header is printed in green, so the
 * matching line stands out; when the output is redirected or {@code NO_COLOR} is set, the text is
 * plain and identical to the alert file.
 */
public final class ConsoleNotifier implements Notifier {

    private static final String GREEN = "\u001B[32m";
    private static final String RESET = "\u001B[0m";

    private final boolean colored;

    /** Enables colors only if standard output is a terminal and {@code NO_COLOR} is not set. */
    public ConsoleNotifier() {
        this(System.console() != null && System.getenv("NO_COLOR") == null);
    }

    /** @param colored whether the header is printed in color */
    public ConsoleNotifier(boolean colored) {
        this.colored = colored;
    }

    @Override
    public void notify(Finding finding) {
        System.out.println(format(finding));
    }

    String format(Finding finding) {
        if (!colored) {
            return finding.format();
        }
        return GREEN + finding.header() + RESET + " " + finding.line();
    }
}
