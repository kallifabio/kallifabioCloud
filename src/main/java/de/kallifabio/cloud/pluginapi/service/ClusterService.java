package de.kallifabio.cloud.pluginapi.service;

import com.google.gson.JsonObject;
import de.kallifabio.cloud.pluginapi.CloudApiClient;
import de.kallifabio.cloud.pluginapi.model.CloudClusterInfo;
import de.kallifabio.cloud.pluginapi.model.CloudClusterNodeInfo;

import java.util.ArrayList;
import java.util.List;

public final class ClusterService {

    private final CloudApiClient client;

    public ClusterService(CloudApiClient client) {
        this.client = client;
    }

    public JsonObject info() {
        return client.get("/api/v1/cluster/info");
    }

    public CloudClusterInfo infoModel() {
        return CloudClusterInfo.from(info());
    }

    public JsonObject nodes() {
        return client.get("/api/v1/cluster/nodes");
    }

    public List<CloudClusterNodeInfo> nodeList() {
        JsonObject response = nodes();
        List<CloudClusterNodeInfo> nodes = new ArrayList<>();
        if (response.has("nodes") && response.get("nodes").isJsonArray()) {
            response.getAsJsonArray("nodes").forEach(element -> {
                if (element.isJsonObject()) {
                    nodes.add(CloudClusterNodeInfo.from(element.getAsJsonObject()));
                }
            });
        }
        return nodes;
    }
}
