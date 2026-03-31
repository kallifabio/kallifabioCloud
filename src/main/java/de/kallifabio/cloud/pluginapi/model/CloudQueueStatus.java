package de.kallifabio.cloud.pluginapi.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;

public record CloudQueueStatus(Map<String, Integer> perGroup, int total) {

    public static CloudQueueStatus from(JsonObject object) {
        Map<String, Integer> groups = new HashMap<>();
        int total = 0;
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isNumber()) {
                int value = entry.getValue().getAsInt();
                groups.put(entry.getKey(), value);
                total += Math.max(0, value);
            }
        }
        if (object.has("queueTotal")) {
            total = object.get("queueTotal").getAsInt();
        } else if (object.has("total")) {
            total = object.get("total").getAsInt();
        }
        return new CloudQueueStatus(groups, total);
    }
}
