package de.kallifabio.cloud.commands.groups;

import de.kallifabio.cloud.commands.BaseCloudCommand;

import java.util.List;

public class GroupsCommand extends BaseCloudCommand {
    @Override
    public boolean execute(String sender, String[] args) {
        if (!ensureMaster()) return false;
        List<String> groups = master().getConfigManager().getAllServerGroups();
        if (groups.isEmpty()) {
            info("Keine Server-Groups gefunden.");
            return true;
        }
        for (String group : groups) {
            info(group + " min=" + master().getConfigManager().getMinServersForGroup(group) +
                    " max=" + master().getConfigManager().getMaxServersForGroup(group) +
                    " maintenance=" + master().getConfigManager().isMaintenanceMode(group));
        }
        return true;
    }

    @Override
    public String getDescription() {
        return "Listet alle Server-Groups.";
    }

    @Override
    public String getUsage() {
        return "groups";
    }
}
