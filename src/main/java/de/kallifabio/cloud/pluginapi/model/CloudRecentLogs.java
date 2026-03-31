package de.kallifabio.cloud.pluginapi.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public record CloudRecentLogs(List<String> lines) {

    public static CloudRecentLogs from(JsonObject object) {
        List<String> lines = new ArrayList<>();
        JsonArray array = object.has("lines") ? object.getAsJsonArray("lines") : new JsonArray();
        for (JsonElement element : array) {
            lines.add(element.getAsString());
        }
        return new CloudRecentLogs(lines);
    }
}
