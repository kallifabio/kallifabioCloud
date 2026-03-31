package de.kallifabio.cloud.pluginapi.request;

import java.util.Objects;
import java.util.UUID;

public final class ServerStartRequestBuilder {

    private String serverName;
    private String groupName;

    public static ServerStartRequestBuilder create() {
        return new ServerStartRequestBuilder();
    }

    public ServerStartRequestBuilder serverName(String serverName) {
        this.serverName = serverName;
        return this;
    }

    public ServerStartRequestBuilder autoName(String prefix) {
        this.serverName = prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        return this;
    }

    public ServerStartRequestBuilder group(String groupName) {
        this.groupName = groupName;
        return this;
    }

    public ServerStartRequest build() {
        return new ServerStartRequest(
                Objects.requireNonNull(serverName, "serverName"),
                Objects.requireNonNull(groupName, "groupName")
        );
    }
}
