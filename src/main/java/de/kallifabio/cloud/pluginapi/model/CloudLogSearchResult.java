package de.kallifabio.cloud.pluginapi.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.kallifabio.cloud.pluginapi.util.JsonValueReader;

import java.util.ArrayList;
import java.util.List;

public record CloudLogSearchResult(
        String query,
        String level,
        int limit,
        int count,
        List<String> lines
) {

    public static CloudLogSearchResult from(JsonObject object) {
        List<String> lines = new ArrayList<>();
        JsonArray array = object != null && object.has("lines") && object.get("lines").isJsonArray()
                ? object.getAsJsonArray("lines")
                : new JsonArray();
        for (JsonElement element : array) {
            lines.add(element.getAsString());
        }
        return new CloudLogSearchResult(
                JsonValueReader.getString(object, "query", ""),
                JsonValueReader.getString(object, "level", "all"),
                JsonValueReader.getInt(object, "limit", lines.size()),
                JsonValueReader.getInt(object, "count", lines.size()),
                lines
        );
    }
}
