package de.kallifabio.cloud.pluginapi.enforcer;

import de.kallifabio.cloud.pluginapi.model.CloudPermissionProfileInfo;

public record CloudPermissionDecision(
        boolean allowed,
        String permission,
        String reason,
        CloudPermissionProfileInfo profile
) {
    public static CloudPermissionDecision allow(String permission, String reason, CloudPermissionProfileInfo profile) {
        return new CloudPermissionDecision(true, permission, reason, profile);
    }

    public static CloudPermissionDecision deny(String permission, String reason, CloudPermissionProfileInfo profile) {
        return new CloudPermissionDecision(false, permission, reason, profile);
    }
}
