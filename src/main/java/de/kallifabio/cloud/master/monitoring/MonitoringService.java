/**
 * Erstellt von Gamer_Kidd_LP | kallifabio
 * am 09.01.2026 um 20:58
 * Projektname: CloudSystemTest
 * Packagename: de.kallifabio.cloud.master.monitoring
 */

package de.kallifabio.cloud.master.monitoring;

import de.kallifabio.cloud.libs.console.ConsoleColors;
import de.kallifabio.cloud.libs.console.ConsoleScreenManager;
import de.kallifabio.cloud.master.Master;
import de.kallifabio.cloud.master.ServerInstance;
import de.kallifabio.cloud.master.WrapperConnection;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

public class MonitoringService {

    private final Master master;

    // Metrics Storage
    private final Map<String, List<WrapperMetric>> wrapperMetrics = new ConcurrentHashMap<>();
    private final Map<String, List<ServerMetric>> serverMetrics = new ConcurrentHashMap<>();

    // Alerts
    private final List<Alert> activeAlerts = new CopyOnWriteArrayList<>();
    private final List<Alert> alertHistory = new CopyOnWriteArrayList<>();

    // Thresholds
    private static final double CPU_WARNING_THRESHOLD = 80.0;
    private static final double CPU_CRITICAL_THRESHOLD = 95.0;
    private static final double MEMORY_WARNING_THRESHOLD = 85.0;
    private static final double MEMORY_CRITICAL_THRESHOLD = 95.0;
    private static final double TPS_WARNING_THRESHOLD = 17.0;
    private static final double TPS_CRITICAL_THRESHOLD = 15.0;

    public MonitoringService(Master master) {
        this.master = master;
        ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Monitoring Service initialisiert");
    }

    public void recordWrapperMetrics(WrapperConnection wrapper) {
        WrapperMetric metric = new WrapperMetric(
                wrapper.getWrapperId(),
                wrapper.getAvailableMemory(),
                wrapper.getMaxMemory(),
                wrapper.getCpuUsage(),
                wrapper.getActiveServers(),
                System.currentTimeMillis()
        );

        wrapperMetrics.computeIfAbsent(wrapper.getWrapperId(), k -> new ArrayList<>()).add(metric);

        // Keep only last 1000 metrics per wrapper
        List<WrapperMetric> metrics = wrapperMetrics.get(wrapper.getWrapperId());
        if (metrics.size() > 1000) {
            metrics.remove(0);
        }

        // Check thresholds
        checkWrapperThresholds(wrapper);
    }

    public void recordServerMetrics(ServerInstance server) {
        ServerMetric metric = new ServerMetric(
                server.serverName,
                server.playerCount,
                server.maxPlayers,
                server.tps,
                server.memoryUsage,
                System.currentTimeMillis()
        );

        serverMetrics.computeIfAbsent(server.serverName, k -> new ArrayList<>()).add(metric);

        // Keep only last 1000 metrics per server
        List<ServerMetric> metrics = serverMetrics.get(server.serverName);
        if (metrics.size() > 1000) {
            metrics.remove(0);
        }

        // Check thresholds
        checkServerThresholds(server);
    }

    private void checkWrapperThresholds(WrapperConnection wrapper) {
        // CPU Check
        if (wrapper.getCpuUsage() > CPU_CRITICAL_THRESHOLD) {
            createAlert("CRITICAL", "wrapper", wrapper.getWrapperId(),
                    "CPU Usage critical: " + String.format("%.1f%%", wrapper.getCpuUsage()));
        } else if (wrapper.getCpuUsage() > CPU_WARNING_THRESHOLD) {
            createAlert("WARNING", "wrapper", wrapper.getWrapperId(),
                    "CPU Usage high: " + String.format("%.1f%%", wrapper.getCpuUsage()));
        }

        // Memory Check
        double memUsage = ((double)(wrapper.getMaxMemory() - wrapper.getAvailableMemory()) / wrapper.getMaxMemory()) * 100;
        if (memUsage > MEMORY_CRITICAL_THRESHOLD) {
            createAlert("CRITICAL", "wrapper", wrapper.getWrapperId(),
                    "Memory usage critical: " + String.format("%.1f%%", memUsage));
        } else if (memUsage > MEMORY_WARNING_THRESHOLD) {
            createAlert("WARNING", "wrapper", wrapper.getWrapperId(),
                    "Memory usage high: " + String.format("%.1f%%", memUsage));
        }
    }

    private void checkServerThresholds(ServerInstance server) {
        // TPS Check
        if (server.tps < TPS_CRITICAL_THRESHOLD) {
            createAlert("CRITICAL", "server", server.serverName,
                    "TPS critical: " + String.format("%.1f", server.tps));
        } else if (server.tps < TPS_WARNING_THRESHOLD) {
            createAlert("WARNING", "server", server.serverName,
                    "TPS low: " + String.format("%.1f", server.tps));
        }

        // Player capacity check
        if (server.playerCount >= server.maxPlayers) {
            createAlert("INFO", "server", server.serverName,
                    "Server full: " + server.playerCount + "/" + server.maxPlayers);
        }
    }

    private void createAlert(String severity, String componentType, String componentId, String message) {
        // Check if alert already exists
        boolean exists = activeAlerts.stream()
                .anyMatch(a -> a.component.equals(componentId) && a.message.equals(message));

        if (!exists) {
            Alert alert = new Alert(
                    UUID.randomUUID().toString(),
                    severity,
                    componentType + ":" + componentId,
                    message,
                    System.currentTimeMillis()
            );

            activeAlerts.add(alert);
            alertHistory.add(alert);

            // Keep only last 10000 in history
            if (alertHistory.size() > 10000) {
                alertHistory.remove(0);
            }

            ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                    " [" + severity + "] " + componentType + " " + componentId + ": " + message);
        }
    }

    public void clearAlert(String alertId) {
        activeAlerts.removeIf(a -> a.alertId.equals(alertId));
    }

    public void aggregateMetrics() {
        // Calculate aggregate statistics
        // Could be used for dashboards, reports, etc.

        long now = System.currentTimeMillis();
        long fiveMinutesAgo = now - (5 * 60 * 1000);

        // Cleanup old active alerts (older than 5 minutes)
        activeAlerts.removeIf(alert -> alert.timestamp < fiveMinutesAgo);
    }

    public Map<String, Object> getMonitoringSnapshot() {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("timestamp", System.currentTimeMillis());
        snapshot.put("wrapperMetrics", getWrapperMetrics());
        snapshot.put("serverMetrics", getServerMetrics());
        snapshot.put("activeAlerts", activeAlerts.size());
        snapshot.put("totalAlerts", alertHistory.size());
        return snapshot;
    }

    public Map<String, Object> getWrapperMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        wrapperMetrics.forEach((wrapperId, metricsList) -> {
            if (!metricsList.isEmpty()) {
                WrapperMetric latest = metricsList.get(metricsList.size() - 1);
                Map<String, Object> wrapperInfo = new HashMap<>();
                wrapperInfo.put("availableMemory", latest.availableMemory);
                wrapperInfo.put("maxMemory", latest.maxMemory);
                wrapperInfo.put("cpuUsage", latest.cpuUsage);
                wrapperInfo.put("activeServers", latest.activeServers);
                wrapperInfo.put("lastUpdate", latest.timestamp);
                metrics.put(wrapperId, wrapperInfo);
            }
        });
        return metrics;
    }

    public Map<String, Object> getServerMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        serverMetrics.forEach((serverName, metricsList) -> {
            if (!metricsList.isEmpty()) {
                ServerMetric latest = metricsList.get(metricsList.size() - 1);
                Map<String, Object> serverInfo = new HashMap<>();
                serverInfo.put("playerCount", latest.playerCount);
                serverInfo.put("maxPlayers", latest.maxPlayers);
                serverInfo.put("tps", latest.tps);
                serverInfo.put("memoryUsage", latest.memoryUsage);
                serverInfo.put("lastUpdate", latest.timestamp);
                metrics.put(serverName, serverInfo);
            }
        });
        return metrics;
    }

    public List<Alert> getActiveAlerts() {
        return new ArrayList<>(activeAlerts);
    }

    public List<Alert> getAlertHistory() {
        return new ArrayList<>(alertHistory);
    }
}

class WrapperMetric {
    String wrapperId;
    int availableMemory;
    int maxMemory;
    double cpuUsage;
    int activeServers;
    long timestamp;

    public WrapperMetric(String wrapperId, int availableMemory, int maxMemory,
                         double cpuUsage, int activeServers, long timestamp) {
        this.wrapperId = wrapperId;
        this.availableMemory = availableMemory;
        this.maxMemory = maxMemory;
        this.cpuUsage = cpuUsage;
        this.activeServers = activeServers;
        this.timestamp = timestamp;
    }
}

class ServerMetric {
    String serverName;
    int playerCount;
    int maxPlayers;
    double tps;
    long memoryUsage;
    long timestamp;

    public ServerMetric(String serverName, int playerCount, int maxPlayers,
                        double tps, long memoryUsage, long timestamp) {
        this.serverName = serverName;
        this.playerCount = playerCount;
        this.maxPlayers = maxPlayers;
        this.tps = tps;
        this.memoryUsage = memoryUsage;
        this.timestamp = timestamp;
    }
}

class Alert {
    String alertId;
    String severity;
    String component;
    String message;
    long timestamp;
    Map<String, String> details;

    public Alert(String alertId, String severity, String component, String message, long timestamp) {
        this.alertId = alertId;
        this.severity = severity;
        this.component = component;
        this.message = message;
        this.timestamp = timestamp;
        this.details = new HashMap<>();
    }
}

enum AlertSeverity {
    INFO,
    WARNING,
    CRITICAL
}

class HealthStatus {
    String componentType;
    String status; // HEALTHY, DEGRADED, UNHEALTHY
    long lastCheck;

    public HealthStatus(String componentType, String status, long lastCheck) {
        this.componentType = componentType;
        this.status = status;
        this.lastCheck = lastCheck;
    }
}
