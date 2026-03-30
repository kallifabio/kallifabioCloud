package de.kallifabio.cloud.commands.groups;

import de.kallifabio.cloud.commands.BaseCloudCommand;

public class DeleteGroupCommand extends BaseCloudCommand {
    @Override
    public boolean execute(String sender, String[] args) {
        if (!ensureMaster()) return false;
        if (args.length < 1) {
            warn("Usage: " + getUsage());
            return false;
        }
        String groupName = args[0];
        if (master().getRunningServers().values().stream().anyMatch(s -> s.groupName.equalsIgnoreCase(groupName))) {
            error("Group hat noch laufende Server und kann nicht gelöscht werden.");
            return false;
        }
        boolean ok = master().getConfigManager().deleteGroup(groupName);
        if (!ok) {
            error("Group konnte nicht gelöscht werden.");
            return false;
        }
        master().reloadConfiguration("deletegroup:" + sender);
        info("Group gelöscht: " + groupName);
        return true;
    }

    @Override
    public String getDescription() {
        return "Löscht eine Group aus der Config.";
    }

    @Override
    public String getUsage() {
        return "deletegroup <group>";
    }
}
