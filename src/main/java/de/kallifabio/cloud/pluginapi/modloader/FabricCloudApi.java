package de.kallifabio.cloud.pluginapi.modloader;

import de.kallifabio.cloud.pluginapi.CloudPluginApi;

public final class FabricCloudApi extends ModLoaderCloudFacade {

    public FabricCloudApi(CloudPluginApi api) {
        super(api, CloudModLoader.FABRIC);
    }

    public static FabricCloudApi create(String baseUrl, String apiKey) {
        return new FabricCloudApi(CloudPluginApi.create(baseUrl, apiKey));
    }
}
