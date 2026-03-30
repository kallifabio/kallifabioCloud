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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ConfigManager {

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

    private void createDefaultMasterConfig() {
        try {
            masterConfigFile.getParentFile().mkdirs();
            masterConfigFile.createNewFile();

            masterConfigData.set("CloudMaster.Max_Ram", "16384");
            masterConfigData.set("CloudMaster.Default_Ram", "16384");
            masterConfigData.set("CloudMaster.Hostname", Master.getInstance() != null ? Master.getInstance().getMasterHost() : "localhost");
            masterConfigData.set("CloudMaster.Port", Master.getInstance() != null ? Master.getInstance().getMasterPort().toString() : "9000");

            // API Configuration
            masterConfigData.set("CloudMaster.API.Enabled", true);
            masterConfigData.set("CloudMaster.API.Port", 8080);
            masterConfigData.set("CloudMaster.API.AllowedOrigins", "*");

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

    public Integer getMaxPlayersForGroup(String groupName) {
        return serverGroupsData.getInt("ServerGroup." + groupName + ".MaxPlayers", 100);
    }

    public Integer getRamForGroup(String groupName) {
        return serverGroupsData.getInt("ServerGroup." + groupName + ".Ram", 1024);
    }

    public boolean isDynamicGroup(String groupName) {
        return serverGroupsData.getBoolean("ServerGroup." + groupName + ".Dynamic", false);
    }

    public int getMinServersForGroup(String groupName) {
        return serverGroupsData.getInt("ServerGroup." + groupName + ".MinServers", 1);
    }

    public int getMaxServersForGroup(String groupName) {
        return serverGroupsData.getInt("ServerGroup." + groupName + ".MaxServers", 5);
    }

    public boolean isAutoScalingEnabled(String groupName) {
        return serverGroupsData.getBoolean("ServerGroup." + groupName + ".AutoScaling", false);
    }

    public double getScaleUpThreshold(String groupName) {
        return serverGroupsData.getDouble("ServerGroup." + groupName + ".ScaleUpThreshold", 0.75);
    }

    public double getScaleDownThreshold(String groupName) {
        return serverGroupsData.getDouble("ServerGroup." + groupName + ".ScaleDownThreshold", 0.30);
    }

    public String getServerGroupPriority(String groupName) {
        return serverGroupsData.getString("ServerGroup." + groupName + ".Priority", "NORMAL");
    }

    public boolean isMaintenanceMode(String groupName) {
        return serverGroupsData.getBoolean("ServerGroup." + groupName + ".Maintenance", false);
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
