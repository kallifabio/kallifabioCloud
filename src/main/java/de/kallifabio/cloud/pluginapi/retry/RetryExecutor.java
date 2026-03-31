package de.kallifabio.cloud.pluginapi.retry;

import java.util.Objects;
import java.util.concurrent.Callable;

public final class RetryExecutor {

    private final RetryPolicy policy;

    public RetryExecutor(RetryPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    public <T> T execute(Callable<T> callable) {
        Exception last = null;
        for (int attempt = 1; attempt <= policy.maxAttempts(); attempt++) {
            try {
                long delay = policy.delayForAttempt(attempt);
                if (delay > 0L) {
                    Thread.sleep(delay);
                }
                return callable.call();
            } catch (Exception e) {
                last = e;
            }
        }
        throw new RuntimeException("Retry exhausted", last);
    }
}
