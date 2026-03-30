package de.kallifabio.cloud.commands.permissions;

import de.kallifabio.cloud.commands.BaseCloudCommand;
import de.kallifabio.cloud.master.permissions.PermissionGroup;

import java.util.ArrayList;

public class PermissionGroupGrantCommand extends BaseCloudCommand {
    @Override
    public boolean execute(String sender, String[] args) {
        if (!ensureMaster()) return false;
        if (args.length < 2) {
            warn("Usage: " + getUsage());
            return false;
        }
        String groupName = args[0];
        String permission = args[1];

        PermissionGroup group = master().getDataStore().getPermissionGroup(groupName);
        if (group == null) {
            error("PermissionGroup nicht gefunden: " + groupName);
            return false;
        }
        if (group.permissions == null) {
            group.permissions = new ArrayList<>();
        }
        if (!group.permissions.contains(permission)) {
            group.permissions.add(permission);
        }
        master().getDataStore().upsertPermissionGroup(group);
        info("Permission hinzugefuegt: " + groupName + " -> " + permission);
        return true;
    }

    @Override
    public String getDescription() {
        return "Fuegt einer Permission-Group eine Permission hinzu.";
    }

    @Override
    public String getUsage() {
        return "permgroupgrant <group> <permission>";
    }
}
