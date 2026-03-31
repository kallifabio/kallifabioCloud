package de.kallifabio.cloud.pluginapi.core;

public record CloudEnvironment(
        String apiBaseUrl,
        String wsUrl,
        String region,
        String nodeName
) {
}
