package de.kallifabio.cloud.commands.permissions;

import de.kallifabio.cloud.commands.BaseCloudCommand;
import de.kallifabio.cloud.master.permissions.PermissionGroup;

public class PermissionGroupCreateCommand extends BaseCloudCommand {
    @Override
    public boolean execute(String sender, String[] args) {
        if (!ensureMaster()) return false;
        if (args.length < 1) {
            warn("Usage: " + getUsage());
            return false;
        }
        String name = args[0];
        String parent = args.length > 1 ? args[1] : "";
        int weight = args.length > 2 ? parseInt(args[2], 0) : 0;
        String prefix = args.length > 3 ? args[3] : "";
        String suffix = args.length > 4 ? args[4] : "";

        PermissionGroup group = new PermissionGroup(name);
        group.parent = parent;
        group.weight = weight;
        group.prefix = prefix;
        group.suffix = suffix;

        master().getDataStore().upsertPermissionGroup(group);
        info("PermissionGroup gespeichert: " + name);
        return true;
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    @Override
    public String getDescription() {
        return "Erstellt oder aktualisiert eine Permission-Group.";
    }

    @Override
    public String getUsage() {
        return "permgroupcreate <group> [parent] [weight] [prefix] [suffix]";
    }
}
