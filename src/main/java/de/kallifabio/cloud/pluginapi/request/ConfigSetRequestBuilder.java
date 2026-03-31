package de.kallifabio.cloud.pluginapi.request;

import java.util.Objects;

public final class ConfigSetRequestBuilder {

    private String key;
    private String value;

    public static ConfigSetRequestBuilder create() {
        return new ConfigSetRequestBuilder();
    }

    public ConfigSetRequestBuilder key(String key) {
        this.key = key;
        return this;
    }

    public ConfigSetRequestBuilder value(String value) {
        this.value = value;
        return this;
    }

    public ConfigSetRequest build() {
        return new ConfigSetRequest(
                Objects.requireNonNull(key, "key"),
                Objects.requireNonNull(value, "value")
        );
    }
}
