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
import de.kallifabio.cloud.libs.logging.CentralLogger;
import oshi.SystemInfo;
import oshi.software.os.OSProcess;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

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
    private volatile double cpuUsage = 0.0;
    private volatile long diskReadBytes = 0L;
    private volatile long diskWriteBytes = 0L;
    private long networkInBytes = 0L;
    private long networkOutBytes = 0L;
    private long lastObservedSocketInBytes = -1L;
    private long lastObservedSocketOutBytes = -1L;
    private long lastNetworkSampleAt = 0L;
    private String networkCollectionMode = "NONE";
    private long lastMetricUpdate = 0;

    // Monitoring
    private final ScheduledExecutorService metricsScheduler = Executors.newSingleThreadScheduledExecutor();
    private final SystemInfo systemInfo = new SystemInfo();
    private long processId = -1L;
    private OSProcess previousProcess;

    // Regex patterns for log parsing
    private static final Pattern PLAYER_JOIN_PATTERN = Pattern.compile("(\\w+)\\[.+\\] logged in");
    private static final Pattern PLAYER_LEAVE_PATTERN = Pattern.compile("(\\w+) lost connection");
    private static final Pattern TPS_PATTERN = Pattern.compile("TPS from last \\d+m.*?([0-9.]+)");
    private static final Pattern SS_BYTES_RECEIVED_PATTERN = Pattern.compile("bytes_received:(\\d+)");
    private static final Pattern SS_BYTES_ACKED_PATTERN = Pattern.compile("bytes_acked:(\\d+)");

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
            ConsoleScreenManager.printToTerminal(ConsoleColors.YELLOW + ConsoleColors.PREFIX +
                    ConsoleColors.getCurrentTime() + " Server " + serverName + " läuft bereits");
            return;
        }

        ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Starte Server: " + serverName + " (" + groupName + ") auf Port " + port);
        long startNs = System.nanoTime();

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
        processId = process.pid();
        previousProcess = systemInfo.getOperatingSystem().getProcess((int) processId);

        // Setup process I/O
        setupProcessIO();

        // Send initial status to master
        wrapper.sendServerStatus(serverName, "STARTING");

        // Start metrics collection
        startMetricsCollection();

        ConsoleScreenManager.printToTerminal(ConsoleColors.GREEN + ConsoleColors.PREFIX +
                ConsoleColors.getCurrentTime() + " Server " + serverName + " gestartet auf Port " + port);
        long tookMs = (System.nanoTime() - startNs) / 1_000_000;
        CentralLogger.performance("server_start", tookMs, serverName + " (" + groupName + ")");
    }

    private File setupServerDirectory() throws IOException {
        File serverDir = new File("./servers/" + groupName + "/" + serverName);
        if (isDynamicGroup() && serverDir.exists()) {
            clearDirectory(serverDir.toPath());
        }
        Files.createDirectories(serverDir.toPath());
        return serverDir;
    }

    private void prepareServerFiles(File serverDir) throws IOException {
        if (isDynamicGroup()) {
            copyTemplateFiles(serverDir);
        }
    }

    private void copyTemplateFiles(File serverDir) throws IOException {
        File templateDir = configManager.isTemplateTestingMode()
                ? new File("./templates_test/" + groupName)
                : new File("./templates/" + groupName);
        File backupTemplateDir = new File("./templates_backup/" + groupName);

        if (!templateDir.exists()) {
            if (!backupTemplateDir.exists()) {
                throw new FileNotFoundException("Template-Verzeichnis nicht gefunden: " + templateDir.getAbsolutePath());
            }
            copyDirectory(backupTemplateDir.toPath(), serverDir.toPath());
            ConsoleScreenManager.printToTerminal(ConsoleColors.YELLOW + ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                    " Backup-Template verwendet für " + serverName);
            return;
        }

        ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Kopiere Template-Dateien fuer " + serverName + "...");

        try {
            copyDirectory(templateDir.toPath(), serverDir.toPath());
        } catch (IOException ex) {
            CentralLogger.error("Template", "Template-Kopie fehlgeschlagen für " + serverName + ", versuche Backup", ex);
            if (!backupTemplateDir.exists()) {
                throw ex;
            }
            clearDirectory(serverDir.toPath());
            copyDirectory(backupTemplateDir.toPath(), serverDir.toPath());
            ConsoleScreenManager.printToTerminal(ConsoleColors.YELLOW + ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                    " Korruptes Template erkannt, Backup-Template verwendet für " + serverName);
        }
    }

    private boolean isDynamicGroup() {
        return "true".equalsIgnoreCase(
                configManager.getServergroup("ServerGroup." + groupName + ".Dynamic")
        );
    }

    private void clearDirectory(Path directory) throws IOException {
        try (Stream<Path> walk = Files.walk(directory)) {
            walk.sorted(Comparator.reverseOrder())
                    .filter(path -> !path.equals(directory))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            throw new RuntimeException("Konnte Datei nicht loeschen: " + path, e);
                        }
                    });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw e;
        }
    }

    private void copyDirectory(Path sourceDirectory, Path targetDirectory) throws IOException {
        try (Stream<Path> stream = Files.walk(sourceDirectory)) {
            stream.forEach(source -> {
                try {
                    Path target = targetDirectory.resolve(sourceDirectory.relativize(source));
                    if (Files.isDirectory(source)) {
                        if (!Files.exists(target)) {
                            Files.createDirectories(target);
                        }
                    } else {
                        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new RuntimeException("Fehler beim Kopieren: " + e.getMessage(), e);
                }
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw e;
        }
    }

    private void acceptEula(File serverDir) throws IOException {
        File eulaFile = new File(serverDir, "eula.txt");
        List<String> eulaLines = Arrays.asList(
                "#By changing the setting below to TRUE you are indicating your agreement to our EULA (https://account.mojang.com/documents/minecraft_eula).",
                "#" + new java.util.Date(),
                "eula=true"
        );
        Files.write(eulaFile.toPath(), eulaLines);

        ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
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

        ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
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
        processInput = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));

        outputThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {

                String line;
                while ((line = reader.readLine()) != null && running) {
                    wrapper.sendServerLog(serverName, determineLogLevel(line), line);

                    // NEU: Nur wichtige Logs an Main-Screen
                    if (shouldprintToTerminal(line)) {
                        ConsoleScreenManager.printToTerminal(
                                ConsoleColors.RED + "[" + serverName + "] " + line);
                    }

                    // Immer an Server-Screen
                    ConsoleScreenManager.logToServerScreen(serverName, line);

                    parseLogLine(line);

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
                if (running) {
                    ConsoleScreenManager.printToTerminal(ConsoleColors.YELLOW + ConsoleColors.PREFIX +
                            ConsoleColors.getCurrentTime() + " Server " + serverName + " wurde unerwartet beendet");
                    running = false;
                    wrapper.sendServerStatus(serverName, "CRASHED");
                }
            }
        }, "ServerOutput-" + serverName);

        outputThread.start();
    }

    private boolean shouldprintToTerminal(String line) {
        // Nur wichtige Events loggen
        return line.contains("Done (")
                || line.contains("Listening on")
                || line.contains("logged in")
                || line.contains("lost connection")
                || line.contains("WARN")
                || line.contains("ERROR")
                || line.contains("SEVERE");
    }

    private String determineLogLevel(String line) {
        String upper = line.toUpperCase();
        if (upper.contains("ERROR") || upper.contains("SEVERE") || upper.contains("EXCEPTION")) {
            return "ERROR";
        }
        if (upper.contains("WARN")) {
            return "WARN";
        }
        if (upper.contains("DEBUG")) {
            return "DEBUG";
        }
        return "INFO";
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
        ConsoleScreenManager.printToTerminal(ConsoleColors.GREEN + ConsoleColors.PREFIX +
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
            OSProcess current = systemInfo.getOperatingSystem().getProcess((int) processId);
            if (current != null) {
                memoryUsage = current.getResidentSetSize() / 1024 / 1024;
                diskReadBytes = current.getBytesRead();
                diskWriteBytes = current.getBytesWritten();
                if (previousProcess != null) {
                    cpuUsage = current.getProcessCpuLoadBetweenTicks(previousProcess) * 100.0;
                    if (cpuUsage < 0) {
                        cpuUsage = 0.0;
                    }
                }
                previousProcess = current;
            }
            updateNetworkMetrics();
        } catch (Exception e) {
            // Ignore
        }
    }

    private void updateNetworkMetrics() {
        boolean linuxSocketBytes = collectLinuxSocketBytes();
        if (!linuxSocketBytes) {
            collectFallbackNetworkEstimate();
        }
    }

    private boolean collectLinuxSocketBytes() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!osName.contains("linux")) {
            return false;
        }

        String command = "ss -tin \"( sport = :" + port + " )\"";
        ProcessBuilder builder = new ProcessBuilder("sh", "-c", command);
        builder.redirectErrorStream(true);

        long receivedSum = 0L;
        long ackedSum = 0L;
        int byteMatches = 0;

        try {
            Process p = builder.start();
            String output;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                output = sb.toString();
            }
            p.waitFor(2, TimeUnit.SECONDS);
            if (p.isAlive()) {
                p.destroyForcibly();
                return false;
            }

            Matcher inMatcher = SS_BYTES_RECEIVED_PATTERN.matcher(output);
            while (inMatcher.find()) {
                receivedSum += Long.parseLong(inMatcher.group(1));
                byteMatches++;
            }

            Matcher outMatcher = SS_BYTES_ACKED_PATTERN.matcher(output);
            while (outMatcher.find()) {
                ackedSum += Long.parseLong(outMatcher.group(1));
                byteMatches++;
            }

            if (byteMatches == 0) {
                return false;
            }

            long deltaIn = counterDelta(receivedSum, lastObservedSocketInBytes);
            long deltaOut = counterDelta(ackedSum, lastObservedSocketOutBytes);

            networkInBytes += Math.max(0L, deltaIn);
            networkOutBytes += Math.max(0L, deltaOut);
            lastObservedSocketInBytes = receivedSum;
            lastObservedSocketOutBytes = ackedSum;
            networkCollectionMode = "LINUX_SOCKET_BYTES";
            lastNetworkSampleAt = System.currentTimeMillis();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void collectFallbackNetworkEstimate() {
        int establishedConnections = countEstablishedConnections();
        long now = System.currentTimeMillis();
        long previousSample = lastNetworkSampleAt;
        lastNetworkSampleAt = now;

        if (previousSample <= 0L) {
            networkCollectionMode = "ESTIMATED_CONNECTIONS";
            return;
        }

        long elapsedSeconds = Math.max(1L, (now - previousSample) / 1000L);
        long bytesPerConnectionPerSecond = 2048L;
        long estimatedDelta = establishedConnections * elapsedSeconds * bytesPerConnectionPerSecond;
        networkInBytes += estimatedDelta;
        networkOutBytes += estimatedDelta;
        networkCollectionMode = "ESTIMATED_CONNECTIONS";
    }

    private int countEstablishedConnections() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        try {
            if (osName.contains("windows")) {
                return countEstablishedConnectionsWindows();
            }
            return countEstablishedConnectionsLinux();
        } catch (Exception ignored) {
            return 0;
        }
    }

    private int countEstablishedConnectionsWindows() throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder("cmd", "/c", "netstat -ano -p tcp");
        builder.redirectErrorStream(true);
        Process p = builder.start();

        int count = 0;
        String portSuffix = ":" + port;
        String pidString = String.valueOf(processId);
        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) {
                String normalized = line.trim().replaceAll("\\s+", " ");
                if (!normalized.startsWith("TCP ")) {
                    continue;
                }
                String[] parts = normalized.split(" ");
                if (parts.length < 5) {
                    continue;
                }
                String localAddress = parts[1];
                String state = parts[3];
                String pid = parts[4];
                if (localAddress.endsWith(portSuffix) && "ESTABLISHED".equalsIgnoreCase(state) && pidString.equals(pid)) {
                    count++;
                }
            }
        }
        p.waitFor(2, TimeUnit.SECONDS);
        if (p.isAlive()) {
            p.destroyForcibly();
        }
        return count;
    }

    private int countEstablishedConnectionsLinux() throws IOException, InterruptedException {
        String command = "ss -tn state established \"( sport = :" + port + " )\"";
        ProcessBuilder builder = new ProcessBuilder("sh", "-c", command);
        builder.redirectErrorStream(true);
        Process p = builder.start();

        int count = 0;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("Recv-Q") || trimmed.startsWith("Netid")) {
                    continue;
                }
                count++;
            }
        }
        p.waitFor(2, TimeUnit.SECONDS);
        if (p.isAlive()) {
            p.destroyForcibly();
        }
        return count;
    }

    private long counterDelta(long current, long previous) {
        if (previous < 0L) {
            return 0L;
        }
        if (current >= previous) {
            return current - previous;
        }
        return current;
    }

    public void stop() {
        stop(true);
    }

    public void stop(boolean graceful) {
        if (!running) {
            return;
        }

        ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Stoppe Server: " + serverName);

        running = false;

        if (!graceful) {
            process.destroyForcibly();
            cleanup();
            wrapper.sendServerStatus(serverName, "KILLED");
            ConsoleScreenManager.printToTerminal(ConsoleColors.YELLOW + ConsoleColors.PREFIX +
                    ConsoleColors.getCurrentTime() + " Server " + serverName + " wurde hart beendet");
            return;
        }

        // Send stop command gracefully
        if (!groupName.equalsIgnoreCase("Proxy")) {
            sendCommand("say Server shutdown in 30 seconds.");
            sleepQuietly(15000);
            sendCommand("say Server shutdown in 15 seconds.");
            sleepQuietly(5000);
            sendCommand("say Server shutdown in 10 seconds.");
            sleepQuietly(5000);
            sendCommand("say Server shutdown in 5 seconds.");
            sleepQuietly(5000);
        }
        sendCommand("stop");

        // Wait for graceful shutdown
        try {
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                ConsoleScreenManager.printToTerminal(ConsoleColors.YELLOW + ConsoleColors.PREFIX +
                        ConsoleColors.getCurrentTime() + " Server " + serverName +
                        " reagiert nicht, erzwinge Beendigung...");
                process.destroyForcibly();
                wrapper.sendServerStatus(serverName, "KILLED");
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }

        // Cleanup
        cleanup();

        // Send status to master
        wrapper.sendServerStatus(serverName, "OFFLINE");

        ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Server " + serverName + " gestoppt");
    }

    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
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
        metrics.cpuUsage = cpuUsage;
        metrics.networkInBytes = networkInBytes;
        metrics.networkOutBytes = networkOutBytes;
        metrics.networkMode = networkCollectionMode;
        metrics.diskReadBytes = diskReadBytes;
        metrics.diskWriteBytes = diskWriteBytes;
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

