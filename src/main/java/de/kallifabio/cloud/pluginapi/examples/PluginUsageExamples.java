package de.kallifabio.cloud.pluginapi.examples;

import de.kallifabio.cloud.pluginapi.CloudApiConfig;
import de.kallifabio.cloud.pluginapi.CloudPluginApi;
import de.kallifabio.cloud.pluginapi.model.CloudAuthInfo;
import de.kallifabio.cloud.pluginapi.model.CloudOperationResult;
import de.kallifabio.cloud.pluginapi.model.CloudServerInfo;
import de.kallifabio.cloud.pluginapi.request.PermissionAssignRequest;
import de.kallifabio.cloud.pluginapi.request.ServerActionRequest;
import de.kallifabio.cloud.pluginapi.request.ServerStartRequest;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Example helper methods for Bukkit/Spigot/Paper plugins.
 * You can copy these snippets into your own plugin class (Lobby, Bedwars, etc.).
 */
public final class PluginUsageExamples {

    private PluginUsageExamples() {
    }

    public static CloudPluginApi createApi(String apiUrl, String apiKey) {
        CloudApiConfig config = CloudApiConfig.builder(apiUrl, apiKey).build();
        return new CloudPluginApi(config);
    }

    public static CloudAuthInfo checkAuthentication(CloudPluginApi api) {
        return api.auth().me();
    }

    public static List<CloudServerInfo> listOnlineServers(CloudPluginApi api) {
        return api.servers().list();
    }

    public static void startDynamicBedwarsServer(CloudPluginApi api, String groupName) {
        String serverName = groupName + "-" + System.currentTimeMillis();
        api.servers().start(new ServerStartRequest(serverName, groupName));
    }

    public static void assignCloudPermissionGroup(CloudPluginApi api, UUID playerUuid, String groupName) {
        api.permissions().assignGroup(new PermissionAssignRequest(playerUuid.toString(), groupName));
    }

    public static void grantTemporaryPermission(CloudPluginApi api, UUID playerUuid, String permission, long durationSeconds) {
        api.permissions().setTempPermission(playerUuid.toString(), permission, durationSeconds);
    }

    public static CloudOperationResult startLobbyQuick(CloudPluginApi api) {
        return api.gameModes().startLobbyInstance("Lobby");
    }

    public static CloudOperationResult restartAndWait(CloudPluginApi api, String serverName) {
        return api.orchestration().restartAndWaitOnline(
                new ServerActionRequest(serverName),
                Duration.ofSeconds(60),
                Duration.ofSeconds(2)
        );
    }
}
