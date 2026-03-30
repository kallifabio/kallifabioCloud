package de.kallifabio.cloud.commands.templates;

import de.kallifabio.cloud.Launcher;
import de.kallifabio.cloud.commands.BaseCloudCommand;
import de.kallifabio.cloud.master.ServerInstance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Stream;

public class TemplatePullCommand extends BaseCloudCommand {
    private static final Set<String> EXCLUDED_NAMES = Set.of(
            "logs", "cache", "crash-reports", "session.lock", "usercache.json"
    );

    @Override
    public boolean execute(String sender, String[] args) {
        if (!ensureMaster()) return false;
        if (args.length < 1) {
            warn("Usage: " + getUsage());
            return false;
        }
        if (Launcher.getWrapper() == null) {
            error("Kein lokaler Wrapper verfügbar (nur für lokale/running Server möglich).");
            return false;
        }

        String serverName = args[0];
        ServerInstance instance = master().getRunningServers().get(serverName);
        if (instance == null) {
            error("Server nicht gefunden/running: " + serverName);
            return false;
        }
        if (!Launcher.getWrapper().getManagedServers().containsKey(serverName)) {
            error("Server liegt nicht auf diesem lokalen Wrapper: " + serverName);
            return false;
        }

        String group = instance.groupName;
        String templateRoot = master().getConfigManager().isTemplateTestingMode() ? "templates_test" : "templates";
        Path source = Path.of("servers", group, serverName);
        Path target = Path.of(templateRoot, group);
        Path backup = Path.of("templates_backup", group);

        if (!Files.isDirectory(source)) {
            error("Server-Verzeichnis fehlt: " + source.toAbsolutePath());
            return false;
        }

        try {
            if (Files.exists(target)) {
                recreateDirectory(backup);
                copyDirectoryFiltered(target, backup);
            }
            recreateDirectory(target);
            copyDirectoryFiltered(source, target);
            info("[OK] Template aktualisiert aus laufendem Server:");
            info("  Quelle: " + source.toAbsolutePath());
            info("  Ziel:   " + target.toAbsolutePath());
            return true;
        } catch (IOException e) {
            error("Template-Pull fehlgeschlagen: " + e.getMessage());
            return false;
        }
    }

    @Override
    public String getDescription() {
        return "Kopiert laufenden Serverinhalt in das Group-Template (inkl. Backup).";
    }

    @Override
    public String getUsage() {
        return "templatepull <serverName>";
    }

    private void recreateDirectory(Path dir) throws IOException {
        if (Files.exists(dir)) {
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (RuntimeException ex) {
                if (ex.getCause() instanceof IOException io) {
                    throw io;
                }
                throw ex;
            }
        }
        Files.createDirectories(dir);
    }

    private void copyDirectoryFiltered(Path source, Path target) throws IOException {
        try (Stream<Path> walk = Files.walk(source)) {
            walk.forEach(path -> {
                try {
                    Path relative = source.relativize(path);
                    if (shouldSkip(relative)) {
                        return;
                    }
                    Path out = target.resolve(relative);
                    if (Files.isDirectory(path)) {
                        Files.createDirectories(out);
                    } else {
                        if (out.getParent() != null) {
                            Files.createDirectories(out.getParent());
                        }
                        Files.copy(path, out, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException ex) {
            if (ex.getCause() instanceof IOException io) {
                throw io;
            }
            throw ex;
        }
    }

    private boolean shouldSkip(Path relative) {
        if (relative == null || relative.toString().isBlank()) {
            return false;
        }
        String first = relative.getName(0).toString().toLowerCase();
        if (EXCLUDED_NAMES.contains(first)) {
            return true;
        }
        String filename = relative.getFileName() == null ? "" : relative.getFileName().toString().toLowerCase();
        return filename.endsWith(".log");
    }
}

