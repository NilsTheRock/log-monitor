package io.github.nils897.logmonitor;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Locale;

/** Entry point: reads the configuration, wires the components and starts watching. */
public final class LogMonitorApplication {

    private static final String HELP_ARGUMENT = "--help";

    private LogMonitorApplication() {
    }

    /**
     * @param args command line arguments, see {@link Config#USAGE}
     */
    public static void main(String[] args) {
        configureLogging();
        if (List.of(args).contains(HELP_ARGUMENT)) {
            System.out.println(Config.USAGE);
            return;
        }
        try {
            watch(Config.load(args));
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid configuration: " + e.getMessage());
            System.err.println();
            System.err.println(Config.USAGE);
            System.exit(1);
        } catch (IOException e) {
            System.err.println("log-monitor stopped: " + e);
            System.exit(1);
        }
    }

    private static void watch(Config config) throws IOException {
        StateStore stateStore = new StateStore(config.stateFile());
        stateStore.load();
        LogScanner scanner = new LogScanner(config.watchDirectory(), config.filePattern(),
                new LineMatcher(config.matchPattern()), stateStore, notifier(config.alertFile()),
                Clock.systemDefaultZone());
        DirectoryWatcher watcher = new DirectoryWatcher(config.watchDirectory(), config.filePattern(),
                config.scanIntervalSeconds(), scanner);
        Runtime.getRuntime().addShutdownHook(new Thread(watcher::close, "log-monitor-shutdown"));
        watcher.run();
    }

    private static Notifier notifier(Path alertFile) {
        return new CompositeNotifier(new ConsoleNotifier(), new FileNotifier(alertFile));
    }

    /**
     * Logs the tool's own messages as single lines on standard error. The locale is fixed so that
     * the output is identical on every machine, no matter which language the system is set to.
     */
    private static void configureLogging() {
        Locale.setDefault(Locale.ENGLISH);
        System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tF %1$tT %4$-7s %5$s%6$s%n");
    }
}
