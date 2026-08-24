package de.kallifabio.cloud.pluginapi.service;

import com.google.gson.JsonObject;
import de.kallifabio.cloud.pluginapi.CloudApiClient;
import de.kallifabio.cloud.pluginapi.model.CloudCapacityPlanInfo;
import de.kallifabio.cloud.pluginapi.model.CloudLogSearchResult;
import de.kallifabio.cloud.pluginapi.model.CloudOperationResult;
import de.kallifabio.cloud.pluginapi.model.CloudRecentLogs;
import de.kallifabio.cloud.pluginapi.model.CloudSystemDiagnosticsInfo;
import de.kallifabio.cloud.pluginapi.request.ConfigSetRequest;

import java.util.Map;

public final class OperationsService {

    private final CloudApiClient client;

    public OperationsService(CloudApiClient client) {
        this.client = client;
    }

    public JsonObject recentLogs() {
        return client.get("/api/v1/logs/recent");
    }

    public CloudRecentLogs recentLogsModel() {
        return CloudRecentLogs.from(recentLogs());
    }

    public JsonObject setupReport() {
        return client.get("/api/v1/setup/report");
    }

    public JsonObject diagnostics() {
        return client.get("/api/v1/system/diagnostics");
    }

    public CloudSystemDiagnosticsInfo diagnosticsModel() {
        return CloudSystemDiagnosticsInfo.from(diagnostics());
    }

    public JsonObject capacityPlanner() {
        return client.get("/api/v1/system/capacity");
    }

    public CloudCapacityPlanInfo capacityPlannerModel() {
        return CloudCapacityPlanInfo.from(capacityPlanner());
    }

    public JsonObject systemReport() {
        return client.get("/api/v1/system/report");
    }

    public JsonObject recentEvents(int limit) {
        return client.get("/api/v1/events/recent", Map.of("limit", String.valueOf(limit)));
    }

    public JsonObject lifecycle(String serverName, int limit) {
        if (serverName == null || serverName.isBlank()) {
            return client.get("/api/v1/lifecycle", Map.of("limit", String.valueOf(limit)));
        }
        return client.get("/api/v1/lifecycle", Map.of(
                "serverName", serverName,
                "limit", String.valueOf(limit)
        ));
    }

    public JsonObject incidents(int limit) {
        return client.get("/api/v1/incidents", Map.of("limit", String.valueOf(limit)));
    }

    public JsonObject backups(int limit) {
        return client.get("/api/v1/backups", Map.of("limit", String.valueOf(limit)));
    }

    public JsonObject createBackup(String name, boolean includeLogs) {
        return client.post("/api/v1/backups/create", Map.of(
                "name", name == null ? "manual" : name,
                "includeLogs", includeLogs
        ));
    }

    public JsonObject restoreBackupToStaging(String backupName) {
        return client.post("/api/v1/backups/restore-staging", Map.of("backupName", backupName));
    }

    public JsonObject rollingRestart(String groupName, int delaySeconds) {
        return client.post("/api/v1/rolling/restart", Map.of(
                "groupName", groupName == null ? "" : groupName,
                "delaySeconds", delaySeconds
        ));
    }

    public JsonObject firewallCheck() {
        return client.get("/api/v1/firewall/check");
    }

    public JsonObject searchLogs(String query, String level, int limit) {
        return client.get("/api/v1/logs/search", Map.of(
                "query", query == null ? "" : query,
                "level", level == null ? "all" : level,
                "limit", String.valueOf(limit)
        ));
    }

    public CloudLogSearchResult searchLogsModel(String query, String level, int limit) {
        return CloudLogSearchResult.from(searchLogs(query, level, limit));
    }

    public CloudLogSearchResult recentAudit(int limit) {
        return CloudLogSearchResult.from(client.get("/api/v1/audit/recent", Map.of(
                "limit", String.valueOf(limit)
        )));
    }

    public JsonObject configGet(String key) {
        return client.get("/api/v1/config/get", Map.of("key", key));
    }

    public JsonObject configSet(String key, String value) {
        return client.post("/api/v1/config/set", Map.of(
                "key", key,
                "value", value
        ));
    }

    public CloudOperationResult configSet(ConfigSetRequest request) {
        return CloudOperationResult.from(configSet(request.key(), request.value()));
    }

    public JsonObject webhookTest(String message) {
        if (message == null || message.isBlank()) {
            return client.post("/api/v1/webhook/test", Map.of());
        }
        return client.post("/api/v1/webhook/test", Map.of("message", message));
    }

    public CloudOperationResult webhookTestResult(String message) {
        return CloudOperationResult.from(webhookTest(message));
    }

    public JsonObject loadBalancerStats() {
        return client.get("/api/v1/loadbalancer/stats");
    }
}
