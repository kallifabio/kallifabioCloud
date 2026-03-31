package de.kallifabio.cloud.pluginapi.request;

import java.util.Objects;

public final class GroupCreateRequestBuilder {

    private String groupName;
    private String parent = "";

    public static GroupCreateRequestBuilder create() {
        return new GroupCreateRequestBuilder();
    }

    public GroupCreateRequestBuilder groupName(String groupName) {
        this.groupName = groupName;
        return this;
    }

    public GroupCreateRequestBuilder parent(String parent) {
        this.parent = parent == null ? "" : parent;
        return this;
    }

    public GroupCreateRequest build() {
        return new GroupCreateRequest(Objects.requireNonNull(groupName, "groupName"), parent);
    }
}
