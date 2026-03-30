package de.kallifabio.cloud.master.player;

import de.kallifabio.cloud.data.CloudDataStore;
import de.kallifabio.cloud.data.PlayerData;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerSessionManager {

    private final CloudDataStore dataStore;
    private final Map<String, String> currentServerByPlayer = new ConcurrentHashMap<>();
    private final Map<String, Long> lastSeenByPlayer = new ConcurrentHashMap<>();

    private static final long DUPLICATE_TIMEOUT_MS = 15_000;

    public PlayerSessionManager(CloudDataStore dataStore) {
        this.dataStore = dataStore;
    }

    public boolean isDuplicateConnection(String playerUuid) {
        Long seen = lastSeenByPlayer.get(playerUuid);
        if (seen == null) {
            return false;
        }
        return System.currentTimeMillis() - seen < DUPLICATE_TIMEOUT_MS;
    }

    public void touch(String playerUuid) {
        lastSeenByPlayer.put(playerUuid, System.currentTimeMillis());
    }

    public void assignServer(String playerUuid, String serverName) {
        currentServerByPlayer.put(playerUuid, serverName);
        touch(playerUuid);

        PlayerData data = dataStore.getPlayerData(playerUuid);
        data.lastServer = serverName;
        data.lastSeen = System.currentTimeMillis();
        dataStore.savePlayerData(data);
    }

    public String getCurrentServer(String playerUuid) {
        return currentServerByPlayer.get(playerUuid);
    }

    public String getLastServer(String playerUuid) {
        PlayerData data = dataStore.getPlayerData(playerUuid);
        return data.lastServer;
    }

    public void clearServer(String playerUuid) {
        currentServerByPlayer.remove(playerUuid);
        touch(playerUuid);
    }

    public Map<String, String> getPlayerServerMapSnapshot() {
        return new ConcurrentHashMap<>(currentServerByPlayer);
    }
}
