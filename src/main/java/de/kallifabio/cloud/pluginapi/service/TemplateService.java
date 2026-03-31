package de.kallifabio.cloud.pluginapi.service;

import com.google.gson.JsonObject;
import de.kallifabio.cloud.pluginapi.CloudApiClient;
import de.kallifabio.cloud.pluginapi.model.CloudTemplateDiffInfo;
import de.kallifabio.cloud.pluginapi.model.CloudOperationResult;
import de.kallifabio.cloud.pluginapi.model.CloudTemplateVersionsInfo;
import de.kallifabio.cloud.pluginapi.request.TemplateRollbackRequest;

import java.util.Map;

public final class TemplateService {

    private final CloudApiClient client;

    public TemplateService(CloudApiClient client) {
        this.client = client;
    }

    public JsonObject diff(String groupName) {
        return client.get("/api/v1/templates/diff", Map.of("group", groupName));
    }

    public CloudTemplateDiffInfo diffModel(String groupName) {
        return CloudTemplateDiffInfo.from(diff(groupName));
    }

    public JsonObject versions(String groupName) {
        return client.get("/api/v1/templates/versions", Map.of("group", groupName));
    }

    public CloudTemplateVersionsInfo versionsModel(String groupName) {
        return CloudTemplateVersionsInfo.from(versions(groupName));
    }

    public JsonObject rollback(String groupName, String version) {
        return client.post("/api/v1/templates/rollback", Map.of(
                "groupName", groupName,
                "version", version
        ));
    }

    public CloudOperationResult rollback(TemplateRollbackRequest request) {
        return CloudOperationResult.from(rollback(request.groupName(), request.version()));
    }
}
