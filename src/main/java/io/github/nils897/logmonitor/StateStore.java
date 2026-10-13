package io.github.nils897.logmonitor;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Keeps the scan progress of every known log file and persists it, so that a restart does not
 * report findings that have already been reported.
 *
 * <p>The state is stored as a properties file: the key is the absolute, normalized file path, the
 * value is {@code <byteOffset>;<lineCount>}.
 */
public final class StateStore {

    private static final Logger LOG = Logger.getLogger(StateStore.class.getName());
    private static final String VALUE_SEPARATOR = ";";

    private final Path stateFile;
    private final Properties entries = new Properties();

    /** @param stateFile the file the state is persisted in */
    public StateStore(Path stateFile) {
        this.stateFile = stateFile.toAbsolutePath().normalize();
    }

    /**
     * Loads the persisted state. A missing, unreadable or corrupt state file is not fatal: the
     * store starts out empty, which means every matching file is scanned from its beginning.
     */
    public void load() {
        entries.clear();
        if (!Files.exists(stateFile)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(stateFile, StandardCharsets.UTF_8)) {
            entries.load(reader);
        } catch (IOException | IllegalArgumentException e) {
            entries.clear();
            LOG.log(Level.WARNING, "Cannot read state file " + stateFile + ", starting with an empty state", e);
        }
    }

    /**
     * @param file a log file
     * @return the stored state, or {@link FileState#INITIAL} if the file is unknown or its entry
     *         is malformed
     */
    public FileState get(Path file) {
        String value = entries.getProperty(key(file));
        if (value == null) {
            return FileState.INITIAL;
        }
        String[] parts = value.split(VALUE_SEPARATOR, 2);
        try {
            return new FileState(Long.parseLong(parts[0]), Long.parseLong(parts[1]));
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            LOG.warning("Ignoring malformed state entry for " + file + ": " + value);
            return FileState.INITIAL;
        }
    }

    /**
     * @param file  a log file
     * @param state its new scan progress
     */
    public void put(Path file, FileState state) {
        entries.setProperty(key(file), state.byteOffset() + VALUE_SEPARATOR + state.lineCount());
    }

    /** @param file a log file whose entry is no longer needed, for example because it was deleted */
    public void remove(Path file) {
        entries.remove(key(file));
    }

    /**
     * Writes the state to disk. The data is written to a temporary file in the same directory and
     * then moved onto the state file, so an interrupted write cannot leave a half-written state
     * behind.
     *
     * @throws IOException if the state could not be written
     */
    public void save() throws IOException {
        Path directory = stateFile.getParent();
        Files.createDirectories(directory);
        Path temporaryFile = Files.createTempFile(directory, "log-monitor-state", ".tmp");
        try (Writer writer = Files.newBufferedWriter(temporaryFile, StandardCharsets.UTF_8)) {
            entries.store(writer, "log-monitor state: <log file>=<byteOffset>;<lineCount>");
        }
        try {
            Files.move(temporaryFile, stateFile,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporaryFile, stateFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String key(Path file) {
        return file.toAbsolutePath().normalize().toString();
    }
}
