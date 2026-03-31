package de.kallifabio.cloud.pluginapi.model;

import com.google.gson.JsonObject;

public record CloudPlayerDataModel(
        String uuid,
        int coins,
        int kills,
        int deaths,
        int wins,
        int losses,
        String rank
) {

    public static CloudPlayerDataModel from(JsonObject object) {
        return new CloudPlayerDataModel(
                getString(object, "playerUuid", getString(object, "uuid", "")),
                getInt(object, "coins"),
                getInt(object, "kills"),
                getInt(object, "deaths"),
                getInt(object, "wins"),
                getInt(object, "losses"),
                getString(object, "rank", "default")
        );
    }

    private static String getString(JsonObject object, String key, String fallback) {
        return object.has(key) ? object.get(key).getAsString() : fallback;
    }

    private static int getInt(JsonObject object, String key) {
        return object.has(key) ? object.get(key).getAsInt() : 0;
    }
}
