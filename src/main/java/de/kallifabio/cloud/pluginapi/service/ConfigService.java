package de.kallifabio.cloud.pluginapi.service;

import de.kallifabio.cloud.pluginapi.model.CloudConfigValue;
import de.kallifabio.cloud.pluginapi.model.CloudOperationResult;
import de.kallifabio.cloud.pluginapi.request.ConfigSetRequest;

public final class ConfigService {

    private final OperationsService operationsService;

    public ConfigService(OperationsService operationsService) {
        this.operationsService = operationsService;
    }

    public CloudConfigValue get(String key) {
        return CloudConfigValue.from(operationsService.configGet(key));
    }

    public CloudOperationResult set(String key, String value) {
        return operationsService.configSet(new ConfigSetRequest(key, value));
    }
}
