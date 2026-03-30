package de.kallifabio.cloud.commands.core;

import de.kallifabio.cloud.commands.BaseCloudCommand;
import de.kallifabio.cloud.wrapper.Wrapper;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class RestartStatusCommand extends BaseCloudCommand {

    @Override
    public boolean execute(String sender, String[] args) {
        if (!ensureMaster()) {
            return false;
        }

        Set<String> masterLocks = new TreeSet<>(master().getRestartInProgress());
        Map<String, Integer> masterRetries = new TreeMap<>(master().getRestartRetryCounts());

        info("Restart-Status (Master):");
        info("  Locks aktiv: " + (masterLocks.isEmpty() ? "-" : String.join(", ", masterLocks)));
        if (masterRetries.isEmpty()) {
            info("  Retry-Zähler: -");
        } else {
            for (Map.Entry<String, Integer> entry : masterRetries.entrySet()) {
                info("  Retry " + entry.getKey() + " = " + entry.getValue());
            }
        }

        Wrapper wrapper = master().getWrapper();
        if (wrapper == null) {
            warn("[WARN] Kein lokaler Wrapper verfügbar (Master-only Ansicht).");
            return true;
        }

        Set<String> wrapperLocks = new TreeSet<>(wrapper.getRestartInProgress());
        Map<String, Integer> wrapperRetries = new TreeMap<>(wrapper.getRestartRetryCounts());
        info("Restart-Status (Lokaler Wrapper):");
        info("  Locks aktiv: " + (wrapperLocks.isEmpty() ? "-" : String.join(", ", wrapperLocks)));
        if (wrapperRetries.isEmpty()) {
            info("  Retry-Zähler: -");
        } else {
            for (Map.Entry<String, Integer> entry : wrapperRetries.entrySet()) {
                info("  Retry " + entry.getKey() + " = " + entry.getValue());
            }
        }

        return true;
    }

    @Override
    public String getDescription() {
        return "Zeigt aktive Neustart-Locks und Retry-Zähler.";
    }

    @Override
    public String getUsage() {
        return "restartstatus";
    }
}

