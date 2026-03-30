package de.kallifabio.cloud.commands.permissions;

import de.kallifabio.cloud.commands.BaseCloudCommand;

public class PermissionSyncCommand extends BaseCloudCommand {
    @Override
    public boolean execute(String sender, String[] args) {
        if (!ensureMaster()) return false;
        if (args.length < 1) {
            warn("Usage: " + getUsage());
            return false;
        }
        master().syncPermissionsForPlayer(args[0]);
        info("Permission-Sync getriggert fuer " + args[0]);
        return true;
    }

    @Override
    public String getDescription() {
        return "Sync't Permissions eines Spielers sofort.";
    }

    @Override
    public String getUsage() {
        return "permsync <playerUuid>";
    }
}
