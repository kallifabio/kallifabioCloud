package de.kallifabio.cloud.pluginapi.async;

import java.util.Objects;
import java.util.concurrent.*;

public final class CloudPollingTask implements AutoCloseable {

    private final ScheduledExecutorService executorService;
    private ScheduledFuture<?> future;

    public CloudPollingTask() {
        this.executorService = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "cloud-pluginapi-poller");
            thread.setDaemon(true);
            return thread;
        });
    }

    public synchronized void start(long periodMs, Runnable task) {
        Objects.requireNonNull(task, "task");
        stop();
        future = executorService.scheduleAtFixedRate(task, 0L, Math.max(100L, periodMs), TimeUnit.MILLISECONDS);
    }

    public synchronized void stop() {
        if (future != null) {
            future.cancel(false);
            future = null;
        }
    }

    @Override
    public void close() {
        stop();
        executorService.shutdownNow();
    }
}
