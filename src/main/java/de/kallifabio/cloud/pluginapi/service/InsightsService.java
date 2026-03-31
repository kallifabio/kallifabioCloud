package de.kallifabio.cloud.pluginapi.service;

import de.kallifabio.cloud.pluginapi.model.CloudInsightReport;

import java.util.ArrayList;
import java.util.List;

public final class InsightsService {

    private final ServerService serverService;
    private final WrapperService wrapperService;
    private final QueueService queueService;
    private final AlertService alertService;

    public InsightsService(ServerService serverService, WrapperService wrapperService, QueueService queueService, AlertService alertService) {
        this.serverService = serverService;
        this.wrapperService = wrapperService;
        this.queueService = queueService;
        this.alertService = alertService;
    }

    public CloudInsightReport createReport() {
        int totalServers = serverService.list().size();
        int totalWrappers = wrapperService.list().size();
        int queueTotal = queueService.status().total();
        boolean critical = alertService.hasCritical();

        List<String> recommendations = new ArrayList<>();
        if (queueTotal > 0) {
            recommendations.add("Queue detected: consider scaling target group.");
        }
        if (critical) {
            recommendations.add("Critical alerts active: review alerts and mitigate.");
        }
        if (totalWrappers == 0) {
            recommendations.add("No wrappers online.");
        }
        if (recommendations.isEmpty()) {
            recommendations.add("System looks healthy.");
        }

        return new CloudInsightReport(
                System.currentTimeMillis(),
                totalServers,
                totalWrappers,
                queueTotal,
                critical,
                List.copyOf(recommendations)
        );
    }
}
