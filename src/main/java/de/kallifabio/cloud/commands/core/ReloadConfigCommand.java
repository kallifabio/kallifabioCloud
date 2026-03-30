package de.kallifabio.cloud.commands.core;

import de.kallifabio.cloud.commands.BaseCloudCommand;

public class ReloadConfigCommand extends BaseCloudCommand {
    @Override
    public boolean execute(String sender, String[] args) {
        if (!ensureMaster()) return false;
        master().reloadConfiguration("command:" + sender);
        return true;
    }

    @Override
    public String getDescription() {
        return "Laedt Konfiguration neu (mit Backup).";
    }

    @Override
    public String getUsage() {
        return "reloadconfig";
    }
}
