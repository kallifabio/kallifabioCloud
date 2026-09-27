package de.kallifabio.cloud.pluginapi.async;

import com.google.gson.JsonObject;
import de.kallifabio.cloud.pluginapi.CloudPluginApi;
import de.kallifabio.cloud.pluginapi.model.CloudOperationResult;
import de.kallifabio.cloud.pluginapi.model.CloudServerInfo;
import de.kallifabio.cloud.pluginapi.model.CloudSystemDoctorInfo;
import de.kallifabio.cloud.pluginapi.request.ServerActionRequest;
import de.kallifabio.cloud.pluginapi.request.ServerStartRequest;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class CloudPluginApiAsync {

    private final CloudPluginApi api;
    private final Executor executor;

    public CloudPluginApiAsync(CloudPluginApi api, Executor executor) {
        this.api = api;
        this.executor = executor;
    }

    public CompletableFuture<List<CloudServerInfo>> servers() {
        return CompletableFuture.supplyAsync(() -> api.servers().list(), executor);
    }

    public CompletableFuture<JsonObject> overview() {
        return CompletableFuture.supplyAsync(() -> api.status().dashboardOverview(), executor);
    }

    public CompletableFuture<CloudSystemDoctorInfo> systemDoctor() {
        return CompletableFuture.supplyAsync(() -> api.operations().systemDoctorModel(), executor);
    }

    public CompletableFuture<CloudOperationResult> start(ServerStartRequest request) {
        return CompletableFuture.supplyAsync(() -> api.servers().start(request), executor);
    }

    public CompletableFuture<CloudOperationResult> stop(ServerActionRequest request) {
        return CompletableFuture.supplyAsync(() -> api.servers().stop(request), executor);
    }

    public CompletableFuture<CloudOperationResult> restart(ServerActionRequest request) {
        return CompletableFuture.supplyAsync(() -> api.servers().restart(request), executor);
    }
}
