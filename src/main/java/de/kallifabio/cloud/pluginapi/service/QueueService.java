package de.kallifabio.cloud.pluginapi.service;

import de.kallifabio.cloud.pluginapi.model.CloudQueueStatus;

public final class QueueService {

    private final PlayerService playerService;

    public QueueService(PlayerService playerService) {
        this.playerService = playerService;
    }

    public CloudQueueStatus status() {
        return playerService.queueStatusModel();
    }

    public int groupSize(String groupName) {
        CloudQueueStatus queue = status();
        return queue.perGroup().getOrDefault(groupName, 0);
    }

    public boolean hasQueue(String groupName) {
        return groupSize(groupName) > 0;
    }
}
