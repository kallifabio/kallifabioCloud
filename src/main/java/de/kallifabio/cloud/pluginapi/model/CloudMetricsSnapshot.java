package de.kallifabio.cloud.pluginapi.model;

import com.google.gson.JsonObject;

public record CloudMetricsSnapshot(
        int runningServers,
        int onlineWrappers,
        int queueTotal,
        double avgTps,
        double avgCpuUsage,
        double avgMemoryUsage,
        long timestamp
) {

    public static CloudMetricsSnapshot from(JsonObject object) {
        return new CloudMetricsSnapshot(
                intValue(object, "runningServers"),
                intValue(object, "onlineWrappers"),
                intValue(object, "queueTotal"),
                doubleValue(object, "avgTps"),
                doubleValue(object, "avgCpuUsage"),
                doubleValue(object, "avgMemoryUsage"),
                longValue(object, "timestamp")
        );
    }

    private static int intValue(JsonObject object, String key) {
        return object.has(key) ? object.get(key).getAsInt() : 0;
    }

    private static double doubleValue(JsonObject object, String key) {
        return object.has(key) ? object.get(key).getAsDouble() : 0.0;
    }

    private static long longValue(JsonObject object, String key) {
        return object.has(key) ? object.get(key).getAsLong() : 0L;
    }
}
