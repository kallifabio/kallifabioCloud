/**
 * Erstellt von Gamer_Kidd_LP | kallifabio
 * am 09.01.2026 um 20:55
 * Projektname: CloudSystemTest
 * Packagename: de.kallifabio.cloud.master.queue
 */

package de.kallifabio.cloud.master.queue;

import de.kallifabio.cloud.libs.console.ConsoleColors;
import de.kallifabio.cloud.libs.Message;
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

    // Queues per group
    private final Map<String, PriorityBlockingQueue<QueuedPlayer>> groupQueues = new ConcurrentHashMap<>();

    // Player tracking
    private final Map<String, QueuedPlayer> playerQueue = new ConcurrentHashMap<>();

    // Queue statistics
    private final Map<String, QueueStats> queueStats = new ConcurrentHashMap<>();

    // VIP priorities
    private static final int PRIORITY_VIP_PLUS = 100;
    private static final int PRIORITY_VIP = 50;
    private static final int PRIORITY_NORMAL = 0;

    public PlayerQueueManager(Master master, LoadBalancerManager loadBalancer) {
        this.master = master;
        this.loadBalancer = loadBalancer;
        initializeQueues();
    }

    private void initializeQueues() {
        groupQueues.put("Lobby", new PriorityBlockingQueue<>());
        groupQueues.put("Proxy", new PriorityBlockingQueue<>());

        ConsoleScreenManager.logToMainScreen(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Player Queue Manager initialisiert");
    }

    public void addToQueue(String playerUuid, String groupName) {
        addToQueue(playerUuid, "Player", groupName, PRIORITY_NORMAL);
    }

    public void addToQueue(String playerUuid, String playerName, String groupName, int priority) {
        PriorityBlockingQueue<QueuedPlayer> queue = groupQueues.get(groupName);
        if (queue == null) {
            ConsoleScreenManager.logToMainScreen(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                    " FEHLER: Queue für Gruppe " + groupName + " nicht gefunden");
            return;
        }

        QueuedPlayer queuedPlayer = new QueuedPlayer(
                playerUuid,
                playerName,
                groupName,
                priority,
                System.currentTimeMillis()
        );

        queue.offer(queuedPlayer);
        playerQueue.put(playerUuid, queuedPlayer);

        updateQueueStats(groupName);

        ConsoleScreenManager.logToMainScreen(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Spieler zur Queue hinzugefügt: " + playerName + " (" + groupName + ") - Position: " +
                getQueuePosition(playerUuid));

        // Notify player of queue position
        notifyPlayerQueueUpdate(queuedPlayer);
    }

    public void processQueue() {
        for (Map.Entry<String, PriorityBlockingQueue<QueuedPlayer>> entry : groupQueues.entrySet()) {
            String groupName = entry.getKey();
            PriorityBlockingQueue<QueuedPlayer> queue = entry.getValue();

            if (queue.isEmpty()) continue;

            // Try to place queued players
            while (!queue.isEmpty()) {
                String availableServer = loadBalancer.getBestServer(groupName, queue.peek().playerUuid);

                if (availableServer != null) {
                    QueuedPlayer player = queue.poll();
                    playerQueue.remove(player.playerUuid);

                    // Send player to server
                    sendPlayerToServer(player, availableServer);

                    updateQueueStats(groupName);
                } else {
                    break; // No servers available, stop processing
                }
            }

            // Update waiting players
            notifyWaitingPlayers(groupName);
        }
    }

    private void sendPlayerToServer(QueuedPlayer player, String targetServer) {
        ConsoleScreenManager.logToMainScreen(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Sende Spieler aus Queue: " + player.playerName + " -> " + targetServer);

        Message.PlayerJoinResponse response = new Message.PlayerJoinResponse();
        response.playerUuid = player.playerUuid;
        response.targetServer = targetServer;
        response.success = true;
        response.message = "Server verfügbar - Verbindung wird hergestellt";

        // Send to appropriate wrapper/server
        // In real implementation, would route to correct connection
        master.getServer().sendToAllTCP(response);
    }

    private void notifyPlayerQueueUpdate(QueuedPlayer player) {
        int position = getQueuePosition(player.playerUuid);
        int totalInQueue = groupQueues.get(player.groupName).size();

        Message.QueueUpdate update = new Message.QueueUpdate();
        update.playerUuid = player.playerUuid;
        update.position = position;
        update.totalInQueue = totalInQueue;
        update.estimatedWait = calculateEstimatedWait(player.groupName, position);

        master.getServer().sendToAllTCP(update);
    }

    private void notifyWaitingPlayers(String groupName) {
        PriorityBlockingQueue<QueuedPlayer> queue = groupQueues.get(groupName);
        if (queue == null) return;

        List<QueuedPlayer> queueList = new ArrayList<>(queue);
        for (int i = 0; i < queueList.size(); i++) {
            QueuedPlayer player = queueList.get(i);

            Message.QueueUpdate update = new Message.QueueUpdate();
            update.playerUuid = player.playerUuid;
            update.position = i + 1;
            update.totalInQueue = queue.size();
            update.estimatedWait = calculateEstimatedWait(groupName, i + 1);

            master.getServer().sendToAllTCP(update);
        }
    }

    private String calculateEstimatedWait(String groupName, int position) {
        QueueStats stats = queueStats.get(groupName);
        if (stats == null || stats.avgProcessingTime == 0) {
            return "Unbekannt";
        }

        long estimatedMs = (long) (position * stats.avgProcessingTime);
        long seconds = estimatedMs / 1000;

        if (seconds < 60) {
            return seconds + " Sekunden";
        } else {
            long minutes = seconds / 60;
            return minutes + " Minute" + (minutes > 1 ? "n" : "");
        }
    }

    public int getQueuePosition(String playerUuid) {
        QueuedPlayer player = playerQueue.get(playerUuid);
        if (player == null) return -1;

        PriorityBlockingQueue<QueuedPlayer> queue = groupQueues.get(player.groupName);
        if (queue == null) return -1;

        List<QueuedPlayer> queueList = new ArrayList<>(queue);
        return queueList.indexOf(player) + 1;
    }

    public void removeFromQueue(String playerUuid) {
        QueuedPlayer player = playerQueue.remove(playerUuid);
        if (player != null) {
            PriorityBlockingQueue<QueuedPlayer> queue = groupQueues.get(player.groupName);
            if (queue != null) {
                queue.remove(player);
                updateQueueStats(player.groupName);
            }
        }
    }

    private void updateQueueStats(String groupName) {
        PriorityBlockingQueue<QueuedPlayer> queue = groupQueues.get(groupName);
        if (queue == null) return;

        QueueStats stats = queueStats.computeIfAbsent(groupName, k -> new QueueStats(groupName));
        stats.currentSize = queue.size();
        stats.lastUpdate = System.currentTimeMillis();

        if (stats.currentSize > stats.peakSize) {
            stats.peakSize = stats.currentSize;
        }

        // Calculate average processing time
        if (stats.processedCount > 0) {
            stats.avgProcessingTime = stats.totalWaitTime / stats.processedCount;
        }
    }

    public void recordPlayerPlaced(String groupName, long waitTime) {
        QueueStats stats = queueStats.get(groupName);
        if (stats != null) {
            stats.processedCount++;
            stats.totalWaitTime += waitTime;
            stats.avgProcessingTime = stats.totalWaitTime / stats.processedCount;
        }
    }

    public int getTotalQueued() {
        return groupQueues.values().stream()
                .mapToInt(PriorityBlockingQueue::size)
                .sum();
    }

    public Map<String, Integer> getQueueStats() {
        Map<String, Integer> stats = new HashMap<>();
        groupQueues.forEach((groupName, queue) -> {
            stats.put(groupName, queue.size());
        });
        return stats;
    }

    public Map<String, QueueStats> getDetailedQueueStats() {
        return new HashMap<>(queueStats);
    }

    public int getQueueSize(String groupName) {
        PriorityBlockingQueue<QueuedPlayer> queue = groupQueues.get(groupName);
        return queue != null ? queue.size() : 0;
    }
}

class QueuedPlayer implements Comparable<QueuedPlayer> {
    String playerUuid;
    String playerName;
    String groupName;
    int priority;
    long queuedAt;

    public QueuedPlayer(String playerUuid, String playerName, String groupName, int priority, long queuedAt) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.groupName = groupName;
        this.priority = priority;
        this.queuedAt = queuedAt;
    }

    @Override
    public int compareTo(QueuedPlayer other) {
        // Higher priority first, then FIFO
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

    public QueueStats(String groupName) {
        this.groupName = groupName;
    }
}
