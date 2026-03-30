package de.kallifabio.cloud.commands.groups;

import de.kallifabio.cloud.commands.BaseCloudCommand;

import java.util.Arrays;
import java.util.List;

public class GroupWhitelistCommand extends BaseCloudCommand {
    @Override
    public boolean execute(String sender, String[] args) {
        if (!ensureMaster()) return false;
        if (args.length < 2) {
            warn("Usage: " + getUsage());
            return false;
        }
        String group = args[0];
        List<String> whitelist = Arrays.stream(args[1].split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
        boolean ok = master().getConfigManager().setGroupWhitelist(group, whitelist);
        if (!ok) {
            error("Whitelist konnte nicht gespeichert werden.");
            return false;
        }
        master().reloadConfiguration("groupwhitelist:" + sender);
        info("Whitelist für " + group + " gesetzt: " + whitelist.size() + " Einträge");
        return true;
    }

    @Override
    public String getDescription() {
        return "Setzt Group-Whitelist (CSV).";
    }

    @Override
    public String getUsage() {
        return "groupwhitelist <group> <uuid1,uuid2,...>";
    }
}
