/**
 * Erstellt von Gamer_Kidd_LP | kallifabio
 * am 09.01.2026 um 20:58
 * Projektname: CloudSystemTest
 * Packagename: de.kallifabio.cloud.master.monitoring
 */

package de.kallifabio.cloud.master.monitoring;

import com.google.gson.Gson;
import de.kallifabio.cloud.libs.console.ConsoleColors;
import de.kallifabio.cloud.libs.console.ConsoleScreenManager;
import de.kallifabio.cloud.master.Master;
import de.kallifabio.cloud.master.ServerInstance;
import de.kallifabio.cloud.master.WrapperConnection;
import de.kallifabio.cloud.config.ConfigManager;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public class MonitoringService {

    private final Master master;
    private final Gson gson = new Gson();

    // Metrics Storage
    private final Map<String, Deque<WrapperMetric>> wrapperMetrics = new ConcurrentHashMap<>();
    private final Map<String, Deque<ServerMetric>> serverMetrics = new ConcurrentHashMap<>();

    // Alerts
    private final Deque<Alert> activeAlerts = new ConcurrentLinkedDeque<>();
    private final Deque<Alert> alertHistory = new ConcurrentLinkedDeque<>();
    private final Set<String> activeAlertSignatures = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> lastAlertByKey = new ConcurrentHashMap<>();
    private final Map<String, Integer> consecutiveBreaches = new ConcurrentHashMap<>();

    // Thresholds
    private volatile double cpuWarningThreshold = 90.0;
    private volatile double cpuCriticalThreshold = 97.0;
    private volatile double memoryWarningThreshold = 90.0;
    private volatile double memoryCriticalThreshold = 96.0;
    private volatile double tpsWarningThreshold = 18.0;
    private volatile double tpsCriticalThreshold = 15.0;
    private volatile int requiredConsecutiveBreaches = 2;
    private volatile long warningAlertCooldownMs = 60_000L;
    private volatile long criticalAlertCooldownMs = 120_000L;
    private volatile long infoAlertCooldownMs = 300_000L;
    private final int hostCores = Math.max(1, Runtime.getRuntime().availableProcessors());
    private static final int MAX_ALERT_HISTORY = 10_000;

    public MonitoringService(Master master) {
        this.master = master;
        reloadThresholds();
        ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Monitoring Service initialisiert");
    }

    public void reloadThresholds() {
        ConfigManager cfg = master.getConfigManager();
        if (cfg == null) {
            return;
        }
        cpuWarningThreshold = cfg.getMonitoringCpuWarning();
        cpuCriticalThreshold = cfg.getMonitoringCpuCritical();
        memoryWarningThreshold = cfg.getMonitoringMemoryWarning();
        memoryCriticalThreshold = cfg.getMonitoringMemoryCritical();
        tpsWarningThreshold = cfg.getMonitoringTpsWarning();
        tpsCriticalThreshold = cfg.getMonitoringTpsCritical();
        requiredConsecutiveBreaches = cfg.getMonitoringRequiredConsecutiveBreaches();
        warningAlertCooldownMs = cfg.getMonitoringWarningCooldownMs();
        criticalAlertCooldownMs = cfg.getMonitoringCriticalCooldownMs();
        infoAlertCooldownMs = cfg.getMonitoringInfoCooldownMs();
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

        Deque<WrapperMetric> metrics = wrapperMetrics.computeIfAbsent(wrapper.getWrapperId(),
                k -> new ConcurrentLinkedDeque<>());
        metrics.addLast(metric);

        // Keep only last 1000 metrics per wrapper
        while (metrics.size() > 1000) {
            metrics.pollFirst();
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
                server.cpuUsage,
                server.networkInBytes,
                server.networkOutBytes,
                server.networkMode,
                server.diskReadBytes,
                server.diskWriteBytes,
                System.currentTimeMillis()
        );

        Deque<ServerMetric> metrics = serverMetrics.computeIfAbsent(server.serverName,
                k -> new ConcurrentLinkedDeque<>());
        metrics.addLast(metric);

        // Keep only last 1000 metrics per server
        while (metrics.size() > 1000) {
            metrics.pollFirst();
        }

        // Check thresholds
        checkServerThresholds(server);
    }

    private void checkWrapperThresholds(WrapperConnection wrapper) {
        double wrapperCpu = clampPercent(wrapper.getCpuUsage());
        // CPU Check
        if (wrapperCpu > cpuCriticalThreshold) {
            maybeCreateAlert("CRITICAL", "wrapper", wrapper.getWrapperId(), "WRAPPER_CPU",
                    "CPU usage critical: " + String.format("%.1f%%", wrapperCpu));
        } else if (wrapperCpu > cpuWarningThreshold) {
            maybeCreateAlert("WARNING", "wrapper", wrapper.getWrapperId(), "WRAPPER_CPU",
                    "CPU usage high: " + String.format("%.1f%%", wrapperCpu));
        } else {
            resetBreach("wrapper:" + wrapper.getWrapperId() + ":WRAPPER_CPU");
        }

        // Memory Check
        double memUsage = wrapper.getMaxMemory() <= 0 ? 0.0
                : ((double) (wrapper.getMaxMemory() - wrapper.getAvailableMemory()) / wrapper.getMaxMemory()) * 100.0;
        memUsage = clampPercent(memUsage);
        if (memUsage > memoryCriticalThreshold) {
            maybeCreateAlert("CRITICAL", "wrapper", wrapper.getWrapperId(), "WRAPPER_MEMORY",
                    "Memory usage critical: " + String.format("%.1f%%", memUsage));
        } else if (memUsage > memoryWarningThreshold) {
            maybeCreateAlert("WARNING", "wrapper", wrapper.getWrapperId(), "WRAPPER_MEMORY",
                    "Memory usage high: " + String.format("%.1f%%", memUsage));
        } else {
            resetBreach("wrapper:" + wrapper.getWrapperId() + ":WRAPPER_MEMORY");
        }
    }

    private void checkServerThresholds(ServerInstance server) {
        // TPS Check
        if (server.tps < tpsCriticalThreshold) {
            maybeCreateAlert("CRITICAL", "server", server.serverName, "SERVER_TPS",
                    "TPS critical: " + String.format("%.1f", server.tps));
        } else if (server.tps < tpsWarningThreshold) {
            maybeCreateAlert("WARNING", "server", server.serverName, "SERVER_TPS",
                    "TPS low: " + String.format("%.1f", server.tps));
        } else {
            resetBreach("server:" + server.serverName + ":SERVER_TPS");
        }

        // Player capacity check
        if (server.playerCount >= server.maxPlayers) {
            maybeCreateAlert("INFO", "server", server.serverName, "SERVER_FULL",
                    "Server full: " + server.playerCount + "/" + server.maxPlayers);
        }

        double normalizedCpu = normalizeServerCpu(server.cpuUsage);
        if (normalizedCpu > cpuCriticalThreshold) {
            maybeCreateAlert("CRITICAL", "server", server.serverName, "SERVER_CPU",
                    "CPU usage critical: " + String.format("%.1f%%", normalizedCpu));
        } else if (normalizedCpu > cpuWarningThreshold) {
            maybeCreateAlert("WARNING", "server", server.serverName, "SERVER_CPU",
                    "CPU usage high: " + String.format("%.1f%%", normalizedCpu));
        } else {
            resetBreach("server:" + server.serverName + ":SERVER_CPU");
        }
    }

    private void maybeCreateAlert(String severity, String componentType, String componentId, String alertType, String message) {
        String key = componentType + ":" + componentId + ":" + alertType;
        int breaches = consecutiveBreaches.merge(key, 1, Integer::sum);
        if (breaches < requiredConsecutiveBreaches) {
            return;
        }
        long now = System.currentTimeMillis();
        long cooldown = switch (severity.toUpperCase(Locale.ROOT)) {
            case "CRITICAL" -> criticalAlertCooldownMs;
            case "WARNING" -> warningAlertCooldownMs;
            default -> infoAlertCooldownMs;
        };
        Long last = lastAlertByKey.get(key);
        if (last != null && now - last < cooldown) {
            return;
        }
        lastAlertByKey.put(key, now);
        createAlert(severity, componentType, componentId, message);
    }

    private void resetBreach(String key) {
        consecutiveBreaches.remove(key);
    }

    private void createAlert(String severity, String componentType, String componentId, String message) {
        String componentKey = componentType + ":" + componentId;
        String signature = componentKey + "|" + message;
        if (!activeAlertSignatures.add(signature)) {
            return;
        }

        Alert alert = new Alert(
                UUID.randomUUID().toString(),
                severity,
                componentKey,
                message,
                System.currentTimeMillis()
        );

        activeAlerts.addLast(alert);
        alertHistory.addLast(alert);

        // Keep only last N in history
        while (alertHistory.size() > MAX_ALERT_HISTORY) {
            alertHistory.pollFirst();
        }

        ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " [" + severity + "] " + componentType + " " + componentId + ": " + message);

        if ("CRITICAL".equalsIgnoreCase(severity) || "WARNING".equalsIgnoreCase(severity)) {
            sendWebhookAlert(severity, componentType, componentId, message);
        }
    }

    private void sendWebhookAlert(String severity, String componentType, String componentId, String message) {
        try {
            String webhookUrl = master.getConfigManager().getAlertWebhookUrl();
            if (webhookUrl == null || webhookUrl.isBlank()) {
                return;
            }

            Map<String, Object> field1 = Map.of(
                    "name", "Component",
                    "value", componentType + ":" + componentId,
                    "inline", true
            );
            Map<String, Object> field2 = Map.of(
                    "name", "Severity",
                    "value", severity,
                    "inline", true
            );
            Map<String, Object> embed = new LinkedHashMap<>();
            embed.put("title", "[" + severity + "] Monitoring Alert");
            embed.put("description", message);
            embed.put("color", mapSeverityColor(severity));
            embed.put("timestamp", Instant.now().toString());
            embed.put("fields", List.of(field1, field2));
            embed.put("footer", Map.of("text", "KalliCloud Monitoring"));

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("username", "KalliCloud Alerts");
            payload.put("embeds", List.of(embed));

            postWebhookJson(webhookUrl, gson.toJson(payload));
        } catch (Exception ignored) {
        }
    }

    public void publishEvent(String eventType, Map<String, Object> details) {
        try {
            String webhookUrl = master.getConfigManager().getAlertWebhookUrl();
            if (webhookUrl == null || webhookUrl.isBlank()) {
                return;
            }
            Map<String, Object> embed = buildEventEmbed(eventType, details);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("username", "KalliCloud Events");
            payload.put("embeds", List.of(embed));

            postWebhookJson(webhookUrl, gson.toJson(payload));
        } catch (Exception ignored) {
        }
    }

    private Map<String, Object> buildEventEmbed(String eventType, Map<String, Object> details) {
        String normalizedType = eventType == null ? "UNKNOWN" : eventType.toUpperCase(Locale.ROOT);
        EventStyle style = styleForEvent(normalizedType);
        Map<String, Object> safeDetails = details == null ? Map.of() : details;
        Set<String> usedKeys = new HashSet<>();
        List<Map<String, Object>> fields = new ArrayList<>();

        for (String key : style.preferredKeys) {
            if (!safeDetails.containsKey(key)) {
                continue;
            }
            fields.add(Map.of(
                    "name", keyToLabel(key),
                    "value", truncate(String.valueOf(safeDetails.get(key)), 1024),
                    "inline", true
            ));
            usedKeys.add(key);
        }

        for (Map.Entry<String, Object> entry : safeDetails.entrySet()) {
            if (fields.size() >= 10) {
                break;
            }
            if (usedKeys.contains(entry.getKey())) {
                continue;
            }
            fields.add(Map.of(
                    "name", truncate(entry.getKey(), 64),
                    "value", truncate(String.valueOf(entry.getValue()), 1024),
                    "inline", true
            ));
        }

        Map<String, Object> embed = new LinkedHashMap<>();
        embed.put("title", style.icon + " Event: " + normalizedType);
        embed.put("description", style.description);
        embed.put("color", style.color);
        embed.put("timestamp", Instant.now().toString());
        embed.put("fields", fields);
        embed.put("footer", Map.of("text", "KalliCloud Events"));
        return embed;
    }

    private EventStyle styleForEvent(String eventType) {
        return switch (eventType) {
            case "SERVER_START" -> new EventStyle("[START]", 0x2ECC71, "Server startup has been initiated",
                    List.of("server", "group", "wrapper", "port"));
            case "SERVER_STOP" -> new EventStyle("[STOP]", 0xE67E22, "Server stop has been initiated",
                    List.of("server", "group", "wrapper", "mode"));
            case "SERVER_CRASH" -> new EventStyle("[CRASH]", 0xE74C3C, "Server crash was detected",
                    List.of("server", "group", "wrapper", "recover"));
            case "CRASH_PLAYER_TRANSFER" -> new EventStyle("[TRANSFER]", 0x3498DB, "Players were transferred after crash",
                    List.of("fromServer", "toServer", "playersTransferred"));
            case "NO_FALLBACK_SERVER" -> new EventStyle("[FALLBACK]", 0x9B59B6, "No fallback server available",
                    List.of("failedServer", "group"));
            case "WEBHOOK_TEST" -> new EventStyle("[TEST]", 0x95A5A6, "Manual webhook test",
                    List.of("message", "timestamp"));
            default -> new EventStyle("[EVENT]", 0x7F8C8D, "Cloud event notification",
                    List.of("server", "group", "wrapper", "mode"));
        };
    }

    private String keyToLabel(String key) {
        return switch (key) {
            case "server" -> "Server";
            case "group" -> "Group";
            case "wrapper" -> "Wrapper";
            case "port" -> "Port";
            case "mode" -> "Mode";
            case "recover" -> "Recover";
            case "fromServer" -> "From Server";
            case "toServer" -> "To Server";
            case "playersTransferred" -> "Players Transferred";
            case "failedServer" -> "Failed Server";
            case "message" -> "Message";
            case "timestamp" -> "Timestamp";
            default -> key;
        };
    }

    private void postWebhookJson(String webhookUrl, String json) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(webhookUrl).openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(3000);
        try (OutputStream os = connection.getOutputStream()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }
        connection.getResponseCode();
        connection.disconnect();
    }

    private int mapSeverityColor(String severity) {
        if ("CRITICAL".equalsIgnoreCase(severity)) {
            return 0xE74C3C;
        }
        if ("WARNING".equalsIgnoreCase(severity)) {
            return 0xF39C12;
        }
        return 0x2ECC71;
    }

    private double clampPercent(double value) {
        if (Double.isNaN(value) || value < 0) {
            return 0.0;
        }
        if (value > 100.0) {
            return 100.0;
        }
        return value;
    }

    private double normalizeServerCpu(double rawCpu) {
        if (Double.isNaN(rawCpu) || rawCpu < 0) {
            return 0.0;
        }
        double normalized = rawCpu;
        if (rawCpu > 100.0) {
            normalized = rawCpu / hostCores;
        }
        return clampPercent(normalized);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    public void clearAlert(String alertId) {
        if (alertId == null || alertId.isBlank()) {
            return;
        }
        Iterator<Alert> iterator = activeAlerts.iterator();
        while (iterator.hasNext()) {
            Alert alert = iterator.next();
            if (alertId.equals(alert.alertId)) {
                activeAlertSignatures.remove(alert.component + "|" + alert.message);
                iterator.remove();
                return;
            }
        }
    }

    public int clearAllAlerts() {
        int count = activeAlerts.size();
        activeAlerts.clear();
        activeAlertSignatures.clear();
        return count;
    }

    public void aggregateMetrics() {
        // Calculate aggregate statistics
        // Could be used for dashboards, reports, etc.

        long now = System.currentTimeMillis();
        long fiveMinutesAgo = now - (5 * 60 * 1000);

        // Cleanup old active alerts (older than 5 minutes)
        Iterator<Alert> iterator = activeAlerts.iterator();
        while (iterator.hasNext()) {
            Alert alert = iterator.next();
            if (alert.timestamp < fiveMinutesAgo) {
                activeAlertSignatures.remove(alert.component + "|" + alert.message);
                iterator.remove();
            }
        }
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
            WrapperMetric latest = metricsList.peekLast();
            if (latest != null) {
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
            ServerMetric latest = metricsList.peekLast();
            if (latest != null) {
                Map<String, Object> serverInfo = new HashMap<>();
                serverInfo.put("playerCount", latest.playerCount);
                serverInfo.put("maxPlayers", latest.maxPlayers);
                serverInfo.put("tps", latest.tps);
                serverInfo.put("memoryUsage", latest.memoryUsage);
                serverInfo.put("cpuUsage", latest.cpuUsage);
                serverInfo.put("networkInBytes", latest.networkInBytes);
                serverInfo.put("networkOutBytes", latest.networkOutBytes);
                serverInfo.put("networkMode", latest.networkMode);
                serverInfo.put("diskReadBytes", latest.diskReadBytes);
                serverInfo.put("diskWriteBytes", latest.diskWriteBytes);
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

class EventStyle {
    String icon;
    int color;
    String description;
    List<String> preferredKeys;

    EventStyle(String icon, int color, String description, List<String> preferredKeys) {
        this.icon = icon;
        this.color = color;
        this.description = description;
        this.preferredKeys = preferredKeys;
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
    double cpuUsage;
    long networkInBytes;
    long networkOutBytes;
    String networkMode;
    long diskReadBytes;
    long diskWriteBytes;
    long timestamp;

    public ServerMetric(String serverName, int playerCount, int maxPlayers,
                        double tps, long memoryUsage, double cpuUsage,
                        long networkInBytes, long networkOutBytes, String networkMode,
                        long diskReadBytes, long diskWriteBytes, long timestamp) {
        this.serverName = serverName;
        this.playerCount = playerCount;
        this.maxPlayers = maxPlayers;
        this.tps = tps;
        this.memoryUsage = memoryUsage;
        this.cpuUsage = cpuUsage;
        this.networkInBytes = networkInBytes;
        this.networkOutBytes = networkOutBytes;
        this.networkMode = networkMode == null ? "UNKNOWN" : networkMode;
        this.diskReadBytes = diskReadBytes;
        this.diskWriteBytes = diskWriteBytes;
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
