package de.kallifabio.cloud.commands.groups;

import de.kallifabio.cloud.commands.BaseCloudCommand;

public class CreateGroupCommand extends BaseCloudCommand {
    @Override
    public boolean execute(String sender, String[] args) {
        if (!ensureMaster()) return false;
        if (args.length < 1) {
            warn("Usage: " + getUsage());
            return false;
        }
        String groupName = args[0];
        String parent = args.length > 1 ? args[1] : "";
        boolean ok = master().getConfigManager().createOrUpdateGroup(groupName, parent);
        if (!ok) {
            error("Group konnte nicht erstellt werden.");
            return false;
        }
        master().reloadConfiguration("creategroup:" + sender);
        info("Group erstellt/aktualisiert: " + groupName + (parent.isBlank() ? "" : " parent=" + parent));
        return true;
    }

    @Override
    public String getDescription() {
        return "Erstellt eine Group in der Config.";
    }

    @Override
    public String getUsage() {
        return "creategroup <group> [parent]";
    }
}
