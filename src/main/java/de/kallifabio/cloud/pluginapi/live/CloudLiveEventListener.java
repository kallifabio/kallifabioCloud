package de.kallifabio.cloud.pluginapi.live;

import com.google.gson.JsonObject;

public interface CloudLiveEventListener {

    default void onOpen() {
    }

    default void onEvent(JsonObject event) {
    }

    default void onError(Throwable error) {
    }

    default void onClose(int statusCode, String reason) {
    }
}
