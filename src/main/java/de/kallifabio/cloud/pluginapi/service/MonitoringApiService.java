package de.kallifabio.cloud.pluginapi.service;

import com.google.gson.JsonObject;
import de.kallifabio.cloud.pluginapi.CloudApiClient;
import de.kallifabio.cloud.pluginapi.model.CloudAlertInfo;
import de.kallifabio.cloud.pluginapi.model.CloudMetricsSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class MonitoringApiService {

    private final CloudApiClient client;

    public MonitoringApiService(CloudApiClient client) {
        this.client = client;
    }

    public JsonObject metrics() {
        return client.get("/api/v1/metrics");
    }

    public CloudMetricsSnapshot metricsSnapshot() {
        return CloudMetricsSnapshot.from(metrics());
    }

    public JsonObject metricsHistory() {
        return client.get("/api/v1/metrics/history");
    }

    public String prometheusMetrics() {
        return client.getText("/api/v1/metrics/prometheus");
    }

    public JsonObject alerts() {
        return client.get("/api/v1/alerts");
    }

    public List<CloudAlertInfo> activeAlerts() {
        JsonObject response = alerts();
        List<CloudAlertInfo> list = new ArrayList<>();
        if (response.has("active") && response.get("active").isJsonArray()) {
            response.getAsJsonArray("active").forEach(element -> {
                if (element.isJsonObject()) {
                    list.add(CloudAlertInfo.from(element.getAsJsonObject()));
                }
            });
        }
        return list;
    }

    public JsonObject clearAlerts() {
        return client.post("/api/v1/alerts/clear", Map.of());
    }
}
