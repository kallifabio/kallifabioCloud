package de.kallifabio.cloud.commands.monitoring;

import de.kallifabio.cloud.commands.BaseCloudCommand;

public class ClearAlertsCommand extends BaseCloudCommand {
    @Override
    public boolean execute(String sender, String[] args) {
        if (!ensureMaster()) return false;
        int cleared = master().getMonitoringService().clearAllAlerts();
        info("Alerts gelöscht: " + cleared);
        return true;
    }

    @Override
    public String getDescription() {
        return "Löscht aktive Alerts.";
    }

    @Override
    public String getUsage() {
        return "clearalerts";
    }
}
