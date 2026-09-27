package de.kallifabio.cloud.pluginapi.modloader;

import de.kallifabio.cloud.pluginapi.CloudPluginApi;

public final class NeoForgeCloudApi extends ModLoaderCloudFacade {

    public NeoForgeCloudApi(CloudPluginApi api) {
        super(api, CloudModLoader.NEOFORGE);
    }

    public static NeoForgeCloudApi create(String baseUrl, String apiKey) {
        return new NeoForgeCloudApi(CloudPluginApi.create(baseUrl, apiKey));
    }
}
