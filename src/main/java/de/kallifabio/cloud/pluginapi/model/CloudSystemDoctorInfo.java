package de.kallifabio.cloud.pluginapi.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.kallifabio.cloud.pluginapi.util.JsonValueReader;

import java.util.ArrayList;
import java.util.List;

public record CloudSystemDoctorInfo(
        long timestamp,
        String state,
        int criticalCount,
        int warningCount,
        int infoCount,
        int findingCount,
        int groupCount,
        List<CloudSystemDoctorFinding> findings,
        JsonObject raw
) {

    public static CloudSystemDoctorInfo from(JsonObject object) {
        JsonObject summary = object != null && object.has("summary") && object.get("summary").isJsonObject()
                ? object.getAsJsonObject("summary")
                : new JsonObject();

        List<CloudSystemDoctorFinding> findings = new ArrayList<>();
        if (object != null && object.has("findings") && object.get("findings").isJsonArray()) {
            for (JsonElement element : object.getAsJsonArray("findings")) {
                if (element.isJsonObject()) {
                    findings.add(CloudSystemDoctorFinding.from(element.getAsJsonObject()));
                }
            }
        }

        return new CloudSystemDoctorInfo(
                JsonValueReader.getLong(object, "timestamp", 0L),
                JsonValueReader.getString(summary, "state", "UNKNOWN"),
                JsonValueReader.getInt(summary, "critical", 0),
                JsonValueReader.getInt(summary, "warnings", 0),
                JsonValueReader.getInt(summary, "info", 0),
                JsonValueReader.getInt(summary, "findings", findings.size()),
                JsonValueReader.getInt(summary, "groups", 0),
                List.copyOf(findings),
                object == null ? new JsonObject() : object
        );
    }

    public boolean ok() {
        return "OK".equalsIgnoreCase(state);
    }

    public boolean hasCriticalFindings() {
        return criticalCount > 0;
    }
}
