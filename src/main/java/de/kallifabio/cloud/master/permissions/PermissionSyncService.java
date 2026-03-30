package de.kallifabio.cloud.master.permissions;

import de.kallifabio.cloud.data.CloudDataStore;
import de.kallifabio.cloud.data.PlayerData;
import de.kallifabio.cloud.libs.Message;
import de.kallifabio.cloud.master.Master;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PermissionSyncService {

    private final Master master;
    private final CloudDataStore dataStore;

    public PermissionSyncService(Master master, CloudDataStore dataStore) {
        this.master = master;
        this.dataStore = dataStore;
    }

    public PermissionProfile buildProfile(String playerUuid) {
        PermissionProfile profile = new PermissionProfile(playerUuid);
        PlayerData data = dataStore.getPlayerData(playerUuid);

        List<PermissionGroup> groups = dataStore.getPermissionGroupsForPlayer(playerUuid);
        if (groups.isEmpty()) {
            PermissionGroup def = dataStore.getPermissionGroup("default");
            if (def != null) {
                groups = List.of(def);
            }
        }

        groups = new ArrayList<>(groups);
        groups.sort(Comparator.comparingInt((PermissionGroup g) -> g.weight).reversed());

        Set<String> mergedPermissions = new HashSet<>();
        for (PermissionGroup group : groups) {
            mergeGroupPermissions(group, mergedPermissions, new HashSet<>());
        }

        mergedPermissions.addAll(data.permissions);
        mergedPermissions.addAll(dataStore.getActiveTempPermissions(playerUuid));

        profile.permissions.addAll(mergedPermissions);

        if (!groups.isEmpty()) {
            PermissionGroup primary = groups.get(0);
            profile.primaryGroup = primary.name;
            profile.prefix = primary.prefix == null ? "" : primary.prefix;
            profile.suffix = primary.suffix == null ? "" : primary.suffix;
        }

        return profile;
    }

    private void mergeGroupPermissions(PermissionGroup group, Set<String> target, Set<String> visited) {
        if (group == null || group.name == null || visited.contains(group.name)) {
            return;
        }
        visited.add(group.name);

        if (group.parent != null && !group.parent.isBlank()) {
            PermissionGroup parent = dataStore.getPermissionGroup(group.parent);
            mergeGroupPermissions(parent, target, visited);
        }

        target.addAll(group.permissions);
    }

    public void syncPlayerPermissions(String playerUuid) {
        PermissionProfile profile = buildProfile(playerUuid);
        Message.PermissionSync sync = new Message.PermissionSync();
        sync.playerUuid = playerUuid;
        sync.permissions = new ArrayList<>(profile.permissions);
        sync.primaryGroup = profile.primaryGroup;
        sync.prefix = profile.prefix;
        sync.suffix = profile.suffix;
        sync.targetServer = master.getPlayerSessionManager().getCurrentServer(playerUuid);
        sync.timestamp = System.currentTimeMillis();

        master.getServer().sendToAllTCP(sync);
    }
}
