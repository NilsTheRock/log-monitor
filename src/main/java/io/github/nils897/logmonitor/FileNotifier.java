package io.github.nils897.logmonitor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Appends findings to an alert file, using the same format as the console output. */
public final class FileNotifier implements Notifier {

    private final Path alertFile;

    /** @param alertFile the file findings are appended to; it is created if it does not exist */
    public FileNotifier(Path alertFile) {
        this.alertFile = alertFile.toAbsolutePath().normalize();
    }

    @Override
    public void notify(Finding finding) throws NotificationException {
        try {
            Files.createDirectories(alertFile.getParent());
            Files.writeString(alertFile, finding.format() + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new NotificationException("Cannot append to alert file " + alertFile, e);
        }
    }
}
