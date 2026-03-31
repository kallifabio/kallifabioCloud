package de.kallifabio.cloud.pluginapi.service;

import de.kallifabio.cloud.pluginapi.model.CloudOperationResult;
import de.kallifabio.cloud.pluginapi.model.CloudServerInfo;

import java.util.Optional;
import java.util.UUID;

public final class BedwarsPluginApi {

    private final MatchmakingService matchmakingService;
    private final ServerService serverService;

    public BedwarsPluginApi(MatchmakingService matchmakingService, ServerService serverService) {
        this.matchmakingService = matchmakingService;
        this.serverService = serverService;
    }

    public Optional<CloudServerInfo> bestArena() {
        return matchmakingService.bestServerForGroup("Bedwars");
    }

    public CloudOperationResult startArena() {
        String serverName = "Bedwars-" + UUID.randomUUID().toString().substring(0, 8);
        return CloudOperationResult.from(serverService.start(serverName, "Bedwars"));
    }
}
