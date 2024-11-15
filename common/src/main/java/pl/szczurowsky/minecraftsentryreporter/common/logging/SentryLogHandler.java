package pl.szczurowsky.minecraftsentryreporter.common.logging;

import io.sentry.Sentry;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.protocol.Message;
import pl.szczurowsky.minecraftsentryreporter.common.MSRPlugin;
import pl.szczurowsky.minecraftsentryreporter.common.config.file.SentryConfig;
import pl.szczurowsky.minecraftsentryreporter.common.sentry.sanitizers.StackInformation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.ConsoleHandler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Used ForwardLogHandler from CraftBukkit
 * @see <a href="https://hub.spigotmc.org/stash/projects/SPIGOT/repos/craftbukkit/browse/src/main/java/org/bukkit/craftbukkit/util/ForwardLogHandler.java">ForwardLogHandler</a>
 * LogHandler is used to forward all logs to Sentry
 * @author Szczurowsky
 */
public class SentryLogHandler extends ConsoleHandler {
    private Map<String, Logger> cachedLoggers = new ConcurrentHashMap<>();
    private AtomicInteger logsSentPerSecond = new AtomicInteger(0);
    private final SentryConfig config;
    private final MSRPlugin plugin;

    public SentryLogHandler(SentryConfig config, MSRPlugin plugin) {
        this.config = config;
        this.plugin = plugin;
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                logsSentPerSecond.set(0);
            }
        }, "MSRClearLogsPerSecondThread").start();
    }

    @Override
    public void publish(LogRecord record) {
        Throwable exception = record.getThrown();
        String message = getFormatter().formatMessage(record);

        if (exception != null && config.isProduction()) {
            if (this.logsSentPerSecond.get() > 20) {
                this.plugin.getLogger().warning("Sentry is being flooded with logs, skipping sending this one");
                return;
            }
            StackInformation.sanitizeStack(exception);
            SentryEvent event = new SentryEvent(exception);
            event.setLevel(SentryLevel.ERROR);
            Message sentryMessage = new Message();
            sentryMessage.setMessage(message);
            event.setMessage(sentryMessage);
            event.setLogger(record.getLoggerName());
            event.setServerName(plugin.getServerName());
            event.setTag("ServerVersion", plugin.getServerVersion());
            event.setTag("ServerImplementation", plugin.getServerImplementation());
            event.setExtra("Online players", plugin.getOnlinePlayers());
            event.setExtra("Plugins", plugin.getLoadedPlugins());
            event.setExtra("MinecraftSentryReporter version", plugin.getPluginRelease());
            StringBuilder systemName = new StringBuilder();
            systemName.append(System.getProperty("os.name"));
            systemName.append(" ");
            systemName.append(System.getProperty("os.version"));
            systemName.append(" ");
            systemName.append(System.getProperty("os.arch"));
            event.setEnvironment(systemName.toString());
            Sentry.captureEvent(event);
            this.logsSentPerSecond.getAndIncrement();
        }
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() throws SecurityException {
    }
}
