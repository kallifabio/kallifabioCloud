package de.kallifabio.cloud.pluginapi;

import java.time.Duration;
import java.util.Objects;

public final class CloudApiConfig {

    private final String baseUrl;
    private final String apiKey;
    private final Duration connectTimeout;
    private final Duration readTimeout;
    private final Duration writeTimeout;
    private final String userAgent;

    private CloudApiConfig(Builder builder) {
        this.baseUrl = normalizeBaseUrl(builder.baseUrl);
        this.apiKey = Objects.requireNonNull(builder.apiKey, "apiKey");
        this.connectTimeout = builder.connectTimeout;
        this.readTimeout = builder.readTimeout;
        this.writeTimeout = builder.writeTimeout;
        this.userAgent = builder.userAgent;
    }

    public static Builder builder(String baseUrl, String apiKey) {
        return new Builder(baseUrl, apiKey);
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String apiKey() {
        return apiKey;
    }

    public Duration connectTimeout() {
        return connectTimeout;
    }

    public Duration readTimeout() {
        return readTimeout;
    }

    public Duration writeTimeout() {
        return writeTimeout;
    }

    public String userAgent() {
        return userAgent;
    }

    private static String normalizeBaseUrl(String raw) {
        Objects.requireNonNull(raw, "baseUrl");
        String trimmed = raw.trim();
        if (trimmed.endsWith("/")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    public static final class Builder {
        private final String baseUrl;
        private final String apiKey;
        private Duration connectTimeout = Duration.ofSeconds(5);
        private Duration readTimeout = Duration.ofSeconds(10);
        private Duration writeTimeout = Duration.ofSeconds(10);
        private String userAgent = "KalliCloud-PluginApi/1.0";

        private Builder(String baseUrl, String apiKey) {
            this.baseUrl = baseUrl;
            this.apiKey = apiKey;
        }

        public Builder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = Objects.requireNonNull(connectTimeout, "connectTimeout");
            return this;
        }

        public Builder readTimeout(Duration readTimeout) {
            this.readTimeout = Objects.requireNonNull(readTimeout, "readTimeout");
            return this;
        }

        public Builder writeTimeout(Duration writeTimeout) {
            this.writeTimeout = Objects.requireNonNull(writeTimeout, "writeTimeout");
            return this;
        }

        public Builder userAgent(String userAgent) {
            this.userAgent = Objects.requireNonNull(userAgent, "userAgent");
            return this;
        }

        public CloudApiConfig build() {
            return new CloudApiConfig(this);
        }
    }
}
