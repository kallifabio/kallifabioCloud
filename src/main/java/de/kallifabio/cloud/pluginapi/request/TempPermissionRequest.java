package de.kallifabio.cloud.pluginapi.request;

public record TempPermissionRequest(String playerUuid, String permission, long durationSeconds) {
}
