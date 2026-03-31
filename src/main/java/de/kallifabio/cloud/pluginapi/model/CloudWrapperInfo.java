package de.kallifabio.cloud.pluginapi.model;

import com.google.gson.JsonObject;

public record CloudWrapperInfo(
        String wrapperId,
        String hostname,
        int activeServers,
        int availableMemory,
        int maxMemory,
        double cpuUsage,
        long lastHeartbeat,
        boolean draining
) {

    public static CloudWrapperInfo from(JsonObject object) {
        return new CloudWrapperInfo(
                getString(object, "wrapperId"),
                getString(object, "hostname"),
                getInt(object, "activeServers"),
                getInt(object, "availableMemory"),
                getInt(object, "maxMemory"),
                getDouble(object, "cpuUsage"),
                getLong(object, "lastHeartbeat"),
                object.has("draining") && object.get("draining").getAsBoolean()
        );
    }

    private static String getString(JsonObject object, String key) {
        return object.has(key) ? object.get(key).getAsString() : "";
    }

    private static int getInt(JsonObject object, String key) {
        return object.has(key) ? object.get(key).getAsInt() : 0;
    }

    private static long getLong(JsonObject object, String key) {
        return object.has(key) ? object.get(key).getAsLong() : 0L;
    }

    private static double getDouble(JsonObject object, String key) {
        return object.has(key) ? object.get(key).getAsDouble() : 0.0;
    }
}
