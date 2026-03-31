package de.kallifabio.cloud.pluginapi.model;

public record CloudServerCapacity(
        String serverName,
        int players,
        int maxPlayers
) {
    public double fillRatio() {
        if (maxPlayers <= 0) {
            return 0.0;
        }
        return (double) players / (double) maxPlayers;
    }
}
