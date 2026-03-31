package de.kallifabio.cloud.pluginapi.service;

import com.google.gson.JsonObject;
import de.kallifabio.cloud.pluginapi.CloudApiClient;
import de.kallifabio.cloud.pluginapi.model.CloudOperationResult;
import de.kallifabio.cloud.pluginapi.request.GroupCreateRequest;
import de.kallifabio.cloud.pluginapi.request.GroupDeleteRequest;
import de.kallifabio.cloud.pluginapi.request.GroupUpdateRequest;

import java.util.Map;

public final class GroupService {

    private final CloudApiClient client;

    public GroupService(CloudApiClient client) {
        this.client = client;
    }

    public JsonObject listGroups() {
        return client.get("/api/v1/groups");
    }

    public JsonObject createGroup(String groupName, String parent) {
        return client.post("/api/v1/groups/create", Map.of(
                "groupName", groupName,
                "parent", parent == null ? "" : parent
        ));
    }

    public CloudOperationResult createGroup(GroupCreateRequest request) {
        return CloudOperationResult.from(createGroup(request.groupName(), request.parent()));
    }

    public JsonObject deleteGroup(String groupName) {
        return client.post("/api/v1/groups/delete", Map.of("groupName", groupName));
    }

    public CloudOperationResult deleteGroup(GroupDeleteRequest request) {
        return CloudOperationResult.from(deleteGroup(request.groupName()));
    }

    public JsonObject updateGroupSetting(String groupName, String key, String value) {
        return client.post("/api/v1/groups/update", Map.of(
                "groupName", groupName,
                "key", key,
                "value", value
        ));
    }

    public CloudOperationResult updateGroupSetting(GroupUpdateRequest request) {
        return CloudOperationResult.from(updateGroupSetting(request.groupName(), request.key(), request.value()));
    }
}
