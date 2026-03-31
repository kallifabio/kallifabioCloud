package de.kallifabio.cloud.pluginapi.service;

import com.google.gson.JsonObject;
import de.kallifabio.cloud.pluginapi.CloudApiClient;
import de.kallifabio.cloud.pluginapi.model.CloudOperationResult;
import de.kallifabio.cloud.pluginapi.model.CloudRecentLogs;
import de.kallifabio.cloud.pluginapi.request.ConfigSetRequest;

import java.util.Map;

public final class OperationsService {

    private final CloudApiClient client;

    public OperationsService(CloudApiClient client) {
        this.client = client;
    }

    public JsonObject recentLogs() {
        return client.get("/api/v1/logs/recent");
    }

    public CloudRecentLogs recentLogsModel() {
        return CloudRecentLogs.from(recentLogs());
    }

    public JsonObject setupReport() {
        return client.get("/api/v1/setup/report");
    }

    public JsonObject configGet(String key) {
        return client.get("/api/v1/config/get", Map.of("key", key));
    }

    public JsonObject configSet(String key, String value) {
        return client.post("/api/v1/config/set", Map.of(
                "key", key,
                "value", value
        ));
    }

    public CloudOperationResult configSet(ConfigSetRequest request) {
        return CloudOperationResult.from(configSet(request.key(), request.value()));
    }

    public JsonObject webhookTest(String message) {
        if (message == null || message.isBlank()) {
            return client.post("/api/v1/webhook/test", Map.of());
        }
        return client.post("/api/v1/webhook/test", Map.of("message", message));
    }

    public CloudOperationResult webhookTestResult(String message) {
        return CloudOperationResult.from(webhookTest(message));
    }

    public JsonObject loadBalancerStats() {
        return client.get("/api/v1/loadbalancer/stats");
    }
}
