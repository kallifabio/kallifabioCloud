package de.kallifabio.cloud.pluginapi.model;

import com.google.gson.JsonObject;

public record CloudAuthInfo(boolean authenticated, String role, String wsUrl, String wsTicket) {

    public static CloudAuthInfo from(JsonObject object) {
        return new CloudAuthInfo(
                object.has("authenticated") && object.get("authenticated").getAsBoolean(),
                object.has("role") ? object.get("role").getAsString() : "UNKNOWN",
                object.has("wsUrl") ? object.get("wsUrl").getAsString() : "",
                object.has("wsTicket") ? object.get("wsTicket").getAsString() : ""
        );
    }
}
