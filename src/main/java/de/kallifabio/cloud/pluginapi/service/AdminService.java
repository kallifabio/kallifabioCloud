package de.kallifabio.cloud.pluginapi.service;

import de.kallifabio.cloud.pluginapi.model.CloudOperationResult;

public final class AdminService {

    private final MonitoringApiService monitoringService;
    private final OperationsService operationsService;
    private final ScalingService scalingService;

    public AdminService(MonitoringApiService monitoringService, OperationsService operationsService, ScalingService scalingService) {
        this.monitoringService = monitoringService;
        this.operationsService = operationsService;
        this.scalingService = scalingService;
    }

    public CloudOperationResult clearAlerts() {
        return CloudOperationResult.from(monitoringService.clearAlerts());
    }

    public CloudOperationResult triggerScaleEvaluation() {
        return CloudOperationResult.from(scalingService.triggerEvaluation());
    }

    public CloudOperationResult testWebhook() {
        return CloudOperationResult.from(operationsService.webhookTest(null));
    }
}
