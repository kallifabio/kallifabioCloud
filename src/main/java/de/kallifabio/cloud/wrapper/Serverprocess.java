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
import de.kallifabio.cloud.software.ServerSoftware;
import oshi.SystemInfo;
import oshi.software.os.OSProcess;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class Serverprocess {

    private final String serverName;
    private final String groupName;
    private int port;
    private final Wrapper wrapper;
    private final ConfigManager configManager;
    private final ServerSoftware software;

    private Process process;
    private BufferedWriter processInput;
    private Thread outputThread;

    private int allocatedMemory;
    private volatile boolean running = false;
    private volatile boolean stopping = false;
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
    private final Map<String, Set<String>> enforcedPermissionsByPlayer = new ConcurrentHashMap<>();
    private final Map<String, String> enforcedGroupByPlayer = new ConcurrentHashMap<>();
    private final Map<String, String> enforcedPrefixByPlayer = new ConcurrentHashMap<>();
    private final Map<String, String> enforcedSuffixByPlayer = new ConcurrentHashMap<>();

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
        this.software = ServerSoftware.resolve(configManager.getSoftwareForGroup(groupName), groupName);
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

        // Template roots must exist before any copy/bootstrap decision is made.
        ensureTemplateDirectories();

        // Prepare server files (template copy/bootstrap)
        prepareServerFiles(serverDir);

        // Validate/setup runtime prerequisites
        runStartupPreflight(serverDir);

        // WICHTIG: Akzeptiere EULA automatisch
        acceptEula(serverDir);

        // WICHTIG: Update server.properties mit korrektem Port
        updateServerProperties(serverDir, port);
        configureNetworkFiles(serverDir);

        // Resolve launch command (JAR or modloader argfile)
        LaunchPlan launchPlan = resolveLaunchPlan(serverDir);

        // Create server screen
        ConsoleScreenManager.createServerScreen(serverName);

        // Start the server process
        ProcessBuilder processBuilder = new ProcessBuilder(launchPlan.command);

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
            return;
        }

        if (configManager.isTemplateAutoUpdateAfterRestart()) {
            ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                    " Template-Refresh für statischen Server " + serverName + " gestartet");
            copyTemplateFiles(serverDir);
            return;
        }

        if (shouldBootstrapFromTemplate(serverDir)) {
            ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                    " Bootstrap aus Template für statischen Server " + serverName + " gestartet");
            copyTemplateFiles(serverDir);
        } else {
            ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                    " Statischer Server " + serverName + " nutzt bestehende Dateien (kein Template-Overwrite)");
        }
    }

    private boolean shouldBootstrapFromTemplate(File serverDir) throws IOException {
        if (!serverDir.exists()) {
            return true;
        }

        try (Stream<Path> files = Files.list(serverDir.toPath())) {
            if (!files.findAny().isPresent()) {
                return true;
            }
        }

        String expectedJar = getJarFileName();
        if (findModernModloaderArgs(serverDir) != null) {
            return false;
        }
        File primaryJar = new File(serverDir, expectedJar);
        if (primaryJar.exists()) {
            return false;
        }

        for (String alias : getJarCandidateNames(expectedJar)) {
            if (new File(serverDir, alias).exists()) {
                return false;
            }
        }
        if (findMatchingJarInDirectory(serverDir, getJarCandidateNames(expectedJar)) != null) {
            return false;
        }
        return true;
    }

    private void runStartupPreflight(File serverDir) throws IOException {
        ensureTemplateDirectories();
        resolveLaunchPlan(serverDir);
    }

    private void ensureTemplateDirectories() throws IOException {
        File templateDir = configManager.isTemplateTestingMode()
                ? resolveGroupDirectory("./templates_test")
                : resolveGroupDirectory("./templates");
        File backupTemplateDir = resolveGroupDirectory("./templates_backup");

        if (templateDir.exists() || backupTemplateDir.exists()) {
            return;
        }

        Files.createDirectories(templateDir.toPath());
        ConsoleScreenManager.printToTerminal(
                ConsoleColors.YELLOW + ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                        " Preflight: Kein Template gefunden, leeres Template erstellt: " +
                        templateDir.getAbsolutePath()
        );
    }

    private void copyTemplateFiles(File serverDir) throws IOException {
        File templateDir = configManager.isTemplateTestingMode()
                ? resolveGroupDirectory("./templates_test")
                : resolveGroupDirectory("./templates");
        File backupTemplateDir = resolveGroupDirectory("./templates_backup");

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
                " Kopiere Template-Dateien für " + serverName + "...");

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
                            throw new RuntimeException("Konnte Datei nicht löschen: " + path, e);
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
        boolean enforceBind = getBackendBindEnforcement();
        String backendBindAddress = getBackendBindAddress();

        if (!propertiesFile.exists()) {
            // Erstelle neue server.properties
            createDefaultProperties(propertiesFile, port, enforceBind, backendBindAddress);
        } else {
            // Update existierende server.properties
            List<String> lines = Files.readAllLines(propertiesFile.toPath());
            List<String> newLines = new ArrayList<>();

            boolean portSet = false;
            boolean onlineModeSet = false;
            boolean maxPlayersSet = false;
            boolean serverIpSet = false;

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
                } else if (line.startsWith("server-ip=")) {
                    if (!isProxyGroup() && enforceBind) {
                        newLines.add("server-ip=" + backendBindAddress);
                    } else {
                        newLines.add(line);
                    }
                    serverIpSet = true;
                } else {
                    newLines.add(line);
                }
            }

            // Fuege fehlende Zeilen hinzu
            if (!portSet) {
                newLines.add("server-port=" + port);
            }
            if (!onlineModeSet) {
                newLines.add("online-mode=false");
            }
            if (!maxPlayersSet) {
                newLines.add("max-players=" + maxPlayers);
            }
            if (!serverIpSet && !isProxyGroup() && enforceBind) {
                newLines.add("server-ip=" + backendBindAddress);
            }

            Files.write(propertiesFile.toPath(), newLines);
        }

        ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " server.properties aktualisiert (Port: " + port + ", Max-Players: " + maxPlayers + ")");
    }

    private void createDefaultProperties(File propertiesFile, int port, boolean enforceBind, String backendBindAddress) throws IOException {
        StringBuilder properties = new StringBuilder();
        properties.append("#Minecraft server properties\n");
        properties.append("#").append(new java.util.Date()).append("\n");
        properties.append("server-port=").append(port).append("\n");
        properties.append("max-players=").append(maxPlayers).append("\n");
        properties.append("online-mode=false\n");
        if (!isProxyGroup() && enforceBind) {
            properties.append("server-ip=").append(backendBindAddress).append("\n");
        }
        properties.append("view-distance=8\n");
        properties.append("simulation-distance=10\n");
        properties.append("difficulty=easy\n");
        properties.append("gamemode=survival\n");
        properties.append("pvp=true\n");
        properties.append("spawn-protection=0\n");
        properties.append("motd=Cloud Server - ").append(serverName).append("\n");

        Files.write(propertiesFile.toPath(), properties.toString().getBytes());
    }

    private void configureNetworkFiles(File serverDir) {
        try {
            configurePluginApiKeys(serverDir);
            if (isProxyGroup()) {
                configureProxyConfig(serverDir);
                configureProxyForwardingSecret(serverDir);
            } else {
                if (software.isSpigotLike()) {
                    configureSpigotConfig(serverDir);
                } else {
                    ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                            " Modded/Vanilla Backend erkannt (" + software.name() + ") - spigot.yml-Konfiguration übersprungen für " + serverName);
                }
                configureProxyAllowlist(serverDir);
                registerBackendInLocalProxyConfigs();
            }
        } catch (Exception e) {
            CentralLogger.error("NetworkConfig", "Automatische Netzwerk-Konfiguration fehlgeschlagen für " + serverName, e);
        }
    }

    private static final Set<String> API_KEY_PLACEHOLDERS = Set.of("", "change_me", "dashboard", "admin");

    /**
     * Traegt den Dashboard-Key der Cloud in die Configs der Cloud-Plugins ein, solange dort noch
     * ein Platzhalter steht. Fehlt die Config noch, wird die Standard-Config aus dem Plugin-Jar entpackt.
     */
    private void configurePluginApiKeys(File serverDir) {
        String dashboardKey = trimToNull(configManager.getMaster("CloudMaster.API.DashboardKey"));
        String adminKey = trimToNull(configManager.getMaster("CloudMaster.API.AdminKey"));
        // Lobby/Bungee schreiben in die Cloud (Events, Settings, Party, Selektoren) und brauchen ADMIN.
        // Der Enforcer liest nur (GET profile/check) - dort reicht der Viewer-Key (Dashboard).
        // {Plugin, Config-Pfad, benoetigt Schreibrechte}
        String[][] plugins = {
                {"KalliCloud-BungeeSystem", "cloud.api-key", "write"},
                {"Cloud-Lobbysystem", "cloud.api-key", "write"},
                {"KalliCloudPermissionEnforcer", "cloud.apiKey", "read"}};
        for (String[] plugin : plugins) {
            boolean write = "write".equals(plugin[2]);
            String key = write ? adminKey : dashboardKey;
            if (key == null) {
                continue;
            }
            try {
                File pluginDir = new File(serverDir, "plugins/" + plugin[0]);
                File cfgFile = new File(pluginDir, "config.yml");
                if (!cfgFile.exists() && !extractDefaultPluginConfig(new File(serverDir, "plugins"), cfgFile)) {
                    continue;
                }
                YamlConfiguration cfg = YamlConfiguration.loadConfiguration(cfgFile);
                String current = cfg.getString(plugin[1], "");
                String normalized = current == null ? "" : current.trim();
                boolean placeholder = API_KEY_PLACEHOLDERS.contains(normalized.toLowerCase(Locale.ROOT));
                // Migration: Schreib-Plugins hatten bisher den Viewer-Key und bekamen 403 auf POSTs.
                boolean viewerKeyButNeedsWrite = write && dashboardKey != null && normalized.equals(dashboardKey);
                if (!placeholder && !viewerKeyButNeedsWrite) {
                    continue;
                }
                cfg.set(plugin[1], key);
                cfg.save(cfgFile);
                CentralLogger.info("NetworkConfig", "API-Key in " + plugin[0] + "/config.yml gesetzt für " + serverName
                        + (write ? " (Schreibzugriff)" : " (nur lesen)"));
            } catch (Exception e) {
                CentralLogger.warn("NetworkConfig", "API-Key für " + plugin[0] + " nicht gesetzt: " + e.getMessage());
            }
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean extractDefaultPluginConfig(File pluginsDir, File target) throws IOException {
        File[] jars = pluginsDir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".jar"));
        if (jars == null) {
            return false;
        }
        String pluginName = target.getParentFile().getName();
        for (File jar : jars) {
            try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(jar)) {
                java.util.zip.ZipEntry descriptor = zip.getEntry("plugin.yml");
                if (descriptor == null) {
                    descriptor = zip.getEntry("bungee.yml");
                }
                java.util.zip.ZipEntry entry = zip.getEntry("config.yml");
                if (descriptor == null || entry == null) {
                    continue;
                }
                YamlConfiguration meta;
                try (Reader r = new InputStreamReader(zip.getInputStream(descriptor), java.nio.charset.StandardCharsets.UTF_8)) {
                    meta = YamlConfiguration.loadConfiguration(r);
                }
                if (!pluginName.equals(meta.getString("name"))) {
                    continue;
                }
                Files.createDirectories(target.getParentFile().toPath());
                try (InputStream in = zip.getInputStream(entry)) {
                    Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                return true;
            }
        }
        return false;
    }

    private boolean isProxyGroup() {
        return software.isProxy();
    }

    private void configureSpigotConfig(File serverDir) throws IOException {
        File spigotFile = new File(serverDir, "spigot.yml");
        YamlConfiguration spigot = YamlConfiguration.loadConfiguration(spigotFile);
        spigot.set("settings.bungeecord", true);
        spigot.save(spigotFile);
        ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " spigot.yml aktualisiert (settings.bungeecord=true) für " + serverName);
    }

    @SuppressWarnings("unchecked")
    private void configureProxyConfig(File serverDir) throws IOException {
        File proxyConfigFile = new File(serverDir, "config.yml");
        YamlConfiguration proxyCfg = YamlConfiguration.loadConfiguration(proxyConfigFile);

        proxyCfg.set("ip_forward", true);
        proxyCfg.set("online_mode", getProxyOnlineModeDefaultTrue());
        proxyCfg.set("server_connect_timeout", getProxyServerConnectTimeoutMs());
        proxyCfg.set("timeout", getProxyTimeoutMs());
        proxyCfg.set("remote_ping_timeout", getProxyRemotePingTimeoutMs());
        proxyCfg.set("remote_ping_cache", -1);
        proxyCfg.set("enforce_secure_profile", false);
        proxyCfg.set("prevent_proxy_connections", false);
        proxyCfg.set("reject_transfers", false);

        String lobbyName = "Lobby-1";
        int lobbyPort = configManager.getFirstLobbyPort();

        List<Map<String, Object>> listeners = (List<Map<String, Object>>) proxyCfg.getList("listeners");
        Map<String, Object> listener;
        if (listeners == null || listeners.isEmpty()) {
            listener = new LinkedHashMap<>();
        } else {
            listener = new LinkedHashMap<>(listeners.get(0));
        }

        listener.put("host", getProxyBindHost() + ":" + port);
        listener.put("query_port", port + 1);
        listener.put("max_players", maxPlayers);
        listener.put("force_default_server", getProxyForceDefaultServer());
        listener.put("tab_size", 60);
        listener.put("bind_local_address", getProxyBindLocalAddress());
        listener.put("ping_passthrough", false);
        listener.put("query_enabled", false);
        listener.put("proxy_protocol", false);
        listener.put("priorities", List.of(lobbyName));
        listener.put("forced_hosts", new LinkedHashMap<String, List<String>>());
        if (!listener.containsKey("motd")) {
            listener.put("motd", "&1KalliCloud Proxy");
        }
        if (!listener.containsKey("tab_list")) {
            listener.put("tab_list", "GLOBAL_PING");
        }
        proxyCfg.set("listeners", List.of(listener));
        proxyCfg.set("forced_hosts", new LinkedHashMap<String, List<String>>());

        ConfigurationSection serversSection = proxyCfg.getConfigurationSection("servers");
        if (serversSection == null) {
            serversSection = proxyCfg.createSection("servers");
        }
        serversSection.set(lobbyName + ".motd", "&aLobby");
        serversSection.set(lobbyName + ".address", getGameRouteHost() + ":" + lobbyPort);
        serversSection.set(lobbyName + ".restricted", false);

        proxyCfg.save(proxyConfigFile);
        ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Proxy config.yml aktualisiert (Listener/Forwarding/Lobby-Route) für " + serverName);
    }

    private void configureProxyForwardingSecret(File serverDir) {
        try {
            String secret = configManager.getMaster("CloudMaster.Network.ForwardingSecret");
            if (secret == null || secret.isBlank()) {
                return;
            }
            File secretFile = new File(serverDir, "forwarding.secret");
            Files.writeString(secretFile.toPath(), secret.trim());
        } catch (Exception e) {
            CentralLogger.warn("NetworkConfig", "Konnte forwarding.secret nicht schreiben für " + serverName);
        }
    }

    private boolean getProxyOnlineModeDefaultTrue() {
        String configured = configManager.getMaster("CloudMaster.Network.ProxyOnlineMode");
        if (configured == null || configured.isBlank()) {
            return true;
        }
        return Boolean.parseBoolean(configured);
    }

    private String getProxyBindHost() {
        String configured = configManager.getMaster("CloudMaster.Network.ProxyBindHost");
        if (configured == null || configured.isBlank()) {
            return "0.0.0.0";
        }
        return configured.trim();
    }

    private boolean getProxyBindLocalAddress() {
        String configured = configManager.getMaster("CloudMaster.Network.ProxyBindLocalAddress");
        if (configured == null || configured.isBlank()) {
            return false;
        }
        return Boolean.parseBoolean(configured);
    }

    private boolean getProxyForceDefaultServer() {
        String configured = configManager.getMaster("CloudMaster.Network.ProxyForceDefaultServer");
        if (configured == null || configured.isBlank()) {
            return true;
        }
        return Boolean.parseBoolean(configured);
    }

    private int getProxyServerConnectTimeoutMs() {
        return getConfiguredInt("CloudMaster.Network.ProxyServerConnectTimeoutMs", 15000, 1000, 120000);
    }

    private int getProxyTimeoutMs() {
        return getConfiguredInt("CloudMaster.Network.ProxyTimeoutMs", 60000, 10000, 300000);
    }

    private int getProxyRemotePingTimeoutMs() {
        return getConfiguredInt("CloudMaster.Network.ProxyRemotePingTimeoutMs", 5000, 1000, 60000);
    }

    private int getConfiguredInt(String key, int fallback, int min, int max) {
        String configured = configManager.getMaster(key);
        if (configured == null || configured.isBlank()) {
            return fallback;
        }
        try {
            int value = Integer.parseInt(configured.trim());
            return Math.max(min, Math.min(max, value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private boolean getBackendBindEnforcement() {
        String configured = configManager.getMaster("CloudMaster.Network.EnforceBackendBind");
        if (configured == null || configured.isBlank()) {
            return true;
        }
        return Boolean.parseBoolean(configured);
    }

    /**
     * Single-Host: laufen alle Proxys auf diesem Wrapper (und ist nur ein Wrapper verbunden), reicht Loopback fuer
     * Backends - dann ist nur der Proxy-Port oeffentlich. Nur im Master-Prozess sicher feststellbar; auf
     * reinen Remote-Wrappern ist die Topologie unbekannt (-> false).
     */
    private boolean isSingleHostTopology() {
        try {
            de.kallifabio.cloud.master.Master master = de.kallifabio.cloud.master.Master.getInstance();
            if (master == null || master.getConnectedWrappers().size() > 1) {
                return false;
            }
            for (de.kallifabio.cloud.master.ServerInstance instance : master.getRunningServers().values()) {
                if (isProxyGroupName(instance.groupName) && wrapper != null
                        && !Objects.equals(instance.wrapperId, wrapper.getWrapperId())) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String getBackendBindAddress() {
        String configured = configManager.getMaster("CloudMaster.Network.BackendBindAddress");
        String routeHost = getGameRouteHost();
        boolean singleHost = isSingleHostTopology();
        if (configured == null || configured.isBlank() || "auto".equalsIgnoreCase(configured.trim())
                || "detect".equalsIgnoreCase(configured.trim())) {
            if (singleHost) {
                return "127.0.0.1";
            }
            return isLoopbackHost(routeHost) ? "127.0.0.1" : "0.0.0.0";
        }
        if (isLoopbackHost(configured) && !isLoopbackHost(routeHost)) {
            if (singleHost) {
                return configured.trim();
            }
            CentralLogger.warn("NetworkConfig", "BackendBindAddress ist loopback, RouteHost ist remote (" +
                    routeHost + ") und es gibt mehrere Hosts - nutze automatisch 0.0.0.0 fuer " + serverName +
                    " (Zugriff wird per security.allowedProxyAddresses im Enforcer eingeschraenkt)");
            return "0.0.0.0";
        }
        return configured.trim();
    }

    /** Adresse, unter der lokale Proxys (gleicher Wrapper) dieses Backend erreichen. */
    private String getLocalProxyBackendHost() {
        String bind = getBackendBindAddress();
        if ("0.0.0.0".equals(bind) || isLoopbackHost(bind)) {
            return "127.0.0.1";
        }
        return bind;
    }

    /**
     * Schreibt security.allowedProxyAddresses in plugins/KalliCloudPermissionEnforcer/config.yml, wenn das Backend
     * nicht nur auf Loopback lauscht (Multi-Host). Ohne verlaessliche Liste wird nichts geschrieben, damit
     * keine Spieler ausgesperrt werden.
     */
    private void configureProxyAllowlist(File serverDir) {
        try {
            String bind = getBackendBindAddress();
            if (isLoopbackHost(bind)) {
                return;
            }
            java.util.LinkedHashSet<String> addresses = new java.util.LinkedHashSet<>();
            addresses.add("127.0.0.1");
            addresses.add("::1");
            List<String> configuredExtra = configManager.getMasterConfigData().getStringList("CloudMaster.Network.AllowedProxyAddresses");
            for (String extra : configuredExtra) {
                if (extra != null && !extra.isBlank()) {
                    addresses.add(extra.trim());
                }
            }
            de.kallifabio.cloud.master.Master master = de.kallifabio.cloud.master.Master.getInstance();
            if (master != null) {
                // Alle Wrapper-Hosts sind potenzielle Proxy-Hosts (Proxys koennen ueberall geplant werden).
                for (de.kallifabio.cloud.master.WrapperConnection wc : master.getConnectedWrappers().values()) {
                    addResolved(addresses, wc.routeHost);
                    addResolved(addresses, wc.hostname);
                    try {
                        if (wc.connection != null && wc.connection.getRemoteAddressTCP() != null) {
                            addresses.add(wc.connection.getRemoteAddressTCP().getAddress().getHostAddress());
                        }
                    } catch (Exception ignored) {
                    }
                }
            } else if (configuredExtra.isEmpty()) {
                CentralLogger.warn("NetworkConfig", serverName + " lauscht auf " + bind + " (online-mode=false), aber die Proxy-Hosts sind " +
                        "auf diesem Wrapper unbekannt. Setze CloudMaster.Network.AllowedProxyAddresses oder Firewall-Regeln, " +
                        "sonst ist das Backend direkt erreichbar.");
                return;
            }
            addResolved(addresses, getGameRouteHost());
            File cfgFile = new File(serverDir, "plugins/KalliCloudPermissionEnforcer/config.yml");
            cfgFile.getParentFile().mkdirs();
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(cfgFile);
            cfg.set("security.allowedProxyAddresses", new ArrayList<>(addresses));
            cfg.save(cfgFile);
            CentralLogger.info("NetworkConfig", "security.allowedProxyAddresses fuer " + serverName + " gesetzt: " + addresses);
        } catch (Exception e) {
            CentralLogger.warn("NetworkConfig", "Proxy-Allowlist fuer " + serverName + " nicht geschrieben: " + e.getMessage());
        }
    }

    private void addResolved(Set<String> target, String host) {
        if (host == null || host.isBlank()) {
            return;
        }
        target.add(host.trim());
        try {
            target.add(java.net.InetAddress.getByName(host.trim()).getHostAddress());
        } catch (Exception ignored) {
        }
    }

    private void registerBackendInLocalProxyConfigs() {
        try {
            List<File> proxyConfigFiles = new ArrayList<>();

            for (Serverprocess process : wrapper.getManagedServers().values()) {
                if (!isProxyGroupName(process.getGroupName())) {
                    continue;
                }
                File cfg = new File("./servers/" + process.getGroupName() + "/" + process.getServerName() + "/config.yml");
                proxyConfigFiles.add(cfg);
            }

            File proxiesRoot = new File("./servers/Proxy");
            if (proxiesRoot.exists() && proxiesRoot.isDirectory()) {
                File[] proxyDirs = proxiesRoot.listFiles(File::isDirectory);
                if (proxyDirs != null) {
                    for (File proxyDir : proxyDirs) {
                        proxyConfigFiles.add(new File(proxyDir, "config.yml"));
                    }
                }
            }

            for (File proxyConfig : proxyConfigFiles) {
                if (!proxyConfig.exists()) {
                    continue;
                }
                YamlConfiguration cfg = YamlConfiguration.loadConfiguration(proxyConfig);
                ConfigurationSection serversSection = cfg.getConfigurationSection("servers");
                if (serversSection == null) {
                    serversSection = cfg.createSection("servers");
                }
                serversSection.set(serverName + ".motd", "&a" + serverName);
                serversSection.set(serverName + ".address", getLocalProxyBackendHost() + ":" + port);
                serversSection.set(serverName + ".restricted", false);
                cfg.save(proxyConfig);
            }
            ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                    " Proxy-Backends aktualisiert: " + serverName + " -> " + getLocalProxyBackendHost() + ":" + port);
        } catch (Exception e) {
            CentralLogger.error("NetworkConfig", "Konnte Proxy-Backend-Routing nicht aktualisieren für " + serverName, e);
        }
    }

    public void registerBackendRouteInProxyConfig(String backendServerName, String targetHost, int targetPort) {
        if (!isProxyGroup()) {
            return;
        }
        if (backendServerName == null || backendServerName.isBlank() || targetPort <= 0) {
            return;
        }
        String routeHost = (targetHost == null || targetHost.isBlank()) ? getGameRouteHost() : targetHost.trim();
        File proxyConfigFile = new File("./servers/" + groupName + "/" + serverName + "/config.yml");
        if (!proxyConfigFile.exists()) {
            return;
        }
        try {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(proxyConfigFile);
            ConfigurationSection serversSection = cfg.getConfigurationSection("servers");
            if (serversSection == null) {
                serversSection = cfg.createSection("servers");
            }
            serversSection.set(backendServerName + ".motd", "&a" + backendServerName);
            serversSection.set(backendServerName + ".address", routeHost + ":" + targetPort);
            serversSection.set(backendServerName + ".restricted", false);
            cfg.save(proxyConfigFile);
        } catch (Exception e) {
            CentralLogger.error("NetworkConfig", "Route-Update fehlgeschlagen auf " + serverName, e);
        }
    }

    public void unregisterBackendRouteInProxyConfig(String backendServerName) {
        if (!isProxyGroup()) {
            return;
        }
        if (backendServerName == null || backendServerName.isBlank()) {
            return;
        }
        File proxyConfigFile = new File("./servers/" + groupName + "/" + serverName + "/config.yml");
        if (!proxyConfigFile.exists()) {
            return;
        }
        try {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(proxyConfigFile);
            ConfigurationSection serversSection = cfg.getConfigurationSection("servers");
            if (serversSection == null) {
                return;
            }
            serversSection.set(backendServerName, null);
            cfg.save(proxyConfigFile);
        } catch (Exception e) {
            CentralLogger.error("NetworkConfig", "Route-Removal fehlgeschlagen auf " + serverName, e);
        }
    }

    private boolean isProxyGroupName(String group) {
        return ServerSoftware.resolve(configManager.getSoftwareForGroup(group), group).isProxy();
    }

    private String getGameRouteHost() {
        if (wrapper != null && wrapper.getRouteHost() != null && !wrapper.getRouteHost().isBlank()) {
            return wrapper.getRouteHost().trim();
        }
        String configured = configManager.getMaster("CloudMaster.Network.GameHost");
        if (configured == null || configured.isBlank() || "auto".equalsIgnoreCase(configured.trim())
                || "detect".equalsIgnoreCase(configured.trim())) {
            return "127.0.0.1";
        }
        return configured.trim();
    }

    private boolean isLoopbackHost(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        String normalized = host.trim().toLowerCase(Locale.ROOT);
        return "localhost".equals(normalized) || normalized.startsWith("127.");
    }

    private String getJarFileName() {
        return software.primaryJarName();
    }

    private LaunchPlan resolveLaunchPlan(File serverDir) throws IOException {
        File modernArgs = findModernModloaderArgs(serverDir);
        if (modernArgs != null) {
            List<String> command = baseJavaCommand();
            command.add("@" + serverDir.toPath().relativize(modernArgs.toPath()).toString().replace('\\', '/'));
            command.addAll(resolveStartArgs("nogui"));
            return new LaunchPlan(command, null, modernArgs);
        }

        String jarFile = getJarFileName();
        ensureServerJar(serverDir, jarFile);
        List<String> command = baseJavaCommand();
        command.add("-jar");
        command.add(jarFile);
        command.addAll(resolveStartArgs("--nogui"));
        return new LaunchPlan(command, new File(serverDir, jarFile), null);
    }

    private List<String> baseJavaCommand() {
        List<String> command = new ArrayList<>();
        command.add("java");
        command.add("-Xmx" + allocatedMemory + "M");
        command.add("-Xms" + Math.max(128, allocatedMemory / 2) + "M");
        command.add("-XX:+UseG1GC");
        command.add("-XX:+ParallelRefProcEnabled");
        command.add("-XX:MaxGCPauseMillis=200");
        command.add("-XX:+UnlockExperimentalVMOptions");
        command.add("-XX:+DisableExplicitGC");
        command.add("-XX:G1NewSizePercent=30");
        command.add("-XX:G1MaxNewSizePercent=40");
        command.add("-XX:G1HeapRegionSize=8M");
        command.add("-XX:G1ReservePercent=20");
        command.add("-XX:G1HeapWastePercent=5");
        command.addAll(configManager.getJavaArgsForGroup(groupName));
        return command;
    }

    private List<String> resolveStartArgs(String fallbackNoGuiArg) {
        List<String> configured = configManager.getStartArgsForGroup(groupName);
        if (configured == null || configured.isEmpty()) {
            return List.of(fallbackNoGuiArg);
        }
        return configured;
    }

    private File findModernModloaderArgs(File serverDir) {
        if (!software.supportsModernArgFile()) {
            return null;
        }
        List<String> roots = software == ServerSoftware.NEOFORGE
                ? List.of("libraries/net/neoforged/neoforge", "libraries/net/minecraftforge/forge")
                : List.of("libraries/net/minecraftforge/forge", "libraries/net/neoforged/neoforge");
        for (String root : roots) {
            File rootDir = new File(serverDir, root);
            File found = findFileByName(rootDir, "unix_args.txt");
            if (found != null) {
                return found;
            }
        }
        File fallback = findFileByName(new File(serverDir, "libraries"), "unix_args.txt");
        if (fallback != null) {
            return fallback;
        }
        File userJvmArgs = new File(serverDir, "user_jvm_args.txt");
        return userJvmArgs.exists() ? null : null;
    }

    private File findFileByName(File root, String fileName) {
        if (root == null || !root.exists()) {
            return null;
        }
        try (Stream<Path> paths = Files.walk(root.toPath(), 8)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equalsIgnoreCase(fileName))
                    .map(Path::toFile)
                    .findFirst()
                    .orElse(null);
        } catch (IOException ignored) {
            return null;
        }
    }

    private record LaunchPlan(List<String> command, File jarFile, File argFile) {
    }

    private File ensureServerJar(File serverDir, String expectedJarName) throws IOException {
        File targetJar = new File(serverDir, expectedJarName);
        List<String> aliasNames = getJarCandidateNames(expectedJarName);
        File refreshedAlias = findMatchingJarInDirectory(serverDir, aliasNames, Set.of(expectedJarName.toLowerCase(Locale.ROOT)));
        if (refreshedAlias != null && (!targetJar.exists() || refreshedAlias.lastModified() >= targetJar.lastModified())) {
            Files.copy(refreshedAlias.toPath(), targetJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
            ConsoleScreenManager.printToTerminal(
                    ConsoleColors.YELLOW + ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                            " Preflight: " + refreshedAlias.getName() + " als " + expectedJarName +
                            " für " + serverName + " übernommen"
            );
            return targetJar;
        }
        if (targetJar.exists()) {
            return targetJar;
        }

        List<File> searchDirectories = getJarSearchDirectories(serverDir);
        File matched = null;
        for (File dir : searchDirectories) {
            matched = findMatchingJarInDirectory(dir, aliasNames);
            if (matched != null) {
                break;
            }
        }
        List<File> candidates = buildJarCandidates(searchDirectories, aliasNames);
        if (matched != null) {
            Files.copy(matched.toPath(), targetJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
            ConsoleScreenManager.printToTerminal(
                    ConsoleColors.YELLOW + ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                            " Preflight: Fehlende " + expectedJarName + " aus " + matched.getAbsolutePath() +
                            " übernommen"
            );
            return targetJar;
        }

        String searched = candidates.stream()
                .map(File::getAbsolutePath)
                .distinct()
                .reduce((a, b) -> a + "; " + b)
                .orElse("keine Kandidaten");
        throw new FileNotFoundException(
                "JAR-Datei nicht gefunden: " + targetJar.getAbsolutePath() +
                        " | Geprüfte Pfade: " + searched +
                        " | Hinweis: Suche ist case-insensitive und prüft auch <name>-*.jar"
        );
    }

    private List<String> getJarCandidateNames(String expectedJarName) {
        return software.jarAliases();
    }

    private List<File> buildJarCandidates(List<File> searchDirectories, List<String> aliasNames) {
        List<File> candidates = new ArrayList<>();
        for (File baseDir : searchDirectories) {
            for (String alias : aliasNames) {
                candidates.add(new File(baseDir, alias));
                candidates.add(new File(baseDir, alias.replace(".jar", "-*.jar")));
            }
        }
        return candidates;
    }

    private List<File> getJarSearchDirectories(File serverDir) {
        File templateDir = resolveGroupDirectory("./templates");
        File templateTestDir = resolveGroupDirectory("./templates_test");
        File templateBackupDir = resolveGroupDirectory("./templates_backup");
        File jarsGroupDir = resolveGroupDirectory("./jars");
        return List.of(
                serverDir,
                templateDir,
                templateTestDir,
                templateBackupDir,
                jarsGroupDir,
                new File("./jars"),
                new File(".")
        );
    }

    private File findMatchingJarInDirectory(File directory, List<String> aliasNames) {
        return findMatchingJarInDirectory(directory, aliasNames, Set.of());
    }

    private File findMatchingJarInDirectory(File directory, List<String> aliasNames, Set<String> ignoredLowercaseNames) {
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            return null;
        }
        File[] files = directory.listFiles(File::isFile);
        if (files == null || files.length == 0) {
            return null;
        }

        for (String alias : aliasNames) {
            String aliasLower = alias.toLowerCase(Locale.ROOT);
            String baseLower = aliasLower.endsWith(".jar")
                    ? aliasLower.substring(0, aliasLower.length() - 4)
                    : aliasLower;
            for (File file : files) {
                String name = file.getName().toLowerCase(Locale.ROOT);
                if (ignoredLowercaseNames.contains(name)) {
                    continue;
                }
                if (!name.endsWith(".jar")) {
                    continue;
                }
                if (name.equals(aliasLower)) {
                    return file;
                }
                if (name.startsWith(baseLower + "-")) {
                    return file;
                }
            }
        }
        return null;
    }

    private File resolveGroupDirectory(String baseDir) {
        File base = new File(baseDir);
        File exact = new File(base, groupName);
        if (exact.exists()) {
            return exact;
        }
        File[] children = base.listFiles(File::isDirectory);
        if (children != null) {
            for (File child : children) {
                if (child.getName().equalsIgnoreCase(groupName)) {
                    return child;
                }
            }
        }
        return exact;
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
                        // Nur Anzeige: die Zeile wird bereits ueber sendServerLog ins Log geschrieben
                        ConsoleScreenManager.printToTerminalOnly(
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
                if (running && !stopping) {
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

    // Level-Tag der Zeile: "[Server thread/INFO]", "/WARN]", Bungee "[INFO]" / "[WARNING]"
    private static final Pattern LOG_LEVEL_TAG = Pattern.compile(
            "(?:/|\\[)(TRACE|DEBUG|INFO|WARN|WARNING|ERROR|SEVERE|FATAL)\\]", Pattern.CASE_INSENSITIVE);

    private String determineLogLevel(String line) {
        if (line == null) {
            return "INFO";
        }
        Matcher matcher = LOG_LEVEL_TAG.matcher(line);
        if (matcher.find()) {
            return switch (matcher.group(1).toUpperCase(Locale.ROOT)) {
                case "ERROR", "SEVERE", "FATAL" -> "ERROR";
                case "WARN", "WARNING" -> "WARN";
                case "DEBUG", "TRACE" -> "DEBUG";
                default -> "INFO";
            };
        }
        return "INFO";
    }

    public void applyPermissionSync(Message.PermissionSync sync) {
        if (!running || sync == null || sync.playerUuid == null || sync.playerUuid.isBlank()) {
            return;
        }
        if (!isRuntimePermissionEnforcerEnabled()) {
            return;
        }
        String provider = getRuntimePermissionProvider();
        try {
            if ("luckperms".equalsIgnoreCase(provider)) {
                applyLuckPermsProfile(sync);
            } else {
                applyCloudProfile(sync);
            }
        } catch (Exception e) {
            CentralLogger.warn("PermissionEnforcer", "Runtime-Apply fehlgeschlagen für " + sync.playerUuid +
                    " auf " + serverName + ": " + e);
        }
    }

    private void applyCloudProfile(Message.PermissionSync sync) {
        String uuid = sync.playerUuid;
        Set<String> incoming = new java.util.LinkedHashSet<>();
        if (sync.permissions != null) {
            for (String perm : sync.permissions) {
                if (perm == null || perm.isBlank()) continue;
                if (isPermissionManagedByCloud(perm)) {
                    incoming.add(perm.trim());
                }
            }
        }
        enforcedPermissionsByPlayer.put(uuid, new java.util.LinkedHashSet<>(incoming));
        putOrRemove(enforcedGroupByPlayer, uuid, normalizeMeta(sync.primaryGroup));
        if (isPrefixSuffixEnforcementEnabled()) {
            putOrRemove(enforcedPrefixByPlayer, uuid, normalizeMeta(sync.prefix));
            putOrRemove(enforcedSuffixByPlayer, uuid, normalizeMeta(sync.suffix));
        }

        String syncCommand = getCloudRuntimeSyncCommand();
        if (syncCommand != null && !syncCommand.isBlank()) {
            sendCommand(syncCommand
                    .replace("{uuid}", uuid)
                    .replace("{server}", serverName));
        }
    }

    private void applyLuckPermsProfile(Message.PermissionSync sync) {
        String uuid = sync.playerUuid;
        Set<String> incoming = new java.util.LinkedHashSet<>();
        if (sync.permissions != null) {
            for (String perm : sync.permissions) {
                if (perm == null || perm.isBlank()) continue;
                if (isPermissionManagedByCloud(perm)) {
                    incoming.add(perm.trim());
                }
            }
        }
        Set<String> previous = enforcedPermissionsByPlayer.getOrDefault(uuid, Set.of());

        for (String removed : previous) {
            if (!incoming.contains(removed)) {
                sendCommand("lp user " + uuid + " permission unset " + removed);
            }
        }
        for (String perm : incoming) {
            if (!previous.contains(perm)) {
                sendCommand("lp user " + uuid + " permission set " + perm + " true");
            }
        }

        String currentGroup = normalizeMeta(sync.primaryGroup);
        String previousGroup = enforcedGroupByPlayer.get(uuid);
        if (!Objects.equals(previousGroup, currentGroup) && currentGroup != null) {
            sendCommand("lp user " + uuid + " parent set " + currentGroup);
            putOrRemove(enforcedGroupByPlayer, uuid, currentGroup);
        }

        if (isPrefixSuffixEnforcementEnabled()) {
            String prefix = normalizeMeta(sync.prefix);
            String suffix = normalizeMeta(sync.suffix);
            String previousPrefix = enforcedPrefixByPlayer.get(uuid);
            String previousSuffix = enforcedSuffixByPlayer.get(uuid);
            if (!Objects.equals(previousPrefix, prefix)) {
                if (prefix == null) {
                    sendCommand("lp user " + uuid + " meta clearsetprefix");
                } else {
                    sendCommand("lp user " + uuid + " meta setprefix 100 " + quote(prefix));
                }
                putOrRemove(enforcedPrefixByPlayer, uuid, prefix);
            }
            if (!Objects.equals(previousSuffix, suffix)) {
                if (suffix == null) {
                    sendCommand("lp user " + uuid + " meta clearsetsuffix");
                } else {
                    sendCommand("lp user " + uuid + " meta setsuffix 100 " + quote(suffix));
                }
                putOrRemove(enforcedSuffixByPlayer, uuid, suffix);
            }
        }

        enforcedPermissionsByPlayer.put(uuid, new java.util.LinkedHashSet<>(incoming));
    }

    private boolean isPermissionManagedByCloud(String permission) {
        String filter = configManager.getMaster("CloudMaster.Permissions.Runtime.PermissionPrefixFilter");
        if (filter == null || filter.isBlank()) {
            filter = "cloud.";
        }
        String p = permission.toLowerCase(Locale.ROOT);
        String f = filter.toLowerCase(Locale.ROOT);
        return p.equals("*") || p.equals("cloud.*") || p.startsWith(f);
    }

    private boolean isRuntimePermissionEnforcerEnabled() {
        String configured = configManager.getMaster("CloudMaster.Permissions.Runtime.Enabled");
        if (configured == null || configured.isBlank()) {
            return true;
        }
        return Boolean.parseBoolean(configured);
    }

    private boolean isPrefixSuffixEnforcementEnabled() {
        String configured = configManager.getMaster("CloudMaster.Permissions.Runtime.EnforcePrefixSuffix");
        if (configured == null || configured.isBlank()) {
            return true;
        }
        return Boolean.parseBoolean(configured);
    }

    private String getRuntimePermissionProvider() {
        String configured = configManager.getMaster("CloudMaster.Permissions.Runtime.Provider");
        if (configured == null || configured.isBlank()) {
            return "cloud";
        }
        return configured.trim();
    }

    private String getCloudRuntimeSyncCommand() {
        String configured = configManager.getMaster("CloudMaster.Permissions.Runtime.CloudSyncCommand");
        return configured == null ? "" : configured.trim();
    }

    private static void putOrRemove(Map<String, String> map, String key, String value) {
        if (value == null) {
            map.remove(key);
        } else {
            map.put(key, value);
        }
    }

    private String normalizeMeta(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String quote(String value) {
        return "\"" + value.replace("\"", "\\\"") + "\"";
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
                ConsoleColors.getCurrentTime() + " [OK] Server " + serverName + " ist bereit!");

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
        stop(graceful, false);
    }

    private int getStopTimeoutSeconds() {
        try {
            return Math.max(1, configManager.getMasterConfigData().getInt("CloudMaster.Servers.StopTimeoutSeconds", 30));
        } catch (Exception e) {
            return 30;
        }
    }

    private boolean isProxyProcess() {
        String group = groupName == null ? "" : groupName.toLowerCase(Locale.ROOT);
        return group.contains("proxy") || group.contains("bungee") || group.contains("waterfall") || group.contains("velocity");
    }

    /**
     * Stoppt den Server. graceful: erst Stop-Befehl ueber stdin (Proxy "end"), dann bis zum
     * konfigurierten Timeout warten, erst danach destroy/destroyForcibly.
     *
     * @param skipCountdown ueberspringt die Ingame-Countdown-Ansagen (z.B. beim Cloud-Shutdown)
     */
    public void stop(boolean graceful, boolean skipCountdown) {
        if (!running || stopping) {
            return;
        }
        stopping = true;

        ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Stoppe Server: " + serverName);

        if (!graceful) {
            running = false;
            process.destroyForcibly();
            cleanup();
            wrapper.sendServerStatus(serverName, "KILLED");
            ConsoleScreenManager.printToTerminal(ConsoleColors.YELLOW + ConsoleColors.PREFIX +
                    ConsoleColors.getCurrentTime() + " Server " + serverName + " wurde hart beendet");
            return;
        }

        boolean proxy = isProxyProcess();
        if (!proxy && !skipCountdown) {
            sendCommand("say Server shutdown in 30 seconds.");
            sleepQuietly(15000);
            sendCommand("say Server shutdown in 15 seconds.");
            sleepQuietly(5000);
            sendCommand("say Server shutdown in 10 seconds.");
            sleepQuietly(5000);
            sendCommand("say Server shutdown in 5 seconds.");
            sleepQuietly(5000);
        }
        // sendCommand prueft "running" - das ist hier noch true, der Prozess laeuft ja noch.
        sendCommand(proxy ? (software.toString().toLowerCase(Locale.ROOT).contains("velocity") ? "shutdown" : "end") : "stop");

        boolean killed = false;
        int timeout = getStopTimeoutSeconds();
        try {
            if (!process.waitFor(timeout, TimeUnit.SECONDS)) {
                ConsoleScreenManager.printToTerminal(ConsoleColors.YELLOW + ConsoleColors.PREFIX +
                        ConsoleColors.getCurrentTime() + " Server " + serverName +
                        " reagiert nach " + timeout + "s nicht auf den Stop-Befehl, beende Prozess...");
                killed = true;
                process.destroy();
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            killed = true;
            Thread.currentThread().interrupt();
        }

        running = false;
        cleanup();

        // Sauberes Ende -> OFFLINE, nur bei erzwungenem Ende KILLED
        wrapper.sendServerStatus(serverName, killed ? "KILLED" : "OFFLINE");

        ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Server " + serverName + (killed ? " wurde nach Timeout beendet" : " gestoppt"));
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

