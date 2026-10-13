package io.github.nilstherock.logmonitor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigTest {

    @TempDir
    Path directory;

    @Test
    void appliesDefaultsForEveryKeyButTheWatchedDirectory() {
        Config config = Config.load(new String[] {"--watch.directory=" + directory});

        assertEquals(directory.toAbsolutePath().normalize(), config.watchDirectory());
        assertEquals("*.log", config.filePattern());
        assertEquals("\\bERROR\\b", config.matchPattern());
        assertEquals(60, config.scanIntervalSeconds());
        assertEquals(Path.of("alerts.log"), config.alertFile());
        assertEquals(Path.of(".log-monitor-state.properties"), config.stateFile());
    }

    @Test
    void readsThePropertiesFileAndLetsTheCommandLineWin() throws IOException {
        Path configFile = writeConfigFile("""
                watch.directory=%s
                file.pattern=*.txt
                scan.interval.seconds=30
                """);

        Config config = Config.load(new String[] {"--config=" + configFile, "--scan.interval.seconds=10"});

        assertEquals("*.txt", config.filePattern());
        assertEquals(10, config.scanIntervalSeconds());
    }

    @Test
    void rejectsAMissingWatchedDirectory() {
        assertEquals("'watch.directory' is required",
                assertThrows(IllegalArgumentException.class, () -> Config.load(new String[0])).getMessage());
    }

    @Test
    void rejectsAWatchedDirectoryThatDoesNotExist() {
        String missing = directory.resolve("nowhere").toString();

        assertThrows(IllegalArgumentException.class,
                () -> Config.load(new String[] {"--watch.directory=" + missing}));
    }

    @Test
    void rejectsAnInvalidScanInterval() {
        assertThrows(IllegalArgumentException.class, () -> Config.load(
                new String[] {"--watch.directory=" + directory, "--scan.interval.seconds=0"}));
    }

    @Test
    void rejectsAnInvalidMatchPattern() {
        assertThrows(IllegalArgumentException.class, () -> Config.load(
                new String[] {"--watch.directory=" + directory, "--match.pattern=[unclosed"}));
    }

    @Test
    void rejectsAnArgumentThatIsNotAKeyValuePair() {
        assertThrows(IllegalArgumentException.class, () -> Config.load(new String[] {"-watch.directory"}));
    }

    private Path writeConfigFile(String template) throws IOException {
        Path configFile = directory.resolve("log-monitor.properties");
        // A backslash has to be escaped in a properties file, which matters for Windows paths.
        String escapedDirectory = directory.toString().replace("\\", "\\\\");
        Files.writeString(configFile, template.formatted(escapedDirectory), StandardCharsets.UTF_8);
        return configFile;
    }
}
