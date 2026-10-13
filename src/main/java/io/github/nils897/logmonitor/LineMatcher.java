package io.github.nils897.logmonitor;

import java.util.regex.Pattern;

/**
 * Decides whether a log line is worth reporting. Wraps the compiled regular expression so that
 * it is compiled once and the matching rule lives in a single place.
 */
public final class LineMatcher {

    private final Pattern pattern;

    /**
     * @param regex the regular expression to search for in every line
     * @throws java.util.regex.PatternSyntaxException if the expression is not a valid regex
     */
    public LineMatcher(String regex) {
        this.pattern = Pattern.compile(regex);
    }

    /**
     * @param line a single log line
     * @return {@code true} if the pattern occurs anywhere in the line
     */
    public boolean matches(String line) {
        return pattern.matcher(line).find();
    }

    /** @return the regular expression this matcher was created with */
    public String pattern() {
        return pattern.pattern();
    }
}
