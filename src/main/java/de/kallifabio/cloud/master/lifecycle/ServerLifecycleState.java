package de.kallifabio.cloud.master.lifecycle;

import java.util.Locale;

public enum ServerLifecycleState {
    QUEUED,
    PREPARING,
    STARTING,
    ONLINE,
    DRAINING,
    STOPPING,
    FAILED,
    OFFLINE,
    QUARANTINED;

    public static ServerLifecycleState fromStatus(String status) {
        if (status == null || status.isBlank()) {
            return OFFLINE;
        }
        return switch (status.trim().toUpperCase(Locale.ROOT)) {
            case "QUEUED" -> QUEUED;
            case "PREPARING" -> PREPARING;
            case "STARTING" -> STARTING;
            case "ONLINE" -> ONLINE;
            case "DRAINING" -> DRAINING;
            case "STOPPING" -> STOPPING;
            case "FAILED", "CRASHED", "KILLED" -> FAILED;
            case "QUARANTINED" -> QUARANTINED;
            default -> OFFLINE;
        };
    }
}
