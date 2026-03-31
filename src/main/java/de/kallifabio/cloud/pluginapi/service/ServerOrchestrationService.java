package de.kallifabio.cloud.pluginapi.service;

import de.kallifabio.cloud.pluginapi.model.CloudOperationResult;
import de.kallifabio.cloud.pluginapi.model.CloudServerInfo;
import de.kallifabio.cloud.pluginapi.request.ServerActionRequest;
import de.kallifabio.cloud.pluginapi.request.ServerStartRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public final class ServerOrchestrationService {

    private final ServerService serverService;

    public ServerOrchestrationService(ServerService serverService) {
        this.serverService = serverService;
    }

    public CloudOperationResult startAndWaitOnline(ServerStartRequest request, Duration timeout, Duration pollInterval) {
        CloudOperationResult start = serverService.start(request);
        if (!start.success()) {
            return start;
        }
        Optional<CloudServerInfo> online = waitForStatus(request.serverName(), "ONLINE", timeout, pollInterval);
        if (online.isPresent()) {
            return new CloudOperationResult(true, "Server is ONLINE", "", start.raw());
        }
        return new CloudOperationResult(false, "", "Timeout waiting for ONLINE", start.raw());
    }

    public CloudOperationResult restartAndWaitOnline(ServerActionRequest request, Duration timeout, Duration pollInterval) {
        CloudOperationResult restart = serverService.restart(request);
        if (!restart.success()) {
            return restart;
        }
        Optional<CloudServerInfo> online = waitForStatus(request.serverName(), "ONLINE", timeout, pollInterval);
        if (online.isPresent()) {
            return new CloudOperationResult(true, "Server restarted and ONLINE", "", restart.raw());
        }
        return new CloudOperationResult(false, "", "Timeout waiting for ONLINE after restart", restart.raw());
    }

    public Optional<CloudServerInfo> waitForStatus(String serverName, String expectedStatus, Duration timeout, Duration pollInterval) {
        Instant until = Instant.now().plus(timeout);
        while (Instant.now().isBefore(until)) {
            Optional<CloudServerInfo> current = serverService.list().stream()
                    .filter(s -> s.serverName().equalsIgnoreCase(serverName))
                    .findFirst();
            if (current.isPresent() && current.get().status().equalsIgnoreCase(expectedStatus)) {
                return current;
            }
            try {
                Thread.sleep(Math.max(100L, pollInterval.toMillis()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
        }
        return Optional.empty();
    }
}
