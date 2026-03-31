package de.kallifabio.cloud.pluginapi.model;

public record CloudServerLoadSnapshot(
        String serverName,
        String groupName,
        double cpuUsage,
        double memoryUsage,
        double tps,
        int playerCount,
        int maxPlayers
) {
    public double playerFillRatio() {
        if (maxPlayers <= 0) {
            return 0.0;
        }
        return (double) playerCount / (double) maxPlayers;
    }
}
