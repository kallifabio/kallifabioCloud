package de.kallifabio.cloud.pluginapi.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.kallifabio.cloud.pluginapi.util.JsonValueReader;

import java.util.ArrayList;
import java.util.List;

public record CloudCapacityPlanInfo(
        long generatedAt,
        int groups,
        int startableGroups,
        int healthyWrappers,
        int drainingWrappers,
        int totalWrapperMemoryMb,
        int availableWrapperMemoryMb,
        int queueTotal,
        List<GroupCapacity> groupPlans,
        List<String> recommendations,
        JsonObject raw
) {

    public record GroupCapacity(
            String groupName,
            int ramMb,
            int minServers,
            int maxServers,
            int runningServers,
            int recommendedServers,
            int queuedPlayers,
            boolean maintenance,
            boolean canStartNow,
            String bestWrapperId,
            int capacityShortfallMb,
            String recommendation
    ) {
    }

    public static CloudCapacityPlanInfo from(JsonObject object) {
        JsonObject summary = object != null && object.has("summary") && object.get("summary").isJsonObject()
                ? object.getAsJsonObject("summary")
                : new JsonObject();

        List<GroupCapacity> groupPlans = new ArrayList<>();
        if (object != null && object.has("groups") && object.get("groups").isJsonArray()) {
            for (JsonElement element : object.getAsJsonArray("groups")) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject group = element.getAsJsonObject();
                groupPlans.add(new GroupCapacity(
                        JsonValueReader.getString(group, "groupName", "-"),
                        JsonValueReader.getInt(group, "ramMb", 0),
                        JsonValueReader.getInt(group, "minServers", 0),
                        JsonValueReader.getInt(group, "maxServers", 0),
                        JsonValueReader.getInt(group, "runningServers", 0),
                        JsonValueReader.getInt(group, "recommendedServers", 0),
                        JsonValueReader.getInt(group, "queuedPlayers", 0),
                        JsonValueReader.getBoolean(group, "maintenance", false),
                        JsonValueReader.getBoolean(group, "canStartNow", false),
                        JsonValueReader.getString(group, "bestWrapperId", ""),
                        JsonValueReader.getInt(group, "capacityShortfallMb", 0),
                        JsonValueReader.getString(group, "recommendation", "")
                ));
            }
        }

        List<String> recommendations = new ArrayList<>();
        if (object != null && object.has("recommendations") && object.get("recommendations").isJsonArray()) {
            for (JsonElement element : object.getAsJsonArray("recommendations")) {
                recommendations.add(element.getAsString());
            }
        }

        return new CloudCapacityPlanInfo(
                JsonValueReader.getLong(object, "generatedAt", 0L),
                JsonValueReader.getInt(summary, "groups", 0),
                JsonValueReader.getInt(summary, "startableGroups", 0),
                JsonValueReader.getInt(summary, "healthyWrappers", 0),
                JsonValueReader.getInt(summary, "drainingWrappers", 0),
                JsonValueReader.getInt(summary, "totalWrapperMemoryMb", 0),
                JsonValueReader.getInt(summary, "availableWrapperMemoryMb", 0),
                JsonValueReader.getInt(summary, "queueTotal", 0),
                List.copyOf(groupPlans),
                List.copyOf(recommendations),
                object == null ? new JsonObject() : object
        );
    }
}
