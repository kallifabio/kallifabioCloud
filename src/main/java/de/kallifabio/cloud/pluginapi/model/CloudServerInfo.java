package de.kallifabio.cloud.pluginapi.model;

import com.google.gson.JsonObject;

public record CloudServerInfo(
        String serverName,
        String groupName,
        String status,
        String wrapperId,
        int playerCount,
        int maxPlayers,
        int port,
        double cpuUsage,
        double memoryUsage,
        double tps
) {

    public static CloudServerInfo from(JsonObject object) {
        return new CloudServerInfo(
                getString(object, "serverName"),
                getString(object, "groupName"),
                getString(object, "status"),
                getString(object, "wrapperId"),
                getInt(object, "playerCount"),
                getInt(object, "maxPlayers"),
                getInt(object, "port"),
                getDouble(object, "cpuUsage"),
                getDouble(object, "memoryUsage"),
                getDouble(object, "tps")
        );
    }

    private static String getString(JsonObject object, String key) {
        return object.has(key) ? object.get(key).getAsString() : "";
    }

    private static int getInt(JsonObject object, String key) {
        return object.has(key) ? object.get(key).getAsInt() : 0;
    }

    private static double getDouble(JsonObject object, String key) {
        return object.has(key) ? object.get(key).getAsDouble() : 0.0;
    }
}
