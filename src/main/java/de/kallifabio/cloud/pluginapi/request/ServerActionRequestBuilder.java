package de.kallifabio.cloud.pluginapi.request;

import java.util.Objects;

public final class ServerActionRequestBuilder {

    private String serverName;

    public static ServerActionRequestBuilder create() {
        return new ServerActionRequestBuilder();
    }

    public ServerActionRequestBuilder serverName(String serverName) {
        this.serverName = serverName;
        return this;
    }

    public ServerActionRequest build() {
        return new ServerActionRequest(Objects.requireNonNull(serverName, "serverName"));
    }
}
