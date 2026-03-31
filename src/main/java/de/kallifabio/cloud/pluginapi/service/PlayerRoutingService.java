package de.kallifabio.cloud.pluginapi.service;

import de.kallifabio.cloud.pluginapi.model.CloudPlayerRoutingDecision;
import de.kallifabio.cloud.pluginapi.model.CloudServerInfo;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class PlayerRoutingService {

    private final ServerService serverService;

    public PlayerRoutingService(ServerService serverService) {
        this.serverService = serverService;
    }

    public Optional<CloudPlayerRoutingDecision> routeToBest(String groupName) {
        List<CloudServerInfo> online = serverService.list().stream()
                .filter(s -> s.groupName().equalsIgnoreCase(groupName))
                .filter(s -> "ONLINE".equalsIgnoreCase(s.status()))
                .toList();
        return online.stream()
                .min(Comparator.comparingDouble(this::score))
                .map(best -> new CloudPlayerRoutingDecision(
                        best.serverName(),
                        "lowest_load",
                        score(best)
                ));
    }

    private double score(CloudServerInfo server) {
        double playerRatio = server.maxPlayers() <= 0 ? 0.0 : (double) server.playerCount() / (double) server.maxPlayers();
        double cpu = Math.max(0.0, server.cpuUsage()) / 100.0;
        double mem = Math.max(0.0, server.memoryUsage()) / 100.0;
        double tpsPenalty = Math.max(0.0, 20.0 - server.tps()) / 20.0;
        return playerRatio * 0.45 + cpu * 0.25 + mem * 0.20 + tpsPenalty * 0.10;
    }
}
