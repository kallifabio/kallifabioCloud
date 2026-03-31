package de.kallifabio.cloud.pluginapi.service;

import de.kallifabio.cloud.pluginapi.model.CloudServerInfo;
import de.kallifabio.cloud.pluginapi.model.CloudServerLoadSnapshot;

import java.util.List;

public final class ServerLoadService {

    private final ServerService serverService;

    public ServerLoadService(ServerService serverService) {
        this.serverService = serverService;
    }

    public List<CloudServerLoadSnapshot> snapshot() {
        return serverService.list().stream()
                .map(s -> new CloudServerLoadSnapshot(
                        s.serverName(),
                        s.groupName(),
                        s.cpuUsage(),
                        s.memoryUsage(),
                        s.tps(),
                        s.playerCount(),
                        s.maxPlayers()))
                .toList();
    }

    public double averageCpu() {
        List<CloudServerInfo> servers = serverService.list();
        if (servers.isEmpty()) {
            return 0.0;
        }
        return servers.stream().mapToDouble(CloudServerInfo::cpuUsage).average().orElse(0.0);
    }
}
