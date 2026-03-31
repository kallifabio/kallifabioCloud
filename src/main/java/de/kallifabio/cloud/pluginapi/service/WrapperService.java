package de.kallifabio.cloud.pluginapi.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.kallifabio.cloud.pluginapi.CloudApiClient;
import de.kallifabio.cloud.pluginapi.model.CloudOperationResult;
import de.kallifabio.cloud.pluginapi.model.CloudWrapperInfo;
import de.kallifabio.cloud.pluginapi.request.WrapperDrainRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class WrapperService {

    private final CloudApiClient client;

    public WrapperService(CloudApiClient client) {
        this.client = client;
    }

    public JsonObject rawWrappers() {
        return client.get("/api/v1/wrappers");
    }

    public List<CloudWrapperInfo> list() {
        JsonObject response = rawWrappers();
        List<CloudWrapperInfo> wrappers = new ArrayList<>();
        JsonArray array = response.has("wrappers") ? response.getAsJsonArray("wrappers") : new JsonArray();
        for (JsonElement element : array) {
            if (element.isJsonObject()) {
                wrappers.add(CloudWrapperInfo.from(element.getAsJsonObject()));
            }
        }
        return wrappers;
    }

    public JsonObject setDraining(String wrapperId, boolean draining) {
        return client.post("/api/v1/wrappers/drain", Map.of(
                "wrapperId", wrapperId,
                "draining", draining
        ));
    }

    public CloudOperationResult setDraining(WrapperDrainRequest request) {
        return CloudOperationResult.from(setDraining(request.wrapperId(), request.draining()));
    }
}
