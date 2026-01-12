/**
 * Erstellt von Gamer_Kidd_LP | kallifabio
 * am 09.01.2026 um 21:26
 * Projektname: CloudSystemTest
 * Packagename: de.kallifabio.cloud.master
 */

package de.kallifabio.cloud.master;

public class ServerInstance {

    // Basic Info
    public String serverName;
    public String groupName;
    public String wrapperId;
    public int allocatedRam;
    public int port;

    // Status
    public String status = "STARTING";
    public long startTime;
    public long lastUpdate;

    // Player Info
    public int playerCount = 0;
    public int maxPlayers = 100;

    // Performance Metrics
    public double tps = 20.0;
    public long memoryUsage = 0;
    public double cpuUsage = 0.0;

    // Flags
    public boolean isCritical = false;
    public boolean isTemplate = false;

    public ServerInstance(String serverName, String groupName, String wrapperId, int allocatedRam) {
        this.serverName = serverName;
        this.groupName = groupName;
        this.wrapperId = wrapperId;
        this.allocatedRam = allocatedRam;
        this.port = -1;
        this.startTime = System.currentTimeMillis();
        this.lastUpdate = System.currentTimeMillis();
    }

    // Convenience Methods
    public boolean isOnline() {
        return "ONLINE".equalsIgnoreCase(status);
    }

    public boolean isHealthy() {
        // Server is healthy if:
        // 1. Status is ONLINE
        // 2. Last update was less than 30 seconds ago
        // 3. TPS is above 15 (for game servers)

        if (!isOnline()) {
            return false;
        }

        if (System.currentTimeMillis() - lastUpdate > 30000) {
            return false;
        }

        if (!groupName.equalsIgnoreCase("Proxy") && tps < 15.0) {
            return false;
        }

        return true;
    }

    public long getUptime() {
        return System.currentTimeMillis() - startTime;
    }

    public int getTimeSinceLastUpdate() {
        return (int) ((System.currentTimeMillis() - lastUpdate) / 1000);
    }

    public boolean isFull() {
        return playerCount >= maxPlayers;
    }

    public int getFreeSlots() {
        return Math.max(0, maxPlayers - playerCount);
    }

    public double getPlayerFillPercentage() {
        if (maxPlayers == 0) return 0.0;
        return ((double) playerCount / maxPlayers) * 100.0;
    }

    public double getMemoryUsagePercentage() {
        if (allocatedRam == 0) return 0.0;
        return ((double) memoryUsage / (allocatedRam * 1024 * 1024)) * 100.0;
    }

    public void updateMetrics(int playerCount, int maxPlayers, double tps, long memoryUsage) {
        this.playerCount = playerCount;
        this.maxPlayers = maxPlayers;
        this.tps = tps;
        this.memoryUsage = memoryUsage;
        this.lastUpdate = System.currentTimeMillis();
    }

    public void updateStatus(String newStatus) {
        this.status = newStatus;
        this.lastUpdate = System.currentTimeMillis();
    }

    @Override
    public String toString() {
        return String.format("ServerInstance{name='%s', group='%s', status='%s', players=%d/%d, tps=%.1f, port=%d}",
                serverName, groupName, status, playerCount, maxPlayers, tps, port);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServerInstance that = (ServerInstance) o;
        return serverName.equals(that.serverName);
    }

    @Override
    public int hashCode() {
        return serverName.hashCode();
    }
}
