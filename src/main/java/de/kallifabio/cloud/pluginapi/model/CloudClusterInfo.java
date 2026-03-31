package de.kallifabio.cloud.pluginapi.model;

import com.google.gson.JsonObject;

public record CloudClusterInfo(
        String masterId,
        boolean primary,
        String primaryMasterId,
        String clusterState,
        int clusterNodes
) {

    public static CloudClusterInfo from(JsonObject object) {
        return new CloudClusterInfo(
                string(object, "masterId"),
                bool(object, "isPrimary"),
                string(object, "primaryMasterId"),
                string(object, "clusterState"),
                intValue(object, "clusterNodes")
        );
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) ? object.get(key).getAsString() : "";
    }

    private static int intValue(JsonObject object, String key) {
        return object.has(key) ? object.get(key).getAsInt() : 0;
    }

    private static boolean bool(JsonObject object, String key) {
        return object.has(key) && object.get(key).getAsBoolean();
    }
}
