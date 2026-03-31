package de.kallifabio.cloud.pluginapi.request;

import java.util.Objects;

public final class GroupDeleteRequestBuilder {

    private String groupName;

    public static GroupDeleteRequestBuilder create() {
        return new GroupDeleteRequestBuilder();
    }

    public GroupDeleteRequestBuilder groupName(String groupName) {
        this.groupName = groupName;
        return this;
    }

    public GroupDeleteRequest build() {
        return new GroupDeleteRequest(Objects.requireNonNull(groupName, "groupName"));
    }
}
