package de.kallifabio.cloud.pluginapi;

import java.time.Duration;

public final class CloudApiFactory {

    private CloudApiFactory() {
    }

    public static CloudPluginApi quick(String baseUrl, String apiKey) {
        return CloudPluginApi.create(baseUrl, apiKey);
    }

    public static CloudPluginApi tuned(String baseUrl, String apiKey, Duration connectTimeout, Duration readTimeout) {
        CloudApiConfig config = CloudApiConfig.builder(baseUrl, apiKey)
                .connectTimeout(connectTimeout)
                .readTimeout(readTimeout)
                .build();
        return new CloudPluginApi(config);
    }
}
