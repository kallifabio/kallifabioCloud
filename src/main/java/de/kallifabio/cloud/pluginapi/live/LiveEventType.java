package de.kallifabio.cloud.pluginapi.live;

public enum LiveEventType {
    SERVER_STATUS,
    WRAPPER_STATUS,
    ALERT,
    METRIC,
    UNKNOWN;

    public static LiveEventType from(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        try {
            return LiveEventType.valueOf(value.toUpperCase());
        } catch (Exception ignored) {
            return UNKNOWN;
        }
    }
}
