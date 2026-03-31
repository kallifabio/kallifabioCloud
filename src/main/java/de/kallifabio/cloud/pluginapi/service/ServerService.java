package de.kallifabio.cloud.pluginapi.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.kallifabio.cloud.pluginapi.CloudApiClient;
import de.kallifabio.cloud.pluginapi.model.CloudOperationResult;
import de.kallifabio.cloud.pluginapi.model.CloudServerInfo;
import de.kallifabio.cloud.pluginapi.request.ServerActionRequest;
import de.kallifabio.cloud.pluginapi.request.ServerStartRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ServerService {

    private final CloudApiClient client;

    public ServerService(CloudApiClient client) {
        this.client = client;
    }

    public JsonObject rawServers() {
        return client.get("/api/v1/servers");
    }

    public List<CloudServerInfo> list() {
        JsonObject response = rawServers();
        List<CloudServerInfo> servers = new ArrayList<>();
        JsonArray array = response.has("servers") ? response.getAsJsonArray("servers") : new JsonArray();
        for (JsonElement element : array) {
            if (element.isJsonObject()) {
                servers.add(CloudServerInfo.from(element.getAsJsonObject()));
            }
        }
        return servers;
    }

    public JsonObject start(String serverName, String groupName) {
        return client.post("/api/v1/servers/start", Map.of(
                "serverName", serverName,
                "groupName", groupName
        ));
    }

    public CloudOperationResult start(ServerStartRequest request) {
        return CloudOperationResult.from(start(request.serverName(), request.groupName()));
    }

    public JsonObject stop(String serverName) {
        return client.post("/api/v1/servers/stop", Map.of("serverName", serverName));
    }

    public CloudOperationResult stop(ServerActionRequest request) {
        return CloudOperationResult.from(stop(request.serverName()));
    }

    public JsonObject restart(String serverName) {
        return client.post("/api/v1/servers/restart", Map.of("serverName", serverName));
    }

    public CloudOperationResult restart(ServerActionRequest request) {
        return CloudOperationResult.from(restart(request.serverName()));
    }
}
