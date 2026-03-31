package de.kallifabio.cloud.pluginapi.model;

import com.google.gson.JsonObject;

public record CloudHealthInfo(String status, long timestamp, String version) {

    public static CloudHealthInfo from(JsonObject object) {
        return new CloudHealthInfo(
                object.has("status") ? object.get("status").getAsString() : "UNKNOWN",
                object.has("timestamp") ? object.get("timestamp").getAsLong() : 0L,
                object.has("version") ? object.get("version").getAsString() : "unknown"
        );
    }
}
