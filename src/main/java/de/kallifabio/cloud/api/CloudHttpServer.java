/**
 * Erstellt von Gamer_Kidd_LP | kallifabio
 * am 09.01.2026 um 21:01
 * Projektname: CloudSystemTest
 * Packagename: de.kallifabio.cloud.api
 */

package de.kallifabio.cloud.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import de.kallifabio.cloud.data.PlayerData;
import de.kallifabio.cloud.libs.Message;
import de.kallifabio.cloud.libs.console.ConsoleColors;
import de.kallifabio.cloud.libs.console.ConsoleScreenManager;
import de.kallifabio.cloud.libs.logging.CentralLogger;
import de.kallifabio.cloud.master.Master;
import de.kallifabio.cloud.master.permissions.PermissionGroup;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

public class CloudHttpServer {

    private HttpServer server;
    private LiveWebSocketServer liveWebSocketServer;
    private final Gson gson;
    private static final int DEFAULT_PORT = 8081;
    private static final int DEFAULT_WS_PORT = 8090;
    private static final int PORT_FALLBACK_RANGE = 20;
    private static final int PORT_BIND_RETRIES = 3;
    private static final long PORT_RETRY_DELAY_MS = 300L;
    private final Map<String, String> apiKeys = new HashMap<>();
    private final Map<String, String> apiKeyRoles = new HashMap<>();
    private final Map<String, Deque<Long>> requestTimestampsByIp = new ConcurrentHashMap<>();
    private final Map<String, Long> blockedIps = new ConcurrentHashMap<>();
    private static final int RATE_LIMIT_PER_MINUTE = 240;
    private static final long BLOCK_DURATION_MS = 5 * 60 * 1000L;
    private final Deque<Map<String, Object>> metricHistory = new ArrayDeque<>();
    private boolean tlsEnabled = false;
    private int port = DEFAULT_PORT;
    private int wsPort = DEFAULT_WS_PORT;

    public CloudHttpServer() {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        initializeApiKeys();
        try {
            startServer();
        } catch (IOException e) {
            ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                    " FEHLER beim Starten des HTTP-Servers: " + e.getMessage());
        }
    }

    private void initializeApiKeys() {
        String configuredAdmin = Master.getInstance() != null
                ? Master.getInstance().getConfigManager().getMaster("CloudMaster.API.AdminKey")
                : null;
        if (configuredAdmin == null || configuredAdmin.isBlank()) {
            configuredAdmin = UUID.randomUUID().toString();
        }

        String configuredDashboard = Master.getInstance() != null
                ? Master.getInstance().getConfigManager().getMaster("CloudMaster.API.DashboardKey")
                : null;
        if (configuredDashboard == null || configuredDashboard.isBlank()) {
            configuredDashboard = UUID.randomUUID().toString();
        }

        apiKeys.put("admin", configuredAdmin);
        apiKeys.put("dashboard", configuredDashboard);
        apiKeyRoles.put(configuredAdmin, "ADMIN");
        apiKeyRoles.put(configuredDashboard, "VIEWER");
        ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " API Keys loaded (admin + dashboard)");
    }

    private void startServer() throws IOException {
        Master master = Master.getInstance();
        tlsEnabled = master != null && master.getConfigManager().isApiTlsEnabled();
        if (master != null) {
            try {
                port = Integer.parseInt(master.getConfigManager().getMaster("CloudMaster.API.Port"));
            } catch (Exception ignored) {
                port = DEFAULT_PORT;
            }
        }
        SSLContext sslContext = null;

        if (tlsEnabled) {
            try {
                sslContext = buildSslContext();
            } catch (Exception e) {
                tlsEnabled = false;
                ConsoleScreenManager.printToTerminal(ConsoleColors.YELLOW + ConsoleColors.PREFIX +
                        ConsoleColors.getCurrentTime() + " TLS deaktiviert (Setup fehlgeschlagen): " + e.getMessage());
            }
        }

        server = bindHttpServerWithFallback(port, sslContext);
        server.setExecutor(Executors.newFixedThreadPool(10));

        // API Endpoints
        setupEndpoints();

        server.start();
        startLiveWebSocket(sslContext);
        ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " REST API gestartet auf Port " + port + (tlsEnabled ? " (TLS)" : " (HTTP)"));
    }

    private void startLiveWebSocket(SSLContext sslContext) {
        Exception lastError = null;
        for (int offset = 0; offset <= 20; offset++) {
            int candidate = wsPort + offset;
            try {
                LiveWebSocketServer ws = new LiveWebSocketServer(candidate);
                ws.setSnapshotSupplier(this::buildLiveSnapshot);
                ws.setApiKeyValidator(this::isKnownApiKey);
                if (tlsEnabled && sslContext != null) {
                    ws.configureTls(sslContext);
                }
                ws.start();
                liveWebSocketServer = ws;
                wsPort = candidate;
                if (offset > 0) {
                    ConsoleScreenManager.printToTerminal(ConsoleColors.YELLOW + ConsoleColors.PREFIX +
                            ConsoleColors.getCurrentTime() + " WS-Port belegt, nutze Fallback-Port " + wsPort);
                }
                ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                        " Live WebSocket gestartet auf Port " + wsPort + (tlsEnabled ? " (WSS)" : " (WS)"));
                return;
            } catch (Exception ex) {
                lastError = ex;
            }
        }
        String msg = lastError == null ? "unbekannter Fehler" : lastError.getMessage();
        ConsoleScreenManager.printToTerminal(ConsoleColors.RED + ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Konnte WebSocket nicht starten: " + msg);
    }

    private void setupEndpoints() {
        // Health & Status
        server.createContext("/api/v1/health", this::handleHealth);
        server.createContext("/api/v1/auth/me", this::handleAuthMe);
        server.createContext("/api/v1/auth/rotate", this::handleRotateKey);
        server.createContext("/api/v1/status", this::handleStatus);
        server.createContext("/api/v1/dashboard/overview", this::handleDashboardOverview);

        // Cluster Management
        server.createContext("/api/v1/cluster/info", this::handleClusterInfo);
        server.createContext("/api/v1/cluster/nodes", this::handleClusterNodes);

        // Server Management
        server.createContext("/api/v1/servers", this::handleServers);
        server.createContext("/api/v1/servers/start", this::handleServerStart);
        server.createContext("/api/v1/servers/stop", this::handleServerStop);
        server.createContext("/api/v1/servers/restart", this::handleServerRestart);

        // Wrapper Management
        server.createContext("/api/v1/wrappers", this::handleWrappers);

        // Monitoring
        server.createContext("/api/v1/metrics", this::handleMetrics);
        server.createContext("/api/v1/metrics/history", this::handleMetricsHistory);
        server.createContext("/api/v1/metrics/prometheus", this::handlePrometheusMetrics);
        server.createContext("/api/v1/alerts", this::handleAlerts);

        // Scaling
        server.createContext("/api/v1/scaling/policies", this::handleScalingPolicies);
        server.createContext("/api/v1/scaling/trigger", this::handleScalingTrigger);

        // Queue Management
        server.createContext("/api/v1/queue/status", this::handleQueueStatus);
        server.createContext("/api/v1/player/data", this::handlePlayerData);
        server.createContext("/api/v1/player/friends", this::handlePlayerFriends);
        server.createContext("/api/v1/player/party", this::handlePlayerParty);
        server.createContext("/api/v1/party/switch", this::handlePartySwitch);
        server.createContext("/api/v1/permissions/group", this::handlePermissionGroup);
        server.createContext("/api/v1/permissions/assign", this::handlePermissionAssign);
        server.createContext("/api/v1/permissions/temp", this::handleTempPermission);

        // Load Balancer
        server.createContext("/api/v1/loadbalancer/stats", this::handleLoadBalancerStats);

        // Group runtime management
        server.createContext("/api/v1/groups", this::handleGroups);
        server.createContext("/api/v1/groups/create", this::handleGroupCreate);
        server.createContext("/api/v1/groups/delete", this::handleGroupDelete);
        server.createContext("/api/v1/groups/update", this::handleGroupUpdate);

        // Template management
        server.createContext("/api/v1/templates/diff", this::handleTemplateDiff);
        server.createContext("/api/v1/templates/rollback", this::handleTemplateRollback);
        server.createContext("/api/v1/templates/versions", this::handleTemplateVersions);

        // Operations
        server.createContext("/api/v1/logs/recent", this::handleRecentLogs);
        server.createContext("/api/v1/config/get", this::handleConfigGet);
        server.createContext("/api/v1/config/set", this::handleConfigSet);
        server.createContext("/api/v1/webhook/test", this::handleWebhookTest);

        // Dashboard (serve static HTML)
        server.createContext("/dashboard", this::handleDashboard);
        server.createContext("/", this::handleRoot);
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }

        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", System.currentTimeMillis());
        health.put("version", "1.0.0");

        sendResponse(exchange, 200, health);
    }

    private void handleAuthMe(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        String apiKey = readApiKey(exchange);
        String role = apiKeyRoles.getOrDefault(apiKey, "UNKNOWN");
        String hostHeader = exchange.getRequestHeaders().getFirst("Host");
        String host = (hostHeader != null && !hostHeader.isBlank())
                ? hostHeader.split(":")[0]
                : (exchange.getLocalAddress() != null ? exchange.getLocalAddress().getHostString() : "localhost");
        String wsScheme = tlsEnabled ? "wss" : "ws";
        sendResponse(exchange, 200, Map.of(
                "authenticated", true,
                "role", role,
                "wsUrl", wsScheme + "://" + host + ":" + wsPort + "/live"
        ));
    }

    private void handleRotateKey(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        String apiKey = readApiKey(exchange);
        String callerRole = apiKeyRoles.getOrDefault(apiKey, "VIEWER");
        if (!"ADMIN".equalsIgnoreCase(callerRole)) {
            sendResponse(exchange, 403, Map.of("error", "Admin key required"));
            return;
        }
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }

        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, Object> request = gson.fromJson(body, Map.class);
        String keyName = request != null && request.get("keyName") != null ? request.get("keyName").toString() : "dashboard";
        if (!apiKeys.containsKey(keyName)) {
            sendResponse(exchange, 400, Map.of("error", "Unknown keyName. Use admin|dashboard"));
            return;
        }

        String previous = apiKeys.get(keyName);
        String rotated = UUID.randomUUID().toString();
        apiKeys.put(keyName, rotated);
        if (previous != null) {
            apiKeyRoles.remove(previous);
        }
        apiKeyRoles.put(apiKeys.get("admin"), "ADMIN");
        apiKeyRoles.put(apiKeys.get("dashboard"), "VIEWER");

        if (Master.getInstance() != null) {
            String configKey = "admin".equalsIgnoreCase(keyName) ? "CloudMaster.API.AdminKey" : "CloudMaster.API.DashboardKey";
            Master.getInstance().getConfigManager().getMasterConfigData().set(configKey, rotated);
            Master.getInstance().getConfigManager().getMasterConfigData()
                    .save(Master.getInstance().getConfigManager().getMasterConfigFile());
        }

        sendResponse(exchange, 200, Map.of(
                "message", "Key rotated",
                "keyName", keyName,
                "newKey", rotated
        ));
    }

    private void handleDashboardOverview(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }

        Master master = Master.getInstance();
        Map<String, Object> monitoring = master.getMonitoringService().getMonitoringSnapshot();
        List<Map<String, Object>> servers = new ArrayList<>();
        master.getRunningServers().values().forEach(server -> {
            Map<String, Object> serverInfo = new HashMap<>();
            serverInfo.put("serverName", server.serverName);
            serverInfo.put("groupName", server.groupName);
            serverInfo.put("status", server.status);
            serverInfo.put("wrapperId", server.wrapperId);
            serverInfo.put("playerCount", server.playerCount);
            serverInfo.put("maxPlayers", server.maxPlayers);
            serverInfo.put("tps", server.tps);
            serverInfo.put("cpuUsage", server.cpuUsage);
            serverInfo.put("memoryUsage", server.memoryUsage);
            serverInfo.put("port", server.port);
            servers.add(serverInfo);
        });

        List<Map<String, Object>> wrappers = new ArrayList<>();
        master.getConnectedWrappers().values().forEach(wrapper -> {
            Map<String, Object> w = new HashMap<>();
            w.put("wrapperId", wrapper.getWrapperId());
            w.put("hostname", wrapper.getHostname());
            w.put("activeServers", wrapper.getActiveServers());
            w.put("cpuUsage", wrapper.getCpuUsage());
            w.put("availableMemory", wrapper.getAvailableMemory());
            w.put("maxMemory", wrapper.getMaxMemory());
            w.put("lastHeartbeat", wrapper.getLastHeartbeat());
            w.put("draining", master.getLoadBalancerManager().isWrapperDraining(wrapper.getWrapperId()));
            wrappers.add(w);
        });

        Map<String, Object> payload = new HashMap<>();
        payload.put("timestamp", System.currentTimeMillis());
        payload.put("servers", servers);
        payload.put("wrappers", wrappers);
        payload.put("queue", master.getPlayerQueueManager().getQueueStats());
        payload.put("queueTotal", master.getPlayerQueueManager().getTotalQueued());
        payload.put("monitoring", monitoring);
        payload.put("runningServers", master.getRunningServers().size());
        payload.put("connectedWrappers", master.getConnectedWrappers().size());
        sendResponse(exchange, 200, payload);
    }

    private void handleStatus(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }

        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }

        Master master = Master.getInstance();
        Map<String, Object> status = new HashMap<>();

        status.put("masterId", master.getMasterId());
        status.put("isPrimary", master.isPrimaryMaster());
        status.put("connectedWrappers", master.getConnectedWrappers().size());
        status.put("runningServers", master.getRunningServers().size());
        status.put("uptime", System.currentTimeMillis());

        sendResponse(exchange, 200, status);
    }

    private void handleClusterInfo(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }

        Master master = Master.getInstance();
        Map<String, Object> clusterInfo = new HashMap<>();

        clusterInfo.put("masterId", master.getMasterId());
        clusterInfo.put("isPrimary", master.isPrimaryMaster());
        clusterInfo.put("clusterState", master.getClusterManager().getCurrentState());
        clusterInfo.put("primaryMasterId", master.getClusterManager().getPrimaryMasterId());
        clusterInfo.put("clusterNodes", master.getClusterManager().getClusterNodes().size());

        sendResponse(exchange, 200, clusterInfo);
    }

    private void handleClusterNodes(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }

        Master master = Master.getInstance();
        List<Map<String, Object>> nodes = new ArrayList<>();

        master.getClusterManager().getClusterNodes().values().forEach(node -> {
            Map<String, Object> nodeInfo = new HashMap<>();
            nodeInfo.put("masterId", node.getMasterId());
            nodeInfo.put("hostname", node.getHostname());
            nodeInfo.put("port", node.getPort());
            nodeInfo.put("isPrimary", node.isPrimary());
            nodeInfo.put("uptime", System.currentTimeMillis() - node.getStartTime());
            nodes.add(nodeInfo);
        });

        sendResponse(exchange, 200, Map.of("nodes", nodes, "count", nodes.size()));
    }

    private void handleServers(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }

        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }

        Master master = Master.getInstance();
        List<Map<String, Object>> servers = new ArrayList<>();

        master.getRunningServers().values().forEach(server -> {
            Map<String, Object> serverInfo = new HashMap<>();
            serverInfo.put("serverName", server.serverName);
            serverInfo.put("groupName", server.groupName);
            serverInfo.put("status", server.status);
            serverInfo.put("wrapperId", server.wrapperId);  // FIX: war getWrapperId()
            serverInfo.put("playerCount", server.playerCount);
            serverInfo.put("maxPlayers", server.maxPlayers);
            serverInfo.put("tps", server.tps);
            serverInfo.put("ram", server.allocatedRam);
            serverInfo.put("cpuUsage", server.cpuUsage);
            serverInfo.put("networkInBytes", server.networkInBytes);
            serverInfo.put("networkOutBytes", server.networkOutBytes);
            serverInfo.put("networkMode", server.networkMode);
            serverInfo.put("diskReadBytes", server.diskReadBytes);
            serverInfo.put("diskWriteBytes", server.diskWriteBytes);  // FIX: war server.ram
            serverInfo.put("port", server.port);  // NEU: Port hinzugefuegt
            serverInfo.put("lastUpdate", server.lastUpdate);  // FIX: statt getStartTime()
            servers.add(serverInfo);
        });

        sendResponse(exchange, 200, Map.of("servers", servers, "count", servers.size()));
    }

    private void handleServerStart(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }

        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }

        try {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, Object> request = gson.fromJson(body, Map.class);

            String serverName = (String) request.get("serverName");
            String groupName = (String) request.get("groupName");

            if (serverName == null || groupName == null) {
                sendResponse(exchange, 400, Map.of("error", "serverName and groupName required"));
                return;
            }

            Master.getInstance().startServer(serverName, groupName);
            CentralLogger.audit("api", "server_start", serverName + " group=" + groupName);
            sendResponse(exchange, 200, Map.of("message", "Server start initiated", "serverName", serverName));
        } catch (Exception e) {
            sendResponse(exchange, 500, Map.of("error", e.getMessage()));
        }
    }

    private void handleServerStop(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }

        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }

        try {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, Object> request = gson.fromJson(body, Map.class);

            String serverName = (String) request.get("serverName");

            if (serverName == null) {
                sendResponse(exchange, 400, Map.of("error", "serverName required"));
                return;
            }

            Master.getInstance().stopServer(serverName);
            CentralLogger.audit("api", "server_stop", serverName);
            sendResponse(exchange, 200, Map.of("message", "Server stop initiated", "serverName", serverName));
        } catch (Exception e) {
            sendResponse(exchange, 500, Map.of("error", e.getMessage()));
        }
    }

    private void handleServerRestart(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }

        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }

        try {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, Object> request = gson.fromJson(body, Map.class);

            String serverName = (String) request.get("serverName");

            if (serverName == null) {
                sendResponse(exchange, 400, Map.of("error", "serverName required"));
                return;
            }

            Master.getInstance().restartServer(serverName);
            CentralLogger.audit("api", "server_restart", serverName);
            sendResponse(exchange, 200, Map.of("message", "Server restart initiated", "serverName", serverName));
        } catch (Exception e) {
            sendResponse(exchange, 500, Map.of("error", e.getMessage()));
        }
    }

    private void handleWrappers(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }

        Master master = Master.getInstance();
        List<Map<String, Object>> wrappers = new ArrayList<>();

        master.getConnectedWrappers().values().forEach(wrapper -> {
            Map<String, Object> wrapperInfo = new HashMap<>();
            wrapperInfo.put("wrapperId", wrapper.getWrapperId());
            wrapperInfo.put("hostname", wrapper.getHostname());
            wrapperInfo.put("maxMemory", wrapper.getMaxMemory());
            wrapperInfo.put("availableMemory", wrapper.getAvailableMemory());
            wrapperInfo.put("cpuUsage", wrapper.getCpuUsage());
            wrapperInfo.put("activeServers", wrapper.getActiveServers());
            wrapperInfo.put("connected", System.currentTimeMillis() - wrapper.getConnectedSince());
            wrapperInfo.put("draining", master.getLoadBalancerManager().isWrapperDraining(wrapper.getWrapperId()));
            wrappers.add(wrapperInfo);
        });

        sendResponse(exchange, 200, Map.of("wrappers", wrappers, "count", wrappers.size()));
    }

    private void handleMetrics(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }

        Master master = Master.getInstance();
        Map<String, Object> metrics = master.getMonitoringService().getMonitoringSnapshot();
        addMetricsHistory(metrics);

        sendResponse(exchange, 200, metrics);
    }

    private void handleMetricsHistory(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        List<Map<String, Object>> history;
        synchronized (metricHistory) {
            history = new ArrayList<>(metricHistory);
        }
        sendResponse(exchange, 200, Map.of("history", history));
    }

    private void handlePrometheusMetrics(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        Master master = Master.getInstance();
        StringBuilder sb = new StringBuilder();
        sb.append("# TYPE cloud_running_servers gauge\n");
        sb.append("cloud_running_servers ").append(master.getRunningServers().size()).append('\n');
        sb.append("# TYPE cloud_connected_wrappers gauge\n");
        sb.append("cloud_connected_wrappers ").append(master.getConnectedWrappers().size()).append('\n');
        sb.append("# TYPE cloud_queue_total gauge\n");
        sb.append("cloud_queue_total ").append(master.getPlayerQueueManager().getTotalQueued()).append('\n');

        for (var entry : master.getRunningServers().entrySet()) {
            String server = entry.getKey();
            var value = entry.getValue();
            sb.append("cloud_server_players{server=\"").append(server).append("\"} ").append(value.playerCount).append('\n');
            sb.append("cloud_server_tps{server=\"").append(server).append("\"} ").append(value.tps).append('\n');
            sb.append("cloud_server_cpu_usage{server=\"").append(server).append("\"} ").append(value.cpuUsage).append('\n');
            sb.append("cloud_server_memory_mb{server=\"").append(server).append("\"} ").append(value.memoryUsage).append('\n');
            sb.append("cloud_server_disk_read_bytes{server=\"").append(server).append("\"} ").append(value.diskReadBytes).append('\n');
            sb.append("cloud_server_disk_write_bytes{server=\"").append(server).append("\"} ").append(value.diskWriteBytes).append('\n');
            sb.append("cloud_server_network_in_bytes{server=\"").append(server).append("\"} ").append(value.networkInBytes).append('\n');
            sb.append("cloud_server_network_out_bytes{server=\"").append(server).append("\"} ").append(value.networkOutBytes).append('\n');
            sb.append("cloud_server_network_mode_info{server=\"").append(server)
                    .append("\",mode=\"").append(value.networkMode == null ? "NONE" : value.networkMode)
                    .append("\"} 1\n");
        }

        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; version=0.0.4");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void handleAlerts(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }

        Master master = Master.getInstance();
        Map<String, Object> alerts = new HashMap<>();

        alerts.put("active", master.getMonitoringService().getActiveAlerts());
        alerts.put("history", master.getMonitoringService().getAlertHistory());

        sendResponse(exchange, 200, alerts);
    }

    private void handleScalingPolicies(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }

        Master master = Master.getInstance();
        Map<String, Object> policies = new HashMap<>();

        master.getAutoScalingManager().getScalingPolicies().forEach((group, policy) -> {
            Map<String, Object> policyInfo = new HashMap<>();
            policyInfo.put("groupName", policy.getGroupName());
            policyInfo.put("minServers", policy.getMinServers());
            policyInfo.put("maxServers", policy.getMaxServers());
            policyInfo.put("scaleUpThreshold", policy.getScaleUpThreshold());
            policyInfo.put("scaleDownThreshold", policy.getScaleDownThreshold());
            policyInfo.put("predictiveScaling", policy.isPredictiveScaling());
            policies.put(group, policyInfo);
        });

        sendResponse(exchange, 200, policies);
    }

    private void handleScalingTrigger(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }

        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }

        sendResponse(exchange, 200, Map.of("message", "Manual scaling evaluation triggered"));
        Master.getInstance().getAutoScalingManager().evaluate();
    }

    private void handleQueueStatus(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }

        Master master = Master.getInstance();
        Map<String, Object> queueStatus = new HashMap<>();

        queueStatus.put("totalQueued", master.getPlayerQueueManager().getTotalQueued());
        queueStatus.put("stats", master.getPlayerQueueManager().getQueueStats());

        sendResponse(exchange, 200, queueStatus);
    }

    private void handlePlayerData(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }

        Master master = Master.getInstance();
        if ("GET".equals(exchange.getRequestMethod())) {
            String query = exchange.getRequestURI().getQuery();
            if (query == null || !query.startsWith("uuid=")) {
                sendResponse(exchange, 400, Map.of("error", "uuid query required"));
                return;
            }
            String uuid = query.substring("uuid=".length());
            PlayerData data = master.getDataStore().getPlayerData(uuid);
            sendResponse(exchange, 200, data);
            return;
        }

        if ("POST".equals(exchange.getRequestMethod())) {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, Object> req = gson.fromJson(body, Map.class);
            String uuid = req.get("playerUuid") == null ? null : req.get("playerUuid").toString();
            if (uuid == null || uuid.isBlank()) {
                sendResponse(exchange, 400, Map.of("error", "playerUuid required"));
                return;
            }
            PlayerData data = master.getDataStore().getPlayerData(uuid);
            if (req.get("coins") != null) {
                data.coins = ((Number) req.get("coins")).intValue();
            }
            if (req.get("lastServer") != null) {
                data.lastServer = req.get("lastServer").toString();
            }
            if (req.get("stats") instanceof Map<?, ?> map) {
                data.stats.clear();
                map.forEach((k, v) -> data.stats.put(String.valueOf(k), ((Number) v).intValue()));
            }
            if (req.get("permissions") instanceof List<?> list) {
                data.permissions.clear();
                list.forEach(v -> data.permissions.add(String.valueOf(v)));
            }
            data.lastSeen = System.currentTimeMillis();
            master.getDataStore().savePlayerData(data);
            CentralLogger.audit("api", "player_data_save", uuid);
            sendResponse(exchange, 200, Map.of("message", "player data saved", "playerUuid", uuid));
            return;
        }

        sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
    }

    private void handlePlayerFriends(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        Master master = Master.getInstance();

        if ("GET".equals(exchange.getRequestMethod())) {
            String query = exchange.getRequestURI().getQuery();
            if (query == null || !query.startsWith("uuid=")) {
                sendResponse(exchange, 400, Map.of("error", "uuid query required"));
                return;
            }
            String uuid = query.substring("uuid=".length());
            sendResponse(exchange, 200, Map.of("friends", master.getDataStore().getFriends(uuid)));
            return;
        }

        if ("POST".equals(exchange.getRequestMethod())) {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, Object> req = gson.fromJson(body, Map.class);
            String uuid = req.get("playerUuid") == null ? null : req.get("playerUuid").toString();
            if (uuid == null || !(req.get("friends") instanceof List<?> list)) {
                sendResponse(exchange, 400, Map.of("error", "playerUuid and friends[] required"));
                return;
            }
            List<String> friends = new ArrayList<>();
            list.forEach(v -> friends.add(String.valueOf(v)));
            master.getDataStore().setFriends(uuid, friends);
            CentralLogger.audit("api", "friends_save", uuid + " size=" + friends.size());
            sendResponse(exchange, 200, Map.of("message", "friends saved", "playerUuid", uuid));
            return;
        }

        sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
    }

    private void handlePlayerParty(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }

        Master master = Master.getInstance();
        if ("GET".equals(exchange.getRequestMethod())) {
            String query = exchange.getRequestURI().getQuery();
            if (query == null || !query.startsWith("partyId=")) {
                sendResponse(exchange, 400, Map.of("error", "partyId query required"));
                return;
            }
            String partyId = query.substring("partyId=".length());
            sendResponse(exchange, 200, Map.of("partyId", partyId, "members", master.getDataStore().getPartyMembers(partyId)));
            return;
        }

        if ("POST".equals(exchange.getRequestMethod())) {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, Object> req = gson.fromJson(body, Map.class);
            String partyId = req.get("partyId") == null ? null : req.get("partyId").toString();
            String leader = req.get("leaderUuid") == null ? null : req.get("leaderUuid").toString();
            if (partyId == null || leader == null || !(req.get("members") instanceof List<?> list)) {
                sendResponse(exchange, 400, Map.of("error", "partyId, leaderUuid and members[] required"));
                return;
            }
            List<String> members = new ArrayList<>();
            list.forEach(v -> members.add(String.valueOf(v)));
            if (!members.contains(leader)) {
                members.add(leader);
            }
            master.getDataStore().setPartyMembers(partyId, leader, members);
            CentralLogger.audit("api", "party_save", partyId + " size=" + members.size());
            sendResponse(exchange, 200, Map.of("message", "party saved", "partyId", partyId));
            return;
        }

        sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
    }

    private void handlePartySwitch(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }

        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, Object> req = gson.fromJson(body, Map.class);
        String partyId = req.get("partyId") == null ? null : req.get("partyId").toString();
        String targetServer = req.get("targetServer") == null ? null : req.get("targetServer").toString();
        if (partyId == null || targetServer == null) {
            sendResponse(exchange, 400, Map.of("error", "partyId and targetServer required"));
            return;
        }

        List<String> members = Master.getInstance().getDataStore().getPartyMembers(partyId);
        for (String member : members) {
            Message.PlayerTransfer transfer = new Message.PlayerTransfer();
            transfer.playerUuid = member;
            transfer.toServer = targetServer;
            transfer.reason = "Party switch";
            Master.getInstance().getServer().sendToAllTCP(transfer);
            Master.getInstance().getPlayerSessionManager().assignServer(member, targetServer);
        }
        CentralLogger.audit("api", "party_switch", partyId + " -> " + targetServer + " members=" + members.size());
        sendResponse(exchange, 200, Map.of("message", "party switched", "partyId", partyId, "members", members.size()));
    }

    private void handlePermissionGroup(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, Object> req = gson.fromJson(body, Map.class);
        String name = req.get("name") == null ? null : req.get("name").toString();
        if (name == null || name.isBlank()) {
            sendResponse(exchange, 400, Map.of("error", "name required"));
            return;
        }
        PermissionGroup group = new PermissionGroup(name);
        group.parent = req.get("parent") == null ? "" : req.get("parent").toString();
        group.weight = req.get("weight") == null ? 0 : ((Number) req.get("weight")).intValue();
        group.prefix = req.get("prefix") == null ? "" : req.get("prefix").toString();
        group.suffix = req.get("suffix") == null ? "" : req.get("suffix").toString();
        if (req.get("permissions") instanceof List<?> list) {
            for (Object p : list) {
                group.permissions.add(String.valueOf(p));
            }
        }
        Master.getInstance().getDataStore().upsertPermissionGroup(group);
        CentralLogger.audit("api", "permission_group_upsert", name);
        sendResponse(exchange, 200, Map.of("message", "permission group upserted", "name", name));
    }

    private void handlePermissionAssign(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, Object> req = gson.fromJson(body, Map.class);
        String uuid = req.get("playerUuid") == null ? null : req.get("playerUuid").toString();
        String group = req.get("group") == null ? null : req.get("group").toString();
        if (uuid == null || group == null) {
            sendResponse(exchange, 400, Map.of("error", "playerUuid and group required"));
            return;
        }
        Master.getInstance().getDataStore().assignPermissionGroup(uuid, group);
        Master.getInstance().syncPermissionsForPlayer(uuid);
        CentralLogger.audit("api", "permission_assign", uuid + " -> " + group);
        sendResponse(exchange, 200, Map.of("message", "permission group assigned"));
    }

    private void handleTempPermission(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, Object> req = gson.fromJson(body, Map.class);
        String uuid = req.get("playerUuid") == null ? null : req.get("playerUuid").toString();
        String permission = req.get("permission") == null ? null : req.get("permission").toString();
        Number duration = (Number) req.get("durationSeconds");
        if (uuid == null || permission == null || duration == null) {
            sendResponse(exchange, 400, Map.of("error", "playerUuid, permission, durationSeconds required"));
            return;
        }
        long expiresAt = System.currentTimeMillis() + (duration.longValue() * 1000L);
        Master.getInstance().getDataStore().setTempPermission(uuid, permission, expiresAt);
        Master.getInstance().syncPermissionsForPlayer(uuid);
        CentralLogger.audit("api", "permission_temp", uuid + " " + permission + " until=" + expiresAt);
        sendResponse(exchange, 200, Map.of("message", "temp permission set", "expiresAt", expiresAt));
    }

    private void handleLoadBalancerStats(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }

        Master master = Master.getInstance();
        Map<String, Object> stats = new HashMap<>();

        stats.put("serverLoads", master.getLoadBalancerManager().getServerLoads());

        sendResponse(exchange, 200, stats);
    }

    private void handleGroups(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }

        Master master = Master.getInstance();
        List<Map<String, Object>> groups = new ArrayList<>();
        for (String groupName : master.getConfigManager().getAllServerGroups()) {
            Map<String, Object> g = new HashMap<>();
            g.put("groupName", groupName);
            g.put("ram", master.getConfigManager().getRamForGroup(groupName));
            g.put("maxPlayers", master.getConfigManager().getMaxPlayersForGroup(groupName));
            g.put("minServers", master.getConfigManager().getMinServersForGroup(groupName));
            g.put("maxServers", master.getConfigManager().getMaxServersForGroup(groupName));
            g.put("maintenance", master.getConfigManager().isMaintenanceMode(groupName));
            g.put("tags", master.getConfigManager().getGroupTags(groupName));
            g.put("whitelist", master.getConfigManager().getGroupWhitelist(groupName));
            groups.add(g);
        }
        sendResponse(exchange, 200, Map.of("groups", groups));
    }

    private void handleGroupCreate(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }

        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, Object> req = gson.fromJson(body, Map.class);
        String groupName = req.get("groupName") == null ? null : req.get("groupName").toString();
        String parent = req.get("parentGroup") == null ? "" : req.get("parentGroup").toString();
        if (groupName == null || groupName.isBlank()) {
            sendResponse(exchange, 400, Map.of("error", "groupName required"));
            return;
        }

        boolean ok = Master.getInstance().getConfigManager().createOrUpdateGroup(groupName, parent);
        if (ok) {
            Master.getInstance().reloadConfiguration("api:create-group");
            CentralLogger.audit("api", "group_create", groupName + " parent=" + parent);
            sendResponse(exchange, 200, Map.of("message", "Group created", "groupName", groupName));
        } else {
            sendResponse(exchange, 500, Map.of("error", "Could not create group"));
        }
    }

    private void handleGroupDelete(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, Object> req = gson.fromJson(body, Map.class);
        String groupName = req.get("groupName") == null ? null : req.get("groupName").toString();
        if (groupName == null || groupName.isBlank()) {
            sendResponse(exchange, 400, Map.of("error", "groupName required"));
            return;
        }
        boolean ok = Master.getInstance().getConfigManager().deleteGroup(groupName);
        if (ok) {
            Master.getInstance().reloadConfiguration("api:delete-group");
            CentralLogger.audit("api", "group_delete", groupName);
            sendResponse(exchange, 200, Map.of("message", "Group deleted", "groupName", groupName));
        } else {
            sendResponse(exchange, 500, Map.of("error", "Could not delete group"));
        }
    }

    private void handleGroupUpdate(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, Object> req = gson.fromJson(body, Map.class);
        String groupName = req.get("groupName") == null ? null : req.get("groupName").toString();
        String key = req.get("key") == null ? null : req.get("key").toString();
        Object value = req.get("value");
        if (groupName == null || key == null) {
            sendResponse(exchange, 400, Map.of("error", "groupName and key required"));
            return;
        }
        boolean ok = Master.getInstance().getConfigManager().setGroupSetting(groupName, key, value);
        if (ok) {
            Master.getInstance().reloadConfiguration("api:update-group");
            CentralLogger.audit("api", "group_update", groupName + " " + key + "=" + value);
            sendResponse(exchange, 200, Map.of("message", "Group updated", "groupName", groupName, "key", key));
        } else {
            sendResponse(exchange, 500, Map.of("error", "Could not update group"));
        }
    }

    private void handleTemplateDiff(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        String query = exchange.getRequestURI().getQuery();
        String group = "Lobby";
        if (query != null && query.startsWith("group=")) {
            group = query.substring("group=".length());
        }
        List<String> diff = Master.getInstance().getTemplateManager().calculateDiff(group);
        sendResponse(exchange, 200, Map.of("group", group, "changedFiles", diff, "count", diff.size()));
    }

    private void handleTemplateRollback(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, Object> req = gson.fromJson(body, Map.class);
        String group = req.get("groupName") == null ? null : req.get("groupName").toString();
        String version = req.get("version") == null ? null : req.get("version").toString();
        if (group == null || version == null) {
            sendResponse(exchange, 400, Map.of("error", "groupName and version required"));
            return;
        }

        boolean ok = Master.getInstance().getTemplateManager().rollback(group, version);
        if (ok) {
            CentralLogger.audit("api", "template_rollback", group + " -> " + version);
            sendResponse(exchange, 200, Map.of("message", "Rollback done", "group", group, "version", version));
        } else {
            sendResponse(exchange, 500, Map.of("error", "Rollback failed"));
        }
    }

    private void handleTemplateVersions(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        String query = exchange.getRequestURI().getQuery();
        String group = "Lobby";
        if (query != null && query.startsWith("group=")) {
            group = query.substring("group=".length());
        }

        Path versionsPath = Path.of("templates_versions", group);
        if (!Files.exists(versionsPath)) {
            sendResponse(exchange, 200, Map.of("group", group, "versions", List.of()));
            return;
        }
        List<String> versions = Files.list(versionsPath)
                .filter(Files::isDirectory)
                .map(path -> path.getFileName().toString())
                .sorted(Comparator.reverseOrder())
                .toList();
        sendResponse(exchange, 200, Map.of("group", group, "versions", versions));
    }

    private void handleRecentLogs(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        Path today = Path.of("logs", "cloud-" + java.time.LocalDate.now() + ".log");
        if (!Files.exists(today)) {
            sendResponse(exchange, 200, Map.of("lines", List.of()));
            return;
        }
        List<String> lines = Files.readAllLines(today);
        int size = lines.size();
        int from = Math.max(0, size - 300);
        sendResponse(exchange, 200, Map.of("lines", lines.subList(from, size)));
    }

    private void handleConfigGet(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        String query = exchange.getRequestURI().getQuery();
        String key = query != null && query.startsWith("key=") ? query.substring("key=".length()) : null;
        if (key == null || key.isBlank()) {
            sendResponse(exchange, 400, Map.of("error", "key query required"));
            return;
        }
        String value = Master.getInstance().getConfigManager().getMaster(key);
        sendResponse(exchange, 200, Map.of("key", key, "value", value));
    }

    private void handleConfigSet(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, Object> req = gson.fromJson(body, Map.class);
        String key = req.get("key") == null ? null : req.get("key").toString();
        String value = req.get("value") == null ? null : req.get("value").toString();
        if (key == null || value == null) {
            sendResponse(exchange, 400, Map.of("error", "key and value required"));
            return;
        }
        Master.getInstance().getConfigManager().getMasterConfigData().set(key, value);
        Master.getInstance().getConfigManager().getMasterConfigData().save(Master.getInstance().getConfigManager().getMasterConfigFile());
        Master.getInstance().reloadConfiguration("api:config-set");
        sendResponse(exchange, 200, Map.of("message", "config updated", "key", key));
    }

    private void handleWebhookTest(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        Master.getInstance().getMonitoringService().publishEvent(
                "WEBHOOK_TEST",
                Map.of("message", "Manual webhook test from API", "timestamp", System.currentTimeMillis())
        );
        sendResponse(exchange, 200, Map.of("message", "webhook test sent"));
    }

    private SSLContext buildSslContext() throws Exception {
        Master master = Master.getInstance();
        if (master == null) {
            throw new IllegalStateException("Master not initialized");
        }

        String keystorePath = master.getConfigManager().getApiTlsKeystorePath();
        String keystorePassword = master.getConfigManager().getApiTlsKeystorePassword();
        String keystoreType = master.getConfigManager().getApiTlsKeystoreType();
        if (keystorePassword == null || keystorePassword.isBlank()) {
            throw new IllegalStateException("CloudMaster.API.TLS.KeystorePassword ist leer");
        }

        KeyStore keyStore = KeyStore.getInstance(keystoreType);
        try (InputStream in = Files.newInputStream(Path.of(keystorePath))) {
            keyStore.load(in, keystorePassword.toCharArray());
        }

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, keystorePassword.toCharArray());

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), null, null);
        return sslContext;
    }

    private HttpServer bindHttpServerWithFallback(int preferredPort, SSLContext sslContext) throws IOException {
        IOException last = null;
        for (int attempt = 1; attempt <= PORT_BIND_RETRIES; attempt++) {
            for (int offset = 0; offset <= PORT_FALLBACK_RANGE; offset++) {
                int candidate = preferredPort + offset;
                try {
                    HttpServer candidateServer;
                    if (tlsEnabled && sslContext != null) {
                        HttpsServer httpsServer = HttpsServer.create(new InetSocketAddress(candidate), 0);
                        httpsServer.setHttpsConfigurator(new HttpsConfigurator(sslContext));
                        candidateServer = httpsServer;
                    } else {
                        candidateServer = HttpServer.create(new InetSocketAddress(candidate), 0);
                    }
                    if (offset > 0) {
                        ConsoleScreenManager.printToTerminal(ConsoleColors.YELLOW + ConsoleColors.PREFIX +
                                ConsoleColors.getCurrentTime() + " API-Port belegt, nutze Fallback-Port " + candidate);
                    }
                    port = candidate;
                    return candidateServer;
                } catch (IOException ex) {
                    last = ex;
                }
            }

            if (attempt < PORT_BIND_RETRIES) {
                ConsoleScreenManager.printToTerminal(ConsoleColors.YELLOW + ConsoleColors.PREFIX +
                        ConsoleColors.getCurrentTime() + " API-Port-Bind fehlgeschlagen (Versuch " + attempt +
                        "), erneuter Versuch...");
                try {
                    Thread.sleep(PORT_RETRY_DELAY_MS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("API-Port-Bind unterbrochen", interrupted);
                }
            }
        }
        throw new IOException("Kein freier API-Port im Bereich " + preferredPort + "-" + (preferredPort + PORT_FALLBACK_RANGE),
                last);
    }

    private void addMetricsHistory(Map<String, Object> snapshot) {
        synchronized (metricHistory) {
            metricHistory.addLast(new HashMap<>(snapshot));
            while (metricHistory.size() > 180) {
                metricHistory.removeFirst();
            }
        }
    }

    private Map<String, Object> buildLiveSnapshot() {
        Master master = Master.getInstance();
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("type", "live_update");
        snapshot.put("timestamp", System.currentTimeMillis());
        snapshot.put("runningServers", master.getRunningServers().size());
        snapshot.put("connectedWrappers", master.getConnectedWrappers().size());
        snapshot.put("queueTotal", master.getPlayerQueueManager().getTotalQueued());
        snapshot.put("servers", new ArrayList<>(master.getRunningServers().values()));
        return snapshot;
    }

    private void handleDashboard(HttpExchange exchange) throws IOException {
        String html = generateDashboardHTML();
        byte[] payload = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html");
        exchange.sendResponseHeaders(200, payload.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(payload);
        }
    }

    private void handleRoot(HttpExchange exchange) throws IOException {
        String response = "KalliCloud API - Use /dashboard for web interface or /api/v1/* for REST API";
        byte[] payload = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, payload.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(payload);
        }
    }

    private boolean authenticateRequest(HttpExchange exchange) {
        if (!checkRateLimit(exchange)) {
            return false;
        }
        String apiKey = readApiKey(exchange);
        if (apiKey == null || !isKnownApiKey(apiKey)) {
            return false;
        }

        String role = apiKeyRoles.getOrDefault(apiKey, "VIEWER");
        String requiredRole = isMutatingRequest(exchange) ? "ADMIN" : "VIEWER";
        return hasRequiredRole(role, requiredRole);
    }

    private String readApiKey(HttpExchange exchange) {
        String authHeader = exchange.getRequestHeaders().getFirst("X-API-Key");
        if (authHeader != null && !authHeader.isBlank()) {
            return authHeader.trim();
        }
        String query = exchange.getRequestURI().getQuery();
        if (query == null || query.isBlank()) {
            return null;
        }
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2 && "apiKey".equalsIgnoreCase(parts[0])) {
                return parts[1];
            }
        }
        return null;
    }

    private boolean isKnownApiKey(String key) {
        return key != null && apiKeyRoles.containsKey(key);
    }

    private boolean isMutatingRequest(HttpExchange exchange) {
        String method = exchange.getRequestMethod();
        if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method)) {
            return true;
        }
        String path = exchange.getRequestURI().getPath();
        return path.contains("/start") || path.contains("/stop") || path.contains("/restart")
                || path.contains("/create") || path.contains("/delete") || path.contains("/update")
                || path.contains("/assign") || path.contains("/temp") || path.contains("/set")
                || path.contains("/switch") || path.contains("/rollback") || path.contains("/trigger");
    }

    private boolean hasRequiredRole(String actualRole, String requiredRole) {
        int actual = roleLevel(actualRole);
        int required = roleLevel(requiredRole);
        return actual >= required;
    }

    private int roleLevel(String role) {
        if ("ADMIN".equalsIgnoreCase(role)) {
            return 2;
        }
        if ("VIEWER".equalsIgnoreCase(role)) {
            return 1;
        }
        return 0;
    }

    private boolean checkRateLimit(HttpExchange exchange) {
        String ip = exchange.getRemoteAddress().getAddress().getHostAddress();
        long now = System.currentTimeMillis();

        Long blockedUntil = blockedIps.get(ip);
        if (blockedUntil != null && blockedUntil > now) {
            return false;
        }
        if (blockedUntil != null && blockedUntil <= now) {
            blockedIps.remove(ip);
        }

        Deque<Long> timestamps = requestTimestampsByIp.computeIfAbsent(ip, k -> new ConcurrentLinkedDeque<>());
        while (!timestamps.isEmpty()) {
            Long first = timestamps.peekFirst();
            if (first == null || now - first <= 60_000) {
                break;
            }
            timestamps.pollFirst();
        }
        timestamps.add(now);

        if (timestamps.size() > RATE_LIMIT_PER_MINUTE) {
            blockedIps.put(ip, now + BLOCK_DURATION_MS);
            return false;
        }
        return true;
    }

    private void sendResponse(HttpExchange exchange, int statusCode, Object data) throws IOException {
        String json = gson.toJson(data);
        byte[] payload = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, X-API-Key");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
        exchange.sendResponseHeaders(statusCode, payload.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(payload);
        }
    }

    private String generateDashboardHTML() {
        return """
        <!DOCTYPE html>
        <html lang='en'>
        <head>
            <meta charset='UTF-8'>
            <meta name='viewport' content='width=device-width, initial-scale=1.0'>
            <title>KalliCloud Dashboard</title>
            <script src='https://cdn.tailwindcss.com'></script>
        </head>
        <body class='min-h-screen bg-slate-950 text-slate-100'>
            <div class='mx-auto max-w-7xl p-4 md:p-6 space-y-6'>
                <header class='rounded-2xl border border-cyan-900/60 bg-gradient-to-r from-slate-900 via-cyan-950 to-slate-900 p-6 shadow-lg shadow-cyan-900/20'>
                    <div class='flex flex-col gap-4 md:flex-row md:items-end md:justify-between'>
                        <div>
                            <h1 class='text-2xl md:text-3xl font-bold tracking-tight'>KalliCloud Control Center</h1>
                            <p class='text-slate-300 mt-1'>Live operations dashboard with API role-based access.</p>
                        </div>
                        <div class='flex w-full md:w-auto gap-2'>
                            <input id='apiKey' type='password' placeholder='X-API-Key' class='w-full md:w-80 rounded-xl border border-slate-700 bg-slate-900 px-3 py-2 text-sm outline-none focus:border-cyan-400'>
                            <button id='connectBtn' class='rounded-xl bg-cyan-500 px-4 py-2 text-sm font-semibold text-slate-950 hover:bg-cyan-400'>Connect</button>
                        </div>
                    </div>
                    <div class='mt-3 flex flex-wrap items-center gap-3 text-xs'>
                        <span id='authState' class='rounded-full border border-amber-500/40 bg-amber-500/10 px-3 py-1 text-amber-200'>Not authenticated</span>
                        <span id='lastUpdate' class='rounded-full border border-slate-700 px-3 py-1 text-slate-300'>Last update: -</span>
                    </div>
                </header>

                <section class='grid grid-cols-2 gap-3 md:grid-cols-4'>
                    <article class='rounded-xl border border-slate-800 bg-slate-900/60 p-4'>
                        <p class='text-xs text-slate-400'>Running Servers</p>
                        <p id='runningServers' class='mt-1 text-2xl font-bold text-cyan-300'>0</p>
                    </article>
                    <article class='rounded-xl border border-slate-800 bg-slate-900/60 p-4'>
                        <p class='text-xs text-slate-400'>Connected Wrappers</p>
                        <p id='connectedWrappers' class='mt-1 text-2xl font-bold text-emerald-300'>0</p>
                    </article>
                    <article class='rounded-xl border border-slate-800 bg-slate-900/60 p-4'>
                        <p class='text-xs text-slate-400'>Queue Total</p>
                        <p id='queueTotal' class='mt-1 text-2xl font-bold text-violet-300'>0</p>
                    </article>
                    <article class='rounded-xl border border-slate-800 bg-slate-900/60 p-4'>
                        <p class='text-xs text-slate-400'>Active Alerts</p>
                        <p id='activeAlerts' class='mt-1 text-2xl font-bold text-rose-300'>0</p>
                    </article>
                </section>

                <section class='grid gap-6 lg:grid-cols-3'>
                    <div class='lg:col-span-2 rounded-2xl border border-slate-800 bg-slate-900/60 p-4'>
                        <div class='mb-3 flex items-center justify-between'>
                            <h2 class='font-semibold'>Servers</h2>
                            <button id='refreshBtn' class='rounded-lg border border-slate-700 px-3 py-1 text-xs hover:border-cyan-400'>Refresh</button>
                        </div>
                        <div class='overflow-auto'>
                            <table class='min-w-full text-sm'>
                                <thead class='text-left text-xs text-slate-400'>
                                    <tr>
                                        <th class='px-2 py-2'>Server</th>
                                        <th class='px-2 py-2'>Group</th>
                                        <th class='px-2 py-2'>Status</th>
                                        <th class='px-2 py-2'>Players</th>
                                        <th class='px-2 py-2'>TPS</th>
                                        <th class='px-2 py-2'>Wrapper</th>
                                        <th class='px-2 py-2'>Actions</th>
                                    </tr>
                                </thead>
                                <tbody id='serversBody' class='divide-y divide-slate-800'></tbody>
                            </table>
                        </div>
                    </div>

                    <div class='rounded-2xl border border-slate-800 bg-slate-900/60 p-4'>
                        <h2 class='mb-3 font-semibold'>Queue by Group</h2>
                        <div id='queueBox' class='space-y-2 text-sm'></div>
                        <h2 class='mb-3 mt-6 font-semibold'>Wrappers</h2>
                        <div id='wrappersBox' class='space-y-2 text-sm'></div>
                    </div>
                </section>
            </div>

            <script>
                const state = {
                    apiKey: localStorage.getItem('cloud_api_key') || '',
                    role: null,
                    ws: null,
                    wsUrl: null
                };
                const apiKeyInput = document.getElementById('apiKey');
                apiKeyInput.value = state.apiKey;

                function headers() {
                    return { 'X-API-Key': state.apiKey };
                }

                async function apiGet(path) {
                    const res = await fetch(path, { headers: headers() });
                    if (!res.ok) throw new Error(path + ' -> ' + res.status);
                    return res.json();
                }

                async function apiPost(path, body) {
                    const res = await fetch(path, {
                        method: 'POST',
                        headers: { ...headers(), 'Content-Type': 'application/json' },
                        body: JSON.stringify(body)
                    });
                    if (!res.ok) throw new Error(path + ' -> ' + res.status);
                    return res.json();
                }

                function statusClass(status) {
                    if (status === 'ONLINE') return 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/40';
                    if (status === 'STARTING') return 'bg-amber-500/20 text-amber-300 border border-amber-500/40';
                    return 'bg-rose-500/20 text-rose-300 border border-rose-500/40';
                }

                async function authenticate() {
                    state.apiKey = apiKeyInput.value.trim();
                    localStorage.setItem('cloud_api_key', state.apiKey);
                    const auth = await apiGet('/api/v1/auth/me');
                    state.role = auth.role;
                    state.wsUrl = auth.wsUrl;
                    document.getElementById('authState').textContent = 'Authenticated as ' + auth.role;
                    document.getElementById('authState').className = 'rounded-full border border-emerald-500/40 bg-emerald-500/10 px-3 py-1 text-emerald-200';
                    openWebSocket();
                    await refresh();
                }

                async function refresh() {
                    const data = await apiGet('/api/v1/dashboard/overview');
                    renderOverview(data);
                }

                function renderOverview(data) {
                    document.getElementById('runningServers').textContent = data.runningServers || 0;
                    document.getElementById('connectedWrappers').textContent = data.connectedWrappers || 0;
                    document.getElementById('queueTotal').textContent = data.queueTotal || 0;
                    document.getElementById('activeAlerts').textContent = (data.monitoring && data.monitoring.activeAlerts) || 0;
                    document.getElementById('lastUpdate').textContent = 'Last update: ' + new Date().toLocaleTimeString();

                    const serverRows = (data.servers || []).map(server => {
                        const canMutate = state.role === 'ADMIN';
                        const disabled = canMutate ? '' : 'disabled';
                        return `
                            <tr>
                                <td class='px-2 py-2 font-medium'>${server.serverName}</td>
                                <td class='px-2 py-2 text-slate-300'>${server.groupName}</td>
                                <td class='px-2 py-2'><span class='rounded-full px-2 py-1 text-xs ${statusClass(server.status)}'>${server.status}</span></td>
                                <td class='px-2 py-2'>${server.playerCount}/${server.maxPlayers}</td>
                                <td class='px-2 py-2'>${Number(server.tps || 0).toFixed(1)}</td>
                                <td class='px-2 py-2 text-slate-400'>${server.wrapperId}</td>
                                <td class='px-2 py-2'>
                                    <div class='flex gap-1'>
                                        <button ${disabled} onclick="restartServer('${server.serverName}')" class='rounded border border-slate-700 px-2 py-1 text-xs hover:border-cyan-400 disabled:opacity-30'>restart</button>
                                        <button ${disabled} onclick="stopServer('${server.serverName}')" class='rounded border border-slate-700 px-2 py-1 text-xs hover:border-rose-400 disabled:opacity-30'>stop</button>
                                    </div>
                                </td>
                            </tr>`;
                    }).join('');
                    document.getElementById('serversBody').innerHTML = serverRows || '<tr><td colspan="7" class="px-2 py-3 text-slate-400">No servers</td></tr>';

                    const queueItems = Object.entries(data.queue || {}).map(([group, size]) =>
                        `<div class='flex items-center justify-between rounded-lg border border-slate-800 bg-slate-950/60 px-3 py-2'><span>${group}</span><span class='font-semibold text-violet-300'>${size}</span></div>`
                    ).join('');
                    document.getElementById('queueBox').innerHTML = queueItems || "<p class='text-slate-400'>No queue data</p>";

                    const wrappers = (data.wrappers || []).map(wrapper => {
                        const usedPct = wrapper.maxMemory > 0 ? Math.round(((wrapper.maxMemory - wrapper.availableMemory) / wrapper.maxMemory) * 100) : 0;
                        const drain = wrapper.draining ? ' | DRAINING' : '';
                        return `<div class='rounded-lg border border-slate-800 bg-slate-950/60 p-3'>
                            <div class='font-medium'>${wrapper.wrapperId}</div>
                            <div class='text-xs text-slate-400'>${wrapper.hostname}</div>
                            <div class='mt-1 text-xs'>CPU ${Number(wrapper.cpuUsage || 0).toFixed(1)}% | RAM ${usedPct}% | Servers ${wrapper.activeServers}${drain}</div>
                        </div>`;
                    }).join('');
                    document.getElementById('wrappersBox').innerHTML = wrappers || "<p class='text-slate-400'>No wrappers connected</p>";
                }

                async function stopServer(serverName) {
                    try {
                        await apiPost('/api/v1/servers/stop', { serverName });
                        await refresh();
                    } catch (e) {
                        alert('Stop failed: ' + e.message);
                    }
                }

                async function restartServer(serverName) {
                    try {
                        await apiPost('/api/v1/servers/restart', { serverName });
                        await refresh();
                    } catch (e) {
                        alert('Restart failed: ' + e.message);
                    }
                }

                function openWebSocket() {
                    if (state.ws) {
                        state.ws.close();
                        state.ws = null;
                    }
                    const baseUrl = state.wsUrl || `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.hostname}:8090/live`;
                    const separator = baseUrl.includes('?') ? '&' : '?';
                    const wsUrl = `${baseUrl}${separator}token=${encodeURIComponent(state.apiKey)}`;
                    state.ws = new WebSocket(wsUrl);
                    state.ws.onmessage = () => { refresh().catch(() => {}); };
                }

                document.getElementById('connectBtn').addEventListener('click', () => authenticate().catch(err => {
                    document.getElementById('authState').textContent = 'Auth failed';
                    document.getElementById('authState').className = 'rounded-full border border-rose-500/40 bg-rose-500/10 px-3 py-1 text-rose-200';
                    console.error(err);
                }));
                document.getElementById('refreshBtn').addEventListener('click', () => refresh().catch(console.error));

                if (state.apiKey) {
                    authenticate().catch(() => {});
                }
            </script>
        </body>
        </html>
        """;
    }
    public void stop() {
        if (liveWebSocketServer != null) {
            liveWebSocketServer.shutdown();
        }
        if (server != null) {
            server.stop(0);
            ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                    " HTTP-Server gestoppt");
        }
    }

    public int getPort() {
        return port;
    }

    public int getWsPort() {
        return wsPort;
    }
}




