package de.kallifabio.cloud.pluginapi.service;

import com.google.gson.JsonObject;
import de.kallifabio.cloud.pluginapi.CloudApiClient;
import de.kallifabio.cloud.pluginapi.model.CloudAuthInfo;

import java.util.Map;

public final class AuthService {

    private final CloudApiClient client;

    public AuthService(CloudApiClient client) {
        this.client = client;
    }

    public CloudAuthInfo me() {
        return CloudAuthInfo.from(client.get("/api/v1/auth/me"));
    }

    public JsonObject rotateDashboardKey() {
        return client.post("/api/v1/auth/rotate", Map.of("keyName", "dashboard"));
    }

    public JsonObject rotateAdminKey() {
        return client.post("/api/v1/auth/rotate", Map.of("keyName", "admin"));
    }

    public JsonObject debug(String path, String method) {
        return client.get("/api/v1/auth/debug",
                Map.of("path", path == null ? "/api/v1/auth/me" : path,
                        "method", method == null ? "GET" : method));
    }
}
