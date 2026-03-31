package de.kallifabio.cloud.pluginapi.model;

import com.google.gson.JsonObject;

public record CloudGroupInfo(String name, JsonObject raw) {

    public static CloudGroupInfo from(String name, JsonObject raw) {
        return new CloudGroupInfo(name, raw);
    }
}
