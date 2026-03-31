package de.kallifabio.cloud.pluginapi.request;

public record PlayerDataSaveRequest(
        String playerUuid,
        int coins,
        int kills,
        int deaths,
        int wins,
        int losses,
        String rank
) {
}
