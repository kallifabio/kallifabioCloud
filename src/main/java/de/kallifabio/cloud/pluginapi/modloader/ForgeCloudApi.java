package de.kallifabio.cloud.pluginapi.modloader;

import de.kallifabio.cloud.pluginapi.CloudPluginApi;

public final class ForgeCloudApi extends ModLoaderCloudFacade {

    public ForgeCloudApi(CloudPluginApi api) {
        super(api, CloudModLoader.FORGE);
    }

    public static ForgeCloudApi create(String baseUrl, String apiKey) {
        return new ForgeCloudApi(CloudPluginApi.create(baseUrl, apiKey));
    }
}
