/**
 * Erstellt von Gamer_Kidd_LP | kallifabio
 * am 09.01.2026 um 21:34
 * Projektname: CloudSystemTest
 * Packagename: de.kallifabio.cloud.master.cluster
 */

package de.kallifabio.cloud.master.cluster;

// Cluster Node representation
public class ClusterNode {

    // Basic Info
    public final String masterId;
    public final String hostname;
    public final int port;

    // Status
    public boolean isPrimary;
    public long startTime;
    public long lastHeartbeat;

    // Cluster Metrics
    public int connectedWrappers;
    public int runningServers;
    public long uptime;

    // Health
    public boolean healthy;

    // Election
    public int electionTerm;

    public ClusterNode(String masterId, String hostname, int port, boolean isPrimary, long startTime) {
        this.masterId = masterId;
        this.hostname = hostname;
        this.port = port;
        this.isPrimary = isPrimary;
        this.startTime = startTime;
        this.lastHeartbeat = System.currentTimeMillis();
        this.healthy = true;
        this.electionTerm = 0;
        this.connectedWrappers = 0;
        this.runningServers = 0;
    }

    public ClusterNode(String masterId, String hostname, int port) {
        this(masterId, hostname, port, false, System.currentTimeMillis());
    }

    public String getMasterId() {
        return masterId;
    }

    public String getHostname() {
        return hostname;
    }

    public int getPort() {
        return port;
    }

    public boolean isPrimary() {
        return isPrimary;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getLastHeartbeat() {
        return lastHeartbeat;
    }

    public int getConnectedWrappers() {
        return connectedWrappers;
    }

    public int getRunningServers() {
        return runningServers;
    }

    public long getUptime() {
        return System.currentTimeMillis() - startTime;
    }

    public boolean isHealthy() {
        // Node is healthy if last heartbeat was less than 30 seconds ago
        return System.currentTimeMillis() - lastHeartbeat < 30000;
    }

    public void setPrimary(boolean primary) {
        this.isPrimary = primary;
    }

    public void updateHeartbeat() {
        this.lastHeartbeat = System.currentTimeMillis();
        this.healthy = true;
    }

    public void updateHeartbeat(int connectedWrappers, int runningServers) {
        this.lastHeartbeat = System.currentTimeMillis();
        this.connectedWrappers = connectedWrappers;
        this.runningServers = runningServers;
        this.uptime = System.currentTimeMillis() - startTime;
        this.healthy = true;
    }

    public void markUnhealthy() {
        this.healthy = false;
    }

    public long getTimeSinceLastHeartbeat() {
        return System.currentTimeMillis() - lastHeartbeat;
    }

    @Override
    public String toString() {
        return String.format("ClusterNode{id='%s', hostname='%s:%d', primary=%s, wrappers=%d, servers=%d, healthy=%s}",
                masterId, hostname, port, isPrimary, connectedWrappers, runningServers, healthy);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClusterNode that = (ClusterNode) o;
        return masterId.equals(that.masterId);
    }

    @Override
    public int hashCode() {
        return masterId.hashCode();
    }
}
