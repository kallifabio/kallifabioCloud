package de.kallifabio.cloud.pluginapi.core;

public enum CloudApiRole {
    ADMIN,
    VIEWER,
    UNKNOWN;

    public static CloudApiRole from(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        try {
            return CloudApiRole.valueOf(value.toUpperCase());
        } catch (Exception ignored) {
            return UNKNOWN;
        }
    }
}
