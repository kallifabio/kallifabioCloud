package de.kallifabio.cloud.commands.permissions;

import de.kallifabio.cloud.commands.BaseCloudCommand;
import de.kallifabio.cloud.data.PlayerData;
import de.kallifabio.cloud.master.permissions.PermissionGroup;

import java.util.List;

public class PermissionProfileCommand extends BaseCloudCommand {
    @Override
    public boolean execute(String sender, String[] args) {
        if (!ensureMaster()) return false;
        if (args.length < 1) {
            warn("Usage: " + getUsage());
            return false;
        }
        String playerUuid = args[0];
        PlayerData data = master().getDataStore().getPlayerData(playerUuid);
        List<PermissionGroup> groups = master().getDataStore().getPermissionGroupsForPlayer(playerUuid);
        List<String> tempPermissions = master().getDataStore().getActiveTempPermissions(playerUuid);

        info("Permission-Profil fuer " + playerUuid);
        info("Direkte Permissions: " + (data.permissions == null ? 0 : data.permissions.size()));
        info("Gruppen: " + groups.stream().map(g -> g.name).toList());
        info("Temp-Permissions: " + tempPermissions);
        return true;
    }

    @Override
    public String getDescription() {
        return "Zeigt gespeichertes Permission-Profil eines Spielers.";
    }

    @Override
    public String getUsage() {
        return "permprofile <playerUuid>";
    }
}
