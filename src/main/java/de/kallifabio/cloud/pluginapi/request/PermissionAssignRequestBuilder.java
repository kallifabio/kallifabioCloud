package de.kallifabio.cloud.pluginapi.request;

import java.util.Objects;

public final class PermissionAssignRequestBuilder {

    private String playerUuid;
    private String group;

    public static PermissionAssignRequestBuilder create() {
        return new PermissionAssignRequestBuilder();
    }

    public PermissionAssignRequestBuilder playerUuid(String playerUuid) {
        this.playerUuid = playerUuid;
        return this;
    }

    public PermissionAssignRequestBuilder group(String group) {
        this.group = group;
        return this;
    }

    public PermissionAssignRequest build() {
        return new PermissionAssignRequest(
                Objects.requireNonNull(playerUuid, "playerUuid"),
                Objects.requireNonNull(group, "group")
        );
    }
}
