package de.kallifabio.cloud.pluginapi.live;

import com.google.gson.JsonObject;

public record LiveEventEnvelope(
        LiveEventType type,
        long timestamp,
        JsonObject payload
) {

    public static LiveEventEnvelope from(JsonObject raw) {
        LiveEventType type = LiveEventType.from(raw.has("type") ? raw.get("type").getAsString() : null);
        long timestamp = raw.has("timestamp") ? raw.get("timestamp").getAsLong() : System.currentTimeMillis();
        return new LiveEventEnvelope(type, timestamp, raw);
    }
}
