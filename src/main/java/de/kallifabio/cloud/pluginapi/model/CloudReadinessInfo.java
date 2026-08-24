package de.kallifabio.cloud.pluginapi.model;

import com.google.gson.JsonObject;
import de.kallifabio.cloud.pluginapi.util.JsonValueReader;

public record CloudReadinessInfo(
        boolean ready,
        String status,
        long timestamp,
        long uptimeMs,
        String diagnosticsState,
        boolean masterReady,
        boolean apiReady,
        boolean websocketReady,
        boolean healthyWrapper,
        JsonObject raw
) {

    public static CloudReadinessInfo from(JsonObject object) {
        JsonObject components = object != null && object.has("components") && object.get("components").isJsonObject()
                ? object.getAsJsonObject("components")
                : new JsonObject();
        return new CloudReadinessInfo(
                JsonValueReader.getBoolean(object, "ready", false),
                JsonValueReader.getString(object, "status", "UNKNOWN"),
                JsonValueReader.getLong(object, "timestamp", 0L),
                JsonValueReader.getLong(object, "uptimeMs", 0L),
                JsonValueReader.getString(object, "diagnosticsState", "UNKNOWN"),
                JsonValueReader.getBoolean(components, "master", false),
                JsonValueReader.getBoolean(components, "api", false),
                JsonValueReader.getBoolean(components, "websocket", false),
                JsonValueReader.getBoolean(components, "healthyWrapper", false),
                object == null ? new JsonObject() : object
        );
    }
}
