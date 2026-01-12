/**
 * Erstellt von Gamer_Kidd_LP | kallifabio
 * am 03.10.2024 um 18:22
 * Projektname: CloudSystemTest
 * Packagename: de.kallifabio.cloudsystem
 */

package de.kallifabio.cloud.config;

import de.kallifabio.cloud.master.Master;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;

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
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void createDefaultServergroupConfig() {
        try {
            serverGroupsFile.getParentFile().mkdirs();
            serverGroupsFile.createNewFile();

            // Lobby Configuration
            serverGroupsData.set("ServerGroup.Lobby.Ram", "2048");
            serverGroupsData.set("ServerGroup.Lobby.MaxPlayers", "200");
            serverGroupsData.set("ServerGroup.Lobby.Maintenance", "false");
            serverGroupsData.set("ServerGroup.Lobby.Dynamic", "true");
            serverGroupsData.set("ServerGroup.Lobby.MinServerOnline", "1");
            serverGroupsData.set("ServerGroup.Lobby.MaxServerOnline", "10");
            serverGroupsData.set("ServerGroup.Lobby.AutoScaling", "true");
            serverGroupsData.set("ServerGroup.Lobby.ScaleUpThreshold", "0.75");
            serverGroupsData.set("ServerGroup.Lobby.ScaleDownThreshold", "0.30");
            serverGroupsData.set("ServerGroup.Lobby.Priority", "HIGH");

            // Proxy Configuration
            serverGroupsData.set("ServerGroup.Proxy.Ram", "4096");
            serverGroupsData.set("ServerGroup.Proxy.MaxPlayers", "500");
            serverGroupsData.set("ServerGroup.Proxy.Maintenance", "false");
            serverGroupsData.set("ServerGroup.Proxy.MinProxyOnline", "1");
            serverGroupsData.set("ServerGroup.Proxy.MaxProxyOnline", "3");
            serverGroupsData.set("ServerGroup.Proxy.AutoScaling", "true");
            serverGroupsData.set("ServerGroup.Proxy.ScaleUpThreshold", "0.85");
            serverGroupsData.set("ServerGroup.Proxy.ScaleDownThreshold", "0.40");
            serverGroupsData.set("ServerGroup.Proxy.Priority", "CRITICAL");

            serverGroupsData.save(serverGroupsFile);
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
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void createDefaultSignsConfig() {
        try {
            signsFile.getParentFile().mkdirs();
            signsFile.createNewFile();
            signsData.save(signsFile);
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
            clusterConfigData.set("Cluster.Discovery.Method", "STATIC"); // STATIC, MULTICAST, CONSUL, ETCD
            clusterConfigData.set("Cluster.Discovery.Peers", new String[]{}); // e.g., ["192.168.1.100:9000", "192.168.1.101:9000"]

            // Split-Brain Prevention
            clusterConfigData.set("Cluster.SplitBrain.PreventionEnabled", true);
            clusterConfigData.set("Cluster.SplitBrain.QuorumSize", 2);

            clusterConfigData.save(clusterConfigFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

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
        String key = "ServerGroup." + groupName + ".MaxPlayers";
        String value = getServergroup(key);

        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
        }
        return 100; // Default
    }

    public Integer getRamForGroup(String groupName) {
        String key = "ServerGroup." + groupName + ".Ram";
        String value = getServergroup(key);

        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
        }
        return 1024; // Default
    }

    public boolean isAutoScalingEnabled(String groupName) {
        String key = "ServerGroup." + groupName + ".AutoScaling";
        return "true".equalsIgnoreCase(getServergroup(key));
    }

    public double getScaleUpThreshold(String groupName) {
        String key = "ServerGroup." + groupName + ".ScaleUpThreshold";
        String value = getServergroup(key);
        try {
            return value != null ? Double.parseDouble(value) : 0.75;
        } catch (NumberFormatException e) {
            return 0.75;
        }
    }

    public double getScaleDownThreshold(String groupName) {
        String key = "ServerGroup." + groupName + ".ScaleDownThreshold";
        String value = getServergroup(key);
        try {
            return value != null ? Double.parseDouble(value) : 0.30;
        } catch (NumberFormatException e) {
            return 0.30;
        }
    }

    // Getters
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
