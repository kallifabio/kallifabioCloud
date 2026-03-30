package de.kallifabio.cloud.libs.logging;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.stream.Stream;

public final class CentralLogger {

    private static final Object LOCK = new Object();
    private static final Path LOG_DIR = Path.of("logs");
    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_DAYS = 7;
    private static volatile boolean initialized = false;

    private CentralLogger() {
    }

    public static void init() {
        synchronized (LOCK) {
            if (initialized) {
                return;
            }

            try {
                Files.createDirectories(LOG_DIR);
                cleanupOldLogs();
                initialized = true;
            } catch (IOException ignored) {
                // If file logging cannot be initialized we keep console logging working.
            }
        }
    }

    public static void log(LogLevel level, String source, String message) {
        write(level, source, message);
    }

    public static void debug(String source, String message) {
        write(LogLevel.DEBUG, source, message);
    }

    public static void info(String source, String message) {
        write(LogLevel.INFO, source, message);
    }

    public static void warn(String source, String message) {
        write(LogLevel.WARN, source, message);
    }

    public static void error(String source, String message) {
        write(LogLevel.ERROR, source, message);
    }

    public static void error(String source, String message, Throwable throwable) {
        StringBuilder builder = new StringBuilder();
        builder.append(message);

        if (throwable != null) {
            StringWriter sw = new StringWriter();
            throwable.printStackTrace(new PrintWriter(sw));
            builder.append(System.lineSeparator()).append(sw);
        }

        write(LogLevel.ERROR, source, builder.toString());
    }

    public static void performance(String operation, long durationMs, String detail) {
        LogLevel level = durationMs >= 1000 ? LogLevel.WARN : LogLevel.DEBUG;
        write(level, "PERFORMANCE", operation + " took " + durationMs + "ms" +
                (detail == null || detail.isEmpty() ? "" : " | " + detail));
    }

    public static void audit(String actor, String action, String detail) {
        write(LogLevel.INFO, "AUDIT", actor + " -> " + action +
                (detail == null || detail.isEmpty() ? "" : " | " + detail));
    }

    private static void write(LogLevel level, String source, String message) {
        init();

        String ts = LocalDateTime.now().format(TS_FORMAT);
        String sanitized = sanitize(message);
        String line = "[" + ts + "] [" + level + "] [" + source + "] " + sanitized;

        synchronized (LOCK) {
            if (!initialized) {
                return;
            }

            try {
                Path logFile = LOG_DIR.resolve("cloud-" + LocalDate.now() + ".log");
                Files.writeString(logFile, line + System.lineSeparator(), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException ignored) {
                // Keep app alive even if logging cannot write to disk.
            }
        }
    }

    private static String sanitize(String message) {
        if (message == null) {
            return "";
        }

        // Remove ANSI escape codes for readable file logs.
        return message.replaceAll("\\u001B\\[[;\\d]*m", "");
    }

    private static void cleanupOldLogs() throws IOException {
        if (!Files.exists(LOG_DIR)) {
            return;
        }

        try (Stream<Path> stream = Files.list(LOG_DIR)) {
            stream.filter(path -> path.getFileName().toString().startsWith("cloud-"))
                    .sorted(Comparator.reverseOrder())
                    .skip(MAX_DAYS)
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
        }
    }
}
