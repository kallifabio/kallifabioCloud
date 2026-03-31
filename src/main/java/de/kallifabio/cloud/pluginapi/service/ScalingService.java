package de.kallifabio.cloud.pluginapi.service;

import com.google.gson.JsonObject;
import de.kallifabio.cloud.pluginapi.CloudApiClient;

import java.util.Map;

public final class ScalingService {

    private final CloudApiClient client;

    public ScalingService(CloudApiClient client) {
        this.client = client;
    }

    public JsonObject policies() {
        return client.get("/api/v1/scaling/policies");
    }

    public JsonObject triggerEvaluation() {
        return client.post("/api/v1/scaling/trigger", Map.of());
    }
}
