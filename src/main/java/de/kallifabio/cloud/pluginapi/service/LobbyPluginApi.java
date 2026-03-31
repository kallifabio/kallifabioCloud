package de.kallifabio.cloud.pluginapi.service;

import de.kallifabio.cloud.pluginapi.model.CloudOperationResult;
import de.kallifabio.cloud.pluginapi.model.CloudServerInfo;

import java.util.Optional;
import java.util.UUID;

public final class LobbyPluginApi {

    private final MatchmakingService matchmakingService;
    private final ServerService serverService;

    public LobbyPluginApi(MatchmakingService matchmakingService, ServerService serverService) {
        this.matchmakingService = matchmakingService;
        this.serverService = serverService;
    }

    public Optional<CloudServerInfo> bestLobby() {
        return matchmakingService.bestLobby();
    }

    public CloudOperationResult startLobbyInstance() {
        String serverName = "Lobby-" + UUID.randomUUID().toString().substring(0, 8);
        return CloudOperationResult.from(serverService.start(serverName, "Lobby"));
    }
}
