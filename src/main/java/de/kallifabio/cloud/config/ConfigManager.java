/**
 * Erstellt von Gamer_Kidd_LP | kallifabio
 * am 03.10.2024 um 18:22
 * Projektname: KalliCloud
 * Packagename: de.kallifabio.cloudsystem
 */

package de.kallifabio.cloud.config;

import de.kallifabio.cloud.libs.console.ConsoleColors;
import de.kallifabio.cloud.libs.console.ConsoleScreenManager;
import de.kallifabio.cloud.master.Master;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class ConfigManager {
    private static final String CONFIG_VERSION = "1.1";

    File masterConfigFile = new File("config", "CloudSystem_Config.yml");
    FileConfiguration masterConfigData = YamlConfiguration.loadConfiguration(masterConfigFile);

    File serverGroupsFile = new File("config", "ServerGroups.yml");
    FileConfiguration serverGroupsData = YamlConfiguration.loadConfiguration(serverGroupsFile);

    File signLayoutFile = new File("config", "SignLayout.yml");
    FileConfiguration signLayoutData = YamlConfiguration.loadConfiguration(signLayoutFile);

    File signsFile = new File("config", "Signs.yml");
    FileConfiguration signsData = YamlConfiguration.loadConfiguration(signsFile);

    File clusterConfigFile = new File("config", "Cluster.yml");
    FileConfiguration clusterConfigData = YamlConfiguration.loadConfiguration(clusterConfigFile);

    public ConfigManager() {
        loadMasterConfig();
        loadServergroupConfig();
        loadSignLayoutConfig();
        loadSignsConfig();
        loadClusterConfig();
        ensureConfigVersion();
        validateAndSanitize();
    }

    public void loadMasterConfig() {
        try {
            if (!masterConfigFile.exists()) {
                createDefaultMasterConfig();
            }
            masterConfigData.load(masterConfigFile);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }
    }

    public void loadServergroupConfig() {
        try {
            if (!serverGroupsFile.exists()) {
                createDefaultServergroupConfig();
            }
            serverGroupsData.load(serverGroupsFile);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }
    }

    public void loadSignLayoutConfig() {
        try {
            if (!signLayoutFile.exists()) {
                createDefaultSignLayoutConfig();
            }
            signLayoutData.load(signLayoutFile);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }
    }

    public void loadSignsConfig() {
        try {
            if (!signsFile.exists()) {
                createDefaultSignsConfig();
            }
            signsData.load(signsFile);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }
    }

    public void loadClusterConfig() {
        try {
            if (!clusterConfigFile.exists()) {
                createDefaultClusterConfig();
            }
            clusterConfigData.load(clusterConfigFile);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }
    }

    public synchronized void reloadAllConfigs() {
        loadMasterConfig();
        loadServergroupConfig();
        loadSignLayoutConfig();
        loadSignsConfig();
        loadClusterConfig();
        ensureConfigVersion();
        validateAndSanitize();
    }

    public synchronized void backupConfigs(String trigger) {
        try {
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Path backupDir = Path.of("config", "backups", ts + "-" + safeName(trigger));
            Files.createDirectories(backupDir);

            copyIfExists(masterConfigFile.toPath(), backupDir.resolve(masterConfigFile.getName()));
            copyIfExists(serverGroupsFile.toPath(), backupDir.resolve(serverGroupsFile.getName()));
            copyIfExists(signLayoutFile.toPath(), backupDir.resolve(signLayoutFile.getName()));
            copyIfExists(signsFile.toPath(), backupDir.resolve(signsFile.getName()));
            copyIfExists(clusterConfigFile.toPath(), backupDir.resolve(clusterConfigFile.getName()));

            ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                    " Config-Backup erstellt: " + backupDir);
        } catch (IOException e) {
            ConsoleScreenManager.printToTerminal(ConsoleColors.RED + ConsoleColors.PREFIX +
                    ConsoleColors.getCurrentTime() + " Config-Backup fehlgeschlagen: " + e.getMessage());
        }
    }

    private void createDefaultMasterConfig() {
        try {
            masterConfigFile.getParentFile().mkdirs();
            masterConfigFile.createNewFile();

            masterConfigData.set("CloudMaster.Max_Ram", "16384");
            masterConfigData.set("CloudMaster.Default_Ram", "16384");
            masterConfigData.set("CloudMaster.Hostname", Master.getInstance() != null ? Master.getInstance().getMasterHost() : "localhost");
            masterConfigData.set("CloudMaster.Port", Master.getInstance() != null ? Master.getInstance().getMasterPort().toString() : "54555");
            masterConfigData.set("CloudMaster.Network.TcpPort", 54555);
            masterConfigData.set("CloudMaster.Network.UdpPort", 54777);
            masterConfigData.set("CloudMaster.Network.ConnectHost", "127.0.0.1");
            masterConfigData.set("CloudMaster.Network.GameHost", "auto");
            masterConfigData.set("CloudWrapper.RouteHost", "auto");

            // API Configuration
            masterConfigData.set("CloudMaster.API.Enabled", true);
            masterConfigData.set("CloudMaster.API.Port", 8081);
            masterConfigData.set("CloudMaster.API.AllowedOrigins", "*");
            masterConfigData.set("CloudMaster.API.AdminKey", java.util.UUID.randomUUID().toString());
            masterConfigData.set("CloudMaster.API.DashboardKey", java.util.UUID.randomUUID().toString());
            masterConfigData.set("CloudMaster.API.OwnerKey", "");
            masterConfigData.set("CloudMaster.API.OperatorKey", "");
            masterConfigData.set("CloudMaster.API.TLS.Enabled", false);
            masterConfigData.set("CloudMaster.API.TLS.KeystorePath", "config/tls/keystore.p12");
            masterConfigData.set("CloudMaster.API.TLS.KeystorePassword", "");
            masterConfigData.set("CloudMaster.API.TLS.KeystoreType", "PKCS12");
            masterConfigData.set("CloudMaster.MOTD.Enabled", true);
            masterConfigData.set("CloudMaster.MOTD.Line1", "&bKalliCloud Network");
            masterConfigData.set("CloudMaster.MOTD.Line2", "&7Powered by KalliCloud");
            masterConfigData.set("CloudMaster.MOTD.MaintenanceLine1", "&cMaintenance");
            masterConfigData.set("CloudMaster.MOTD.MaintenanceLine2", "&7Please try again later");
            masterConfigData.set("CloudMaster.MOTD.FakeSlots.Enabled", false);
            masterConfigData.set("CloudMaster.MOTD.FakeSlots.Online", -1);
            masterConfigData.set("CloudMaster.MOTD.FakeSlots.Max", -1);
            masterConfigData.set("CloudMaster.Network.ProxyOnlineMode", true);
            masterConfigData.set("CloudMaster.Network.ProxyBindHost", "0.0.0.0");
            masterConfigData.set("CloudMaster.Network.ProxyBindLocalAddress", false);
            masterConfigData.set("CloudMaster.Network.ProxyForceDefaultServer", true);
            masterConfigData.set("CloudMaster.Network.ProxyServerConnectTimeoutMs", 15000);
            masterConfigData.set("CloudMaster.Network.ProxyTimeoutMs", 60000);
            masterConfigData.set("CloudMaster.Network.ProxyRemotePingTimeoutMs", 5000);
            masterConfigData.set("CloudMaster.Network.EnforceBackendBind", true);
            masterConfigData.set("CloudMaster.Network.BackendBindAddress", "auto");
            masterConfigData.set("CloudMaster.Network.ForwardingSecret", java.util.UUID.randomUUID().toString().replace("-", ""));
            masterConfigData.set("CloudMaster.ConfigVersion", CONFIG_VERSION);
            masterConfigData.set("CloudMaster.Database.Type", "sqlite");
            masterConfigData.set("CloudMaster.Database.SQLite.File", "data/cloud.db");
            masterConfigData.set("CloudMaster.Database.MySQL.Url", "jdbc:mysql://localhost:3306/cloud");
            masterConfigData.set("CloudMaster.Database.MySQL.User", "root");
            masterConfigData.set("CloudMaster.Database.MySQL.Password", "");
            masterConfigData.set("CloudMaster.Database.Mongo.Uri", "mongodb://localhost:27017");
            masterConfigData.set("CloudMaster.Database.Mongo.Database", "cloud");
            masterConfigData.set("CloudMaster.Alerts.WebhookUrl", "");
            masterConfigData.set("CloudMaster.Templates.TestingMode", false);
            masterConfigData.set("CloudMaster.Templates.AutoUpdateAfterRestart", true);
            masterConfigData.set("CloudMaster.Permissions.Runtime.Enabled", true);
            masterConfigData.set("CloudMaster.Permissions.Runtime.Provider", "cloud");
            masterConfigData.set("CloudMaster.Permissions.Runtime.EnforcePrefixSuffix", true);
            masterConfigData.set("CloudMaster.Permissions.Runtime.PermissionPrefixFilter", "cloud.");
            masterConfigData.set("CloudMaster.Permissions.Runtime.CloudSyncCommand", "");
            masterConfigData.set("CloudMaster.Monitoring.CPU.Warning", 90.0);
            masterConfigData.set("CloudMaster.Monitoring.CPU.Critical", 97.0);
            masterConfigData.set("CloudMaster.Monitoring.Memory.Warning", 90.0);
            masterConfigData.set("CloudMaster.Monitoring.Memory.Critical", 96.0);
            masterConfigData.set("CloudMaster.Monitoring.TPS.Warning", 18.0);
            masterConfigData.set("CloudMaster.Monitoring.TPS.Critical", 15.0);
            masterConfigData.set("CloudMaster.Monitoring.RequiredConsecutiveBreaches", 2);
            masterConfigData.set("CloudMaster.Monitoring.Cooldown.WarningMs", 60000);
            masterConfigData.set("CloudMaster.Monitoring.Cooldown.CriticalMs", 120000);
            masterConfigData.set("CloudMaster.Monitoring.Cooldown.InfoMs", 300000);
            masterConfigData.set("CloudMaster.Recovery.Quarantine.Enabled", true);
            masterConfigData.set("CloudMaster.Recovery.Quarantine.FailureThreshold", 3);
            masterConfigData.set("CloudMaster.Recovery.Quarantine.AutoRestartQuarantined", false);
            masterConfigData.set("CloudMaster.Recovery.ServerHeartbeatTimeoutMs", 30000);
            masterConfigData.set("CloudMaster.Recovery.ServerStartingTimeoutMs", 120000);
            masterConfigData.set("CloudMaster.Recovery.WrapperPongTimeoutMs", 20000);
            masterConfigData.set("CloudMaster.Recovery.RestartPortRetryLimit", 8);

            masterConfigData.save(masterConfigFile);
            ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                    " CloudSystem_Config.yml erstellt");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void createDefaultServergroupConfig() {
        try {
            serverGroupsFile.getParentFile().mkdirs();
            serverGroupsFile.createNewFile();

            // ========================================
            // Auto-Start Configuration
            // ========================================
            serverGroupsData.set("AutoStart.Enabled", true);
            serverGroupsData.set("AutoStart.Groups", "Proxy:1,Lobby:1");
            serverGroupsData.set("AutoStart.DelaySeconds", 10);

            // ========================================
            // Port Configuration
            // ========================================
            serverGroupsData.set("Ports.FirstProxy", 25577);
            serverGroupsData.set("Ports.FirstLobby", 25565);
            serverGroupsData.set("Ports.DynamicStart", 25566);

            // ========================================
            // Proxy Server Group (BungeeCord)
            // ========================================
            serverGroupsData.set("ServerGroup.Proxy.Ram", 512);
            serverGroupsData.set("ServerGroup.Proxy.MaxPlayers", 500);
            serverGroupsData.set("ServerGroup.Proxy.Software", "proxy");
            serverGroupsData.set("ServerGroup.Proxy.JavaArgs", List.of());
            serverGroupsData.set("ServerGroup.Proxy.StartArgs", List.of());
            serverGroupsData.set("ServerGroup.Proxy.Dynamic", false);
            serverGroupsData.set("ServerGroup.Proxy.MinServers", 1);
            serverGroupsData.set("ServerGroup.Proxy.MaxServers", 1);
            serverGroupsData.set("ServerGroup.Proxy.Maintenance", false);
            serverGroupsData.set("ServerGroup.Proxy.AutoScaling", false);
            serverGroupsData.set("ServerGroup.Proxy.Priority", "CRITICAL");
            serverGroupsData.set("ServerGroup.Proxy.Parent", "");
            serverGroupsData.set("ServerGroup.Proxy.Tags", List.of("STABLE"));
            serverGroupsData.set("ServerGroup.Proxy.Whitelist", List.of());

            // ========================================
            // Lobby Server Group
            // ========================================
            serverGroupsData.set("ServerGroup.Lobby.Ram", 1024);
            serverGroupsData.set("ServerGroup.Lobby.MaxPlayers", 100);
            serverGroupsData.set("ServerGroup.Lobby.Software", "paper");
            serverGroupsData.set("ServerGroup.Lobby.JavaArgs", List.of());
            serverGroupsData.set("ServerGroup.Lobby.StartArgs", List.of("--nogui"));
            serverGroupsData.set("ServerGroup.Lobby.Dynamic", true);
            serverGroupsData.set("ServerGroup.Lobby.MinServers", 1);
            serverGroupsData.set("ServerGroup.Lobby.MaxServers", 5);
            serverGroupsData.set("ServerGroup.Lobby.Maintenance", false);
            serverGroupsData.set("ServerGroup.Lobby.AutoScaling", true);
            serverGroupsData.set("ServerGroup.Lobby.ScaleUpThreshold", 0.75);
            serverGroupsData.set("ServerGroup.Lobby.ScaleDownThreshold", 0.30);
            serverGroupsData.set("ServerGroup.Lobby.Priority", "HIGH");
            serverGroupsData.set("ServerGroup.Lobby.Parent", "");
            serverGroupsData.set("ServerGroup.Lobby.Tags", List.of("DEFAULT"));
            serverGroupsData.set("ServerGroup.Lobby.Whitelist", List.of());

            serverGroupsData.save(serverGroupsFile);
            ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                    " ServerGroups.yml erstellt");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void createDefaultSignLayoutConfig() {
        try {
            signLayoutFile.getParentFile().mkdirs();
            signLayoutFile.createNewFile();

            signLayoutData.set("Layout.Signs.Default.Line1", "&7&m--- &e{server_name} &7&m---");
            signLayoutData.set("Layout.Signs.Default.Line2", "{status} &8{animation}");
            signLayoutData.set("Layout.Signs.Default.Line3", "&2{players_online} &8/ &4{max_players}");
            signLayoutData.set("Layout.Signs.Default.Line4", "&7&m--- &e{server_name} &7&m---");
            signLayoutData.set("Layout.Signs.Npc.Line1", "&a{display_name}");
            signLayoutData.set("Layout.Signs.Npc.Line2", "&7{status} &8{animation}");
            signLayoutData.set("Layout.Signs.Npc.Line3", "&e{players} &7Spieler");
            signLayoutData.set("Layout.Signs.Npc.Line4", "&8Klicken zum Verbinden");
            signLayoutData.set("Layout.Signs.Mob.Line1", "&6{display_name}");
            signLayoutData.set("Layout.Signs.Mob.Line2", "&7{group}");
            signLayoutData.set("Layout.Signs.Mob.Line3", "&e{players} &8| &a{tps} TPS");
            signLayoutData.set("Layout.Signs.Mob.Line4", "&8Server Selector");

            signLayoutData.set("Layout.Animations.Frames1", "&7.");
            signLayoutData.set("Layout.Animations.Frames2", "&7..");
            signLayoutData.set("Layout.Animations.Frames3", "&7...");
            signLayoutData.set("Layout.Animations.Frames4", "&7....");
            signLayoutData.set("Layout.Animations.Interval", "10");

            signLayoutData.save(signLayoutFile);
            ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                    " SignLayout.yml erstellt");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void createDefaultSignsConfig() {
        try {
            signsFile.getParentFile().mkdirs();
            signsFile.createNewFile();
            signsData.set("Signs", new LinkedHashMap<>());
            signsData.save(signsFile);
            ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                    " Signs.yml erstellt");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void createDefaultClusterConfig() {
        try {
            clusterConfigFile.getParentFile().mkdirs();
            clusterConfigFile.createNewFile();

            // Cluster Configuration
            clusterConfigData.set("Cluster.Enabled", true);
            clusterConfigData.set("Cluster.Mode", "MULTI_MASTER");
            clusterConfigData.set("Cluster.HeartbeatInterval", 5000);
            clusterConfigData.set("Cluster.HeartbeatTimeout", 15000);
            clusterConfigData.set("Cluster.ElectionTimeout", 5000);

            // Peer Discovery
            clusterConfigData.set("Cluster.Discovery.Method", "STATIC");
            clusterConfigData.set("Cluster.Discovery.Peers", new String[]{});

            // Split-Brain Prevention
            clusterConfigData.set("Cluster.SplitBrain.PreventionEnabled", true);
            clusterConfigData.set("Cluster.SplitBrain.QuorumSize", 2);

            clusterConfigData.save(clusterConfigFile);
            ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                    " Cluster.yml erstellt");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ========================================
    // Auto-Start Configuration Methods
    // ========================================
    public boolean isAutoStartEnabled() {
        return serverGroupsData.getBoolean("AutoStart.Enabled", true);
    }

    public List<String> getAutoStartGroups() {
        List<String> groups = new ArrayList<>();
        String autoStartConfig = serverGroupsData.getString("AutoStart.Groups", "Proxy:1,Lobby:2");

        if (autoStartConfig != null && !autoStartConfig.isEmpty()) {
            String[] groupConfigs = autoStartConfig.split(",");
            for (String config : groupConfigs) {
                groups.add(config.trim());
            }
        }

        return groups;
    }

    public int getAutoStartDelay() {
        return serverGroupsData.getInt("AutoStart.DelaySeconds", 10);
    }

    // ========================================
    // Port Configuration Methods
    // ========================================
    public int getFirstProxyPort() {
        return serverGroupsData.getInt("Ports.FirstProxy", 25577);
    }

    public int getFirstLobbyPort() {
        return serverGroupsData.getInt("Ports.FirstLobby", 25565);
    }

    public int getDynamicPortStart() {
        return serverGroupsData.getInt("Ports.DynamicStart", 25566);
    }

    public int getMasterTcpPort() {
        return masterConfigData.getInt("CloudMaster.Network.TcpPort", 54555);
    }

    public int getMasterUdpPort() {
        return masterConfigData.getInt("CloudMaster.Network.UdpPort", 54777);
    }

    public String getMasterConnectHost() {
        return masterConfigData.getString("CloudMaster.Network.ConnectHost",
                masterConfigData.getString("CloudMaster.Hostname", "127.0.0.1"));
    }

    // ========================================
    // Server Group Configuration Methods
    // ========================================
    public String getMaster(String key) {
        return masterConfigData.getString(key);
    }

    public String getServergroup(String key) {
        return serverGroupsData.getString(key);
    }

    public String getCluster(String key) {
        return clusterConfigData.getString(key);
    }

    public Map<String, Object> getSignCenterSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("layouts", getSignLayouts());
        snapshot.put("signs", getCloudSigns());
        snapshot.put("animation", Map.of(
                "frames", getSignAnimationFrames(),
                "intervalTicks", signLayoutData.getInt("Layout.Animations.Interval", 10)
        ));
        return snapshot;
    }

    public List<Map<String, Object>> getCloudSigns() {
        List<Map<String, Object>> signs = new ArrayList<>();
        if (!signsData.contains("Signs") || signsData.getConfigurationSection("Signs") == null) {
            return signs;
        }
        for (String id : signsData.getConfigurationSection("Signs").getKeys(false)) {
            String base = "Signs." + id;
            Map<String, Object> sign = new LinkedHashMap<>();
            sign.put("id", id);
            sign.put("selectorType", signsData.getString(base + ".Type", signsData.getString(base + ".SelectorType", "SIGN")));
            sign.put("entityType", signsData.getString(base + ".EntityType", "VILLAGER"));
            sign.put("displayName", signsData.getString(base + ".DisplayName", ""));
            sign.put("skinName", signsData.getString(base + ".SkinName", ""));
            sign.put("skinUrl", signsData.getString(base + ".SkinUrl", ""));
            sign.put("glowing", signsData.getBoolean(base + ".Glowing", false));
            sign.put("baby", signsData.getBoolean(base + ".Baby", false));
            sign.put("variant", signsData.getString(base + ".Variant", ""));
            sign.put("customName", signsData.getString(base + ".CustomName", signsData.getString(base + ".DisplayName", "")));
            sign.put("locationMode", signsData.getString(base + ".LocationMode",
                    signsData.getBoolean(base + ".AutoLocation", false) ? "AUTO" : "FIXED"));
            sign.put("autoLocation", signsData.getBoolean(base + ".AutoLocation",
                    "AUTO".equalsIgnoreCase(signsData.getString(base + ".LocationMode", "FIXED"))));
            sign.put("world", signsData.getString(base + ".World", "world"));
            sign.put("x", signsData.getInt(base + ".X", 0));
            sign.put("y", signsData.getInt(base + ".Y", 0));
            sign.put("z", signsData.getInt(base + ".Z", 0));
            sign.put("yaw", signsData.getDouble(base + ".Yaw", 0.0));
            sign.put("pitch", signsData.getDouble(base + ".Pitch", 0.0));
            sign.put("serverName", signsData.getString(base + ".Server", ""));
            sign.put("groupName", signsData.getString(base + ".Group", ""));
            sign.put("layout", signsData.getString(base + ".Layout", "Default"));
            sign.put("category", signsData.getString(base + ".Category", "General"));
            sign.put("permission", signsData.getString(base + ".Permission", ""));
            sign.put("region", signsData.getString(base + ".Region", "GLOBAL"));
            sign.put("selectorTemplate", signsData.getString(base + ".SelectorTemplate", ""));
            sign.put("clickAction", signsData.getString(base + ".ClickAction", "CONNECT"));
            sign.put("fallbackGroup", signsData.getString(base + ".FallbackGroup", ""));
            sign.put("queueOnFull", signsData.getBoolean(base + ".QueueOnFull", true));
            sign.put("partyAware", signsData.getBoolean(base + ".PartyAware", true));
            sign.put("hologramLines", signsData.getStringList(base + ".HologramLines"));
            sign.put("enabled", signsData.getBoolean(base + ".Enabled", true));
            sign.put("priority", signsData.getInt(base + ".Priority", 0));
            sign.put("spawned", signsData.getBoolean(base + ".Spawned", false));
            sign.put("lastSeenAt", signsData.getLong(base + ".LastSeenAt", 0L));
            sign.put("version", signsData.getInt(base + ".Version", 1));
            sign.put("updatedAt", signsData.getLong(base + ".UpdatedAt", 0L));
            signs.add(sign);
        }
        signs.sort((a, b) -> {
            int priority = Integer.compare(asInt(b.get("priority")), asInt(a.get("priority")));
            if (priority != 0) {
                return priority;
            }
            return String.valueOf(a.get("id")).compareToIgnoreCase(String.valueOf(b.get("id")));
        });
        return signs;
    }

    public List<Map<String, Object>> getCloudSelectors(String... selectorTypes) {
        List<String> allowedTypes = Arrays.stream(selectorTypes == null ? new String[0] : selectorTypes)
                .filter(type -> type != null && !type.isBlank())
                .map(this::normalizeSelectorType)
                .toList();
        if (allowedTypes.isEmpty()) {
            return getCloudSigns();
        }
        return getCloudSigns().stream()
                .filter(sign -> allowedTypes.contains(normalizeSelectorType(String.valueOf(sign.get("selectorType")))))
                .toList();
    }

    public Map<String, List<String>> getSignLayouts() {
        Map<String, List<String>> layouts = new LinkedHashMap<>();
        if (!signLayoutData.contains("Layout.Signs") || signLayoutData.getConfigurationSection("Layout.Signs") == null) {
            layouts.put("Default", getSignLayout("Default"));
            return layouts;
        }
        for (String name : signLayoutData.getConfigurationSection("Layout.Signs").getKeys(false)) {
            layouts.put(name, getSignLayout(name));
        }
        if (!layouts.containsKey("Default")) {
            layouts.put("Default", getSignLayout("Default"));
        }
        return layouts;
    }

    public List<String> getSignLayout(String layoutName) {
        String safeLayout = layoutName == null || layoutName.isBlank() ? "Default" : layoutName;
        String base = "Layout.Signs." + safeLayout;
        if (!signLayoutData.contains(base)) {
            base = "Layout.Signs.Default";
        }
        List<String> lines = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            lines.add(signLayoutData.getString(base + ".Line" + i, ""));
        }
        return lines;
    }

    public boolean upsertSignLayout(String layoutName, List<String> lines) {
        if (layoutName == null || layoutName.isBlank()) {
            return false;
        }
        createSelectorVersion("layout_" + sanitizeYamlKey(layoutName));
        String base = "Layout.Signs." + sanitizeYamlKey(layoutName);
        List<String> safeLines = lines == null ? List.of() : lines;
        for (int i = 1; i <= 4; i++) {
            String line = i <= safeLines.size() ? safeLines.get(i - 1) : "";
            signLayoutData.set(base + ".Line" + i, line == null ? "" : line);
        }
        return saveSignLayout();
    }

    public Map<String, Object> upsertCloudSign(Map<String, Object> request) {
        request = applySelectorTemplate(request == null ? Map.of() : request);
        String selectorType = normalizeSelectorType(defaultString(request.get("selectorType"), defaultString(request.get("type"), "SIGN")));
        String id = stringValue(request.get("id"));
        if (id.isBlank()) {
            id = generateSelectorId(selectorType, request);
        }
        String safeId = sanitizeYamlKey(id);
        String base = "Signs." + safeId;
        createSelectorVersion("upsert_" + safeId);
        boolean hasExplicitLocation = request.containsKey("world")
                || request.containsKey("x")
                || request.containsKey("y")
                || request.containsKey("z");
        boolean autoLocation = asBoolean(request.get("autoLocation"), !hasExplicitLocation);
        String locationMode = defaultString(request.get("locationMode"), autoLocation ? "AUTO" : "FIXED").toUpperCase();
        if (!"AUTO".equals(locationMode) && !"FIXED".equals(locationMode)) {
            locationMode = autoLocation ? "AUTO" : "FIXED";
        }
        autoLocation = "AUTO".equals(locationMode);
        String entityType = normalizeEntityType(defaultString(request.get("entityType"), defaultEntityType(selectorType)), selectorType);
        String displayName = defaultString(request.get("displayName"), "");
        signsData.set(base + ".Type", selectorType);
        signsData.set(base + ".EntityType", entityType);
        signsData.set(base + ".DisplayName", displayName);
        signsData.set(base + ".SkinName", defaultString(request.get("skinName"), ""));
        signsData.set(base + ".SkinUrl", defaultString(request.get("skinUrl"), ""));
        signsData.set(base + ".Glowing", asBoolean(request.get("glowing"), false));
        signsData.set(base + ".Baby", asBoolean(request.get("baby"), false));
        signsData.set(base + ".Variant", defaultString(request.get("variant"), ""));
        signsData.set(base + ".CustomName", defaultString(request.get("customName"), displayName));
        signsData.set(base + ".LocationMode", locationMode);
        signsData.set(base + ".AutoLocation", autoLocation);
        signsData.set(base + ".World", defaultString(request.get("world"), autoLocation ? "AUTO" : "world"));
        signsData.set(base + ".X", asInt(request.get("x")));
        signsData.set(base + ".Y", asInt(request.get("y")));
        signsData.set(base + ".Z", asInt(request.get("z")));
        signsData.set(base + ".Yaw", asDouble(request.get("yaw"), 0.0));
        signsData.set(base + ".Pitch", asDouble(request.get("pitch"), 0.0));
        signsData.set(base + ".Server", defaultString(request.get("serverName"), defaultString(request.get("server"), "")));
        signsData.set(base + ".Group", defaultString(request.get("groupName"), defaultString(request.get("group"), "")));
        signsData.set(base + ".Layout", normalizeSelectorLayout(defaultString(request.get("layout"), defaultLayout(selectorType)), selectorType));
        signsData.set(base + ".Category", defaultString(request.get("category"), "General"));
        signsData.set(base + ".Permission", defaultString(request.get("permission"), ""));
        signsData.set(base + ".Region", defaultString(request.get("region"), "GLOBAL").toUpperCase());
        signsData.set(base + ".SelectorTemplate", defaultString(request.get("selectorTemplate"), defaultString(request.get("template"), "")));
        signsData.set(base + ".ClickAction", normalizeClickAction(defaultString(request.get("clickAction"), "CONNECT")));
        signsData.set(base + ".FallbackGroup", defaultString(request.get("fallbackGroup"), ""));
        signsData.set(base + ".QueueOnFull", asBoolean(request.get("queueOnFull"), true));
        signsData.set(base + ".PartyAware", asBoolean(request.get("partyAware"), true));
        signsData.set(base + ".HologramLines", normalizeHologramLines(asStringList(request.get("hologramLines")), selectorType, displayName));
        signsData.set(base + ".Enabled", asBoolean(request.get("enabled"), true));
        signsData.set(base + ".Priority", asInt(request.get("priority")));
        signsData.set(base + ".Spawned", asBoolean(request.get("spawned"), signsData.getBoolean(base + ".Spawned", false)));
        signsData.set(base + ".LastSeenAt", request.containsKey("lastSeenAt")
                ? asLong(request.get("lastSeenAt"), System.currentTimeMillis())
                : signsData.getLong(base + ".LastSeenAt", 0L));
        signsData.set(base + ".Version", signsData.getInt(base + ".Version", 0) + 1);
        signsData.set(base + ".UpdatedAt", System.currentTimeMillis());
        saveSigns();
        return getCloudSigns().stream()
                .filter(sign -> safeId.equals(sign.get("id")))
                .findFirst()
                .orElse(Map.of("id", safeId));
    }

    public boolean deleteCloudSign(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        createSelectorVersion("delete_" + sanitizeYamlKey(id));
        signsData.set("Signs." + sanitizeYamlKey(id), null);
        return saveSigns();
    }

    public Map<String, Map<String, Object>> getSelectorTemplates() {
        Map<String, Map<String, Object>> templates = new LinkedHashMap<>();
        templates.put("LobbyNPC", selectorTemplate("NPC", "VILLAGER", "Npc", "Lobby", "", true, true, "&aLobby"));
        templates.put("GameMob", selectorTemplate("MOB", "ZOMBIE", "Mob", "Games", "", true, true, "&eGame Selector"));
        templates.put("MaintenanceSign", selectorTemplate("SIGN", "VILLAGER", "Default", "Maintenance", "cloud.selector.maintenance", false, false, "&cMaintenance"));
        templates.put("QueueSign", selectorTemplate("SIGN", "VILLAGER", "Default", "Queue", "", true, true, "&eQueue"));
        return templates;
    }

    public Map<String, Object> bulkUpdateSelectors(Map<String, Object> request) {
        String selectorType = stringValue(request.get("selectorType"));
        String group = stringValue(request.get("groupName"));
        String category = stringValue(request.get("category"));
        String action = defaultString(request.get("action"), "disable").toLowerCase();
        String layout = stringValue(request.get("layout"));
        String permission = stringValue(request.get("permission"));
        int changed = 0;
        createSelectorVersion("bulk_" + action);
        for (Map<String, Object> selector : getCloudSigns()) {
            if (!selectorType.isBlank() && !selectorType.equalsIgnoreCase(stringValue(selector.get("selectorType")))) {
                continue;
            }
            if (!group.isBlank() && !group.equalsIgnoreCase(stringValue(selector.get("groupName")))) {
                continue;
            }
            if (!category.isBlank() && !category.equalsIgnoreCase(stringValue(selector.get("category")))) {
                continue;
            }
            switch (action) {
                case "enable" -> selector.put("enabled", true);
                case "disable" -> selector.put("enabled", false);
                case "layout" -> selector.put("layout", layout.isBlank() ? selector.get("layout") : layout);
                case "permission" -> selector.put("permission", permission);
                default -> {
                    continue;
                }
            }
            upsertCloudSign(selector);
            changed++;
        }
        return Map.of("action", action, "changed", changed);
    }

    public Map<String, Object> cleanupStaleSelectors(boolean disableOnly) {
        int stale = 0;
        int changed = 0;
        List<String> groups = getAllServerGroups();
        createSelectorVersion("cleanup");
        for (Map<String, Object> selector : getCloudSigns()) {
            String serverName = stringValue(selector.get("serverName"));
            String groupName = stringValue(selector.get("groupName"));
            boolean groupMissing = !groupName.isBlank() && groups.stream().noneMatch(groupName::equalsIgnoreCase);
            boolean serverMissing = !serverName.isBlank() && Master.getInstance() != null
                    && !Master.getInstance().getRunningServers().containsKey(serverName);
            if (!groupMissing && !serverMissing) {
                continue;
            }
            stale++;
            if (disableOnly) {
                selector.put("enabled", false);
                selector.put("category", "Stale");
                upsertCloudSign(selector);
            } else {
                deleteCloudSign(stringValue(selector.get("id")));
            }
            changed++;
        }
        return Map.of("stale", stale, "changed", changed, "disableOnly", disableOnly);
    }

    public List<String> listSelectorVersions() {
        Path versionDir = Path.of("config", "selector-versions");
        if (!Files.isDirectory(versionDir)) {
            return List.of();
        }
        try (var stream = Files.list(versionDir)) {
            return stream
                    .filter(path -> path.getFileName().toString().endsWith(".yml"))
                    .map(path -> path.getFileName().toString())
                    .sorted((a, b) -> b.compareToIgnoreCase(a))
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    public boolean rollbackSelectorVersion(String version) {
        if (version == null || version.isBlank()) {
            return false;
        }
        Path versionDir = Path.of("config", "selector-versions").toAbsolutePath().normalize();
        Path selected = versionDir.resolve(Path.of(version).getFileName()).normalize();
        if (!selected.startsWith(versionDir) || !Files.exists(selected)) {
            return false;
        }
        try {
            createSelectorVersion("pre_rollback");
            signsData = YamlConfiguration.loadConfiguration(selected.toFile());
            signsData.save(signsFile);
            return true;
        } catch (IOException e) {
            ConsoleScreenManager.printToTerminal(ConsoleColors.RED + ConsoleColors.PREFIX +
                    ConsoleColors.getCurrentTime() + " Selector-Rollback fehlgeschlagen: " + e.getMessage());
            return false;
        }
    }

    public List<String> getSignAnimationFrames() {
        List<String> frames = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            String value = signLayoutData.getString("Layout.Animations.Frames" + i, "");
            if (value == null || value.isBlank()) {
                continue;
            }
            frames.add(value);
        }
        return frames.isEmpty() ? List.of("&7.") : frames;
    }

    public String getConfigVersion() {
        return masterConfigData.getString("CloudMaster.ConfigVersion", CONFIG_VERSION);
    }

    public String getDatabaseType() {
        return masterConfigData.getString("CloudMaster.Database.Type", "sqlite");
    }

    public String getSqliteJdbcUrl() {
        String file = masterConfigData.getString("CloudMaster.Database.SQLite.File", "data/cloud.db");
        return "jdbc:sqlite:" + file;
    }

    public String getDatabaseUrl() {
        return masterConfigData.getString("CloudMaster.Database.MySQL.Url", "jdbc:mysql://localhost:3306/cloud");
    }

    public String getDatabaseUsername() {
        return masterConfigData.getString("CloudMaster.Database.MySQL.User", "root");
    }

    public String getDatabasePassword() {
        return masterConfigData.getString("CloudMaster.Database.MySQL.Password", "");
    }

    public String getMongoUri() {
        return masterConfigData.getString("CloudMaster.Database.Mongo.Uri", "mongodb://localhost:27017");
    }

    public String getMongoDatabase() {
        return masterConfigData.getString("CloudMaster.Database.Mongo.Database", "cloud");
    }

    public String getAlertWebhookUrl() {
        return masterConfigData.getString("CloudMaster.Alerts.WebhookUrl", "");
    }

    public String getApiAllowedOrigins() {
        return masterConfigData.getString("CloudMaster.API.AllowedOrigins", "*");
    }

    public boolean isApiTlsEnabled() {
        return masterConfigData.getBoolean("CloudMaster.API.TLS.Enabled", false);
    }

    public String getApiTlsKeystorePath() {
        return masterConfigData.getString("CloudMaster.API.TLS.KeystorePath", "config/tls/keystore.p12");
    }

    public String getApiTlsKeystorePassword() {
        return masterConfigData.getString("CloudMaster.API.TLS.KeystorePassword", "");
    }

    public String getApiTlsKeystoreType() {
        return masterConfigData.getString("CloudMaster.API.TLS.KeystoreType", "PKCS12");
    }

    public boolean isTemplateTestingMode() {
        return masterConfigData.getBoolean("CloudMaster.Templates.TestingMode", false);
    }

    public boolean isTemplateAutoUpdateAfterRestart() {
        return masterConfigData.getBoolean("CloudMaster.Templates.AutoUpdateAfterRestart", true);
    }

    public double getMonitoringCpuWarning() {
        return masterConfigData.getDouble("CloudMaster.Monitoring.CPU.Warning", 90.0);
    }

    public double getMonitoringCpuCritical() {
        return masterConfigData.getDouble("CloudMaster.Monitoring.CPU.Critical", 97.0);
    }

    public double getMonitoringMemoryWarning() {
        return masterConfigData.getDouble("CloudMaster.Monitoring.Memory.Warning", 90.0);
    }

    public double getMonitoringMemoryCritical() {
        return masterConfigData.getDouble("CloudMaster.Monitoring.Memory.Critical", 96.0);
    }

    public double getMonitoringTpsWarning() {
        return masterConfigData.getDouble("CloudMaster.Monitoring.TPS.Warning", 18.0);
    }

    public double getMonitoringTpsCritical() {
        return masterConfigData.getDouble("CloudMaster.Monitoring.TPS.Critical", 15.0);
    }

    public int getMonitoringRequiredConsecutiveBreaches() {
        return masterConfigData.getInt("CloudMaster.Monitoring.RequiredConsecutiveBreaches", 2);
    }

    public long getMonitoringWarningCooldownMs() {
        return masterConfigData.getLong("CloudMaster.Monitoring.Cooldown.WarningMs", 60_000L);
    }

    public long getMonitoringCriticalCooldownMs() {
        return masterConfigData.getLong("CloudMaster.Monitoring.Cooldown.CriticalMs", 120_000L);
    }

    public long getMonitoringInfoCooldownMs() {
        return masterConfigData.getLong("CloudMaster.Monitoring.Cooldown.InfoMs", 300_000L);
    }

    public boolean isRecoveryQuarantineEnabled() {
        return masterConfigData.getBoolean("CloudMaster.Recovery.Quarantine.Enabled", true);
    }

    public int getRecoveryQuarantineFailureThreshold() {
        return masterConfigData.getInt("CloudMaster.Recovery.Quarantine.FailureThreshold", 3);
    }

    public boolean isRecoveryAutoRestartQuarantined() {
        return masterConfigData.getBoolean("CloudMaster.Recovery.Quarantine.AutoRestartQuarantined", false);
    }

    public long getRecoveryServerHeartbeatTimeoutMs() {
        return masterConfigData.getLong("CloudMaster.Recovery.ServerHeartbeatTimeoutMs", 30_000L);
    }

    public long getRecoveryServerStartingTimeoutMs() {
        return masterConfigData.getLong("CloudMaster.Recovery.ServerStartingTimeoutMs", 120_000L);
    }

    public long getRecoveryWrapperPongTimeoutMs() {
        return masterConfigData.getLong("CloudMaster.Recovery.WrapperPongTimeoutMs", 20_000L);
    }

    public int getRecoveryRestartPortRetryLimit() {
        return masterConfigData.getInt("CloudMaster.Recovery.RestartPortRetryLimit", 8);
    }

    public Integer getMaxPlayersForGroup(String groupName) {
        return resolveIntSetting(groupName, "MaxPlayers", 100);
    }

    public Integer getRamForGroup(String groupName) {
        return resolveIntSetting(groupName, "Ram", 1024);
    }

    public String getSoftwareForGroup(String groupName) {
        return resolveStringSetting(groupName, "Software", "");
    }

    public List<String> getJavaArgsForGroup(String groupName) {
        return resolveStringListSetting(groupName, "JavaArgs");
    }

    public List<String> getStartArgsForGroup(String groupName) {
        return resolveStringListSetting(groupName, "StartArgs");
    }

    public boolean isDynamicGroup(String groupName) {
        return serverGroupsData.getBoolean("ServerGroup." + groupName + ".Dynamic", false);
    }

    public int getMinServersForGroup(String groupName) {
        return resolveIntSetting(groupName, "MinServers", 1);
    }

    public int getMaxServersForGroup(String groupName) {
        return resolveIntSetting(groupName, "MaxServers", 5);
    }

    public boolean isAutoScalingEnabled(String groupName) {
        return resolveBooleanSetting(groupName, "AutoScaling", false);
    }

    public double getScaleUpThreshold(String groupName) {
        return resolveDoubleSetting(groupName, "ScaleUpThreshold", 0.75);
    }

    public double getScaleDownThreshold(String groupName) {
        return resolveDoubleSetting(groupName, "ScaleDownThreshold", 0.30);
    }

    public String getServerGroupPriority(String groupName) {
        return resolveStringSetting(groupName, "Priority", "NORMAL");
    }

    public boolean isMaintenanceMode(String groupName) {
        return resolveBooleanSetting(groupName, "Maintenance", false);
    }

    public List<String> getGroupWhitelist(String groupName) {
        return serverGroupsData.getStringList("ServerGroup." + groupName + ".Whitelist");
    }

    public List<String> getGroupTags(String groupName) {
        return serverGroupsData.getStringList("ServerGroup." + groupName + ".Tags");
    }

    public boolean createOrUpdateGroup(String groupName, String parentGroup) {
        String base = "ServerGroup." + groupName;
        serverGroupsData.set(base + ".Parent", parentGroup == null ? "" : parentGroup);
        serverGroupsData.set(base + ".Ram", 1024);
        serverGroupsData.set(base + ".MaxPlayers", 100);
        serverGroupsData.set(base + ".Software", "paper");
        serverGroupsData.set(base + ".JavaArgs", List.of());
        serverGroupsData.set(base + ".StartArgs", List.of("--nogui"));
        serverGroupsData.set(base + ".Dynamic", true);
        serverGroupsData.set(base + ".MinServers", 1);
        serverGroupsData.set(base + ".MaxServers", 3);
        serverGroupsData.set(base + ".Maintenance", false);
        serverGroupsData.set(base + ".AutoScaling", true);
        serverGroupsData.set(base + ".ScaleUpThreshold", 0.75);
        serverGroupsData.set(base + ".ScaleDownThreshold", 0.30);
        serverGroupsData.set(base + ".Priority", "NORMAL");
        serverGroupsData.set(base + ".Tags", List.of());
        serverGroupsData.set(base + ".Whitelist", List.of());
        return saveServerGroups();
    }

    public boolean deleteGroup(String groupName) {
        serverGroupsData.set("ServerGroup." + groupName, null);
        return saveServerGroups();
    }

    public boolean setGroupSetting(String groupName, String key, Object value) {
        serverGroupsData.set("ServerGroup." + groupName + "." + key, value);
        return saveServerGroups();
    }

    public boolean setGroupMaintenance(String groupName, boolean maintenance) {
        serverGroupsData.set("ServerGroup." + groupName + ".Maintenance", maintenance);
        return saveServerGroups();
    }

    public boolean setGroupWhitelist(String groupName, List<String> whitelist) {
        serverGroupsData.set("ServerGroup." + groupName + ".Whitelist", whitelist);
        return saveServerGroups();
    }

    // ========================================
    // Get all server groups
    // ========================================
    public List<String> getAllServerGroups() {
        List<String> groups = new ArrayList<>();

        if (serverGroupsData.contains("ServerGroup")) {
            for (String key : serverGroupsData.getConfigurationSection("ServerGroup").getKeys(false)) {
                groups.add(key);
            }
        }

        return groups;
    }

    private void validateAndSanitize() {
        boolean changed = false;
        boolean signLayoutChanged = ensureDefaultSelectorLayouts();

        changed |= clampInt(serverGroupsData, "Ports.FirstProxy", 1, 65535, 25577);
        changed |= clampInt(serverGroupsData, "Ports.FirstLobby", 1, 65535, 25565);
        changed |= clampInt(serverGroupsData, "Ports.DynamicStart", 1, 65535, 25566);
        changed |= clampInt(masterConfigData, "CloudMaster.Network.TcpPort", 1, 65535, 54555);
        changed |= clampInt(masterConfigData, "CloudMaster.Network.UdpPort", 1, 65535, 54777);

        changed |= clampDouble(masterConfigData, "CloudMaster.Monitoring.CPU.Warning", 10.0, 100.0, 90.0);
        changed |= clampDouble(masterConfigData, "CloudMaster.Monitoring.CPU.Critical", 10.0, 100.0, 97.0);
        changed |= clampDouble(masterConfigData, "CloudMaster.Monitoring.Memory.Warning", 10.0, 100.0, 90.0);
        changed |= clampDouble(masterConfigData, "CloudMaster.Monitoring.Memory.Critical", 10.0, 100.0, 96.0);
        changed |= clampDouble(masterConfigData, "CloudMaster.Monitoring.TPS.Warning", 1.0, 20.0, 18.0);
        changed |= clampDouble(masterConfigData, "CloudMaster.Monitoring.TPS.Critical", 1.0, 20.0, 15.0);
        changed |= clampInt(masterConfigData, "CloudMaster.Monitoring.RequiredConsecutiveBreaches", 1, 10, 2);
        changed |= clampInt(masterConfigData, "CloudMaster.Monitoring.Cooldown.WarningMs", 5000, 3600000, 60000);
        changed |= clampInt(masterConfigData, "CloudMaster.Monitoring.Cooldown.CriticalMs", 5000, 3600000, 120000);
        changed |= clampInt(masterConfigData, "CloudMaster.Monitoring.Cooldown.InfoMs", 5000, 3600000, 300000);
        changed |= clampInt(masterConfigData, "CloudMaster.Recovery.Quarantine.FailureThreshold", 1, 25, 3);
        changed |= clampInt(masterConfigData, "CloudMaster.Recovery.ServerHeartbeatTimeoutMs", 5000, 600000, 30000);
        changed |= clampInt(masterConfigData, "CloudMaster.Recovery.ServerStartingTimeoutMs", 15000, 900000, 120000);
        changed |= clampInt(masterConfigData, "CloudMaster.Recovery.WrapperPongTimeoutMs", 5000, 300000, 20000);
        changed |= clampInt(masterConfigData, "CloudMaster.Recovery.RestartPortRetryLimit", 1, 50, 8);

        if (serverGroupsData.contains("ServerGroup") && serverGroupsData.getConfigurationSection("ServerGroup") != null) {
            for (String group : serverGroupsData.getConfigurationSection("ServerGroup").getKeys(false)) {
                String base = "ServerGroup." + group + ".";
                changed |= clampInt(serverGroupsData, base + "Ram", 256, 65536, 1024);
                changed |= clampInt(serverGroupsData, base + "MaxPlayers", 1, 2000, 100);
                if (!serverGroupsData.contains(base + "Software")) {
                    serverGroupsData.set(base + "Software", group.equalsIgnoreCase("Proxy") ? "proxy" : "paper");
                    changed = true;
                }
                if (!serverGroupsData.contains(base + "JavaArgs")) {
                    serverGroupsData.set(base + "JavaArgs", List.of());
                    changed = true;
                }
                if (!serverGroupsData.contains(base + "StartArgs")) {
                    serverGroupsData.set(base + "StartArgs", group.equalsIgnoreCase("Proxy") ? List.of() : List.of("--nogui"));
                    changed = true;
                }
                changed |= clampInt(serverGroupsData, base + "MinServers", 0, 100, 1);
                changed |= clampInt(serverGroupsData, base + "MaxServers", 0, 100, 5);
                changed |= clampDouble(serverGroupsData, base + "ScaleUpThreshold", 0.10, 1.0, 0.75);
                changed |= clampDouble(serverGroupsData, base + "ScaleDownThreshold", 0.0, 0.9, 0.30);

                int min = serverGroupsData.getInt(base + "MinServers", 1);
                int max = serverGroupsData.getInt(base + "MaxServers", 5);
                if (max < min) {
                    serverGroupsData.set(base + "MaxServers", min);
                    changed = true;
                }
            }
        }

        if (changed) {
            try {
                serverGroupsData.save(serverGroupsFile);
                masterConfigData.save(masterConfigFile);
            } catch (IOException e) {
                ConsoleScreenManager.printToTerminal(ConsoleColors.RED + ConsoleColors.PREFIX +
                        ConsoleColors.getCurrentTime() + " Config-Validation konnte nicht gespeichert werden: " + e.getMessage());
            }
        }
        if (signLayoutChanged) {
            saveSignLayout();
        }
    }

    private boolean ensureDefaultSelectorLayouts() {
        boolean changed = false;
        changed |= setIfMissing(signLayoutData, "Layout.Signs.Npc.Line1", "&a{display_name}");
        changed |= setIfMissing(signLayoutData, "Layout.Signs.Npc.Line2", "&7{status} &8{animation}");
        changed |= setIfMissing(signLayoutData, "Layout.Signs.Npc.Line3", "&e{players} &7Spieler");
        changed |= setIfMissing(signLayoutData, "Layout.Signs.Npc.Line4", "&8Klicken zum Verbinden");
        changed |= setIfMissing(signLayoutData, "Layout.Signs.Mob.Line1", "&6{display_name}");
        changed |= setIfMissing(signLayoutData, "Layout.Signs.Mob.Line2", "&7{group}");
        changed |= setIfMissing(signLayoutData, "Layout.Signs.Mob.Line3", "&e{players} &8| &a{tps} TPS");
        changed |= setIfMissing(signLayoutData, "Layout.Signs.Mob.Line4", "&8Server Selector");
        return changed;
    }

    private void ensureConfigVersion() {
        String current = masterConfigData.getString("CloudMaster.ConfigVersion", "");
        boolean changed = false;
        if (!CONFIG_VERSION.equals(current)) {
            masterConfigData.set("CloudMaster.ConfigVersion", CONFIG_VERSION);
            changed = true;
        }
        if (!masterConfigData.contains("CloudMaster.Network.TcpPort")) {
            masterConfigData.set("CloudMaster.Network.TcpPort", 54555);
            changed = true;
        }
        if (!masterConfigData.contains("CloudMaster.Network.UdpPort")) {
            masterConfigData.set("CloudMaster.Network.UdpPort", 54777);
            changed = true;
        }
        if (!masterConfigData.contains("CloudMaster.Network.ConnectHost")) {
            masterConfigData.set("CloudMaster.Network.ConnectHost",
                    masterConfigData.getString("CloudMaster.Hostname", "127.0.0.1"));
            changed = true;
        }
        if (!masterConfigData.contains("CloudMaster.Network.GameHost")) {
            masterConfigData.set("CloudMaster.Network.GameHost", "auto");
            changed = true;
        }
        if (!masterConfigData.contains("CloudWrapper.RouteHost")) {
            masterConfigData.set("CloudWrapper.RouteHost", "auto");
            changed = true;
        }
        if (!masterConfigData.contains("CloudMaster.Network.ProxyBindHost")) {
            masterConfigData.set("CloudMaster.Network.ProxyBindHost", "0.0.0.0");
            changed = true;
        }
        if (!masterConfigData.contains("CloudMaster.Network.ProxyBindLocalAddress")) {
            masterConfigData.set("CloudMaster.Network.ProxyBindLocalAddress", false);
            changed = true;
        }
        if (!masterConfigData.contains("CloudMaster.Network.ProxyForceDefaultServer")) {
            masterConfigData.set("CloudMaster.Network.ProxyForceDefaultServer", true);
            changed = true;
        }
        if (!masterConfigData.contains("CloudMaster.Network.ProxyServerConnectTimeoutMs")) {
            masterConfigData.set("CloudMaster.Network.ProxyServerConnectTimeoutMs", 15000);
            changed = true;
        }
        if (!masterConfigData.contains("CloudMaster.Network.ProxyTimeoutMs")) {
            masterConfigData.set("CloudMaster.Network.ProxyTimeoutMs", 60000);
            changed = true;
        }
        if (!masterConfigData.contains("CloudMaster.Network.ProxyRemotePingTimeoutMs")) {
            masterConfigData.set("CloudMaster.Network.ProxyRemotePingTimeoutMs", 5000);
            changed = true;
        }
        if (!masterConfigData.contains("CloudMaster.Network.EnforceBackendBind")) {
            masterConfigData.set("CloudMaster.Network.EnforceBackendBind", true);
            changed = true;
        }
        if (!masterConfigData.contains("CloudMaster.Servers.StopTimeoutSeconds")) {
            masterConfigData.set("CloudMaster.Servers.StopTimeoutSeconds", 30);
            changed = true;
        }
        if (!masterConfigData.contains("CloudMaster.Network.BackendBindAddress")) {
            masterConfigData.set("CloudMaster.Network.BackendBindAddress", "auto");
            changed = true;
        }
        if (masterConfigData.getString("CloudMaster.Network.ForwardingSecret", "").isBlank()) {
            masterConfigData.set("CloudMaster.Network.ForwardingSecret",
                    java.util.UUID.randomUUID().toString().replace("-", ""));
            changed = true;
        }
        if (!masterConfigData.contains("CloudMaster.Permissions.Runtime.Enabled")) {
            masterConfigData.set("CloudMaster.Permissions.Runtime.Enabled", true);
            changed = true;
        }
        if (masterConfigData.getString("CloudMaster.Permissions.Runtime.Provider", "").isBlank()) {
            masterConfigData.set("CloudMaster.Permissions.Runtime.Provider", "cloud");
            changed = true;
        }
        if (!masterConfigData.contains("CloudMaster.Permissions.Runtime.EnforcePrefixSuffix")) {
            masterConfigData.set("CloudMaster.Permissions.Runtime.EnforcePrefixSuffix", true);
            changed = true;
        }
        if (masterConfigData.getString("CloudMaster.Permissions.Runtime.PermissionPrefixFilter", "").isBlank()) {
            masterConfigData.set("CloudMaster.Permissions.Runtime.PermissionPrefixFilter", "cloud.");
            changed = true;
        }
        if (!masterConfigData.contains("CloudMaster.Permissions.Runtime.CloudSyncCommand")) {
            masterConfigData.set("CloudMaster.Permissions.Runtime.CloudSyncCommand", "");
            changed = true;
        }
        if (masterConfigData.getString("CloudMaster.API.AdminKey", "").isBlank()) {
            masterConfigData.set("CloudMaster.API.AdminKey", java.util.UUID.randomUUID().toString());
            changed = true;
        }
        if (masterConfigData.getString("CloudMaster.API.DashboardKey", "").isBlank()) {
            masterConfigData.set("CloudMaster.API.DashboardKey", java.util.UUID.randomUUID().toString());
            changed = true;
        }
        if (!masterConfigData.contains("CloudMaster.API.OwnerKey")) {
            masterConfigData.set("CloudMaster.API.OwnerKey", "");
            changed = true;
        }
        if (!masterConfigData.contains("CloudMaster.API.OperatorKey")) {
            masterConfigData.set("CloudMaster.API.OperatorKey", "");
            changed = true;
        }
        if (!masterConfigData.contains("CloudMaster.Recovery.Quarantine.Enabled")) {
            masterConfigData.set("CloudMaster.Recovery.Quarantine.Enabled", true);
            changed = true;
        }
        if (!masterConfigData.contains("CloudMaster.Recovery.Quarantine.FailureThreshold")) {
            masterConfigData.set("CloudMaster.Recovery.Quarantine.FailureThreshold", 3);
            changed = true;
        }
        if (!masterConfigData.contains("CloudMaster.Recovery.Quarantine.AutoRestartQuarantined")) {
            masterConfigData.set("CloudMaster.Recovery.Quarantine.AutoRestartQuarantined", false);
            changed = true;
        }
        if (!masterConfigData.contains("CloudMaster.Recovery.ServerHeartbeatTimeoutMs")) {
            masterConfigData.set("CloudMaster.Recovery.ServerHeartbeatTimeoutMs", 30000);
            changed = true;
        }
        if (!masterConfigData.contains("CloudMaster.Recovery.ServerStartingTimeoutMs")) {
            masterConfigData.set("CloudMaster.Recovery.ServerStartingTimeoutMs", 120000);
            changed = true;
        }
        if (!masterConfigData.contains("CloudMaster.Recovery.WrapperPongTimeoutMs")) {
            masterConfigData.set("CloudMaster.Recovery.WrapperPongTimeoutMs", 20000);
            changed = true;
        }
        if (!masterConfigData.contains("CloudMaster.Recovery.RestartPortRetryLimit")) {
            masterConfigData.set("CloudMaster.Recovery.RestartPortRetryLimit", 8);
            changed = true;
        }
        if (changed) {
            try {
                masterConfigData.save(masterConfigFile);
            } catch (IOException ignored) {
            }
        }
    }

    private boolean clampInt(FileConfiguration config, String key, int min, int max, int defaultValue) {
        int value = config.getInt(key, defaultValue);
        int clamped = Math.max(min, Math.min(max, value));
        if (value != clamped || !config.contains(key)) {
            config.set(key, clamped);
            return true;
        }
        return false;
    }

    private boolean clampDouble(FileConfiguration config, String key, double min, double max, double defaultValue) {
        double value = config.getDouble(key, defaultValue);
        double clamped = Math.max(min, Math.min(max, value));
        if (Math.abs(value - clamped) > 0.0001 || !config.contains(key)) {
            config.set(key, clamped);
            return true;
        }
        return false;
    }

    private int resolveIntSetting(String groupName, String key, int defaultValue) {
        String current = groupName;
        for (int i = 0; i < 10; i++) {
            String path = "ServerGroup." + current + "." + key;
            if (serverGroupsData.contains(path)) {
                return serverGroupsData.getInt(path, defaultValue);
            }
            current = serverGroupsData.getString("ServerGroup." + current + ".Parent", "");
            if (current == null || current.isBlank()) break;
        }
        return defaultValue;
    }

    private double resolveDoubleSetting(String groupName, String key, double defaultValue) {
        String current = groupName;
        for (int i = 0; i < 10; i++) {
            String path = "ServerGroup." + current + "." + key;
            if (serverGroupsData.contains(path)) {
                return serverGroupsData.getDouble(path, defaultValue);
            }
            current = serverGroupsData.getString("ServerGroup." + current + ".Parent", "");
            if (current == null || current.isBlank()) break;
        }
        return defaultValue;
    }

    private boolean resolveBooleanSetting(String groupName, String key, boolean defaultValue) {
        String current = groupName;
        for (int i = 0; i < 10; i++) {
            String path = "ServerGroup." + current + "." + key;
            if (serverGroupsData.contains(path)) {
                return serverGroupsData.getBoolean(path, defaultValue);
            }
            current = serverGroupsData.getString("ServerGroup." + current + ".Parent", "");
            if (current == null || current.isBlank()) break;
        }
        return defaultValue;
    }

    private String resolveStringSetting(String groupName, String key, String defaultValue) {
        String current = groupName;
        for (int i = 0; i < 10; i++) {
            String path = "ServerGroup." + current + "." + key;
            if (serverGroupsData.contains(path)) {
                return serverGroupsData.getString(path, defaultValue);
            }
            current = serverGroupsData.getString("ServerGroup." + current + ".Parent", "");
            if (current == null || current.isBlank()) break;
        }
        return defaultValue;
    }

    private List<String> resolveStringListSetting(String groupName, String key) {
        String current = groupName;
        for (int i = 0; i < 10; i++) {
            String path = "ServerGroup." + current + "." + key;
            if (serverGroupsData.contains(path)) {
                return serverGroupsData.getStringList(path);
            }
            current = serverGroupsData.getString("ServerGroup." + current + ".Parent", "");
            if (current == null || current.isBlank()) break;
        }
        return List.of();
    }

    private boolean saveServerGroups() {
        try {
            serverGroupsData.save(serverGroupsFile);
            return true;
        } catch (IOException e) {
            ConsoleScreenManager.printToTerminal(ConsoleColors.RED + ConsoleColors.PREFIX +
                    ConsoleColors.getCurrentTime() + " Konnte ServerGroups.yml nicht speichern: " + e.getMessage());
            return false;
        }
    }

    private boolean saveSigns() {
        try {
            signsData.save(signsFile);
            return true;
        } catch (IOException e) {
            ConsoleScreenManager.printToTerminal(ConsoleColors.RED + ConsoleColors.PREFIX +
                    ConsoleColors.getCurrentTime() + " Signs.yml konnte nicht gespeichert werden: " + e.getMessage());
            return false;
        }
    }

    private boolean saveSignLayout() {
        try {
            signLayoutData.save(signLayoutFile);
            return true;
        } catch (IOException e) {
            ConsoleScreenManager.printToTerminal(ConsoleColors.RED + ConsoleColors.PREFIX +
                    ConsoleColors.getCurrentTime() + " SignLayout.yml konnte nicht gespeichert werden: " + e.getMessage());
            return false;
        }
    }

    private void createSelectorVersion(String reason) {
        try {
            Path versionDir = Path.of("config", "selector-versions");
            Files.createDirectories(versionDir);
            if (!Files.exists(signsFile.toPath())) {
                return;
            }
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"));
            String fileName = timestamp + "-" + safeName(reason) + ".yml";
            Files.copy(signsFile.toPath(), versionDir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
        }
    }

    private Map<String, Object> applySelectorTemplate(Map<String, Object> request) {
        Map<String, Object> merged = new LinkedHashMap<>();
        String templateName = defaultString(request.get("selectorTemplate"), defaultString(request.get("template"), ""));
        Map<String, Object> template = getSelectorTemplates().get(templateName);
        if (template != null) {
            merged.putAll(template);
            merged.put("selectorTemplate", templateName);
        }
        merged.putAll(request);
        return merged;
    }

    private Map<String, Object> selectorTemplate(String selectorType, String entityType, String layout,
                                                 String category, String permission, boolean queueOnFull,
                                                 boolean partyAware, String displayName) {
        Map<String, Object> template = new LinkedHashMap<>();
        template.put("selectorType", selectorType);
        template.put("entityType", entityType);
        template.put("layout", layout);
        template.put("category", category);
        template.put("permission", permission);
        template.put("queueOnFull", queueOnFull);
        template.put("partyAware", partyAware);
        template.put("displayName", displayName);
        template.put("clickAction", queueOnFull ? "QUEUE_OR_CONNECT" : "CONNECT");
        template.put("fallbackGroup", "");
        template.put("region", "GLOBAL");
        template.put("glowing", false);
        return template;
    }

    private boolean setIfMissing(FileConfiguration config, String key, Object value) {
        if (config.contains(key)) {
            return false;
        }
        config.set(key, value);
        return true;
    }

    private String sanitizeYamlKey(String value) {
        String safe = value == null ? "" : value.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
        return safe.isBlank() ? "sign-" + UUID.randomUUID().toString().substring(0, 8) : safe;
    }

    private String generateSelectorId(String selectorType, Map<String, Object> request) {
        String target = defaultString(request.get("serverName"), defaultString(request.get("server"),
                defaultString(request.get("groupName"), defaultString(request.get("group"), ""))));
        String prefix = normalizeSelectorType(selectorType).toLowerCase(Locale.ROOT);
        if (!target.isBlank()) {
            prefix += "-" + sanitizeYamlKey(target).toLowerCase(Locale.ROOT);
        }
        return prefix + "-" + Long.toUnsignedString(System.currentTimeMillis(), 36)
                + "-" + UUID.randomUUID().toString().substring(0, 4);
    }

    private String defaultEntityType(String selectorType) {
        if ("MOB".equalsIgnoreCase(selectorType)) {
            return "ZOMBIE";
        }
        return "VILLAGER";
    }

    private String normalizeEntityType(String entityType, String selectorType) {
        String normalized = defaultString(entityType, defaultEntityType(selectorType))
                .trim()
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT);
        if ("NPC".equalsIgnoreCase(selectorType)) {
            return normalized.isBlank() ? "VILLAGER" : normalized;
        }
        if ("MOB".equalsIgnoreCase(selectorType)) {
            return normalized.isBlank() || "PLAYER".equals(normalized) ? "ZOMBIE" : normalized;
        }
        return normalized.isBlank() ? "VILLAGER" : normalized;
    }

    private String defaultLayout(String selectorType) {
        if ("NPC".equalsIgnoreCase(selectorType)) {
            return "Npc";
        }
        if ("MOB".equalsIgnoreCase(selectorType)) {
            return "Mob";
        }
        return "Default";
    }

    private String normalizeSelectorLayout(String layout, String selectorType) {
        String safe = defaultString(layout, defaultLayout(selectorType));
        Map<String, List<String>> layouts = getSignLayouts();
        if (layouts.containsKey(safe)) {
            return safe;
        }
        return layouts.containsKey(defaultLayout(selectorType)) ? defaultLayout(selectorType) : "Default";
    }

    private String normalizeClickAction(String action) {
        String normalized = defaultString(action, "CONNECT").trim().replace('-', '_').toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "CONNECT", "QUEUE", "QUEUE_OR_CONNECT", "FALLBACK", "DISABLED", "PREVIEW" -> normalized;
            default -> "CONNECT";
        };
    }

    private List<String> normalizeHologramLines(List<String> lines, String selectorType, String displayName) {
        if (lines != null && !lines.isEmpty()) {
            return lines.stream().map(line -> line == null ? "" : line).toList();
        }
        if ("SIGN".equalsIgnoreCase(selectorType)) {
            return List.of();
        }
        String title = displayName == null || displayName.isBlank() ? "&a{server}" : displayName;
        return List.of(
                title,
                "&7Status: {health}",
                "&e{players} Spieler",
                "&8Klicken zum Verbinden"
        );
    }

    private String defaultString(Object value, String fallback) {
        String text = stringValue(value);
        return text.isBlank() ? fallback : text;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private int asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private long asLong(Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return value == null ? fallback : Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private List<String> asStringList(Object value) {
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    result.add(String.valueOf(item));
                }
            }
            return result;
        }
        String single = stringValue(value);
        return single.isBlank() ? List.of() : List.of(single);
    }

    private boolean asBoolean(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return fallback;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private double asDouble(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value == null ? fallback : Double.parseDouble(String.valueOf(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String normalizeSelectorType(String selectorType) {
        if ("NPC".equalsIgnoreCase(selectorType) || "MOB".equalsIgnoreCase(selectorType)) {
            return selectorType.toUpperCase();
        }
        return "SIGN";
    }

    private void copyIfExists(Path src, Path dest) throws IOException {
        if (Files.exists(src)) {
            Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String safeName(String trigger) {
        if (trigger == null || trigger.isBlank()) {
            return "manual";
        }
        return trigger.replaceAll("[^a-zA-Z0-9-_]", "_");
    }

    // ========================================
    // Getters for file access
    // ========================================
    public File getMasterConfigFile() { return masterConfigFile; }
    public FileConfiguration getMasterConfigData() { return masterConfigData; }
    public File getServerGroupsFile() { return serverGroupsFile; }
    public FileConfiguration getServerGroupsData() { return serverGroupsData; }
    public File getSignLayoutFile() { return signLayoutFile; }
    public FileConfiguration getSignLayoutData() { return signLayoutData; }
    public File getSignsFile() { return signsFile; }
    public FileConfiguration getSignsData() { return signsData; }
    public File getClusterConfigFile() { return clusterConfigFile; }
    public FileConfiguration getClusterConfigData() { return clusterConfigData; }
}
