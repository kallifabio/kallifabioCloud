package de.kallifabio.cloud.pluginapi.service;

import de.kallifabio.cloud.pluginapi.model.CloudServerInfo;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class MatchmakingService {

    private final ServerService serverService;

    public MatchmakingService(ServerService serverService) {
        this.serverService = serverService;
    }

    public Optional<CloudServerInfo> bestServerForGroup(String groupName) {
        List<CloudServerInfo> servers = serverService.list().stream()
                .filter(s -> s.groupName().equalsIgnoreCase(groupName))
                .filter(s -> "ONLINE".equalsIgnoreCase(s.status()))
                .toList();
        return servers.stream()
                .min(Comparator.comparingDouble(this::loadFactor));
    }

    public Optional<CloudServerInfo> bestLobby() {
        return bestServerForGroup("Lobby");
    }

    private double loadFactor(CloudServerInfo server) {
        if (server.maxPlayers() <= 0) {
            return server.playerCount();
        }
        return (double) server.playerCount() / (double) server.maxPlayers();
    }
}
