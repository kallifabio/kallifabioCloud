package de.kallifabio.cloud.commands.queue;

import de.kallifabio.cloud.commands.BaseCloudCommand;

import java.util.Map;

public class QueueCommand extends BaseCloudCommand {
    @Override
    public boolean execute(String sender, String[] args) {
        if (!ensureMaster()) return false;
        Map<String, Integer> stats = master().getPlayerQueueManager().getQueueStats();
        if (stats.isEmpty()) {
            info("Keine Queue-Daten vorhanden.");
            return true;
        }
        stats.forEach((group, size) -> info("Queue " + group + ": " + size));
        info("Gesamt: " + master().getPlayerQueueManager().getTotalQueued());
        return true;
    }

    @Override
    public String getDescription() {
        return "Zeigt Queue-Größen.";
    }

    @Override
    public String getUsage() {
        return "queue";
    }
}
