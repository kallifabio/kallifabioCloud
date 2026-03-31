package de.kallifabio.cloud.pluginapi.model;

import com.google.gson.JsonObject;
import de.kallifabio.cloud.pluginapi.util.JsonValueReader;

public record CloudOverviewSnapshot(long timestamp, int queueTotal, JsonObject raw) {

    public static CloudOverviewSnapshot from(JsonObject object) {
        return new CloudOverviewSnapshot(
                JsonValueReader.getLong(object, "timestamp", 0L),
                JsonValueReader.getInt(object, "queueTotal", 0),
                object
        );
    }
}
