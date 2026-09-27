package de.kallifabio.cloud.pluginapi.model;

import com.google.gson.JsonObject;
import de.kallifabio.cloud.pluginapi.util.JsonValueReader;

public record CloudSystemDoctorFinding(
        String severity,
        String id,
        String message,
        String recommendation,
        JsonObject raw
) {

    public static CloudSystemDoctorFinding from(JsonObject object) {
        return new CloudSystemDoctorFinding(
                JsonValueReader.getString(object, "severity", "INFO"),
                JsonValueReader.getString(object, "id", ""),
                JsonValueReader.getString(object, "message", ""),
                JsonValueReader.getString(object, "recommendation", ""),
                object == null ? new JsonObject() : object
        );
    }

    public boolean critical() {
        return "CRITICAL".equalsIgnoreCase(severity);
    }

    public boolean warning() {
        return "WARNING".equalsIgnoreCase(severity);
    }
}
