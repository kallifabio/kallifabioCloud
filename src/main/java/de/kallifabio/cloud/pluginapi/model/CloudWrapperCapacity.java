package de.kallifabio.cloud.pluginapi.model;

public record CloudWrapperCapacity(
        String wrapperId,
        int availableMemory,
        int maxMemory,
        int activeServers,
        double cpuUsage,
        boolean draining
) {
    public double memoryUsedRatio() {
        if (maxMemory <= 0) {
            return 0.0;
        }
        return (double) (maxMemory - availableMemory) / (double) maxMemory;
    }
}
