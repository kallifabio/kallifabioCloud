package de.kallifabio.cloud.pluginapi.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.kallifabio.cloud.pluginapi.util.JsonValueReader;

import java.util.ArrayList;
import java.util.List;

public record CloudSystemDiagnosticsInfo(
        long generatedAt,
        String state,
        int score,
        int runningServers,
        int onlineServers,
        int connectedWrappers,
        int queueTotal,
        int activeAlerts,
        List<String> issues,
        List<String> recommendations,
        JsonObject raw
) {

    public static CloudSystemDiagnosticsInfo from(JsonObject object) {
        JsonObject summary = object != null && object.has("summary") && object.get("summary").isJsonObject()
                ? object.getAsJsonObject("summary")
                : new JsonObject();
        List<String> issues = new ArrayList<>();
        if (object != null && object.has("issues") && object.get("issues").isJsonArray()) {
            for (JsonElement element : object.getAsJsonArray("issues")) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject issue = element.getAsJsonObject();
                String severity = JsonValueReader.getString(issue, "severity", "INFO");
                String component = JsonValueReader.getString(issue, "component", "-");
                String message = JsonValueReader.getString(issue, "message", "-");
                issues.add(severity + " " + component + ": " + message);
            }
        }

        List<String> recommendations = new ArrayList<>();
        if (object != null && object.has("recommendations") && object.get("recommendations").isJsonArray()) {
            for (JsonElement element : object.getAsJsonArray("recommendations")) {
                recommendations.add(element.getAsString());
            }
        }

        return new CloudSystemDiagnosticsInfo(
                JsonValueReader.getLong(object, "generatedAt", 0L),
                JsonValueReader.getString(object, "state", "UNKNOWN"),
                JsonValueReader.getInt(object, "score", 0),
                JsonValueReader.getInt(summary, "runningServers", 0),
                JsonValueReader.getInt(summary, "onlineServers", 0),
                JsonValueReader.getInt(summary, "connectedWrappers", 0),
                JsonValueReader.getInt(summary, "queueTotal", 0),
                JsonValueReader.getInt(summary, "activeAlerts", 0),
                List.copyOf(issues),
                List.copyOf(recommendations),
                object == null ? new JsonObject() : object
        );
    }
}
