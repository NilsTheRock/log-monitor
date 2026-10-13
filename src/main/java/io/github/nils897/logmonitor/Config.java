package io.github.nils897.logmonitor;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * All settings of one log-monitor run.
 *
 * @param watchDirectory      the directory that is watched; subdirectories are ignored
 * @param filePattern         glob pattern a file name has to match to be scanned
 * @param matchPattern        regular expression a line has to contain to be reported
 * @param scanIntervalSeconds interval of the periodic full scan that backs up the file system events
 * @param alertFile           file the findings are appended to
 * @param stateFile           file the scan progress is persisted in
 */
public record Config(Path watchDirectory, String filePattern, String matchPattern,
                     int scanIntervalSeconds, Path alertFile, Path stateFile) {

    private static final Logger LOG = Logger.getLogger(Config.class.getName());

    private static final String WATCH_DIRECTORY = "watch.directory";
    private static final String FILE_PATTERN = "file.pattern";
    private static final String MATCH_PATTERN = "match.pattern";
    private static final String SCAN_INTERVAL_SECONDS = "scan.interval.seconds";
    private static final String ALERT_FILE = "alert.file";
    private static final String STATE_FILE = "state.file";

    /** Name of the command line argument that points to the properties file. */
    private static final String CONFIG = "config";

    private static final Map<String, String> DEFAULTS = Map.of(
            FILE_PATTERN, "*.log",
            MATCH_PATTERN, "\\bERROR\\b",
            SCAN_INTERVAL_SECONDS, "60",
            ALERT_FILE, "alerts.log",
            STATE_FILE, ".log-monitor-state.properties");

    private static final Set<String> KNOWN_KEYS = Set.of(WATCH_DIRECTORY, FILE_PATTERN, MATCH_PATTERN,
            SCAN_INTERVAL_SECONDS, ALERT_FILE, STATE_FILE);

    /** Short usage text, printed for {@code --help} and when the configuration is invalid. */
    public static final String USAGE = """
            Usage: java -jar log-monitor.jar [--config=<file>] [--<key>=<value> ...]

            Settings, in increasing precedence: defaults, properties file, command line.

              watch.directory        directory to watch (required)
              file.pattern           glob for log file names (default: *.log)
              match.pattern          regular expression to report a line for (default: \\bERROR\\b)
              scan.interval.seconds  interval of the periodic full scan (default: 60)
              alert.file             file the findings are appended to (default: alerts.log)
              state.file             file the scan progress is stored in
                                     (default: .log-monitor-state.properties)

            Relative paths are resolved against the working directory. In a properties file a
            backslash has to be escaped, so the default pattern is written as \\\\bERROR\\\\b.
            """;

    /**
     * Builds the configuration from the defaults, the optional properties file given by
     * {@code --config=<file>} and the remaining command line arguments, in that order.
     *
     * @param args command line arguments, each of the form {@code --key=value}
     * @return the validated configuration
     * @throws IllegalArgumentException if an argument cannot be parsed, a value is invalid or the
     *                                  watched directory is missing
     */
    public static Config load(String[] args) {
        Map<String, String> values = new LinkedHashMap<>(DEFAULTS);
        Map<String, String> arguments = parseArguments(args);
        String configFile = arguments.remove(CONFIG);
        if (configFile != null) {
            values.putAll(readProperties(Path.of(configFile)));
        }
        warnAboutUnknownKeys(arguments.keySet(), "the command line");
        values.putAll(arguments);
        return validated(values);
    }

    private static Map<String, String> parseArguments(String[] args) {
        Map<String, String> arguments = new LinkedHashMap<>();
        for (String argument : args) {
            int separator = argument.indexOf('=');
            if (!argument.startsWith("--") || separator < 0) {
                throw new IllegalArgumentException(
                        "Cannot read argument '" + argument + "', expected --key=value");
            }
            arguments.put(argument.substring(2, separator), argument.substring(separator + 1));
        }
        return arguments;
    }

    private static Map<String, String> readProperties(Path file) {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot read configuration file " + file.toAbsolutePath()
                    + ": " + e.getMessage(), e);
        }
        Map<String, String> values = new LinkedHashMap<>();
        properties.forEach((key, value) -> values.put(key.toString(), value.toString()));
        warnAboutUnknownKeys(values.keySet(), "configuration file " + file);
        return values;
    }

    private static void warnAboutUnknownKeys(Set<String> keys, String source) {
        keys.stream()
                .filter(key -> !KNOWN_KEYS.contains(key))
                .forEach(key -> LOG.warning("Ignoring unknown key '" + key + "' from " + source));
    }

    private static Config validated(Map<String, String> values) {
        Path watchDirectory = requiredDirectory(values.get(WATCH_DIRECTORY));
        String matchPattern = values.get(MATCH_PATTERN);
        try {
            Pattern.compile(matchPattern);
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException(
                    "'" + MATCH_PATTERN + "' is not a valid regular expression: " + matchPattern, e);
        }
        return new Config(watchDirectory, values.get(FILE_PATTERN), matchPattern,
                positiveInterval(values.get(SCAN_INTERVAL_SECONDS)),
                Path.of(values.get(ALERT_FILE)), Path.of(values.get(STATE_FILE)));
    }

    private static Path requiredDirectory(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("'" + WATCH_DIRECTORY + "' is required");
        }
        Path directory = Path.of(value).toAbsolutePath().normalize();
        if (!Files.isDirectory(directory)) {
            throw new IllegalArgumentException("'" + WATCH_DIRECTORY + "' is not an existing directory: " + directory);
        }
        return directory;
    }

    private static int positiveInterval(String value) {
        int interval;
        try {
            interval = Integer.parseInt(value.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "'" + SCAN_INTERVAL_SECONDS + "' must be a whole number, but was '" + value + "'", e);
        }
        if (interval <= 0) {
            throw new IllegalArgumentException(
                    "'" + SCAN_INTERVAL_SECONDS + "' must be greater than 0, but was " + interval);
        }
        return interval;
    }
}
