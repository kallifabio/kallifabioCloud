package de.kallifabio.cloud.master.lifecycle;

public record LifecycleTransition(
        long timestamp,
        String serverName,
        ServerLifecycleState from,
        ServerLifecycleState to,
        String reason
) {
}
