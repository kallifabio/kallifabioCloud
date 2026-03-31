package de.kallifabio.cloud.pluginapi.game;

import de.kallifabio.cloud.pluginapi.CloudPluginApi;
import de.kallifabio.cloud.pluginapi.model.CloudOperationResult;
import de.kallifabio.cloud.pluginapi.request.ServerActionRequest;
import de.kallifabio.cloud.pluginapi.request.ServerStartRequest;

import java.util.UUID;

public final class GameModeCloudFacade {

    private final CloudPluginApi api;

    public GameModeCloudFacade(CloudPluginApi api) {
        this.api = api;
    }

    public CloudOperationResult startLobbyInstance(String groupName) {
        String serverName = "Lobby-" + UUID.randomUUID().toString().substring(0, 8);
        return api.servers().start(new ServerStartRequest(serverName, groupName));
    }

    public CloudOperationResult startMatchInstance(String groupName, String matchPrefix) {
        String serverName = matchPrefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        return api.servers().start(new ServerStartRequest(serverName, groupName));
    }

    public CloudOperationResult stopInstance(String serverName) {
        return api.servers().stop(new ServerActionRequest(serverName));
    }

    public CloudOperationResult restartInstance(String serverName) {
        return api.servers().restart(new ServerActionRequest(serverName));
    }

    public CloudOperationResult movePartyToServer(String partyId, String targetServer) {
        return CloudOperationResult.from(api.players().switchParty(partyId, targetServer));
    }
}
