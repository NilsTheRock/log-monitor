package io.github.nils897.logmonitor;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LineMatcherTest {

    private final LineMatcher matcher = new LineMatcher("\\bERROR\\b");

    @ParameterizedTest
    @ValueSource(strings = {
            "ERROR",
            "2026-10-12 14:03:10 [ERROR] database connection lost",
            "ERROR: database connection lost",
            "retry failed (ERROR)"
    })
    void matchesErrorAsAnOwnWord(String line) {
        assertTrue(matcher.matches(line));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "2026-10-12 14:03:10 INFO status=NO_ERROR",
            "2026-10-12 14:03:10 INFO 0 ERRORS so far",
            "2026-10-12 14:03:10 WARN error while parsing, retrying",
            "2026-10-12 14:03:10 INFO ErrorHandler registered"
    })
    void doesNotMatchSimilarWords(String line) {
        assertFalse(matcher.matches(line));
    }
}
