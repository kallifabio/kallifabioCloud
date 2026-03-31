package de.kallifabio.cloud.pluginapi.model;

import com.google.gson.JsonObject;

public record CloudAlertInfo(
        String severity,
        String component,
        String message,
        long timestamp
) {

    public static CloudAlertInfo from(JsonObject object) {
        return new CloudAlertInfo(
                string(object, "severity"),
                string(object, "component"),
                string(object, "message"),
                longValue(object, "timestamp")
        );
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) ? object.get(key).getAsString() : "";
    }

    private static long longValue(JsonObject object, String key) {
        return object.has(key) ? object.get(key).getAsLong() : 0L;
    }
}
