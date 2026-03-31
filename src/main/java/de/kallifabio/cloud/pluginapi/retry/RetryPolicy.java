package de.kallifabio.cloud.pluginapi.retry;

public record RetryPolicy(
        int maxAttempts,
        long initialDelayMs,
        double multiplier,
        long maxDelayMs
) {

    public static RetryPolicy standard() {
        return new RetryPolicy(3, 250L, 2.0, 2000L);
    }

    public long delayForAttempt(int attempt) {
        if (attempt <= 1) {
            return 0L;
        }
        double scaled = initialDelayMs * Math.pow(multiplier, Math.max(0, attempt - 2));
        return Math.min((long) scaled, maxDelayMs);
    }
}
