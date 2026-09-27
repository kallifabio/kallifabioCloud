package de.kallifabio.cloud.pluginapi.modloader;

import de.kallifabio.cloud.pluginapi.CloudPluginApi;
import de.kallifabio.cloud.pluginapi.model.CloudOperationResult;
import de.kallifabio.cloud.pluginapi.model.CloudPermissionProfileInfo;
import de.kallifabio.cloud.pluginapi.model.CloudPlayerRoutingDecision;
import de.kallifabio.cloud.pluginapi.model.CloudServerInfo;
import de.kallifabio.cloud.pluginapi.request.GroupUpdateRequest;
import de.kallifabio.cloud.pluginapi.request.ServerActionRequest;
import de.kallifabio.cloud.pluginapi.request.ServerStartRequest;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Dependency-free facade for Forge, NeoForge and Fabric mods.
 * <p>
 * Keep this class free of modloader imports so it can be shaded into mods or
 * used from a tiny loader-specific adapter without pulling Bukkit/Bungee APIs.
 */
public class ModLoaderCloudFacade {

    private final CloudPluginApi api;
    private final CloudModLoader loader;

    public ModLoaderCloudFacade(CloudPluginApi api, CloudModLoader loader) {
        this.api = Objects.requireNonNull(api, "api");
        this.loader = Objects.requireNonNull(loader, "loader");
    }

    public CloudModLoader loader() {
        return loader;
    }

    public String softwareValue() {
        return loader.softwareValue();
    }

    public CloudOperationResult startServer(String serverName, String groupName) {
        return api.servers().start(new ServerStartRequest(serverName, groupName));
    }

    public CloudOperationResult startGeneratedServer(String groupName) {
        String safeGroup = groupName == null || groupName.isBlank() ? loader.displayName() : groupName;
        String serverName = safeGroup + "-" + System.currentTimeMillis();
        return startServer(serverName, safeGroup);
    }

    public CloudOperationResult stopServer(String serverName) {
        return api.servers().stop(new ServerActionRequest(serverName));
    }

    public CloudOperationResult restartServer(String serverName) {
        return api.servers().restart(new ServerActionRequest(serverName));
    }

    public List<CloudServerInfo> onlineServers(String groupName) {
        return api.servers().list().stream()
                .filter(server -> server.groupName().equalsIgnoreCase(groupName))
                .filter(server -> "ONLINE".equalsIgnoreCase(server.status()))
                .toList();
    }

    public Optional<CloudServerInfo> bestServer(String groupName) {
        return api.matchmaking().bestServerForGroup(groupName);
    }

    public Optional<CloudPlayerRoutingDecision> routeToBest(String groupName) {
        return api.routing().routeToBest(groupName);
    }

    public int queueSize(String groupName) {
        return api.queue().groupSize(groupName);
    }

    public CloudPermissionProfileInfo permissionProfile(UUID playerUuid) {
        return api.permissions().profile(playerUuid.toString());
    }

    public boolean hasPermission(UUID playerUuid, String permission) {
        return api.permissions().check(playerUuid.toString(), permission);
    }

    public CloudOperationResult setGroupSoftware(String groupName) {
        return updateGroupSetting(groupName, "Software", loader.softwareValue());
    }

    public List<CloudOperationResult> configureGroup(String groupName, int ramMb, int maxPlayers) {
        return List.of(
                setGroupSoftware(groupName),
                updateGroupSetting(groupName, "Ram", String.valueOf(ramMb)),
                updateGroupSetting(groupName, "MaxPlayers", String.valueOf(maxPlayers)),
                updateGroupSetting(groupName, "StartArgs", recommendedStartArgs())
        );
    }

    public CloudOperationResult setJavaArgs(String groupName, List<String> args) {
        return updateGroupSetting(groupName, "JavaArgs", joinArgs(args));
    }

    public CloudOperationResult setStartArgs(String groupName, List<String> args) {
        return updateGroupSetting(groupName, "StartArgs", joinArgs(args));
    }

    public String recommendedStartArgs() {
        return switch (loader) {
            case FORGE, NEOFORGE, FABRIC -> "nogui";
        };
    }

    private CloudOperationResult updateGroupSetting(String groupName, String key, String value) {
        return api.groups().updateGroupSetting(new GroupUpdateRequest(groupName, key, value));
    }

    private String joinArgs(List<String> args) {
        return String.join(" ", args == null ? List.of() : args);
    }
}
