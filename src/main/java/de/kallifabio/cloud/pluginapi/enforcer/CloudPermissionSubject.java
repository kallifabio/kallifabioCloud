package de.kallifabio.cloud.pluginapi.enforcer;

import java.util.UUID;

public record CloudPermissionSubject(
        UUID uniqueId,
        String name,
        String currentServer,
        String currentGroup
) {
    public static CloudPermissionSubject player(UUID uniqueId, String name) {
        return new CloudPermissionSubject(uniqueId, name, "", "");
    }
}
