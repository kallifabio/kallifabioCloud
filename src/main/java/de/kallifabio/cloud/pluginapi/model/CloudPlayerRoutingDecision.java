package de.kallifabio.cloud.pluginapi.model;

public record CloudPlayerRoutingDecision(
        String targetServer,
        String reason,
        double score
) {
}
