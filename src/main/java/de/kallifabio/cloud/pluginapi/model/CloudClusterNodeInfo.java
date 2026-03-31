package de.kallifabio.cloud.pluginapi.model;

import com.google.gson.JsonObject;

public record CloudClusterNodeInfo(
        String nodeId,
        String hostname,
        String role,
        String state,
        long lastHeartbeat
) {

    public static CloudClusterNodeInfo from(JsonObject object) {
        return new CloudClusterNodeInfo(
                string(object, "nodeId"),
                string(object, "hostname"),
                string(object, "role"),
                string(object, "state"),
                longValue(object, "lastHeartbeat")
        );
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) ? object.get(key).getAsString() : "";
    }

    private static long longValue(JsonObject object, String key) {
        return object.has(key) ? object.get(key).getAsLong() : 0L;
    }
}
