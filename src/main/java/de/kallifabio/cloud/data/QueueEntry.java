package de.kallifabio.cloud.data;

public class QueueEntry {
    public final String playerUuid;
    public final String playerName;
    public final String groupName;
    public final int priority;
    public final long queuedAt;

    public QueueEntry(String playerUuid, String playerName, String groupName, int priority, long queuedAt) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.groupName = groupName;
        this.priority = priority;
        this.queuedAt = queuedAt;
    }
}
