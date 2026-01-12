/**
 * Erstellt von Gamer_Kidd_LP | kallifabio
 * am 10.01.2026 um 01:43
 * Projektname: CloudSystemTest
 * Packagename: de.kallifabio.cloud.wrapper
 */

package de.kallifabio.cloud.wrapper;

import de.kallifabio.cloud.libs.console.ConsoleColors;
import de.kallifabio.cloud.libs.Message;
import de.kallifabio.cloud.config.ConfigManager;
import de.kallifabio.cloud.libs.console.ConsoleScreenManager;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Serverprocess {

    private final String serverName;
    private final String groupName;
    private int port;
    private final Wrapper wrapper;
    private final ConfigManager configManager;

    private Process process;
    private BufferedWriter processInput;
    private Thread outputThread;

    private int allocatedMemory;
    private volatile boolean running = false;
    private long startTime;

    // Metrics
    private volatile int playerCount = 0;
    private volatile int maxPlayers = 100;
    private volatile double tps = 20.0;
    private volatile long memoryUsage = 0;
    private long lastMetricUpdate = 0;

    // Monitoring
    private final ScheduledExecutorService metricsScheduler = Executors.newSingleThreadScheduledExecutor();

    // Regex patterns for log parsing
    private static final Pattern PLAYER_JOIN_PATTERN = Pattern.compile("(\\w+)\\[.+\\] logged in");
    private static final Pattern PLAYER_LEAVE_PATTERN = Pattern.compile("(\\w+) lost connection");
    private static final Pattern TPS_PATTERN = Pattern.compile("TPS from last \\d+m.*?([0-9.]+)");

    public Serverprocess(String serverName, String groupName, int port, Wrapper wrapper) {
        this.serverName = serverName;
        this.groupName = groupName;
        this.port = port;
        this.wrapper = wrapper;
        this.configManager = new ConfigManager();
        this.allocatedMemory = configManager.getRamForGroup(groupName);
        this.maxPlayers = configManager.getMaxPlayersForGroup(groupName);
    }

    public void start() throws IOException {
        if (running) {
            ConsoleScreenManager.logToMainScreen(ConsoleColors.YELLOW + ConsoleColors.PREFIX +
                    ConsoleColors.getCurrentTime() + " Server " + serverName + " läuft bereits");
            return;
        }

        ConsoleScreenManager.logToMainScreen(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Starte Server: " + serverName + " (" + groupName + ") auf Port " + port);

        // Setup server directory
        File serverDir = setupServerDirectory();

        // Prepare server files (template copy)
        prepareServerFiles(serverDir);

        // WICHTIG: Akzeptiere EULA automatisch
        acceptEula(serverDir);

        // WICHTIG: Update server.properties mit korrektem Port
        updateServerProperties(serverDir, port);

        // Get JAR file
        String jarFile = getJarFileName();
        File jarFilePath = new File(serverDir, jarFile);

        if (!jarFilePath.exists()) {
            throw new FileNotFoundException("JAR-Datei nicht gefunden: " + jarFilePath.getAbsolutePath());
        }

        // Create server screen
        ConsoleScreenManager.createServerScreen(serverName);

        // Start the server process
        ProcessBuilder processBuilder = new ProcessBuilder(
                "java",
                "-Xmx" + allocatedMemory + "M",
                "-Xms" + (allocatedMemory / 2) + "M",
                "-XX:+UseG1GC",
                "-XX:+ParallelRefProcEnabled",
                "-XX:MaxGCPauseMillis=200",
                "-XX:+UnlockExperimentalVMOptions",
                "-XX:+DisableExplicitGC",
                "-XX:G1NewSizePercent=30",
                "-XX:G1MaxNewSizePercent=40",
                "-XX:G1HeapRegionSize=8M",
                "-XX:G1ReservePercent=20",
                "-XX:G1HeapWastePercent=5",
                "-jar",
                jarFile,
                "--nogui"
        );

        processBuilder.directory(serverDir);
        processBuilder.redirectErrorStream(true);

        process = processBuilder.start();
        running = true;
        startTime = System.currentTimeMillis();

        // Setup process I/O
        setupProcessIO();

        // Send initial status to master
        wrapper.sendServerStatus(serverName, "STARTING");

        // Start metrics collection
        startMetricsCollection();

        ConsoleScreenManager.logToMainScreen(ConsoleColors.GREEN + ConsoleColors.PREFIX +
                ConsoleColors.getCurrentTime() + " Server " + serverName + " gestartet auf Port " + port);
    }

    private File setupServerDirectory() throws IOException {
        File serverDir = new File("./servers/" + groupName + "/" + serverName);
        if (!serverDir.exists()) {
            serverDir.mkdirs();
        }
        return serverDir;
    }

    private void prepareServerFiles(File serverDir) throws IOException {
        boolean isDynamic = "true".equalsIgnoreCase(
                configManager.getServergroup("ServerGroup." + groupName + ".Dynamic")
        );

        if (isDynamic) {
            copyTemplateFiles(serverDir);
        }
    }

    private void copyTemplateFiles(File serverDir) throws IOException {
        File templateDir = new File("./templates/" + groupName);

        if (!templateDir.exists()) {
            ConsoleScreenManager.logToMainScreen(ConsoleColors.YELLOW + ConsoleColors.PREFIX +
                    ConsoleColors.getCurrentTime() + " Template-Verzeichnis nicht gefunden, erstelle es: " +
                    templateDir.getAbsolutePath());
            templateDir.mkdirs();
            return;
        }

        ConsoleScreenManager.logToMainScreen(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Kopiere Template-Dateien für " + serverName + "...");

        Files.walk(templateDir.toPath()).forEach(source -> {
            try {
                Path target = serverDir.toPath().resolve(templateDir.toPath().relativize(source));
                if (Files.isDirectory(source)) {
                    if (!Files.exists(target)) {
                        Files.createDirectories(target);
                    }
                } else {
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                ConsoleScreenManager.logToMainScreen(ConsoleColors.RED +
                        "Fehler beim Kopieren: " + e.getMessage());
            }
        });
    }

    private void acceptEula(File serverDir) throws IOException {
        File eulaFile = new File(serverDir, "eula.txt");
        List<String> eulaLines = Arrays.asList(
                "#By changing the setting below to TRUE you are indicating your agreement to our EULA (https://account.mojang.com/documents/minecraft_eula).",
                "#" + new java.util.Date(),
                "eula=true"
        );
        Files.write(eulaFile.toPath(), eulaLines);

        ConsoleScreenManager.logToMainScreen(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " EULA akzeptiert für " + serverName);
    }

    private void updateServerProperties(File serverDir, int port) throws IOException {
        File propertiesFile = new File(serverDir, "server.properties");

        if (!propertiesFile.exists()) {
            // Erstelle neue server.properties
            createDefaultProperties(propertiesFile, port);
        } else {
            // Update existierende server.properties
            List<String> lines = Files.readAllLines(propertiesFile.toPath());
            List<String> newLines = new ArrayList<>();

            boolean portSet = false;
            boolean onlineModeSet = false;
            boolean maxPlayersSet = false;

            for (String line : lines) {
                if (line.startsWith("server-port=")) {
                    newLines.add("server-port=" + port);
                    portSet = true;
                } else if (line.startsWith("online-mode=")) {
                    newLines.add("online-mode=false");
                    onlineModeSet = true;
                } else if (line.startsWith("max-players=")) {
                    newLines.add("max-players=" + maxPlayers);
                    maxPlayersSet = true;
                } else {
                    newLines.add(line);
                }
            }

            // Füge fehlende Zeilen hinzu
            if (!portSet) {
                newLines.add("server-port=" + port);
            }
            if (!onlineModeSet) {
                newLines.add("online-mode=false");
            }
            if (!maxPlayersSet) {
                newLines.add("max-players=" + maxPlayers);
            }

            Files.write(propertiesFile.toPath(), newLines);
        }

        ConsoleScreenManager.logToMainScreen(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " server.properties aktualisiert (Port: " + port + ", Max-Players: " + maxPlayers + ")");
    }

    private void createDefaultProperties(File propertiesFile, int port) throws IOException {
        StringBuilder properties = new StringBuilder();
        properties.append("#Minecraft server properties\n");
        properties.append("#").append(new java.util.Date()).append("\n");
        properties.append("server-port=").append(port).append("\n");
        properties.append("max-players=").append(maxPlayers).append("\n");
        properties.append("online-mode=false\n");
        properties.append("view-distance=8\n");
        properties.append("simulation-distance=10\n");
        properties.append("difficulty=easy\n");
        properties.append("gamemode=survival\n");
        properties.append("pvp=true\n");
        properties.append("spawn-protection=0\n");
        properties.append("motd=Cloud Server - ").append(serverName).append("\n");

        Files.write(propertiesFile.toPath(), properties.toString().getBytes());
    }

    private String getJarFileName() {
        if (groupName.equalsIgnoreCase("Proxy")) {
            return "bungeecord.jar";
        } else {
            return "spigot.jar";
        }
    }

    private void setupProcessIO() {
        // Setup input stream to send commands to server
        processInput = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));

        // Setup output stream to read server logs
        outputThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {

                String line;
                while ((line = reader.readLine()) != null && running) {
                    // Log to screen
                    ConsoleScreenManager.logToServerScreen(serverName, line);

                    // Parse for metrics
                    parseLogLine(line);

                    // Check for server ready
                    if (line.contains("Done (") || line.contains("Listening on")) {
                        onServerReady();
                    }
                }
            } catch (IOException e) {
                if (running) {
                    ConsoleScreenManager.logToServerScreen(serverName,
                            ConsoleColors.RED + "Fehler beim Lesen der Ausgabe: " + e.getMessage());
                }
            } finally {
                // Server process ended
                if (running) {
                    ConsoleScreenManager.logToMainScreen(ConsoleColors.YELLOW + ConsoleColors.PREFIX +
                            ConsoleColors.getCurrentTime() + " Server " + serverName + " wurde unerwartet beendet");
                    running = false;
                    wrapper.sendServerStatus(serverName, "CRASHED");
                }
            }
        }, "ServerOutput-" + serverName);

        outputThread.start();
    }

    private void parseLogLine(String line) {
        // Parse player joins
        Matcher joinMatcher = PLAYER_JOIN_PATTERN.matcher(line);
        if (joinMatcher.find()) {
            playerCount++;
            return;
        }

        // Parse player leaves
        Matcher leaveMatcher = PLAYER_LEAVE_PATTERN.matcher(line);
        if (leaveMatcher.find()) {
            playerCount = Math.max(0, playerCount - 1);
            return;
        }

        // Parse TPS (if using Spigot/Paper TPS command output)
        Matcher tpsMatcher = TPS_PATTERN.matcher(line);
        if (tpsMatcher.find()) {
            try {
                tps = Double.parseDouble(tpsMatcher.group(1));
            } catch (NumberFormatException ignored) {}
        }
    }

    private void onServerReady() {
        ConsoleScreenManager.logToMainScreen(ConsoleColors.GREEN + ConsoleColors.PREFIX +
                ConsoleColors.getCurrentTime() + " ✓ Server " + serverName + " ist bereit!");

        wrapper.sendServerStatus(serverName, "ONLINE");
    }

    private void startMetricsCollection() {
        metricsScheduler.scheduleAtFixedRate(() -> {
            if (running && process != null && process.isAlive()) {
                updateMemoryMetrics();
            }
        }, 5, 5, TimeUnit.SECONDS);
    }

    private void updateMemoryMetrics() {
        try {
            // Get memory usage from process (approximation)
            Runtime runtime = Runtime.getRuntime();
            memoryUsage = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024; // MB
        } catch (Exception e) {
            // Ignore
        }
    }

    public void stop() {
        if (!running) {
            return;
        }

        ConsoleScreenManager.logToMainScreen(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Stoppe Server: " + serverName);

        running = false;

        // Send stop command gracefully
        sendCommand("stop");

        // Wait for graceful shutdown
        try {
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                ConsoleScreenManager.logToMainScreen(ConsoleColors.YELLOW + ConsoleColors.PREFIX +
                        ConsoleColors.getCurrentTime() + " Server " + serverName +
                        " reagiert nicht, erzwinge Beendigung...");
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }

        // Cleanup
        cleanup();

        // Send status to master
        wrapper.sendServerStatus(serverName, "OFFLINE");

        ConsoleScreenManager.logToMainScreen(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Server " + serverName + " gestoppt");
    }

    private void cleanup() {
        // Stop metrics collection
        metricsScheduler.shutdown();
        try {
            if (!metricsScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                metricsScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            metricsScheduler.shutdownNow();
        }

        // Close input stream
        if (processInput != null) {
            try {
                processInput.close();
            } catch (IOException ignored) {}
        }

        // Wait for output thread
        if (outputThread != null && outputThread.isAlive()) {
            try {
                outputThread.join(5000);
            } catch (InterruptedException ignored) {}
        }
    }

    public void sendCommand(String command) {
        if (!running || processInput == null) {
            ConsoleScreenManager.logToServerScreen(serverName,
                    ConsoleColors.YELLOW + "Server läuft nicht, Befehl ignoriert: " + command);
            return;
        }

        try {
            processInput.write(command);
            processInput.newLine();
            processInput.flush();

            ConsoleScreenManager.logToServerScreen(serverName,
                    ConsoleColors.CYAN + "[COMMAND] " + command);
        } catch (IOException e) {
            ConsoleScreenManager.logToServerScreen(serverName,
                    ConsoleColors.RED + "Fehler beim Senden des Befehls: " + e.getMessage());
        }
    }

    public Message.ServerMetrics collectMetrics() {
        if (!running) {
            return null;
        }

        Message.ServerMetrics metrics = new Message.ServerMetrics();
        metrics.serverName = serverName;
        metrics.groupName = groupName;
        metrics.playerCount = playerCount;
        metrics.maxPlayers = maxPlayers;
        metrics.tps = tps;
        metrics.memoryUsage = memoryUsage;
        metrics.cpuUsage = 0.0; // Could be calculated if needed
        metrics.timestamp = System.currentTimeMillis();

        lastMetricUpdate = System.currentTimeMillis();

        return metrics;
    }

    public boolean isHealthy() {
        if (!running) {
            return false;
        }

        // Server should be running for at least 30 seconds
        if (System.currentTimeMillis() - startTime < 30000) {
            return true; // Still starting up
        }

        // Check if process is alive
        if (process == null || !process.isAlive()) {
            return false;
        }

        // Check if TPS is acceptable (for game servers)
        if (!groupName.equalsIgnoreCase("Proxy") && tps < 10.0) {
            return false;
        }

        return true;
    }

    public void updatePlayerCount(int count) {
        this.playerCount = count;
    }

    public void updateTPS(double tps) {
        this.tps = tps;
    }

    // Getters
    public String getServerName() {
        return serverName;
    }

    public String getGroupName() {
        return groupName;
    }

    public int getPort() {
        return port;
    }

    public int getAllocatedMemory() {
        return allocatedMemory;
    }

    public int getPlayerCount() {
        return playerCount;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public double getTps() {
        return tps;
    }

    public long getMemoryUsage() {
        return memoryUsage;
    }

    public boolean isRunning() {
        return running;
    }

    public long getUptime() {
        return System.currentTimeMillis() - startTime;
    }
}
