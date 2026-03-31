package de.kallifabio.cloud.pluginapi.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.kallifabio.cloud.pluginapi.util.JsonValueReader;

import java.util.ArrayList;
import java.util.List;

public record CloudTemplateVersionsInfo(String group, List<String> versions) {

    public static CloudTemplateVersionsInfo from(JsonObject object) {
        List<String> versions = new ArrayList<>();
        JsonArray array = object.has("versions") ? object.getAsJsonArray("versions") : new JsonArray();
        for (JsonElement element : array) {
            versions.add(element.getAsString());
        }
        return new CloudTemplateVersionsInfo(JsonValueReader.getString(object, "group", ""), versions);
    }
}
