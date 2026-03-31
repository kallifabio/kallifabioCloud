package de.kallifabio.cloud.pluginapi.service;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.kallifabio.cloud.pluginapi.model.CloudGroupInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class GroupCatalogService {

    private final GroupService groupService;

    public GroupCatalogService(GroupService groupService) {
        this.groupService = groupService;
    }

    public List<CloudGroupInfo> list() {
        JsonObject response = groupService.listGroups();
        List<CloudGroupInfo> groups = new ArrayList<>();
        if (!response.has("groups") || !response.get("groups").isJsonObject()) {
            return groups;
        }
        JsonObject rawGroups = response.getAsJsonObject("groups");
        for (Map.Entry<String, JsonElement> entry : rawGroups.entrySet()) {
            if (entry.getValue().isJsonObject()) {
                groups.add(CloudGroupInfo.from(entry.getKey(), entry.getValue().getAsJsonObject()));
            }
        }
        return groups;
    }
}
