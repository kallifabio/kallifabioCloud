package de.kallifabio.cloud.pluginapi.util;

import com.google.gson.JsonObject;

public final class JsonValueReader {

    private JsonValueReader() {
    }

    public static String getString(JsonObject object, String key, String fallback) {
        return object != null && object.has(key) ? object.get(key).getAsString() : fallback;
    }

    public static int getInt(JsonObject object, String key, int fallback) {
        return object != null && object.has(key) ? object.get(key).getAsInt() : fallback;
    }

    public static long getLong(JsonObject object, String key, long fallback) {
        return object != null && object.has(key) ? object.get(key).getAsLong() : fallback;
    }

    public static boolean getBoolean(JsonObject object, String key, boolean fallback) {
        return object != null && object.has(key) ? object.get(key).getAsBoolean() : fallback;
    }

    public static double getDouble(JsonObject object, String key, double fallback) {
        return object != null && object.has(key) ? object.get(key).getAsDouble() : fallback;
    }
}
