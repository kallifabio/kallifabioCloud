package de.kallifabio.cloud.master.template;

import de.kallifabio.cloud.libs.logging.CentralLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class TemplateManager {

    public List<String> calculateDiff(String groupName) {
        Path template = Path.of("templates", groupName);
        Path backup = Path.of("templates_backup", groupName);
        List<String> changed = new ArrayList<>();

        try {
            if (!Files.exists(template)) {
                return changed;
            }

            Map<String, String> templateHashes = fileHashes(template);
            Map<String, String> backupHashes = Files.exists(backup) ? fileHashes(backup) : new HashMap<>();

            for (Map.Entry<String, String> entry : templateHashes.entrySet()) {
                String previous = backupHashes.get(entry.getKey());
                if (previous == null || !previous.equals(entry.getValue())) {
                    changed.add(entry.getKey());
                }
            }

            for (String oldFile : backupHashes.keySet()) {
                if (!templateHashes.containsKey(oldFile)) {
                    changed.add(oldFile + " (removed)");
                }
            }
        } catch (Exception e) {
            CentralLogger.error("TemplateManager", "Template-Diff fehlgeschlagen für " + groupName, e);
        }

        return changed;
    }

    public String createSnapshot(String groupName) {
        Path template = Path.of("templates", groupName);
        if (!Files.exists(template)) {
            return null;
        }

        String version = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path versionDir = Path.of("templates_versions", groupName, version);

        try {
            copyRecursive(template, versionDir);
            return version;
        } catch (IOException e) {
            CentralLogger.error("TemplateManager", "Snapshot fehlgeschlagen", e);
            return null;
        }
    }

    public boolean rollback(String groupName, String version) {
        Path source = Path.of("templates_versions", groupName, version);
        Path target = Path.of("templates", groupName);
        if (!Files.exists(source)) {
            return false;
        }

        try {
            if (Files.exists(target)) {
                clearDirectory(target);
            }
            copyRecursive(source, target);
            return true;
        } catch (IOException e) {
            CentralLogger.error("TemplateManager", "Rollback fehlgeschlagen", e);
            return false;
        }
    }

    public void applyIncrementalBackup(String groupName) {
        Path template = Path.of("templates", groupName);
        Path backup = Path.of("templates_backup", groupName);
        if (!Files.exists(template)) {
            return;
        }

        try {
            List<String> changed = calculateDiff(groupName);
            if (!Files.exists(backup)) {
                Files.createDirectories(backup);
            }

            for (String relative : changed) {
                if (relative.endsWith(" (removed)")) {
                    String removed = relative.replace(" (removed)", "");
                    Files.deleteIfExists(backup.resolve(removed));
                    continue;
                }
                Path src = template.resolve(relative);
                Path dst = backup.resolve(relative);
                Files.createDirectories(dst.getParent());
                Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            CentralLogger.error("TemplateManager", "Inkrementelles Backup fehlgeschlagen", e);
        }
    }

    private Map<String, String> fileHashes(Path root) throws IOException, NoSuchAlgorithmException {
        Map<String, String> hashes = new HashMap<>();
        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        try (Stream<Path> stream = Files.walk(root)) {
            for (Path path : stream.filter(Files::isRegularFile).toList()) {
                byte[] data = Files.readAllBytes(path);
                byte[] hash = digest.digest(data);
                hashes.put(root.relativize(path).toString().replace('\\', '/'), bytesToHex(hash));
            }
        }
        return hashes;
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private void copyRecursive(Path srcRoot, Path dstRoot) throws IOException {
        try (Stream<Path> stream = Files.walk(srcRoot)) {
            for (Path source : stream.toList()) {
                Path target = dstRoot.resolve(srcRoot.relativize(source));
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private void clearDirectory(Path dir) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                    .filter(path -> !path.equals(dir))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
        }
    }
}
