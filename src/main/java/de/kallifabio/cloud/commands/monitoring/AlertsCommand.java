package de.kallifabio.cloud.commands.monitoring;

import de.kallifabio.cloud.commands.BaseCloudCommand;

import java.util.Map;

public class AlertsCommand extends BaseCloudCommand {
    @Override
    public boolean execute(String sender, String[] args) {
        if (!ensureMaster()) return false;
        Map<String, Object> snapshot = master().getMonitoringService().getMonitoringSnapshot();
        info("Active Alerts: " + snapshot.getOrDefault("activeAlerts", 0));
        info("Total Alerts: " + snapshot.getOrDefault("totalAlerts", 0));
        return true;
    }

    @Override
    public String getDescription() {
        return "Zeigt Alert-Zaehler.";
    }

    @Override
    public String getUsage() {
        return "alerts";
    }
}
