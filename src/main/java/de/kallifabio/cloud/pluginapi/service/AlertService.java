package de.kallifabio.cloud.pluginapi.service;

import de.kallifabio.cloud.pluginapi.model.CloudAlertInfo;
import de.kallifabio.cloud.pluginapi.model.CloudOperationResult;

import java.util.List;

public final class AlertService {

    private final MonitoringApiService monitoringService;

    public AlertService(MonitoringApiService monitoringService) {
        this.monitoringService = monitoringService;
    }

    public List<CloudAlertInfo> active() {
        return monitoringService.activeAlerts();
    }

    public boolean hasCritical() {
        return active().stream().anyMatch(a -> "CRITICAL".equalsIgnoreCase(a.severity()));
    }

    public CloudOperationResult clear() {
        return CloudOperationResult.from(monitoringService.clearAlerts());
    }
}
