package de.kallifabio.cloud.pluginapi.request;

import java.util.Objects;

public final class TemplateRollbackRequestBuilder {

    private String groupName;
    private String version;

    public static TemplateRollbackRequestBuilder create() {
        return new TemplateRollbackRequestBuilder();
    }

    public TemplateRollbackRequestBuilder groupName(String groupName) {
        this.groupName = groupName;
        return this;
    }

    public TemplateRollbackRequestBuilder version(String version) {
        this.version = version;
        return this;
    }

    public TemplateRollbackRequest build() {
        return new TemplateRollbackRequest(
                Objects.requireNonNull(groupName, "groupName"),
                Objects.requireNonNull(version, "version")
        );
    }
}
