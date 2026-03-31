package de.kallifabio.cloud.pluginapi.request;

import java.util.Objects;

public final class GroupUpdateRequestBuilder {

    private String groupName;
    private String key;
    private String value;

    public static GroupUpdateRequestBuilder create() {
        return new GroupUpdateRequestBuilder();
    }

    public GroupUpdateRequestBuilder groupName(String groupName) {
        this.groupName = groupName;
        return this;
    }

    public GroupUpdateRequestBuilder key(String key) {
        this.key = key;
        return this;
    }

    public GroupUpdateRequestBuilder value(String value) {
        this.value = value;
        return this;
    }

    public GroupUpdateRequest build() {
        return new GroupUpdateRequest(
                Objects.requireNonNull(groupName, "groupName"),
                Objects.requireNonNull(key, "key"),
                Objects.requireNonNull(value, "value")
        );
    }
}
