package de.kallifabio.cloud.pluginapi.model;

import com.google.gson.JsonObject;

public record CloudOperationResult(boolean success, String message, String error, JsonObject raw) {

    public static CloudOperationResult from(JsonObject object) {
        String error = object.has("error") ? object.get("error").getAsString() : "";
        String message = object.has("message") ? object.get("message").getAsString() : "";
        boolean success = error.isBlank();
        return new CloudOperationResult(success, message, error, object);
    }
}
