/**
 * Erstellt von Gamer_Kidd_LP | kallifabio
 * am 03.10.2024 um 18:22
 * Projektname: CloudSystemTest
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
import java.util.List;

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

            // API Configuration
            masterConfigData.set("CloudMaster.API.Enabled", true);
            masterConfigData.set("CloudMaster.API.Port", 8081);
            masterConfigData.set("CloudMaster.API.AllowedOrigins", "*");
            masterConfigData.set("CloudMaster.API.AdminKey", java.util.UUID.randomUUID().toString());
            masterConfigData.set("CloudMaster.API.DashboardKey", java.util.UUID.randomUUID().toString());
            masterConfigData.set("CloudMaster.API.TLS.Enabled", false);
            masterConfigData.set("CloudMaster.API.TLS.KeystorePath", "config/tls/keystore.p12");
            masterConfigData.set("CloudMaster.API.TLS.KeystorePassword", "");
            masterConfigData.set("CloudMaster.API.TLS.KeystoreType", "PKCS12");
            masterConfigData.set("CloudMaster.Network.ProxyOnlineMode", true);
            masterConfigData.set("CloudMaster.Network.ProxyBindHost", "0.0.0.0");
            masterConfigData.set("CloudMaster.Network.EnforceBackendBind", true);
            masterConfigData.set("CloudMaster.Network.BackendBindAddress", "127.0.0.1");
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

    public Integer getMaxPlayersForGroup(String groupName) {
        return resolveIntSetting(groupName, "MaxPlayers", 100);
    }

    public Integer getRamForGroup(String groupName) {
        return resolveIntSetting(groupName, "Ram", 1024);
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

        if (serverGroupsData.contains("ServerGroup") && serverGroupsData.getConfigurationSection("ServerGroup") != null) {
            for (String group : serverGroupsData.getConfigurationSection("ServerGroup").getKeys(false)) {
                String base = "ServerGroup." + group + ".";
                changed |= clampInt(serverGroupsData, base + "Ram", 256, 65536, 1024);
                changed |= clampInt(serverGroupsData, base + "MaxPlayers", 1, 2000, 100);
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
        if (!masterConfigData.contains("CloudMaster.Network.ProxyBindHost")) {
            masterConfigData.set("CloudMaster.Network.ProxyBindHost", "0.0.0.0");
            changed = true;
        }
        if (!masterConfigData.contains("CloudMaster.Network.EnforceBackendBind")) {
            masterConfigData.set("CloudMaster.Network.EnforceBackendBind", true);
            changed = true;
        }
        if (!masterConfigData.contains("CloudMaster.Network.BackendBindAddress")) {
            masterConfigData.set("CloudMaster.Network.BackendBindAddress", "127.0.0.1");
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
