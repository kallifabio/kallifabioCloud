package de.kallifabio.cloud.master.permissions;

import java.util.HashSet;
import java.util.Set;

public class PermissionProfile {
    public final String playerUuid;
    public final Set<String> permissions = new HashSet<>();
    public String primaryGroup = "default";
    public String prefix = "";
    public String suffix = "";

    public PermissionProfile(String playerUuid) {
        this.playerUuid = playerUuid;
    }
}
