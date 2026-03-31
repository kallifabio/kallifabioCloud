package de.kallifabio.cloud.pluginapi.request;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class PartySaveRequestBuilder {

    private String partyId;
    private String leaderUuid;
    private final List<String> members = new ArrayList<>();

    public static PartySaveRequestBuilder create() {
        return new PartySaveRequestBuilder();
    }

    public PartySaveRequestBuilder partyId(String partyId) {
        this.partyId = partyId;
        return this;
    }

    public PartySaveRequestBuilder leaderUuid(String leaderUuid) {
        this.leaderUuid = leaderUuid;
        return this;
    }

    public PartySaveRequestBuilder addMember(String memberUuid) {
        if (memberUuid != null && !memberUuid.isBlank()) {
            members.add(memberUuid);
        }
        return this;
    }

    public PartySaveRequest build() {
        return new PartySaveRequest(
                Objects.requireNonNull(partyId, "partyId"),
                Objects.requireNonNull(leaderUuid, "leaderUuid"),
                List.copyOf(members)
        );
    }
}
