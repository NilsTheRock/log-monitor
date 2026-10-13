package io.github.nils897.logmonitor;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Watches a directory and triggers scans.
 *
 * <p>Three triggers are combined: an initial scan of all files at startup, a file system event for
 * every change, and a periodic full scan. The periodic scan is the safety net for events that
 * never arrive - over network shares, for example, or when the event queue overflowed.
 *
 * <p>All scans are handed to a single-threaded executor, so they never run in parallel and the
 * scan state cannot be corrupted by concurrent updates.
 */
public final class DirectoryWatcher implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(DirectoryWatcher.class.getName());
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 10;

    private final Path directory;
    private final PathMatcher filePattern;
    private final int scanIntervalSeconds;
    private final LogScanner scanner;
    private final WatchService watchService;
    private final ScheduledExecutorService scans = Executors.newSingleThreadScheduledExecutor();

    /**
     * @param directory           the directory to watch
     * @param filePattern         glob pattern a file name has to match to be scanned
     * @param scanIntervalSeconds interval of the periodic full scan
     * @param scanner             performs the scans
     * @throws IOException if no watch service could be created for the directory
     */
    public DirectoryWatcher(Path directory, String filePattern, int scanIntervalSeconds, LogScanner scanner)
            throws IOException {
        this.directory = directory;
        this.filePattern = FileSystems.getDefault().getPathMatcher("glob:" + filePattern);
        this.scanIntervalSeconds = scanIntervalSeconds;
        this.scanner = scanner;
        this.watchService = directory.getFileSystem().newWatchService();
    }

    /**
     * Scans all files once, then processes file system events until {@link #close()} is called.
     * Blocks the calling thread.
     *
     * @throws IOException if the directory cannot be registered for file system events
     */
    public void run() throws IOException {
        submit(scanner::scanAll);
        scans.scheduleWithFixedDelay(scanner::scanAll,
                scanIntervalSeconds, scanIntervalSeconds, TimeUnit.SECONDS);
        directory.register(watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE);
        LOG.info("Watching " + directory + ", full scan every " + scanIntervalSeconds + "s");
        try {
            watch();
        } catch (ClosedWatchServiceException e) {
            LOG.fine("Watch service closed, stopping");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void watch() throws InterruptedException {
        while (true) {
            WatchKey key = watchService.take();
            key.pollEvents().forEach(this::handle);
            if (!key.reset()) {
                LOG.warning("Directory " + directory + " is no longer accessible, stopping");
                return;
            }
        }
    }

    private void handle(WatchEvent<?> event) {
        if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
            LOG.warning("File system events were lost, scanning all files");
            submit(scanner::scanAll);
            return;
        }
        Path fileName = (Path) event.context();
        if (filePattern.matches(fileName)) {
            submit(() -> scanner.scanFile(directory.resolve(fileName)));
        }
    }

    private void submit(Runnable scan) {
        try {
            scans.execute(scan);
        } catch (RejectedExecutionException e) {
            LOG.fine("Shutting down, skipping scan");
        }
    }

    /** Stops watching and waits for a scan that is still running. */
    @Override
    public void close() {
        try {
            watchService.close();
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Cannot close the watch service", e);
        }
        scans.shutdown();
        try {
            if (!scans.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                LOG.warning("A scan did not finish within " + SHUTDOWN_TIMEOUT_SECONDS + "s, stopping anyway");
                scans.shutdownNow();
            }
        } catch (InterruptedException e) {
            scans.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
