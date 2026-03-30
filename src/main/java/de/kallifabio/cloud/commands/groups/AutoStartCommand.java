package de.kallifabio.cloud.commands.groups;

import de.kallifabio.cloud.commands.BaseCloudCommand;

public class AutoStartCommand extends BaseCloudCommand {
    @Override
    public boolean execute(String sender, String[] args) {
        if (!ensureMaster()) return false;
        info("AutoStart enabled=" + master().getConfigManager().isAutoStartEnabled());
        info("AutoStart groups=" + String.join(", ", master().getConfigManager().getAutoStartGroups()));
        info("AutoStart delay=" + master().getConfigManager().getAutoStartDelay() + "s");
        return true;
    }

    @Override
    public String getDescription() {
        return "Zeigt AutoStart-Konfiguration.";
    }

    @Override
    public String getUsage() {
        return "autostart";
    }
}
