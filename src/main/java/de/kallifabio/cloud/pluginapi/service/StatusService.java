package de.kallifabio.cloud.pluginapi.service;

import com.google.gson.JsonObject;
import de.kallifabio.cloud.pluginapi.CloudApiClient;
import de.kallifabio.cloud.pluginapi.model.CloudHealthInfo;
import de.kallifabio.cloud.pluginapi.model.CloudStatusSummary;

public final class StatusService {

    private final CloudApiClient client;

    public StatusService(CloudApiClient client) {
        this.client = client;
    }

    public CloudHealthInfo health() {
        return CloudHealthInfo.from(client.get("/api/v1/health"));
    }

    public JsonObject status() {
        return client.get("/api/v1/status");
    }

    public CloudStatusSummary statusSummary() {
        return CloudStatusSummary.from(status());
    }

    public JsonObject dashboardOverview() {
        return client.get("/api/v1/dashboard/overview");
    }
}
