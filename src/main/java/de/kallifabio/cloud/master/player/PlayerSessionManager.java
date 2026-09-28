package de.kallifabio.cloud.master.player;

import de.kallifabio.cloud.data.CloudDataStore;
import de.kallifabio.cloud.data.PlayerData;
import de.kallifabio.cloud.libs.logging.CentralLogger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerSessionManager {

    private final CloudDataStore dataStore;
    private final Map<String, String> currentServerByPlayer = new ConcurrentHashMap<>();
    private final Map<String, Long> lastSeenByPlayer = new ConcurrentHashMap<>();

    private static final long DUPLICATE_TIMEOUT_MS = 15_000;
    private static final long SESSION_TTL_MS = 6 * 60 * 60 * 1000L;

    public PlayerSessionManager(CloudDataStore dataStore) {
        this.dataStore = dataStore;
    }

    public boolean isDuplicateConnection(String playerUuid) {
        String uuid = normalizeUuid(playerUuid);
        if (uuid == null) {
            return false;
        }
        Long seen = lastSeenByPlayer.get(uuid);
        if (seen == null) {
            return false;
        }
        return currentServerByPlayer.containsKey(uuid) && System.currentTimeMillis() - seen < DUPLICATE_TIMEOUT_MS;
    }

    public void touch(String playerUuid) {
        String uuid = normalizeUuid(playerUuid);
        if (uuid != null) {
            lastSeenByPlayer.put(uuid, System.currentTimeMillis());
        }
    }

    public void assignServer(String playerUuid, String serverName) {
        String uuid = normalizeUuid(playerUuid);
        if (uuid == null) {
            return;
        }
        if (serverName == null || serverName.isBlank()) {
            clearServer(uuid);
            return;
        }
        currentServerByPlayer.put(uuid, serverName.trim());
        touch(uuid);

        if (dataStore == null) {
            return;
        }
        try {
            PlayerData data = dataStore.getPlayerData(uuid);
            data.lastServer = serverName.trim();
            data.lastSeen = System.currentTimeMillis();
            dataStore.savePlayerData(data);
        } catch (Exception e) {
            CentralLogger.warn("PlayerSession", "Could not persist player session for " + uuid + ": " + e.getMessage());
        }
    }

    public String getCurrentServer(String playerUuid) {
        String uuid = normalizeUuid(playerUuid);
        return uuid == null ? null : currentServerByPlayer.get(uuid);
    }

    public String getLastServer(String playerUuid) {
        String uuid = normalizeUuid(playerUuid);
        if (uuid == null || dataStore == null) {
            return null;
        }
        try {
            PlayerData data = dataStore.getPlayerData(uuid);
            return data.lastServer;
        } catch (Exception e) {
            CentralLogger.warn("PlayerSession", "Could not load last server for " + uuid + ": " + e.getMessage());
            return null;
        }
    }

    public void clearServer(String playerUuid) {
        String uuid = normalizeUuid(playerUuid);
        if (uuid != null) {
            currentServerByPlayer.remove(uuid);
            touch(uuid);
        }
    }

    public int clearServerAssignments(String serverName) {
        if (serverName == null || serverName.isBlank()) {
            return 0;
        }
        int removed = 0;
        for (Map.Entry<String, String> entry : currentServerByPlayer.entrySet()) {
            if (serverName.equalsIgnoreCase(entry.getValue()) && currentServerByPlayer.remove(entry.getKey(), entry.getValue())) {
                touch(entry.getKey());
                removed++;
            }
        }
        return removed;
    }

    public int cleanupExpiredSessions() {
        long cutoff = System.currentTimeMillis() - SESSION_TTL_MS;
        int removed = 0;
        for (Map.Entry<String, Long> entry : lastSeenByPlayer.entrySet()) {
            if (entry.getValue() < cutoff) {
                lastSeenByPlayer.remove(entry.getKey(), entry.getValue());
                currentServerByPlayer.remove(entry.getKey());
                removed++;
            }
        }
        return removed;
    }

    public int getTrackedOnlineCount() {
        return currentServerByPlayer.size();
    }

    public int getTrackedPlayerCount(String serverName) {
        if (serverName == null || serverName.isBlank()) {
            return 0;
        }
        int count = 0;
        for (String currentServer : currentServerByPlayer.values()) {
            if (serverName.equalsIgnoreCase(currentServer)) {
                count++;
            }
        }
        return count;
    }

    public Map<String, Integer> getServerPlayerCountsSnapshot() {
        Map<String, Integer> counts = new ConcurrentHashMap<>();
        currentServerByPlayer.forEach((playerUuid, serverName) -> {
            if (serverName != null && !serverName.isBlank()) {
                counts.merge(serverName, 1, Integer::sum);
            }
        });
        return counts;
    }

    public Map<String, String> getPlayerServerMapSnapshot() {
        return new ConcurrentHashMap<>(currentServerByPlayer);
    }

    private String normalizeUuid(String playerUuid) {
        if (playerUuid == null || playerUuid.isBlank()) {
            return null;
        }
        return playerUuid.trim().toLowerCase();
    }
}
