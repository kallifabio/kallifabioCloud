package de.kallifabio.cloud.master.permissions;

import java.util.Locale;

public class PermissionEnforcer {

    private final PermissionSyncService permissionSyncService;

    public PermissionEnforcer(PermissionSyncService permissionSyncService) {
        this.permissionSyncService = permissionSyncService;
    }

    public boolean canJoinNetwork(String playerUuid) {
        return hasAny(playerUuid, "cloud.join", "cloud.*", "*");
    }

    public boolean canJoinGroup(String playerUuid, String groupName) {
        if (groupName == null || groupName.isBlank()) {
            return false;
        }
        String group = groupName.toLowerCase(Locale.ROOT);
        return hasAny(playerUuid,
                "cloud.group." + group + ".join",
                "cloud.group.*.join",
                "cloud.join",
                "cloud.*",
                "*");
    }

    public boolean canBypassMaintenance(String playerUuid) {
        return hasAny(playerUuid, "cloud.maintenance.bypass", "cloud.staff.bypass", "cloud.*", "*");
    }

    public boolean canBypassServerFull(String playerUuid) {
        return hasAny(playerUuid, "cloud.server.full.bypass", "cloud.vip.bypass", "cloud.*", "*");
    }

    public boolean canBypassQueue(String playerUuid) {
        return hasAny(playerUuid, "cloud.queue.bypass", "cloud.vip.bypass", "cloud.*", "*");
    }

    public boolean canSwitchServer(String playerUuid) {
        return hasAny(playerUuid, "cloud.server.switch", "cloud.server.*", "cloud.*", "*");
    }

    public boolean canUseHub(String playerUuid) {
        return hasAny(playerUuid, "cloud.hub", "cloud.server.hub", "cloud.server.switch", "cloud.*", "*");
    }

    private boolean hasAny(String playerUuid, String... permissions) {
        PermissionProfile profile = permissionSyncService.buildProfile(playerUuid);
        for (String permission : permissions) {
            if (matches(profile, permission)) {
                return true;
            }
        }
        return false;
    }

    private boolean matches(PermissionProfile profile, String required) {
        return PermissionMatcher.matches(profile.permissions, required);
    }
}
