/**
 * Erstellt von Gamer_Kidd_LP | kallifabio
 * am 04.10.2024 um 03:21
 * Projektname: CloudSystemTest
 * Packagename: de.kallifabio.cloudsystem.managers
 */

package de.kallifabio.cloudsystem.managers;

import de.kallifabio.cloudsystem.libs.ConsoleColors;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

public class ServerGroupManager {

    private String groupName;
    private boolean isDynamic;
    private int ram;
    private final Map<String, Process> serverProcesses = new HashMap<>();
    private final Map<String, Thread> outputThreads = new HashMap<>();
    private final ConfigManager configManager = new ConfigManager();

    public ServerGroupManager(String groupName, boolean isDynamic, int ram) {
        this.groupName = groupName;
        this.isDynamic = isDynamic;
        this.ram = ram;
        setupGroup();
    }

    public void setupGroup() {
        File groupDir = new File("./servers/" + groupName);
        File tempgroupDir = new File("./templates/" + groupName);
        if (!groupDir.exists() && !tempgroupDir.exists()) {
            groupDir.mkdirs();
            tempgroupDir.mkdirs();
        }
    }

    public void startServer(String serverName) throws IOException {
        File serverDir = new File("./servers/" + groupName + "/" + serverName);
        if (!serverDir.exists()) {
            serverDir.mkdirs();
        }

        // Bei dynamischen Servern Template kopieren
        if (isDynamic) {
            File templateDir = new File("./templates/" + groupName);
            if (!templateDir.exists()) {
                throw new FileNotFoundException("Template-Verzeichnis nicht gefunden: " + templateDir.getAbsolutePath());
            }

            Files.walk(templateDir.toPath()).forEach(source -> {
                try {
                    Path target = serverDir.toPath().resolve(templateDir.toPath().relativize(source));
                    if (Files.isDirectory(source)) {
                        // Erstelle Verzeichnis, falls es nicht existiert
                        if (!Files.exists(target)) {
                            Files.createDirectories(target);
                        }
                    } else {
                        // Kopiere Dateien und überschreibe existierende
                        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    ConsoleScreenManager.logToMainScreen("Fehler beim Kopieren aus dem Template: " + e.getMessage());
                    e.printStackTrace();
                }
            });
        }

        String jarFile;
        if (groupName.equalsIgnoreCase("Proxy")) {
            jarFile = "bungeecord.jar";
            updateBungeeConfig(serverDir);
        } else if (groupName.equalsIgnoreCase("Lobby")) {
            jarFile = "spigot.jar";
            updateLobbyConfig(serverDir);
        } else {
            throw new IllegalArgumentException("Unbekannte Gruppe: " + groupName);
        }

        // Überprüfe, ob die .jar-Datei existiert
        File jarFilePath = new File(serverDir, jarFile);
        if (!jarFilePath.exists()) {
            throw new FileNotFoundException(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() + " Die Datei " + jarFile + " wurde im Verzeichnis " + serverDir + " nicht gefunden.");
        }

        // Starte den Server-Prozess
        ProcessBuilder processBuilder = new ProcessBuilder("java", "-Xmx" + ram + "M", "-jar", jarFile);
        processBuilder.directory(serverDir);
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();
        ConsoleScreenManager.createServerScreen(serverName);
        ConsoleScreenManager.logToMainScreen(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() + " Server " + serverName + " wird gestartet...");

        // Async-Prozessausgabe lesen und zum Bildschirm weiterleiten
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    ConsoleScreenManager.logToServerScreen(serverName, line);
                }
            } catch (IOException e) {
                ConsoleScreenManager.logToServerScreen(serverName, "Fehler beim Lesen der Serverausgabe: " + e.getMessage());
            }
        }).start();
    }

    private void updateBungeeConfig(File serverDir) throws IOException {
        int maxPlayers = configManager.getMaxPlayersForGroup("Proxy");

        // Pfad zur BungeeCord config.yml
        File configFile = new File(serverDir, "config.yml");
        if (configFile.exists()) {
            // Lade die Datei als YAML
            String content = new String(Files.readAllBytes(configFile.toPath()));

            // Ersetze den Wert für max_players in der config.yml
            content = content.replaceAll("max_players: \\d+", "max_players: " + maxPlayers);

            // Schreibe den neuen Inhalt zurück in die Datei
            Files.write(configFile.toPath(), content.getBytes());
        }
    }

    private void updateLobbyConfig(File serverDir) throws IOException {
        int maxPlayers = configManager.getMaxPlayersForGroup("Lobby");

        // Pfad zur Spigot server.properties
        File propertiesFile = new File(serverDir, "server.properties");
        if (propertiesFile.exists()) {
            // Lade die server.properties
            String content = new String(Files.readAllBytes(propertiesFile.toPath()));

            // Ersetze den Wert für max-players in der server.properties
            content = content.replaceAll("max-players=\\d+", "max-players=" + maxPlayers);

            // Schreibe den neuen Inhalt zurück in die Datei
            Files.write(propertiesFile.toPath(), content.getBytes());
        }
    }

    public void stopServer(String serverName) {

    }


    public void sendMessage(String currentScreen, String command) {
    }
}
