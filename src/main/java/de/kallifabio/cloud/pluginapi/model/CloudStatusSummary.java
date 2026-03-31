package de.kallifabio.cloud.pluginapi.model;

import com.google.gson.JsonObject;

public record CloudStatusSummary(
        String masterId,
        int wrappersOnline,
        int serversOnline,
        int queueTotal
) {

    public static CloudStatusSummary from(JsonObject object) {
        return new CloudStatusSummary(
                string(object, "masterId"),
                intValue(object, "wrappersOnline"),
                intValue(object, "serversOnline"),
                intValue(object, "queueTotal")
        );
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) ? object.get(key).getAsString() : "";
    }

    private static int intValue(JsonObject object, String key) {
        return object.has(key) ? object.get(key).getAsInt() : 0;
    }
}
