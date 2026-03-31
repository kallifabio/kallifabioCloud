package de.kallifabio.cloud.pluginapi.cache;

public record TimedValue<T>(T value, long expiresAtEpochMs) {

    public boolean isExpired(long nowEpochMs) {
        return nowEpochMs >= expiresAtEpochMs;
    }
}
