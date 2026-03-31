package de.kallifabio.cloud.pluginapi.service;

import de.kallifabio.cloud.pluginapi.model.CloudOperationResult;
import de.kallifabio.cloud.pluginapi.model.CloudServerInfo;

import java.util.Optional;
import java.util.UUID;

public final class SurvivalPluginApi {

    private final MatchmakingService matchmakingService;
    private final ServerService serverService;

    public SurvivalPluginApi(MatchmakingService matchmakingService, ServerService serverService) {
        this.matchmakingService = matchmakingService;
        this.serverService = serverService;
    }

    public Optional<CloudServerInfo> bestSurvival() {
        return matchmakingService.bestServerForGroup("Survival");
    }

    public CloudOperationResult startSurvivalNode() {
        String serverName = "Survival-" + UUID.randomUUID().toString().substring(0, 8);
        return CloudOperationResult.from(serverService.start(serverName, "Survival"));
    }
}
