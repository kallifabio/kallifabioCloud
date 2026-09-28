package de.kallifabio.cloud.master.queue;

import de.kallifabio.cloud.data.CloudDataStore;
import de.kallifabio.cloud.data.QueueEntry;
import de.kallifabio.cloud.libs.Message;
import de.kallifabio.cloud.libs.console.ConsoleColors;
import de.kallifabio.cloud.libs.console.ConsoleScreenManager;
import de.kallifabio.cloud.master.Master;
import de.kallifabio.cloud.master.loadbalancer.LoadBalancerManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;

public class PlayerQueueManager {

    private final Master master;
    private final LoadBalancerManager loadBalancer;
    private final CloudDataStore dataStore;

    private final Map<String, PriorityBlockingQueue<QueuedPlayer>> groupQueues = new ConcurrentHashMap<>();
    private final Map<String, QueuedPlayer> playerQueue = new ConcurrentHashMap<>();
    private final Map<String, Long> queueActivity = new ConcurrentHashMap<>();
    private final Map<String, QueueStats> queueStats = new ConcurrentHashMap<>();

    private static final int PRIORITY_NORMAL = 0;
    private static final int MAX_MATCHES_PER_TICK = 10;
    private static final long QUEUE_TIMEOUT_MS = 10 * 60 * 1000L;
    private static final long QUEUE_AFK_TIMEOUT_MS = 2 * 60 * 1000L;

    public PlayerQueueManager(Master master, LoadBalancerManager loadBalancer, CloudDataStore dataStore) {
        this.master = master;
        this.loadBalancer = loadBalancer;
        this.dataStore = dataStore;
        initializeQueues();
        loadPersistentQueues();
    }

    private void initializeQueues() {
        groupQueues.put("Lobby", new PriorityBlockingQueue<>());
        groupQueues.put("Proxy", new PriorityBlockingQueue<>());

        ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Player Queue Manager initialized");
    }

    public void addToQueue(String playerUuid, String groupName) {
        addToQueue(playerUuid, "Player", groupName, PRIORITY_NORMAL);
    }

    public void addToQueue(String playerUuid, String playerName, String groupName, int priority) {
        if (playerUuid == null || playerUuid.isBlank() || groupName == null || groupName.isBlank()) {
            return;
        }
        playerUuid = playerUuid.trim().toLowerCase();
        groupName = groupName.trim();
        PriorityBlockingQueue<QueuedPlayer> queue = groupQueues.computeIfAbsent(groupName, k -> new PriorityBlockingQueue<>());
        String queueKey = queueKey(groupName, playerUuid);

        QueuedPlayer existing = playerQueue.get(queueKey);
        if (existing != null) {
            notifyPlayerQueueUpdate(existing);
            return;
        }
        removeFromQueue(playerUuid);

        QueuedPlayer queuedPlayer = new QueuedPlayer(
                playerUuid,
                playerName == null || playerName.isBlank() ? "Player" : playerName.trim(),
                groupName,
                priority,
                System.currentTimeMillis()
        );

        queue.offer(queuedPlayer);
        playerQueue.put(queueKey, queuedPlayer);
        queueActivity.put(queueKey, System.currentTimeMillis());
        persistQueueEntry(queuedPlayer);

        updateQueueStats(groupName);

        ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Player queued: " + playerName + " (" + groupName + ") position " + getQueuePosition(playerUuid));

        notifyPlayerQueueUpdate(queuedPlayer);
    }

    public void processQueue() {
        for (Map.Entry<String, PriorityBlockingQueue<QueuedPlayer>> entry : groupQueues.entrySet()) {
            String groupName = entry.getKey();
            PriorityBlockingQueue<QueuedPlayer> queue = entry.getValue();
            removeExpiredEntries(groupName);

            if (queue.isEmpty()) {
                continue;
            }

            int processedThisTick = 0;
            while (!queue.isEmpty() && processedThisTick < MAX_MATCHES_PER_TICK) {
                QueuedPlayer nextPlayer = queue.peek();
                if (nextPlayer == null) {
                    break;
                }

                String availableServer = loadBalancer.getBestServer(groupName, nextPlayer.playerUuid);

                if (availableServer == null) {
                    break;
                }

                boolean removed = queue.remove(nextPlayer);
                if (!removed) {
                    break;
                }

                playerQueue.remove(queueKey(nextPlayer.groupName, nextPlayer.playerUuid));
                queueActivity.remove(queueKey(nextPlayer.groupName, nextPlayer.playerUuid));
                removePersistedQueueEntry(nextPlayer);

                sendPlayerToServer(nextPlayer, availableServer);
                recordPlayerPlaced(groupName, System.currentTimeMillis() - nextPlayer.queuedAt);
                processedThisTick++;

                updateQueueStats(groupName);
            }

            notifyWaitingPlayers(groupName);
        }
    }

    private void sendPlayerToServer(QueuedPlayer player, String targetServer) {
        ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Queue dispatch: " + player.playerName + " -> " + targetServer);

        Message.PlayerJoinResponse response = new Message.PlayerJoinResponse();
        response.playerUuid = player.playerUuid;
        response.targetServer = targetServer;
        response.success = true;
        response.message = "Server available - connecting";

        master.getServer().sendToAllTCP(response);
        if (master.getPlayerSessionManager() != null) {
            master.getPlayerSessionManager().assignServer(player.playerUuid, targetServer);
        }
    }

    private void notifyPlayerQueueUpdate(QueuedPlayer player) {
        int position = getQueuePosition(player.playerUuid, player.groupName);
        int totalInQueue = getQueueSize(player.groupName);

        Message.QueueUpdate update = new Message.QueueUpdate();
        update.playerUuid = player.playerUuid;
        update.position = position;
        update.totalInQueue = totalInQueue;
        update.estimatedWait = calculateEstimatedWait(player.groupName, position);

        master.getServer().sendToAllTCP(update);
    }

    private void notifyWaitingPlayers(String groupName) {
        List<QueuedPlayer> queueList = getOrderedQueue(groupName);
        for (int i = 0; i < queueList.size(); i++) {
            QueuedPlayer player = queueList.get(i);

            Message.QueueUpdate update = new Message.QueueUpdate();
            update.playerUuid = player.playerUuid;
            update.position = i + 1;
            update.totalInQueue = queueList.size();
            update.estimatedWait = calculateEstimatedWait(groupName, i + 1);

            master.getServer().sendToAllTCP(update);
        }
    }

    private String calculateEstimatedWait(String groupName, int position) {
        QueueStats stats = queueStats.get(groupName);
        if (stats == null || stats.avgProcessingTime == 0) {
            return "Unknown";
        }

        long estimatedMs = (long) (Math.max(0, position - 1) * stats.avgProcessingTime);
        long seconds = estimatedMs / 1000;

        if (seconds < 60) {
            return seconds + " seconds";
        }

        long minutes = seconds / 60;
        return minutes + " minute" + (minutes > 1 ? "s" : "");
    }

    public int getQueuePosition(String playerUuid) {
        if (playerUuid == null || playerUuid.isBlank()) {
            return -1;
        }
        playerUuid = playerUuid.trim().toLowerCase();
        for (QueuedPlayer player : playerQueue.values()) {
            int pos = getQueuePosition(playerUuid, player.groupName);
            if (pos > 0) {
                return pos;
            }
        }
        return -1;
    }

    public int getQueuePosition(String playerUuid, String groupName) {
        if (playerUuid == null || playerUuid.isBlank() || groupName == null || groupName.isBlank()) {
            return -1;
        }
        playerUuid = playerUuid.trim().toLowerCase();
        groupName = groupName.trim();
        QueuedPlayer player = playerQueue.get(queueKey(groupName, playerUuid));
        if (player == null) {
            return -1;
        }

        List<QueuedPlayer> queueList = getOrderedQueue(groupName);
        int index = queueList.indexOf(player);
        return index >= 0 ? index + 1 : -1;
    }

    public void removeFromQueue(String playerUuid) {
        if (playerUuid == null || playerUuid.isBlank()) {
            return;
        }
        playerUuid = playerUuid.trim().toLowerCase();
        String normalizedPlayerUuid = playerUuid;
        List<QueuedPlayer> toRemove = playerQueue.values().stream()
                .filter(p -> p.playerUuid.equalsIgnoreCase(normalizedPlayerUuid))
                .toList();
        for (QueuedPlayer player : toRemove) {
            playerQueue.remove(queueKey(player.groupName, player.playerUuid));
            queueActivity.remove(queueKey(player.groupName, player.playerUuid));
            PriorityBlockingQueue<QueuedPlayer> queue = groupQueues.get(player.groupName);
            if (queue != null) {
                queue.remove(player);
            }
            removePersistedQueueEntry(player);
            updateQueueStats(player.groupName);
        }
    }

    private void updateQueueStats(String groupName) {
        PriorityBlockingQueue<QueuedPlayer> queue = groupQueues.get(groupName);
        if (queue == null) {
            return;
        }

        QueueStats stats = queueStats.computeIfAbsent(groupName, QueueStats::new);
        stats.currentSize = queue.size();
        stats.lastUpdate = System.currentTimeMillis();

        if (stats.currentSize > stats.peakSize) {
            stats.peakSize = stats.currentSize;
        }

        if (stats.processedCount > 0) {
            stats.avgProcessingTime = stats.totalWaitTime / stats.processedCount;
        }
    }

    public void recordPlayerPlaced(String groupName, long waitTime) {
        QueueStats stats = queueStats.computeIfAbsent(groupName, QueueStats::new);
        stats.processedCount++;
        stats.totalWaitTime += waitTime;
        stats.avgProcessingTime = stats.totalWaitTime / stats.processedCount;
    }

    public int getTotalQueued() {
        return groupQueues.values().stream().mapToInt(PriorityBlockingQueue::size).sum();
    }

    public Map<String, Integer> getQueueStats() {
        Map<String, Integer> stats = new HashMap<>();
        groupQueues.forEach((groupName, queue) -> stats.put(groupName, queue.size()));
        return stats;
    }

    public Map<String, QueueStats> getDetailedQueueStats() {
        return new HashMap<>(queueStats);
    }

    public int getQueueSize(String groupName) {
        PriorityBlockingQueue<QueuedPlayer> queue = groupQueues.get(groupName);
        return queue != null ? queue.size() : 0;
    }

    private List<QueuedPlayer> getOrderedQueue(String groupName) {
        PriorityBlockingQueue<QueuedPlayer> queue = groupQueues.get(groupName);
        if (queue == null || queue.isEmpty()) {
            return List.of();
        }

        List<QueuedPlayer> ordered = new ArrayList<>(queue);
        ordered.sort(QueuedPlayer::compareTo);
        return ordered;
    }

    private void persistQueueEntry(QueuedPlayer player) {
        if (dataStore == null) {
            return;
        }
        dataStore.saveQueueEntry(new QueueEntry(
                player.playerUuid,
                player.playerName,
                player.groupName,
                player.priority,
                player.queuedAt
        ));
    }

    private void removePersistedQueueEntry(QueuedPlayer player) {
        if (dataStore == null) {
            return;
        }
        dataStore.removeQueueEntry(player.groupName, player.playerUuid);
    }

    private void loadPersistentQueues() {
        if (dataStore == null) {
            return;
        }

        for (String groupName : groupQueues.keySet()) {
            List<QueueEntry> entries = dataStore.loadQueueEntries(groupName);
            for (QueueEntry entry : entries) {
                QueuedPlayer qp = new QueuedPlayer(entry.playerUuid, entry.playerName, entry.groupName, entry.priority, entry.queuedAt);
                groupQueues.get(groupName).offer(qp);
                playerQueue.put(queueKey(entry.groupName, entry.playerUuid), qp);
                queueActivity.put(queueKey(entry.groupName, entry.playerUuid), System.currentTimeMillis());
            }
            updateQueueStats(groupName);
        }
    }

    public void markQueueActivity(String playerUuid, String groupName) {
        if (playerUuid == null || playerUuid.isBlank() || groupName == null || groupName.isBlank()) {
            return;
        }
        playerUuid = playerUuid.trim().toLowerCase();
        groupName = groupName.trim();
        queueActivity.put(queueKey(groupName, playerUuid), System.currentTimeMillis());
    }

    private void removeExpiredEntries(String groupName) {
        PriorityBlockingQueue<QueuedPlayer> queue = groupQueues.get(groupName);
        if (queue == null || queue.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        List<QueuedPlayer> snapshot = new ArrayList<>(queue);
        for (QueuedPlayer player : snapshot) {
            String key = queueKey(player.groupName, player.playerUuid);
            long lastActivity = queueActivity.getOrDefault(key, player.queuedAt);
            boolean timedOut = now - player.queuedAt > QUEUE_TIMEOUT_MS;
            boolean afk = now - lastActivity > QUEUE_AFK_TIMEOUT_MS;
            if (timedOut || afk) {
                queue.remove(player);
                playerQueue.remove(key);
                queueActivity.remove(key);
                removePersistedQueueEntry(player);
                updateQueueStats(groupName);

                Message.PlayerNotification note = new Message.PlayerNotification();
                note.playerUuid = player.playerUuid;
                note.type = timedOut ? "QUEUE_TIMEOUT" : "QUEUE_AFK";
                note.message = timedOut ? "Removed from queue after 10 minutes" : "Removed from queue due to AFK";
                note.timestamp = now;
                master.getServer().sendToAllTCP(note);
            }
        }
    }

    private String queueKey(String groupName, String playerUuid) {
        return groupName.trim() + "|" + playerUuid.trim().toLowerCase();
    }
}

class QueuedPlayer implements Comparable<QueuedPlayer> {
    String playerUuid;
    String playerName;
    String groupName;
    int priority;
    long queuedAt;

    QueuedPlayer(String playerUuid, String playerName, String groupName, int priority, long queuedAt) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.groupName = groupName;
        this.priority = priority;
        this.queuedAt = queuedAt;
    }

    @Override
    public int compareTo(QueuedPlayer other) {
        if (this.priority != other.priority) {
            return Integer.compare(other.priority, this.priority);
        }
        return Long.compare(this.queuedAt, other.queuedAt);
    }
}

class QueueStats {
    String groupName;
    int currentSize;
    int peakSize;
    long processedCount;
    long totalWaitTime;
    double avgProcessingTime;
    long lastUpdate;

    QueueStats(String groupName) {
        this.groupName = groupName;
    }
}
