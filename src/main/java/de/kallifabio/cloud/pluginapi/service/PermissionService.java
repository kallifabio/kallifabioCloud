package de.kallifabio.cloud.pluginapi.service;

import com.google.gson.JsonObject;
import de.kallifabio.cloud.pluginapi.CloudApiClient;
import de.kallifabio.cloud.pluginapi.model.CloudPermissionProfileInfo;
import de.kallifabio.cloud.pluginapi.model.CloudOperationResult;
import de.kallifabio.cloud.pluginapi.request.PermissionAssignRequest;
import de.kallifabio.cloud.pluginapi.request.PermissionGroupUpsertRequest;
import de.kallifabio.cloud.pluginapi.request.TempPermissionRequest;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;

public final class PermissionService {

    private final CloudApiClient client;

    public PermissionService(CloudApiClient client) {
        this.client = client;
    }

    public JsonObject upsertGroup(String name, String parent, int weight, String prefix, String suffix, List<String> permissions) {
        return client.post("/api/v1/permissions/group", Map.of(
                "name", name,
                "parent", parent == null ? "" : parent,
                "weight", weight,
                "prefix", prefix == null ? "" : prefix,
                "suffix", suffix == null ? "" : suffix,
                "permissions", permissions == null ? List.of() : permissions
        ));
    }

    public CloudOperationResult upsertGroup(PermissionGroupUpsertRequest request) {
        return CloudOperationResult.from(upsertGroup(
                request.name(),
                request.parent(),
                request.weight(),
                request.prefix(),
                request.suffix(),
                request.permissions()
        ));
    }

    public JsonObject assignGroup(String playerUuid, String group) {
        return client.post("/api/v1/permissions/assign", Map.of(
                "playerUuid", playerUuid,
                "group", group
        ));
    }

    public CloudOperationResult assignGroup(PermissionAssignRequest request) {
        return CloudOperationResult.from(assignGroup(request.playerUuid(), request.group()));
    }

    public JsonObject setTempPermission(String playerUuid, String permission, long durationSeconds) {
        return client.post("/api/v1/permissions/temp", Map.of(
                "playerUuid", playerUuid,
                "permission", permission,
                "durationSeconds", durationSeconds
        ));
    }

    public CloudOperationResult setTempPermission(TempPermissionRequest request) {
        return CloudOperationResult.from(setTempPermission(
                request.playerUuid(),
                request.permission(),
                request.durationSeconds()
        ));
    }

    public JsonObject profileRaw(String playerUuid) {
        return client.get("/api/v1/permissions/profile", Map.of("playerUuid", playerUuid));
    }

    public CloudPermissionProfileInfo profile(String playerUuid) {
        JsonObject raw = profileRaw(playerUuid);
        List<String> permissions = new ArrayList<>();
        if (raw.has("permissions") && raw.get("permissions").isJsonArray()) {
            raw.getAsJsonArray("permissions").forEach(element -> permissions.add(element.getAsString()));
        }
        return new CloudPermissionProfileInfo(
                raw.has("playerUuid") ? raw.get("playerUuid").getAsString() : playerUuid,
                raw.has("primaryGroup") ? raw.get("primaryGroup").getAsString() : "default",
                raw.has("prefix") ? raw.get("prefix").getAsString() : "",
                raw.has("suffix") ? raw.get("suffix").getAsString() : "",
                permissions
        );
    }

    public boolean check(String playerUuid, String permission) {
        JsonObject raw = client.get("/api/v1/permissions/check", Map.of(
                "playerUuid", playerUuid,
                "permission", permission
        ));
        return raw.has("allowed") && raw.get("allowed").getAsBoolean();
    }
}
