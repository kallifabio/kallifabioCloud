package de.kallifabio.cloud.pluginapi.model;

import com.google.gson.JsonObject;
import de.kallifabio.cloud.pluginapi.util.JsonValueReader;

public record CloudConfigValue(String key, String value) {

    public static CloudConfigValue from(JsonObject object) {
        return new CloudConfigValue(
                JsonValueReader.getString(object, "key", ""),
                JsonValueReader.getString(object, "value", "")
        );
    }
}
