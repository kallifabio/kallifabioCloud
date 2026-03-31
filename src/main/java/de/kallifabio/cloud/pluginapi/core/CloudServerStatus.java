package de.kallifabio.cloud.pluginapi.core;

public enum CloudServerStatus {
    STARTING,
    ONLINE,
    STOPPING,
    OFFLINE,
    CRASHED,
    UNKNOWN;

    public static CloudServerStatus from(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        try {
            return CloudServerStatus.valueOf(value.toUpperCase());
        } catch (Exception ignored) {
            return UNKNOWN;
        }
    }
}
