package de.kallifabio.cloud.commands.permissions;

import de.kallifabio.cloud.commands.BaseCloudCommand;

public class PermissionAssignCommand extends BaseCloudCommand {
    @Override
    public boolean execute(String sender, String[] args) {
        if (!ensureMaster()) return false;
        if (args.length < 2) {
            warn("Usage: " + getUsage());
            return false;
        }
        String playerUuid = args[0];
        String groupName = args[1];
        master().getDataStore().assignPermissionGroup(playerUuid, groupName);
        master().syncPermissionsForPlayer(playerUuid);
        info("Permission-Group zugewiesen: " + playerUuid + " -> " + groupName);
        return true;
    }

    @Override
    public String getDescription() {
        return "Weist einem Spieler eine Permission-Group zu.";
    }

    @Override
    public String getUsage() {
        return "permassign <playerUuid> <group>";
    }
}
