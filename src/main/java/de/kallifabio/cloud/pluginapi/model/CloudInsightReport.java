package de.kallifabio.cloud.pluginapi.model;

import java.util.List;

public record CloudInsightReport(
        long createdAt,
        int totalServers,
        int totalWrappers,
        int queueTotal,
        boolean hasCriticalAlerts,
        List<String> recommendations
) {
}
