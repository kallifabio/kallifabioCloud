package de.kallifabio.cloud.commands.templates;

import de.kallifabio.cloud.Launcher;
import de.kallifabio.cloud.commands.BaseCloudCommand;
import de.kallifabio.cloud.master.ServerInstance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;

public class TemplatePushCommand extends BaseCloudCommand {

    @Override
    public boolean execute(String sender, String[] args) {
        if (!ensureMaster()) return false;
        if (args.length < 1) {
            warn("Usage: " + getUsage());
            return false;
        }
        if (Launcher.getWrapper() == null) {
            error("Kein lokaler Wrapper verfuegbar (nur fuer lokale/running Server moeglich).");
            return false;
        }

        String serverName = args[0];
        boolean clearBeforeCopy = containsArg(args, "--clear");
        boolean restartAfter = containsArg(args, "--restart");

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
        Path source = Path.of(templateRoot, group);
        Path target = Path.of("servers", group, serverName);

        if (!Files.isDirectory(source)) {
            error("Template-Verzeichnis fehlt: " + source.toAbsolutePath());
            return false;
        }
        if (!Files.isDirectory(target)) {
            error("Server-Verzeichnis fehlt: " + target.toAbsolutePath());
            return false;
        }

        try {
            if (clearBeforeCopy) {
                clearDirectory(target);
            }
            copyDirectory(source, target);
            info("[OK] Template in laufenden Server kopiert:");
            info("  Quelle: " + source.toAbsolutePath());
            info("  Ziel:   " + target.toAbsolutePath());

            if (restartAfter) {
                master().restartServer(serverName);
                info("Server wird fuer saubere Uebernahme neugestartet: " + serverName);
            } else {
                warn("Hinweis: Manche Dateien greifen erst nach restartserver " + serverName);
            }
            return true;
        } catch (IOException e) {
            error("Template-Push fehlgeschlagen: " + e.getMessage());
            return false;
        }
    }

    @Override
    public String getDescription() {
        return "Kopiert Group-Template in einen laufenden Server (optional --clear/--restart).";
    }

    @Override
    public String getUsage() {
        return "templatepush <serverName> [--clear] [--restart]";
    }

    private boolean containsArg(String[] args, String value) {
        for (String arg : args) {
            if (value.equalsIgnoreCase(arg)) {
                return true;
            }
        }
        return false;
    }

    private void clearDirectory(Path dir) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                    .filter(path -> !path.equals(dir))
                    .forEach(path -> {
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

    private void copyDirectory(Path source, Path target) throws IOException {
        try (Stream<Path> walk = Files.walk(source)) {
            walk.forEach(path -> {
                try {
                    Path relative = source.relativize(path);
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
}

