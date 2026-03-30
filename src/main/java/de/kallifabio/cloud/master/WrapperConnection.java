/**
 * Erstellt von Gamer_Kidd_LP | kallifabio
 * am 09.01.2026 um 21:28
 * Projektname: CloudSystemTest
 * Packagename: de.kallifabio.cloud.master
 */

package de.kallifabio.cloud.master;

import com.esotericsoftware.kryonet.Connection;

// Inner Classes
public class WrapperConnection {

    // Basic Info
    public final String wrapperId;
    public final Connection connection;
    public final String hostname;

    // Resources
    public final int maxMemory;
    public int availableMemory;

    // Metrics
    public double cpuUsage = 0.0;
    public int activeServers = 0;

    // Connection Status
    public long connectedSince;
    public long lastHeartbeat;
    public long lastPingSent;
    public long lastPong;
    public long lastRttMs = -1;

    // Health
    public boolean healthy = true;

    public WrapperConnection(String wrapperId, Connection connection, String hostname, int maxMemory, int availableMemory) {
        this.wrapperId = wrapperId;
        this.connection = connection;
        this.hostname = hostname;
        this.maxMemory = maxMemory;
        this.availableMemory = availableMemory;
        this.connectedSince = System.currentTimeMillis();
        this.lastHeartbeat = System.currentTimeMillis();
        this.lastPong = System.currentTimeMillis();
    }

    // Getters (fuer HTTP API)
    public String getWrapperId() {
        return wrapperId;
    }

    public String getHostname() {
        return hostname;
    }

    public int getMaxMemory() {
        return maxMemory;
    }

    public int getAvailableMemory() {
        return availableMemory;
    }

    public double getCpuUsage() {
        return cpuUsage;
    }

    public int getActiveServers() {
        return activeServers;
    }

    public long getConnectedSince() {
        return connectedSince;
    }

    public long getLastHeartbeat() {
        return lastHeartbeat;
    }

    public long getLastPong() {
        return lastPong;
    }

    public long getLastRttMs() {
        return lastRttMs;
    }

    // Convenience Methods
    public int getUsedMemory() {
        return maxMemory - availableMemory;
    }

    public double getMemoryUsagePercentage() {
        if (maxMemory == 0) return 0.0;
        return ((double) getUsedMemory() / maxMemory) * 100.0;
    }

    public long getUptime() {
        return System.currentTimeMillis() - connectedSince;
    }

    public long getTimeSinceLastHeartbeat() {
        return System.currentTimeMillis() - lastHeartbeat;
    }

    public boolean isHealthy() {
        // Wrapper is healthy if last heartbeat was less than 15 seconds ago
        return getTimeSinceLastHeartbeat() < 15000;
    }

    public boolean canHostServer(int requiredMemory) {
        return availableMemory >= requiredMemory;
    }

    public void updateHeartbeat(int availableMemory, double cpuUsage, int activeServers) {
        this.availableMemory = availableMemory;
        this.cpuUsage = cpuUsage;
        this.activeServers = activeServers;
        this.lastHeartbeat = System.currentTimeMillis();
        this.healthy = true;
    }

    public void markPingSent(long timestamp) {
        this.lastPingSent = timestamp;
    }

    public void markPong(long pingTimestamp) {
        this.lastPong = System.currentTimeMillis();
        this.lastRttMs = Math.max(0, this.lastPong - pingTimestamp);
    }

    public void markUnhealthy() {
        this.healthy = false;
    }

    @Override
    public String toString() {
        return String.format("WrapperConnection{id='%s', hostname='%s', memory=%dMB/%dMB, cpu=%.1f%%, servers=%d}",
                wrapperId, hostname, getUsedMemory(), maxMemory, cpuUsage, activeServers);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WrapperConnection that = (WrapperConnection) o;
        return wrapperId.equals(that.wrapperId);
    }

    @Override
    public int hashCode() {
        return wrapperId.hashCode();
    }
}
