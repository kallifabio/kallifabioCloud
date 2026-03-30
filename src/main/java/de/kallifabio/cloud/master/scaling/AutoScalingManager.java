/**
 * Erstellt von Gamer_Kidd_LP | kallifabio
 * am 09.01.2026 um 20:46
 * Projektname: CloudSystemTest
 * Packagename: de.kallifabio.cloud.master.scaling
 */

package de.kallifabio.cloud.master.scaling;

import de.kallifabio.cloud.libs.console.ConsoleColors;
import de.kallifabio.cloud.libs.Message;
import de.kallifabio.cloud.config.ConfigManager;
import de.kallifabio.cloud.libs.console.ConsoleScreenManager;
import de.kallifabio.cloud.master.Master;
import de.kallifabio.cloud.master.ServerInstance;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

public class AutoScalingManager {

    private final Master master;
    private final ConfigManager configManager;

    // Scaling policies per group
    private final Map<String, ScalingPolicy> scalingPolicies = new ConcurrentHashMap<>();

    // Scaling history
    private final List<ScalingDecision> scalingHistory = new CopyOnWriteArrayList<>();

    // Cooldown tracking
    private final Map<String, Long> lastScaleUpTime = new ConcurrentHashMap<>();
    private final Map<String, Long> lastScaleDownTime = new ConcurrentHashMap<>();

    private static final long SCALE_UP_COOLDOWN_MS = 60000; // 1 minute
    private static final long SCALE_DOWN_COOLDOWN_MS = 180000; // 3 minutes

    public AutoScalingManager(Master master, ConfigManager configManager) {
        this.master = master;
        this.configManager = configManager;
        initializeScalingPolicies();
    }

    private void initializeScalingPolicies() {
        // Create default scaling policy for Lobby
        ScalingPolicy lobbyPolicy = new ScalingPolicy(
                "Lobby",
                1,  // min servers
                5,  // max servers
                70, // scale up at 70% capacity
                30, // scale down at 30% capacity
                true // enable predictive scaling
        );
        scalingPolicies.put("Lobby", lobbyPolicy);

        ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Auto-Scaling Manager initialisiert");
    }

    public void evaluate() {
        for (Map.Entry<String, ScalingPolicy> entry : scalingPolicies.entrySet()) {
            String groupName = entry.getKey();
            ScalingPolicy policy = entry.getValue();

            if (!policy.enabled) continue;

            List<ServerInstance> servers = getGroupServers(groupName);
            int currentCount = servers.size();

            // Calculate average load
            double avgLoad = calculateAverageLoad(servers);

            // Decide scaling action
            if (shouldScaleUp(policy, currentCount, avgLoad)) {
                scaleUp(groupName, policy, currentCount);
            } else if (shouldScaleDown(policy, currentCount, avgLoad)) {
                scaleDown(groupName, policy, currentCount);
            }
        }
    }

    private boolean shouldScaleUp(ScalingPolicy policy, int currentCount, double avgLoad) {
        // Don't scale if at max
        if (currentCount >= policy.maxServers) {
            return false;
        }

        // Check cooldown
        Long lastScaleUp = lastScaleUpTime.get(policy.groupName);
        if (lastScaleUp != null && System.currentTimeMillis() - lastScaleUp < SCALE_UP_COOLDOWN_MS) {
            return false;
        }

        // Check if load exceeds threshold
        return avgLoad > policy.scaleUpThreshold;
    }

    private boolean shouldScaleDown(ScalingPolicy policy, int currentCount, double avgLoad) {
        // Don't scale if at min
        if (currentCount <= policy.minServers) {
            return false;
        }

        // Check cooldown
        Long lastScaleDown = lastScaleDownTime.get(policy.groupName);
        if (lastScaleDown != null && System.currentTimeMillis() - lastScaleDown < SCALE_DOWN_COOLDOWN_MS) {
            return false;
        }

        // Check if load is below threshold
        return avgLoad < policy.scaleDownThreshold;
    }

    private void scaleUp(String groupName, ScalingPolicy policy, int currentCount) {
        int targetCount = Math.min(currentCount + 1, policy.maxServers);

        ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Scale-Up: " + groupName + " von " + currentCount + " auf " + targetCount);

        // Start new server
        String serverName = groupName + "-" + (currentCount + 1);
        master.startServer(serverName, groupName);

        // Record decision
        recordScalingDecision(groupName, "SCALE_UP", targetCount, "Load exceeded threshold");
        lastScaleUpTime.put(groupName, System.currentTimeMillis());
    }

    private void scaleDown(String groupName, ScalingPolicy policy, int currentCount) {
        int targetCount = Math.max(currentCount - 1, policy.minServers);

        ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Scale-Down: " + groupName + " von " + currentCount + " auf " + targetCount);

        // Find least loaded server to stop
        ServerInstance serverToStop = findLeastLoadedServer(groupName);
        if (serverToStop != null) {
            master.stopServer(serverToStop.serverName);
        }

        // Record decision
        recordScalingDecision(groupName, "SCALE_DOWN", targetCount, "Load below threshold");
        lastScaleDownTime.put(groupName, System.currentTimeMillis());
    }

    private double calculateAverageLoad(List<ServerInstance> servers) {
        if (servers.isEmpty()) return 0.0;

        double totalLoad = 0.0;
        for (ServerInstance server : servers) {
            if ("ONLINE".equals(server.status)) {
                double load = (double) server.playerCount / server.maxPlayers * 100;
                totalLoad += load;
            }
        }

        return totalLoad / servers.size();
    }

    private List<ServerInstance> getGroupServers(String groupName) {
        return master.getRunningServers().values().stream()
                .filter(s -> s.groupName.equalsIgnoreCase(groupName))
                .filter(s -> "ONLINE".equals(s.status) || "STARTING".equals(s.status))
                .toList();
    }

    private ServerInstance findLeastLoadedServer(String groupName) {
        return master.getRunningServers().values().stream()
                .filter(s -> s.groupName.equalsIgnoreCase(groupName))
                .filter(s -> "ONLINE".equals(s.status))
                .min(Comparator.comparingInt(s -> s.playerCount))
                .orElse(null);
    }

    public void evaluateMetrics(Message.ServerMetrics metrics) {
        // Check if specific server needs attention
        if (metrics.tps < 15.0) {
            ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                    " Server " + metrics.serverName + " hat niedrige TPS: " + metrics.tps);
        }
    }

    public void checkScalingNeeded(String serverName) {
        ServerInstance server = master.getRunningServers().get(serverName);
        if (server == null) return;

        // Check if group needs scaling
        List<ServerInstance> groupServers = getGroupServers(server.groupName);
        double avgLoad = calculateAverageLoad(groupServers);

        if (avgLoad > 80.0) {
            ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                    " Gruppe " + server.groupName + " könnte Scaling benötigen (Load: " +
                    String.format("%.1f%%)", avgLoad));
        }
    }

    public void replaceFailedServer(ServerInstance failedServer) {
        ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Ersetze ausgefallenen Server: " + failedServer.serverName);

        // Start replacement server
        String newServerName = failedServer.groupName + "-replacement-" + System.currentTimeMillis();
        master.startServer(newServerName, failedServer.groupName);

        recordScalingDecision(failedServer.groupName, "REPLACE",
                getGroupServers(failedServer.groupName).size() + 1,
                "Replacing failed server " + failedServer.serverName);
    }

    private void recordScalingDecision(String groupName, String action, int targetCount, String reason) {
        ScalingDecision decision = new ScalingDecision(
                groupName,
                action,
                targetCount,
                reason,
                System.currentTimeMillis()
        );

        scalingHistory.add(decision);

        // Keep only last 1000 decisions
        if (scalingHistory.size() > 1000) {
            scalingHistory.remove(0);
        }

        // Broadcast to cluster
        Message.ScalingDecision msg = new Message.ScalingDecision();
        msg.groupName = groupName;
        msg.action = action;
        msg.targetCount = targetCount;
        msg.reason = reason;
        msg.timestamp = System.currentTimeMillis();

        master.getServer().sendToAllTCP(msg);
    }

    public Map<String, ScalingPolicy> getScalingPolicies() {
        return new HashMap<>(scalingPolicies);
    }

    public void updateScalingPolicy(String groupName, ScalingPolicy policy) {
        scalingPolicies.put(groupName, policy);
        ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Scaling-Policy aktualisiert für " + groupName);
    }

    public List<ScalingDecision> getScalingHistory() {
        return new ArrayList<>(scalingHistory);
    }
}

class GroupMetrics {
    int totalPlayers;
    int totalCapacity;
    int serverCount;
    double avgTps;
    long totalMemory;

    public GroupMetrics(int totalPlayers, int totalCapacity, int serverCount,
                        double avgTps, long totalMemory) {
        this.totalPlayers = totalPlayers;
        this.totalCapacity = totalCapacity;
        this.serverCount = serverCount;
        this.avgTps = avgTps;
        this.totalMemory = totalMemory;
    }
}

class MetricSnapshot {
    long timestamp;
    GroupMetrics metrics;

    public MetricSnapshot(long timestamp, GroupMetrics metrics) {
        this.timestamp = timestamp;
        this.metrics = metrics;
    }
}

class ScalingDecision {
    String groupName;
    String action;
    int targetCount;
    String reason;
    long timestamp;

    public ScalingDecision(String groupName, String action, int targetCount, String reason, long timestamp) {
        this.groupName = groupName;
        this.action = action;
        this.targetCount = targetCount;
        this.reason = reason;
        this.timestamp = timestamp;
    }
}

enum ScalingAction {
    SCALE_UP,
    SCALE_DOWN,
    NO_ACTION
}

class LoadPattern {
    String groupName;
    Set<Integer> peakHours = new HashSet<>();
    Map<Integer, Double> hourlyLoadMultiplier = new HashMap<>();

    public LoadPattern(String groupName) {
        this.groupName = groupName;
        initializeDefaults();
    }

    private void initializeDefaults() {
        // Default load pattern - higher in evening
        for (int hour = 0; hour < 24; hour++) {
            if (hour >= 17 && hour <= 22) {
                hourlyLoadMultiplier.put(hour, 1.5); // 50% more load
            } else if (hour >= 12 && hour <= 16) {
                hourlyLoadMultiplier.put(hour, 1.2); // 20% more load
            } else {
                hourlyLoadMultiplier.put(hour, 0.8); // 20% less load
            }
        }
    }

    public void addPeakHour(int hour) {
        peakHours.add(hour);
    }

    public boolean isPeakHour(int hour) {
        return peakHours.contains(hour);
    }

    public double getLoadMultiplier(int hour) {
        return hourlyLoadMultiplier.getOrDefault(hour, 1.0);
    }
}
