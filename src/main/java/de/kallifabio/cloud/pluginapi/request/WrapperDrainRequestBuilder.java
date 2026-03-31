package de.kallifabio.cloud.pluginapi.request;

import java.util.Objects;

public final class WrapperDrainRequestBuilder {

    private String wrapperId;
    private boolean draining;

    public static WrapperDrainRequestBuilder create() {
        return new WrapperDrainRequestBuilder();
    }

    public WrapperDrainRequestBuilder wrapperId(String wrapperId) {
        this.wrapperId = wrapperId;
        return this;
    }

    public WrapperDrainRequestBuilder draining(boolean draining) {
        this.draining = draining;
        return this;
    }

    public WrapperDrainRequest build() {
        return new WrapperDrainRequest(Objects.requireNonNull(wrapperId, "wrapperId"), draining);
    }
}
