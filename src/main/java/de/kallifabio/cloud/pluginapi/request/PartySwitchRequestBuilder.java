package de.kallifabio.cloud.pluginapi.request;

import java.util.Objects;

public final class PartySwitchRequestBuilder {

    private String partyId;
    private String targetServer;

    public static PartySwitchRequestBuilder create() {
        return new PartySwitchRequestBuilder();
    }

    public PartySwitchRequestBuilder partyId(String partyId) {
        this.partyId = partyId;
        return this;
    }

    public PartySwitchRequestBuilder targetServer(String targetServer) {
        this.targetServer = targetServer;
        return this;
    }

    public PartySwitchRequest build() {
        return new PartySwitchRequest(
                Objects.requireNonNull(partyId, "partyId"),
                Objects.requireNonNull(targetServer, "targetServer")
        );
    }
}
