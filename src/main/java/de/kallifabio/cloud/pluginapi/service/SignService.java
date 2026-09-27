package de.kallifabio.cloud.pluginapi.service;

import com.google.gson.JsonObject;
import de.kallifabio.cloud.pluginapi.CloudApiClient;
import de.kallifabio.cloud.pluginapi.core.CloudApiPaths;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SignService {

    private final CloudApiClient client;

    public SignService(CloudApiClient client) {
        this.client = client;
    }

    public JsonObject snapshot() {
        return client.get(CloudApiPaths.SIGNS);
    }

    public JsonObject selectorSnapshot() {
        return client.get(CloudApiPaths.SELECTORS);
    }

    public JsonObject entitySelectorSnapshot() {
        return client.get(CloudApiPaths.ENTITY_SELECTORS);
    }

    public JsonObject render() {
        return client.get(CloudApiPaths.SIGNS_RENDER);
    }

    public JsonObject renderSelectors() {
        return client.get(CloudApiPaths.SELECTORS_RENDER);
    }

    public JsonObject renderEntitySelectors() {
        return client.get(CloudApiPaths.ENTITY_SELECTORS_RENDER);
    }

    public JsonObject layouts() {
        return client.get(CloudApiPaths.SIGNS_LAYOUTS);
    }

    public JsonObject selectorTemplates() {
        return client.get(CloudApiPaths.SELECTORS_TEMPLATES);
    }

    public JsonObject preview(String id) {
        return client.get(CloudApiPaths.SELECTORS_PREVIEW + "?id=" + encode(id));
    }

    public JsonObject preview(Map<String, Object> selectorDraft) {
        return client.post(CloudApiPaths.SELECTORS_PREVIEW, selectorDraft == null ? Map.of() : selectorDraft);
    }

    public JsonObject upsert(String id, String world, int x, int y, int z,
                             String serverName, String groupName, String layout, boolean enabled) {
        return upsertSelector(id, "SIGN", "VILLAGER", "", world, x, y, z, 0.0, 0.0,
                serverName, groupName, layout, enabled);
    }

    public JsonObject createSign(String serverName, String groupName, String layout) {
        return upsertAutoSelector("SIGN", "VILLAGER", "", serverName, groupName, layout, true);
    }

    public JsonObject upsertNpc(String id, String world, int x, int y, int z,
                                double yaw, double pitch, String displayName,
                                String serverName, String groupName, String layout, boolean enabled) {
        return upsertSelector(id, "NPC", "PLAYER", displayName, world, x, y, z, yaw, pitch,
                serverName, groupName, layout, enabled);
    }

    public JsonObject createNpc(String entityType, String displayName, String serverName, String groupName, String layout) {
        return upsertAutoSelector("NPC", entityType == null ? "VILLAGER" : entityType,
                displayName, serverName, groupName, layout == null ? "Npc" : layout, true);
    }

    public JsonObject upsertMob(String id, String entityType, String world, int x, int y, int z,
                                double yaw, double pitch, String displayName,
                                String serverName, String groupName, String layout, boolean enabled) {
        return upsertSelector(id, "MOB", entityType == null ? "VILLAGER" : entityType, displayName,
                world, x, y, z, yaw, pitch, serverName, groupName, layout, enabled);
    }

    public JsonObject createMob(String entityType, String displayName, String serverName, String groupName, String layout) {
        return upsertAutoSelector("MOB", entityType == null ? "ZOMBIE" : entityType,
                displayName, serverName, groupName, layout == null ? "Mob" : layout, true);
    }

    public JsonObject upsertAutoSelector(String selectorType, String entityType, String displayName,
                                         String serverName, String groupName, String layout, boolean enabled) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("selectorType", selectorType == null ? "SIGN" : selectorType);
        body.put("entityType", entityType == null ? "VILLAGER" : entityType);
        body.put("displayName", displayName == null ? "" : displayName);
        body.put("locationMode", "AUTO");
        body.put("autoLocation", true);
        body.put("world", "AUTO");
        body.put("x", 0);
        body.put("y", 0);
        body.put("z", 0);
        body.put("yaw", 0.0);
        body.put("pitch", 0.0);
        body.put("serverName", serverName == null ? "" : serverName);
        body.put("groupName", groupName == null ? "" : groupName);
        body.put("layout", layout == null ? "Default" : layout);
        body.put("enabled", enabled);
        if ("SIGN".equalsIgnoreCase(String.valueOf(body.get("selectorType")))) {
            return client.post(CloudApiPaths.SIGNS_UPSERT, body);
        }
        return client.post(CloudApiPaths.ENTITY_SELECTORS_UPSERT, body);
    }

    public JsonObject bulk(String action, String selectorType, String groupName, String category,
                           String layout, String permission) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("action", action == null ? "disable" : action);
        body.put("selectorType", selectorType == null ? "" : selectorType);
        body.put("groupName", groupName == null ? "" : groupName);
        body.put("category", category == null ? "" : category);
        body.put("layout", layout == null ? "" : layout);
        body.put("permission", permission == null ? "" : permission);
        return client.post(CloudApiPaths.SELECTORS_BULK, body);
    }

    public JsonObject cleanup(boolean disableOnly) {
        return client.post(CloudApiPaths.SELECTORS_CLEANUP, Map.of("disableOnly", disableOnly));
    }

    public JsonObject versions() {
        return client.get(CloudApiPaths.SELECTORS_VERSIONS);
    }

    public JsonObject rollback(String version) {
        return client.post(CloudApiPaths.SELECTORS_ROLLBACK, Map.of("version", version == null ? "" : version));
    }

    public JsonObject heartbeat(String id, boolean spawned, String world, int x, int y, int z, double yaw, double pitch) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id == null ? "" : id);
        body.put("spawned", spawned);
        body.put("world", world == null ? "world" : world);
        body.put("x", x);
        body.put("y", y);
        body.put("z", z);
        body.put("yaw", yaw);
        body.put("pitch", pitch);
        return client.post(CloudApiPaths.SELECTORS_HEARTBEAT, body);
    }

    public JsonObject upsertSelector(String id, String selectorType, String entityType, String displayName,
                                     String world, int x, int y, int z, double yaw, double pitch,
                                     String serverName, String groupName, String layout, boolean enabled) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id == null ? "" : id);
        body.put("selectorType", selectorType == null ? "SIGN" : selectorType);
        body.put("entityType", entityType == null ? "VILLAGER" : entityType);
        body.put("displayName", displayName == null ? "" : displayName);
        body.put("world", world == null ? "world" : world);
        body.put("x", x);
        body.put("y", y);
        body.put("z", z);
        body.put("yaw", yaw);
        body.put("pitch", pitch);
        body.put("serverName", serverName == null ? "" : serverName);
        body.put("groupName", groupName == null ? "" : groupName);
        body.put("layout", layout == null ? "Default" : layout);
        body.put("enabled", enabled);
        if ("SIGN".equalsIgnoreCase(String.valueOf(body.get("selectorType")))) {
            return client.post(CloudApiPaths.SIGNS_UPSERT, body);
        }
        return client.post(CloudApiPaths.ENTITY_SELECTORS_UPSERT, body);
    }

    public JsonObject delete(String id) {
        return client.post(CloudApiPaths.SELECTORS_DELETE, Map.of("id", id));
    }

    public JsonObject upsertLayout(String name, List<String> lines) {
        return client.post(CloudApiPaths.SELECTORS_LAYOUTS, Map.of(
                "name", name,
                "lines", lines == null ? List.of() : lines
        ));
    }

    private String encode(String value) {
        return java.net.URLEncoder.encode(value == null ? "" : value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
