package de.kallifabio.cloud.pluginapi.service;

import de.kallifabio.cloud.pluginapi.model.CloudWrapperCapacity;
import de.kallifabio.cloud.pluginapi.model.CloudWrapperInfo;

import java.util.List;

public final class WrapperCapacityService {

    private final WrapperService wrapperService;

    public WrapperCapacityService(WrapperService wrapperService) {
        this.wrapperService = wrapperService;
    }

    public List<CloudWrapperCapacity> snapshot() {
        return wrapperService.list().stream()
                .map(w -> new CloudWrapperCapacity(
                        w.wrapperId(),
                        w.availableMemory(),
                        w.maxMemory(),
                        w.activeServers(),
                        w.cpuUsage(),
                        w.draining()
                ))
                .toList();
    }

    public double averageMemoryUsedRatio() {
        List<CloudWrapperCapacity> wrappers = snapshot();
        if (wrappers.isEmpty()) {
            return 0.0;
        }
        return wrappers.stream().mapToDouble(CloudWrapperCapacity::memoryUsedRatio).average().orElse(0.0);
    }
}
