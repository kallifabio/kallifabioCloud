package de.kallifabio.cloud.pluginapi.request;

import java.util.Objects;

public final class TempPermissionRequestBuilder {

    private String playerUuid;
    private String permission;
    private long durationSeconds;

    public static TempPermissionRequestBuilder create() {
        return new TempPermissionRequestBuilder();
    }

    public TempPermissionRequestBuilder playerUuid(String playerUuid) {
        this.playerUuid = playerUuid;
        return this;
    }

    public TempPermissionRequestBuilder permission(String permission) {
        this.permission = permission;
        return this;
    }

    public TempPermissionRequestBuilder durationSeconds(long durationSeconds) {
        this.durationSeconds = durationSeconds;
        return this;
    }

    public TempPermissionRequest build() {
        return new TempPermissionRequest(
                Objects.requireNonNull(playerUuid, "playerUuid"),
                Objects.requireNonNull(permission, "permission"),
                durationSeconds
        );
    }
}
