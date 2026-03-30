package de.kallifabio.cloud.commands.core;

import de.kallifabio.cloud.commands.BaseCloudCommand;

public class StatusCommand extends BaseCloudCommand {
    @Override
    public boolean execute(String sender, String[] args) {
        if (!ensureMaster()) return false;
        info("Master-ID: " + master().getMasterId());
        info("Wrapper online: " + master().getConnectedWrappers().size());
        info("Server online: " + master().getRunningServers().size());
        info("Queue total: " + master().getPlayerQueueManager().getTotalQueued());
        return true;
    }

    @Override
    public String getDescription() {
        return "Zeigt den aktuellen Cloud-Status.";
    }

    @Override
    public String getUsage() {
        return "status";
    }
}
