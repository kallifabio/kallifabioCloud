package de.kallifabio.cloud.commands.groups;

import de.kallifabio.cloud.commands.BaseCloudCommand;

public class GroupMaintenanceCommand extends BaseCloudCommand {
    @Override
    public boolean execute(String sender, String[] args) {
        if (!ensureMaster()) return false;
        if (args.length < 2) {
            warn("Usage: " + getUsage());
            return false;
        }
        boolean maintenance = "on".equalsIgnoreCase(args[1]) || "true".equalsIgnoreCase(args[1]);
        boolean ok = master().getConfigManager().setGroupMaintenance(args[0], maintenance);
        if (!ok) {
            error("Maintenance konnte nicht gesetzt werden.");
            return false;
        }
        master().reloadConfiguration("groupmaintenance:" + sender);
        info("Maintenance für " + args[0] + " = " + maintenance);
        return true;
    }

    @Override
    public String getDescription() {
        return "Aktiviert/Deaktiviert Maintenance pro Group.";
    }

    @Override
    public String getUsage() {
        return "groupmaintenance <group> <on|off>";
    }
}
