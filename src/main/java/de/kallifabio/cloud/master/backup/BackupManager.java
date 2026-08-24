package de.kallifabio.cloud.master.backup;

import de.kallifabio.cloud.libs.logging.CentralLogger;
import de.kallifabio.cloud.master.events.EventTimelineService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class BackupManager {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private final EventTimelineService events;

    public BackupManager(EventTimelineService events) {
        this.events = events;
    }

    public Map<String, Object> createBackup(String name, boolean includeLogs) {
        String safeName = safe(name == null || name.isBlank() ? "manual" : name);
        Path target = Path.of("backups", FORMAT.format(LocalDateTime.now()) + "-" + safeName + ".zip");
        try {
            Files.createDirectories(target.getParent());
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target))) {
                addPath(zip, Path.of("config"), "config");
                addPath(zip, Path.of("templates"), "templates");
                addPath(zip, Path.of("templates_backup"), "templates_backup");
                addPath(zip, Path.of("templates_test"), "templates_test");
                addPath(zip, Path.of("data"), "data");
                if (includeLogs) {
                    addPath(zip, Path.of("logs"), "logs");
                } else {
                    addPath(zip, Path.of("logs", "incidents"), "logs/incidents");
                }
            }
            Map<String, Object> result = backupFileToMap(target);
            events.publish("BACKUP_CREATED", "backup", "INFO",
                    "Backup created: " + target, Map.of("path", target.toString(), "includeLogs", includeLogs));
            return result;
        } catch (IOException e) {
            CentralLogger.error("Backup", "Backup failed", e);
            throw new IllegalStateException("Backup failed: " + e.getMessage(), e);
        }
    }

    public List<Map<String, Object>> listBackups(int limit) {
        Path dir = Path.of("backups");
        if (!Files.exists(dir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(path -> path.getFileName().toString().endsWith(".zip"))
                    .sorted(Comparator.reverseOrder())
                    .limit(Math.max(1, limit))
                    .map(this::backupFileToMap)
                    .toList();
        } catch (IOException e) {
            CentralLogger.warn("Backup", "Failed to list backups: " + e.getMessage());
            return List.of();
        }
    }

    public Map<String, Object> restoreToStaging(String backupName) {
        if (backupName == null || backupName.isBlank()) {
            throw new IllegalArgumentException("backupName required");
        }
        Path source = Path.of("backups", Path.of(backupName).getFileName().toString());
        if (!Files.exists(source)) {
            throw new IllegalArgumentException("Backup not found: " + backupName);
        }
        Path target = Path.of("restore_staging", source.getFileName().toString().replace(".zip", ""));
        try {
            Files.createDirectories(target);
            try (java.util.zip.ZipInputStream zip = new java.util.zip.ZipInputStream(Files.newInputStream(source))) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    Path resolved = target.resolve(entry.getName()).normalize();
                    if (!resolved.startsWith(target)) {
                        throw new IOException("Unsafe zip entry: " + entry.getName());
                    }
                    if (entry.isDirectory()) {
                        Files.createDirectories(resolved);
                    } else {
                        Files.createDirectories(resolved.getParent());
                        Files.copy(zip, resolved, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
            events.publish("BACKUP_RESTORED_STAGING", "backup", "INFO",
                    "Backup extracted to staging: " + target, Map.of("backup", source.toString(), "target", target.toString()));
            return Map.of("backup", source.toString(), "stagingPath", target.toString());
        } catch (IOException e) {
            throw new IllegalStateException("Restore staging failed: " + e.getMessage(), e);
        }
    }

    private void addPath(ZipOutputStream zip, Path source, String rootName) throws IOException {
        if (!Files.exists(source)) {
            return;
        }
        Set<String> excluded = Set.of("backups", "restore_staging");
        try (Stream<Path> stream = Files.walk(source)) {
            for (Path path : stream.toList()) {
                if (path.equals(source)) {
                    continue;
                }
                if (excluded.contains(path.getFileName().toString())) {
                    continue;
                }
                String entryName = rootName + "/" + source.relativize(path).toString().replace('\\', '/');
                if (Files.isDirectory(path)) {
                    zip.putNextEntry(new ZipEntry(entryName + "/"));
                    zip.closeEntry();
                } else {
                    zip.putNextEntry(new ZipEntry(entryName));
                    Files.copy(path, zip);
                    zip.closeEntry();
                }
            }
        }
    }

    private Map<String, Object> backupFileToMap(Path path) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", path.getFileName().toString());
        map.put("path", path.toString());
        try {
            map.put("size", Files.size(path));
            map.put("modified", Files.getLastModifiedTime(path).toMillis());
        } catch (IOException ignored) {
        }
        return map;
    }

    private String safe(String value) {
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
