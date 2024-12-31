/**
 * Erstellt von Gamer_Kidd_LP | kallifabio
 * am 03.10.2024 um 18:22
 * Projektname: CloudSystemTest
 * Packagename: de.kallifabio.cloudsystem
 */

package de.kallifabio.cloudsystem.managers;

import de.kallifabio.cloudsystem.master.Master;
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

    public ConfigManager() {
        loadMasterConfig();
        loadServergroupConfig();
        loadSignLayoutConfig();
        loadSignsConfig();
    }

    public void loadMasterConfig() {
        try {
            if (!masterConfigFile.exists()) {
                createDefaultMasterConfig();
            }
            masterConfigData.load(masterConfigFile);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InvalidConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    public void loadServergroupConfig() {
        try {
            if (!serverGroupsFile.exists()) {
                createDefaultServergroupConfig();
            }
            serverGroupsData.load(serverGroupsFile);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InvalidConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    public void loadSignLayoutConfig() {
        try {
            if (!signLayoutFile.exists()) {
                createDefaultSignLayoutConfig();
            }
            signLayoutData.load(signLayoutFile);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InvalidConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    public void loadSignsConfig() {
        try {
            if (!signsFile.exists()) {
                createDefaultSignsConfig();
            }
            signsData.load(signsFile);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InvalidConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    private void createDefaultMasterConfig() {
        try {
            masterConfigFile.getParentFile().mkdirs();
            masterConfigFile.createNewFile();

            masterConfigData.set("CloudMaster.Max_Ram", "16384");
            masterConfigData.set("CloudMaster.Default_Ram", "16384");
            masterConfigData.set("CloudMaster.Hostname", Master.getInstance().getMasterHost());
            masterConfigData.set("CloudMaster.Port", Master.getInstance().getMasterPort().toString());

            masterConfigData.save(masterConfigFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void createDefaultServergroupConfig() {
        try {
            serverGroupsFile.getParentFile().mkdirs();
            serverGroupsFile.createNewFile();

            serverGroupsData.set("ServerGroup.Lobby.Ram", "1024");
            serverGroupsData.set("ServerGroup.Lobby.MaxPlayers", "200");
            serverGroupsData.set("ServerGroup.Lobby.Wartung", "false");
            serverGroupsData.set("ServerGroup.Lobby.Dynamic", "true");
            serverGroupsData.set("ServerGroup.Lobby.MinServerOnline", "1");
            serverGroupsData.set("ServerGroup.Lobby.MaxServerOnline", "1");

            serverGroupsData.set("ServerGroup.Proxy.Ram", "4096");
            serverGroupsData.set("ServerGroup.Proxy.MaxPlayers", "200");
            serverGroupsData.set("ServerGroup.Proxy.Wartung", "false");
            serverGroupsData.set("ServerGroup.Lobby.MinProxyOnline", "1");
            serverGroupsData.set("ServerGroup.Lobby.MaxProxyOnline", "1");

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

    public String getMaster(String key) {
        return masterConfigData.getString(key);
    }

    public String getServergroup(String key) {
        return serverGroupsData.getString(key);
    }

    /*public void setMaster(String key, String value) {
        masterConfig.setProperty(key, value);
        try {
            masterConfig.store(new FileOutputStream(masterConfigPath), null);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }*/

    /*public void setServergroup(String key, String value) {
        servergroupConfig.setProperty(key, value);
        try {
            servergroupConfig.store(new FileOutputStream(servergroupConfigPath), null);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }*/

    public Integer getMaxPlayersForGroup(String groupName) {
        String key = "ServerGroup." + groupName + ".MaxPlayers";  // Erstelle den Schlüssel für die gewünschte Gruppe
        String value = getServergroup(key);

        if (value != null) {
            try {
                return Integer.parseInt(value);  // Wert in Integer umwandeln
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
        }
        return -1;  // Rückgabe von -1, falls der Wert nicht gefunden wurde
    }

    public File getMasterConfigFile() {
        return masterConfigFile;
    }

    public FileConfiguration getMasterConfigData() {
        return masterConfigData;
    }

    public File getServerGroupsFile() {
        return serverGroupsFile;
    }

    public FileConfiguration getServerGroupsData() {
        return serverGroupsData;
    }

    public File getSignLayoutFile() {
        return signLayoutFile;
    }

    public FileConfiguration getSignLayoutData() {
        return signLayoutData;
    }

    public File getSignsFile() {
        return signsFile;
    }

    public FileConfiguration getSignsData() {
        return signsData;
    }
}
