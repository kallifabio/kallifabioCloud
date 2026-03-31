package de.kallifabio.cloud.pluginapi.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public record CloudTemplateDiffInfo(String group, List<String> changedFiles, int count) {

    public static CloudTemplateDiffInfo from(JsonObject object) {
        List<String> changed = new ArrayList<>();
        JsonArray files = object.has("changedFiles") ? object.getAsJsonArray("changedFiles") : new JsonArray();
        for (JsonElement element : files) {
            changed.add(element.getAsString());
        }
        int count = object.has("count") ? object.get("count").getAsInt() : changed.size();
        return new CloudTemplateDiffInfo(
                object.has("group") ? object.get("group").getAsString() : "",
                changed,
                count
        );
    }
}
