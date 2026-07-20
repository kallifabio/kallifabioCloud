/**
 * Erstellt von Gamer_Kidd_LP | kallifabio
 * am 09.01.2026 um 21:01
 * Projektname: CloudSystemTest
 * Packagename: de.kallifabio.cloud.api
 */

package de.kallifabio.cloud.api;

import com.google.gson.Gson;
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
import de.kallifabio.cloud.master.ServerInstance;
import de.kallifabio.cloud.master.permissions.PermissionGroup;
import de.kallifabio.cloud.setup.SetupValidator;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
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
    private volatile String cachedDashboardHtml;
    private static final int DEFAULT_PORT = 8081;
    private static final int DEFAULT_WS_PORT = 8090;
    private static final int PORT_FALLBACK_RANGE = 20;
    private static final int PORT_BIND_RETRIES = 3;
    private static final long PORT_RETRY_DELAY_MS = 300L;
    private final Map<String, String> apiKeys = new HashMap<>();
    private final Map<String, String> apiKeyRoles = new HashMap<>();
    private final Map<String, Long> liveWsTickets = new ConcurrentHashMap<>();
    private final Map<String, Deque<Long>> requestTimestampsByIp = new ConcurrentHashMap<>();
    private final Map<String, Long> blockedIps = new ConcurrentHashMap<>();
    private volatile Set<String> corsAllowedOrigins = Set.of("*");
    private volatile long lastRateLimitCleanupAt = 0L;
    private static final int RATE_LIMIT_PER_MINUTE = 600;
    private static final long BLOCK_DURATION_MS = 60 * 1000L;
    private static final long WS_TICKET_TTL_MS = 2 * 60 * 1000L;
    private static final long RATE_LIMIT_CLEANUP_INTERVAL_MS = 60 * 1000L;
    private final Deque<Map<String, Object>> metricHistory = new ArrayDeque<>();
    private boolean tlsEnabled = false;
    private int port = DEFAULT_PORT;
    private int wsPort = DEFAULT_WS_PORT;

    public CloudHttpServer() {
        this.gson = new Gson();
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
        configuredAdmin = sanitizeApiKey(configuredAdmin);
        if (configuredAdmin == null || configuredAdmin.isBlank()) {
            configuredAdmin = UUID.randomUUID().toString();
        }

        String configuredDashboard = Master.getInstance() != null
                ? Master.getInstance().getConfigManager().getMaster("CloudMaster.API.DashboardKey")
                : null;
        configuredDashboard = sanitizeApiKey(configuredDashboard);
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
        corsAllowedOrigins = resolveAllowedOrigins(master);
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
                ws.setApiKeyValidator(this::isValidLiveToken);
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
        server.createContext("/api/v1/auth/debug", this::handleAuthDebug);
        server.createContext("/api/v1/auth/rotate", this::handleRotateKey);
        server.createContext("/api/v1/status", this::handleStatus);
        server.createContext("/api/v1/dashboard/overview", this::handleDashboardOverview);
        server.createContext("/api/v1/system/diagnostics", this::handleSystemDiagnostics);
        server.createContext("/api/v1/system/capacity", this::handleSystemCapacityPlanner);

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
        server.createContext("/api/v1/wrappers/drain", this::handleWrapperDrain);

        // Monitoring
        server.createContext("/api/v1/metrics", this::handleMetrics);
        server.createContext("/api/v1/metrics/history", this::handleMetricsHistory);
        server.createContext("/api/v1/metrics/prometheus", this::handlePrometheusMetrics);
        server.createContext("/api/v1/alerts", this::handleAlerts);
        server.createContext("/api/v1/alerts/clear", this::handleAlertsClear);

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
        server.createContext("/api/v1/console/screens", this::handleConsoleScreens);
        server.createContext("/api/v1/console/tail", this::handleConsoleTail);
        server.createContext("/api/v1/console/send", this::handleConsoleSend);
        server.createContext("/api/v1/setup/report", this::handleSetupReport);
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
        health.put("version", "1.0.2");

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
        String wsTicket = issueWsTicket();
        sendResponse(exchange, 200, Map.of(
                "authenticated", true,
                "role", role,
                "wsUrl", wsScheme + "://" + host + ":" + wsPort + "/live",
                "wsTicket", wsTicket
        ));
    }

    private void handleAuthDebug(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        if (!isLocalRequest(exchange)) {
            sendResponse(exchange, 403, Map.of("error", "auth debug is local-only"));
            return;
        }

        String apiKey = readApiKey(exchange);
        String role = apiKey == null ? "UNKNOWN" : apiKeyRoles.getOrDefault(apiKey, "UNKNOWN");
        if (!"ADMIN".equalsIgnoreCase(role)) {
            sendResponse(exchange, 403, Map.of("error", "Admin key required for auth debug"));
            return;
        }

        Map<String, String> query = parseQueryParams(exchange.getRequestURI().getQuery());
        String targetPath = query.getOrDefault("path", "/api/v1/auth/me");
        String targetMethod = query.getOrDefault("method", "GET").toUpperCase(Locale.ROOT);

        boolean rateLimited = isRateLimitedNow(exchange);
        String requiredRole = isMutatingPath(targetMethod, targetPath) ? "ADMIN" : "VIEWER";
        boolean keyPresent = apiKey != null && !apiKey.isBlank();
        boolean keyKnown = keyPresent && isKnownApiKey(apiKey);
        boolean roleOk = hasRequiredRole(role, requiredRole);

        String reason;
        if (rateLimited) {
            reason = "rate_limited";
        } else if (!keyPresent) {
            reason = "missing_key";
        } else if (!keyKnown) {
            reason = "invalid_key";
        } else if (!roleOk) {
            reason = "insufficient_role";
        } else {
            reason = "ok";
        }

        long now = System.currentTimeMillis();
        Long blockedUntil = blockedIps.get(getClientIp(exchange));
        long blockedForMs = blockedUntil == null ? 0L : Math.max(0L, blockedUntil - now);
        Map<String, Object> payload = new HashMap<>();
        payload.put("timestamp", now);
        payload.put("clientIp", getClientIp(exchange));
        payload.put("localRequest", true);
        payload.put("targetPath", targetPath);
        payload.put("targetMethod", targetMethod);
        payload.put("requiredRole", requiredRole);
        payload.put("presentedRole", role);
        payload.put("keyPresent", keyPresent);
        payload.put("keyKnown", keyKnown);
        payload.put("rateLimited", rateLimited);
        payload.put("blockedUntil", blockedUntil == null ? 0L : blockedUntil);
        payload.put("blockedForMs", blockedForMs);
        payload.put("wouldAuthorize", "ok".equals(reason));
        payload.put("reason", reason);
        sendResponse(exchange, 200, payload);
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
        int sessionPlayers = getTrackedSessionPlayerCount(master);
        int backendPlayers = getBackendOnlinePlayerCount(master);
        master.getRunningServers().values().forEach(server -> {
            Map<String, Object> serverInfo = new HashMap<>();
            int effectivePlayers = getEffectivePlayerCount(master, server, sessionPlayers, backendPlayers);
            serverInfo.put("serverName", server.serverName);
            serverInfo.put("groupName", server.groupName);
            serverInfo.put("status", server.status);
            serverInfo.put("wrapperId", server.wrapperId);
            serverInfo.put("playerCount", effectivePlayers);
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

    private void handleSystemDiagnostics(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }

        sendResponse(exchange, 200, buildSystemDiagnostics());
    }

    private void handleSystemCapacityPlanner(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }

        sendResponse(exchange, 200, buildSystemCapacityPlanner());
    }

    private Map<String, Object> buildSystemCapacityPlanner() {
        Master master = Master.getInstance();
        long now = System.currentTimeMillis();
        List<Map<String, Object>> wrappers = new ArrayList<>();
        List<Map<String, Object>> groups = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();
        int healthyWrappers = 0;
        int drainingWrappers = 0;
        int totalWrapperMemory = 0;
        int availableWrapperMemory = 0;
        double cpuCritical = master.getConfigManager().getMonitoringCpuCritical();

        for (var wrapper : master.getConnectedWrappers().values()) {
            boolean draining = master.getLoadBalancerManager().isWrapperDraining(wrapper.getWrapperId());
            boolean healthy = wrapper.isHealthy();
            int maxMemory = Math.max(0, wrapper.getMaxMemory());
            int availableMemory = Math.max(0, wrapper.getAvailableMemory());
            totalWrapperMemory += maxMemory;
            availableWrapperMemory += availableMemory;
            if (healthy) {
                healthyWrappers++;
            }
            if (draining) {
                drainingWrappers++;
            }

            Map<String, Object> wrapperInfo = new LinkedHashMap<>();
            wrapperInfo.put("wrapperId", wrapper.getWrapperId());
            wrapperInfo.put("hostname", wrapper.getHostname());
            wrapperInfo.put("routeHost", wrapper.getRouteHost());
            wrapperInfo.put("healthy", healthy);
            wrapperInfo.put("draining", draining);
            wrapperInfo.put("maxMemoryMb", maxMemory);
            wrapperInfo.put("availableMemoryMb", availableMemory);
            wrapperInfo.put("usedMemoryMb", Math.max(0, maxMemory - availableMemory));
            wrapperInfo.put("cpuUsage", Math.round(wrapper.getCpuUsage() * 10.0) / 10.0);
            wrapperInfo.put("activeServers", wrapper.getActiveServers());
            wrappers.add(wrapperInfo);
        }

        Map<String, Object> queueStats = new LinkedHashMap<>(master.getPlayerQueueManager().getQueueStats());
        List<String> configuredGroups = new ArrayList<>(master.getConfigManager().getAllServerGroups());
        for (String groupName : configuredGroups) {
            int requiredRam = Math.max(1, safeInteger(master.getConfigManager().getRamForGroup(groupName), 1024));
            int maxPlayers = Math.max(1, safeInteger(master.getConfigManager().getMaxPlayersForGroup(groupName), 100));
            int minServers = Math.max(0, master.getConfigManager().getMinServersForGroup(groupName));
            int maxServers = Math.max(minServers, master.getConfigManager().getMaxServersForGroup(groupName));
            boolean maintenance = master.getConfigManager().isMaintenanceMode(groupName);
            boolean dynamic = master.getConfigManager().isDynamicGroup(groupName);
            int queuedPlayers = parseQueueSize(queueStats.get(groupName));

            int runningServers = 0;
            int onlineServers = 0;
            int startingServers = 0;
            int totalPlayers = 0;
            for (ServerInstance server : master.getRunningServers().values()) {
                if (!groupName.equalsIgnoreCase(String.valueOf(server.groupName))) {
                    continue;
                }
                runningServers++;
                totalPlayers += Math.max(0, server.playerCount);
                String status = String.valueOf(server.status == null ? "" : server.status);
                if ("ONLINE".equalsIgnoreCase(status)) {
                    onlineServers++;
                }
                if ("STARTING".equalsIgnoreCase(status)) {
                    startingServers++;
                }
            }

            List<String> startableWrappers = new ArrayList<>();
            String bestWrapperId = null;
            int bestAvailableMemory = -1;
            int smallestShortfall = Integer.MAX_VALUE;
            for (var wrapper : master.getConnectedWrappers().values()) {
                boolean healthy = wrapper.isHealthy();
                boolean draining = master.getLoadBalancerManager().isWrapperDraining(wrapper.getWrapperId());
                int availableMemory = Math.max(0, wrapper.getAvailableMemory());
                boolean cpuOk = wrapper.getCpuUsage() < cpuCritical;
                if (healthy && !draining) {
                    smallestShortfall = Math.min(smallestShortfall, Math.max(0, requiredRam - availableMemory));
                }
                if (healthy && !draining && cpuOk && availableMemory >= requiredRam) {
                    startableWrappers.add(wrapper.getWrapperId());
                    if (availableMemory > bestAvailableMemory) {
                        bestAvailableMemory = availableMemory;
                        bestWrapperId = wrapper.getWrapperId();
                    }
                }
            }
            int shortfallMb = startableWrappers.isEmpty()
                    ? (smallestShortfall == Integer.MAX_VALUE ? requiredRam : smallestShortfall)
                    : 0;

            int demandFromQueue = (int) Math.ceil((double) queuedPlayers / Math.max(1, maxPlayers));
            int recommendedServers = Math.max(minServers, runningServers + demandFromQueue);
            if (queuedPlayers > 0 && runningServers == 0) {
                recommendedServers = Math.max(recommendedServers, 1);
            }
            recommendedServers = Math.min(maxServers, recommendedServers);
            boolean canStartNow = !maintenance && runningServers < maxServers && !startableWrappers.isEmpty();

            String recommendation;
            if (maintenance) {
                recommendation = "Maintenance is enabled. Keep capacity stable unless staff bypass is intended.";
            } else if (runningServers >= maxServers) {
                recommendation = "Group already reached MaxServers. Increase MaxServers before scaling further.";
            } else if (startableWrappers.isEmpty()) {
                recommendation = shortfallMb > 0
                        ? "No wrapper has enough free RAM. Need at least " + shortfallMb + "MB more free RAM on one wrapper."
                        : "No healthy non-draining wrapper is available for scheduling.";
            } else if (recommendedServers > runningServers) {
                recommendation = "Scale up recommended: queue/demand suggests " + recommendedServers + " server(s).";
            } else {
                recommendation = "Capacity looks balanced. No immediate scale-up required.";
            }

            if (!canStartNow && queuedPlayers > 0) {
                recommendations.add(groupName + ": queued players exist, but planner cannot start more capacity now.");
            } else if (recommendedServers > runningServers) {
                recommendations.add(groupName + ": start " + (recommendedServers - runningServers)
                        + " more server(s), best wrapper: " + (bestWrapperId == null ? "none" : bestWrapperId) + ".");
            }

            Map<String, Object> groupInfo = new LinkedHashMap<>();
            groupInfo.put("groupName", groupName);
            groupInfo.put("ramMb", requiredRam);
            groupInfo.put("maxPlayers", maxPlayers);
            groupInfo.put("minServers", minServers);
            groupInfo.put("maxServers", maxServers);
            groupInfo.put("runningServers", runningServers);
            groupInfo.put("onlineServers", onlineServers);
            groupInfo.put("startingServers", startingServers);
            groupInfo.put("players", totalPlayers);
            groupInfo.put("queuedPlayers", queuedPlayers);
            groupInfo.put("maintenance", maintenance);
            groupInfo.put("dynamic", dynamic);
            groupInfo.put("tags", master.getConfigManager().getGroupTags(groupName));
            groupInfo.put("canStartNow", canStartNow);
            groupInfo.put("startableWrappers", startableWrappers);
            groupInfo.put("bestWrapperId", bestWrapperId);
            groupInfo.put("capacityShortfallMb", shortfallMb);
            groupInfo.put("recommendedServers", recommendedServers);
            groupInfo.put("recommendation", recommendation);
            groups.add(groupInfo);
        }

        if (master.getConnectedWrappers().isEmpty()) {
            recommendations.add("No wrapper connected. Start or reconnect a wrapper before planning capacity.");
        }
        if (drainingWrappers > 0) {
            recommendations.add(drainingWrappers + " wrapper(s) are draining and excluded from new scheduling.");
        }
        if (recommendations.isEmpty()) {
            recommendations.add("Planner found no immediate capacity action.");
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("groups", groups.size());
        summary.put("healthyWrappers", healthyWrappers);
        summary.put("drainingWrappers", drainingWrappers);
        summary.put("totalWrapperMemoryMb", totalWrapperMemory);
        summary.put("availableWrapperMemoryMb", availableWrapperMemory);
        summary.put("queueTotal", master.getPlayerQueueManager().getTotalQueued());
        summary.put("startableGroups", groups.stream().filter(group -> Boolean.TRUE.equals(group.get("canStartNow"))).count());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("generatedAt", now);
        payload.put("summary", summary);
        payload.put("wrappers", wrappers);
        payload.put("groups", groups);
        payload.put("recommendations", recommendations);
        return payload;
    }

    private int safeInteger(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private int parseQueueSize(Object value) {
        if (value instanceof Number) {
            return Math.max(0, ((Number) value).intValue());
        }
        if (value == null) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(String.valueOf(value)));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private Map<String, Object> buildSystemDiagnostics() {
        Master master = Master.getInstance();
        long now = System.currentTimeMillis();
        List<Map<String, Object>> issues = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();

        int runningServers = master.getRunningServers().size();
        int onlineServers = 0;
        int startingServers = 0;
        int staleServers = 0;
        int lowTpsServers = 0;
        int proxyServers = 0;

        for (ServerInstance server : master.getRunningServers().values()) {
            String status = String.valueOf(server.status == null ? "" : server.status);
            if ("ONLINE".equalsIgnoreCase(status)) {
                onlineServers++;
            }
            if ("STARTING".equalsIgnoreCase(status)) {
                startingServers++;
                if (now - server.startTime > 120_000L) {
                    addDiagnosticIssue(issues, "WARNING", "server:" + server.serverName,
                            "Server has been STARTING for more than 120 seconds.",
                            "Check the screen tail and restart the server if startup is stuck.");
                }
            }
            if (now - server.lastUpdate > 60_000L) {
                staleServers++;
                addDiagnosticIssue(issues, "WARNING", "server:" + server.serverName,
                        "No fresh metrics for more than 60 seconds.",
                        "Verify wrapper connectivity and server heartbeat forwarding.");
            }
            if (!"Proxy".equalsIgnoreCase(server.groupName) && server.tps > 0 && server.tps < 18.0) {
                lowTpsServers++;
                addDiagnosticIssue(issues, "WARNING", "server:" + server.serverName,
                        "TPS is below 18.0.",
                        "Inspect plugins, world load, CPU pressure and recent console logs.");
            }
            if ("Proxy".equalsIgnoreCase(server.groupName) && "ONLINE".equalsIgnoreCase(status)) {
                proxyServers++;
            }
        }

        int wrapperCount = master.getConnectedWrappers().size();
        int unhealthyWrappers = 0;
        int drainingWrappers = 0;
        int totalWrapperMemory = 0;
        int availableWrapperMemory = 0;
        double maxWrapperCpu = 0.0;

        for (var wrapper : master.getConnectedWrappers().values()) {
            totalWrapperMemory += Math.max(0, wrapper.getMaxMemory());
            availableWrapperMemory += Math.max(0, wrapper.getAvailableMemory());
            maxWrapperCpu = Math.max(maxWrapperCpu, wrapper.getCpuUsage());
            if (!wrapper.isHealthy()) {
                unhealthyWrappers++;
                addDiagnosticIssue(issues, "CRITICAL", "wrapper:" + wrapper.getWrapperId(),
                        "Wrapper heartbeat is stale.",
                        "Check wrapper process, network route and firewall between master and wrapper.");
            }
            if (master.getLoadBalancerManager().isWrapperDraining(wrapper.getWrapperId())) {
                drainingWrappers++;
            }
        }

        if (wrapperCount == 0) {
            addDiagnosticIssue(issues, "CRITICAL", "wrappers",
                    "No wrapper is connected.",
                    "Start at least one wrapper before scheduling servers.");
        }
        if (proxyServers == 0 && runningServers > 0) {
            addDiagnosticIssue(issues, "CRITICAL", "proxy",
                    "No online proxy server detected.",
                    "Start or restart the Proxy group so players can reach backend servers.");
        }
        if (drainingWrappers >= wrapperCount && wrapperCount > 0) {
            addDiagnosticIssue(issues, "WARNING", "loadbalancer",
                    "All connected wrappers are in drain mode.",
                    "Disable drain on at least one wrapper before starting new servers.");
        }
        if (master.getPlayerQueueManager().getTotalQueued() > 0 && onlineServers == 0) {
            addDiagnosticIssue(issues, "WARNING", "queue",
                    "Players are queued but no online game servers are available.",
                    "Start capacity for the queued group or review scaling policies.");
        }
        if (!master.getRestartInProgress().isEmpty()) {
            recommendations.add("Restart locks active: " + master.getRestartInProgress().size()
                    + ". Use restartstatus if a restart appears stuck.");
        }

        double memoryUsedPct = totalWrapperMemory <= 0
                ? 0.0
                : ((totalWrapperMemory - availableWrapperMemory) * 100.0) / totalWrapperMemory;
        if (memoryUsedPct >= 90.0) {
            addDiagnosticIssue(issues, "WARNING", "capacity",
                    "Wrapper memory usage is above 90%.",
                    "Scale out to another wrapper or lower group RAM limits.");
        }
        if (maxWrapperCpu >= 95.0) {
            addDiagnosticIssue(issues, "WARNING", "capacity",
                    "At least one wrapper reports CPU above 95%.",
                    "Move load away from the wrapper or reduce auto-start capacity.");
        }

        long critical = issues.stream().filter(i -> "CRITICAL".equals(i.get("severity"))).count();
        long warnings = issues.stream().filter(i -> "WARNING".equals(i.get("severity"))).count();
        int score = Math.max(0, 100 - (int) (critical * 30) - (int) (warnings * 10));
        String state = critical > 0 ? "CRITICAL" : warnings > 0 ? "WARNING" : "OK";

        if (issues.isEmpty()) {
            recommendations.add("System looks healthy. Keep monitoring alerts and setup reports green.");
        } else {
            recommendations.add("Resolve critical issues first, then review warnings by component.");
        }
        if (startingServers > 0) {
            recommendations.add("Watch STARTING servers until they switch to ONLINE or hit timeout handling.");
        }

        Map<String, Object> capacity = new LinkedHashMap<>();
        capacity.put("totalWrapperMemoryMb", totalWrapperMemory);
        capacity.put("availableWrapperMemoryMb", availableWrapperMemory);
        capacity.put("memoryUsedPercent", Math.round(memoryUsedPct * 10.0) / 10.0);
        capacity.put("maxWrapperCpu", Math.round(maxWrapperCpu * 10.0) / 10.0);
        capacity.put("drainingWrappers", drainingWrappers);
        capacity.put("unhealthyWrappers", unhealthyWrappers);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("runningServers", runningServers);
        summary.put("onlineServers", onlineServers);
        summary.put("startingServers", startingServers);
        summary.put("staleServers", staleServers);
        summary.put("lowTpsServers", lowTpsServers);
        summary.put("connectedWrappers", wrapperCount);
        summary.put("queueTotal", master.getPlayerQueueManager().getTotalQueued());
        summary.put("activeAlerts", master.getMonitoringService().getActiveAlerts().size());
        summary.put("restartLocks", master.getRestartInProgress().size());

        Map<String, Object> diagnostics = new LinkedHashMap<>();
        diagnostics.put("generatedAt", now);
        diagnostics.put("state", state);
        diagnostics.put("score", score);
        diagnostics.put("summary", summary);
        diagnostics.put("capacity", capacity);
        diagnostics.put("issues", issues);
        diagnostics.put("recommendations", recommendations);
        return diagnostics;
    }

    private void addDiagnosticIssue(List<Map<String, Object>> issues, String severity, String component,
                                    String message, String recommendation) {
        Map<String, Object> issue = new LinkedHashMap<>();
        issue.put("severity", severity);
        issue.put("component", component);
        issue.put("message", message);
        issue.put("recommendation", recommendation);
        issues.add(issue);
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
        int sessionPlayers = getTrackedSessionPlayerCount(master);
        int backendPlayers = getBackendOnlinePlayerCount(master);

        master.getRunningServers().values().forEach(server -> {
            Map<String, Object> serverInfo = new HashMap<>();
            int effectivePlayers = getEffectivePlayerCount(master, server, sessionPlayers, backendPlayers);
            serverInfo.put("serverName", server.serverName);
            serverInfo.put("groupName", server.groupName);
            serverInfo.put("status", server.status);
            serverInfo.put("wrapperId", server.wrapperId);  // FIX: war getWrapperId()
            serverInfo.put("playerCount", effectivePlayers);
            serverInfo.put("maxPlayers", server.maxPlayers);
            serverInfo.put("tps", server.tps);
            serverInfo.put("ram", server.allocatedRam);
            serverInfo.put("cpuUsage", server.cpuUsage);
            serverInfo.put("networkInBytes", server.networkInBytes);
            serverInfo.put("networkOutBytes", server.networkOutBytes);
            serverInfo.put("networkMode", server.networkMode);
            serverInfo.put("diskReadBytes", server.diskReadBytes);
            serverInfo.put("diskWriteBytes", server.diskWriteBytes);  // FIX: war server.ram
            serverInfo.put("port", server.port);  // NEU: Port hinzugefÃ¼gt
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

    private void handleWrapperDrain(HttpExchange exchange) throws IOException {
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
            String wrapperId = request.get("wrapperId") == null ? null : request.get("wrapperId").toString();
            boolean draining = request.get("draining") != null && Boolean.parseBoolean(request.get("draining").toString());
            if (wrapperId == null || wrapperId.isBlank()) {
                sendResponse(exchange, 400, Map.of("error", "wrapperId required"));
                return;
            }

            Master master = Master.getInstance();
            boolean wrapperExists = master.getConnectedWrappers().values().stream()
                    .anyMatch(w -> wrapperId.equalsIgnoreCase(w.getWrapperId()));
            if (!wrapperExists) {
                sendResponse(exchange, 404, Map.of("error", "wrapper not found", "wrapperId", wrapperId));
                return;
            }

            master.getLoadBalancerManager().setWrapperDraining(wrapperId, draining);
            CentralLogger.audit("api", "wrapper_drain", wrapperId + " draining=" + draining);
            sendResponse(exchange, 200, Map.of("wrapperId", wrapperId, "draining", draining));
        } catch (Exception e) {
            sendResponse(exchange, 500, Map.of("error", e.getMessage()));
        }
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
        int sessionPlayers = getTrackedSessionPlayerCount(master);
        int backendPlayers = getBackendOnlinePlayerCount(master);

        for (var entry : master.getRunningServers().entrySet()) {
            String server = entry.getKey();
            var value = entry.getValue();
            int effectivePlayers = getEffectivePlayerCount(master, value, sessionPlayers, backendPlayers);
            sb.append("cloud_server_players{server=\"").append(server).append("\"} ").append(effectivePlayers).append('\n');
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

    private void handleAlertsClear(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        int cleared = Master.getInstance().getMonitoringService().clearAllAlerts();
        CentralLogger.audit("api", "alerts_clear", "cleared=" + cleared);
        sendResponse(exchange, 200, Map.of("cleared", cleared));
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

    private void handleConsoleScreens(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }

        List<String> screens = new ArrayList<>(ConsoleScreenManager.getScreenNames());
        screens.sort(String::compareToIgnoreCase);
        sendResponse(exchange, 200, Map.of("screens", screens, "count", screens.size()));
    }

    private void handleConsoleTail(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }

        Map<String, String> query = parseQueryParams(exchange.getRequestURI().getQuery());
        String serverName = query.get("serverName");
        if (serverName == null || serverName.isBlank()) {
            sendResponse(exchange, 400, Map.of("error", "serverName query required"));
            return;
        }
        int limit = 120;
        if (query.containsKey("limit")) {
            try {
                limit = Math.max(10, Math.min(1000, Integer.parseInt(query.get("limit"))));
            } catch (Exception ignored) {
                limit = 120;
            }
        }
        List<String> lines = ConsoleScreenManager.getScreenMessages(serverName, limit);
        sendResponse(exchange, 200, Map.of(
                "serverName", serverName,
                "limit", limit,
                "lines", lines,
                "count", lines.size()
        ));
    }

    private void handleConsoleSend(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }

        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, Object> request = gson.fromJson(body, Map.class);
        String serverName = request != null && request.get("serverName") != null ? request.get("serverName").toString() : null;
        String command = request != null && request.get("command") != null ? request.get("command").toString() : null;
        if (serverName == null || serverName.isBlank() || command == null || command.isBlank()) {
            sendResponse(exchange, 400, Map.of("error", "serverName and command required"));
            return;
        }
        boolean sent = ConsoleScreenManager.sendCommandToServer(serverName, command);
        if (!sent) {
            sendResponse(exchange, 404, Map.of("error", "server not available or not running", "serverName", serverName));
            return;
        }
        sendResponse(exchange, 200, Map.of("message", "command sent", "serverName", serverName, "command", command));
    }

    private void handleSetupReport(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        Map<String, Object> report = SetupValidator.buildReport(Master.getInstance().getConfigManager(), false);
        sendResponse(exchange, 200, report);
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
        return snapshot;
    }

    private void handleDashboard(HttpExchange exchange) throws IOException {
        String html = generateDashboardHTML();
        byte[] payload = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html");
        exchange.getResponseHeaders().set("Cache-Control", "public, max-age=300");
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
        String resolved = resolvePresentedApiKey(authHeader);
        if (resolved != null) {
            return resolved;
        }
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        resolved = resolvePresentedApiKey(authorization);
        if (resolved != null) {
            return resolved;
        }
        return null;
    }

    private boolean isKnownApiKey(String key) {
        String sanitized = sanitizeApiKey(key);
        return sanitized != null && apiKeyRoles.containsKey(sanitized);
    }

    private boolean isValidLiveToken(String token) {
        String sanitized = sanitizeApiKey(token);
        if (sanitized == null || sanitized.isBlank()) {
            return false;
        }
        Long expiresAt = liveWsTickets.get(sanitized);
        long now = System.currentTimeMillis();
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt < now) {
            liveWsTickets.remove(sanitized);
            return false;
        }
        return true;
    }

    private String issueWsTicket() {
        long now = System.currentTimeMillis();
        liveWsTickets.entrySet().removeIf(entry -> entry.getValue() < now);
        String ticket = UUID.randomUUID().toString().replace("-", "");
        liveWsTickets.put(ticket, now + WS_TICKET_TTL_MS);
        return ticket;
    }

    private String resolvePresentedApiKey(String raw) {
        String key = sanitizeApiKey(raw);
        if (key == null || key.isBlank()) {
            return null;
        }
        if (key.regionMatches(true, 0, "Bearer ", 0, 7)) {
            key = sanitizeApiKey(key.substring(7));
        }
        if (key == null || key.isBlank()) {
            return null;
        }
        if ("admin".equalsIgnoreCase(key)) {
            return apiKeys.get("admin");
        }
        if ("dashboard".equalsIgnoreCase(key) || "viewer".equalsIgnoreCase(key)) {
            return apiKeys.get("dashboard");
        }
        return key;
    }

    private String sanitizeApiKey(String key) {
        if (key == null) {
            return null;
        }
        String sanitized = key.trim();
        if ((sanitized.startsWith("\"") && sanitized.endsWith("\""))
                || (sanitized.startsWith("'") && sanitized.endsWith("'"))) {
            sanitized = sanitized.substring(1, sanitized.length() - 1).trim();
        }
        return sanitized;
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
        String ip = getClientIp(exchange);
        long now = System.currentTimeMillis();
        cleanupRateLimitState(now);

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

    private void cleanupRateLimitState(long now) {
        if (now - lastRateLimitCleanupAt < RATE_LIMIT_CLEANUP_INTERVAL_MS) {
            return;
        }
        lastRateLimitCleanupAt = now;

        blockedIps.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue() <= now);
        requestTimestampsByIp.entrySet().removeIf(entry -> {
            Deque<Long> timestamps = entry.getValue();
            if (timestamps == null) {
                return true;
            }
            while (!timestamps.isEmpty()) {
                Long first = timestamps.peekFirst();
                if (first == null || now - first > 60_000) {
                    timestamps.pollFirst();
                } else {
                    break;
                }
            }
            return timestamps.isEmpty();
        });
    }

    private boolean isRateLimitedNow(HttpExchange exchange) {
        String ip = getClientIp(exchange);
        long now = System.currentTimeMillis();
        Long blockedUntil = blockedIps.get(ip);
        if (blockedUntil != null && blockedUntil > now) {
            return true;
        }
        Deque<Long> timestamps = requestTimestampsByIp.get(ip);
        if (timestamps == null || timestamps.isEmpty()) {
            return false;
        }
        int recent = 0;
        for (Long ts : timestamps) {
            if (ts != null && now - ts <= 60_000) {
                recent++;
            }
        }
        return recent > RATE_LIMIT_PER_MINUTE;
    }

    private String getClientIp(HttpExchange exchange) {
        if (exchange == null || exchange.getRemoteAddress() == null || exchange.getRemoteAddress().getAddress() == null) {
            return "unknown";
        }
        return exchange.getRemoteAddress().getAddress().getHostAddress();
    }

    private boolean isLocalRequest(HttpExchange exchange) {
        String ip = getClientIp(exchange);
        return "127.0.0.1".equals(ip)
                || "::1".equals(ip)
                || "0:0:0:0:0:0:0:1".equals(ip)
                || "localhost".equalsIgnoreCase(ip);
    }

    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isBlank()) {
            return params;
        }
        for (String pair : query.split("&")) {
            if (pair == null || pair.isBlank()) {
                continue;
            }
            String[] parts = pair.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
            params.put(key, value);
        }
        return params;
    }

    private boolean isMutatingPath(String method, String path) {
        if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method)) {
            return true;
        }
        if (path == null) {
            return false;
        }
        String normalized = path.toLowerCase(Locale.ROOT);
        return normalized.contains("/start") || normalized.contains("/stop") || normalized.contains("/restart")
                || normalized.contains("/create") || normalized.contains("/delete") || normalized.contains("/update")
                || normalized.contains("/assign") || normalized.contains("/temp") || normalized.contains("/set")
                || normalized.contains("/switch") || normalized.contains("/rollback") || normalized.contains("/trigger");
    }

    private void sendResponse(HttpExchange exchange, int statusCode, Object data) throws IOException {
        String json = gson.toJson(data);
        byte[] payload = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        applyCorsHeaders(exchange);
        exchange.sendResponseHeaders(statusCode, payload.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(payload);
        }
    }

    private void applyCorsHeaders(HttpExchange exchange) {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        String allowOrigin = "*";
        if (!corsAllowedOrigins.contains("*")) {
            if (origin != null && corsAllowedOrigins.contains(origin)) {
                allowOrigin = origin;
            } else if (!corsAllowedOrigins.isEmpty()) {
                allowOrigin = corsAllowedOrigins.iterator().next();
            }
            exchange.getResponseHeaders().set("Vary", "Origin");
        }
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", allowOrigin);
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, X-API-Key, Authorization");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
    }

    private Set<String> resolveAllowedOrigins(Master master) {
        if (master == null || master.getConfigManager() == null) {
            return Set.of("*");
        }
        String configured = master.getConfigManager().getApiAllowedOrigins();
        if (configured == null || configured.isBlank()) {
            return Set.of("*");
        }
        if ("*".equals(configured.trim())) {
            return Set.of("*");
        }
        Set<String> values = new LinkedHashSet<>();
        for (String part : configured.split(",")) {
            String entry = part == null ? "" : part.trim();
            if (!entry.isEmpty()) {
                values.add(entry);
            }
        }
        return values.isEmpty() ? Set.of("*") : values;
    }

    private String loadDashboardHtmlResource() {
        try (InputStream in = getClass().getResourceAsStream("/dashboard/index.html")) {
            if (in == null) {
                return null;
            }
            byte[] bytes = in.readAllBytes();
            if (bytes.length == 0) {
                return null;
            }
            String html = new String(bytes, StandardCharsets.UTF_8);
            // Remove UTF-8 BOM if present.
            if (!html.isEmpty() && html.charAt(0) == '\uFEFF') {
                html = html.substring(1);
            }
            return html;
        } catch (Exception e) {
            CentralLogger.warn("Dashboard", "Konnte dashboard/index.html nicht laden, nutze Fallback");
            return null;
        }
    }

    private String generateDashboardHTML() {
        String cached = cachedDashboardHtml;
        if (cached != null && !cached.isBlank()) {
            return cached;
        }

        synchronized (this) {
            if (cachedDashboardHtml != null && !cachedDashboardHtml.isBlank()) {
                return cachedDashboardHtml;
            }
            String resourceHtml = loadDashboardHtmlResource();
            if (resourceHtml != null && !resourceHtml.isBlank()) {
                cachedDashboardHtml = resourceHtml;
                return resourceHtml;
            }
        }

        CentralLogger.warn("Dashboard", "Dashboard-Resource fehlt, nutze sicheren Minimal-Fallback");
        String minimalFallback = """
        <!DOCTYPE html>
        <html lang='en'>
        <head>
            <meta charset='UTF-8'>
            <meta name='viewport' content='width=device-width, initial-scale=1.0'>
            <title>KalliCloud Dashboard</title>
        </head>
        <body style='font-family: sans-serif; background: #0b1220; color: #dbeafe; padding: 24px;'>
            <h1>KalliCloud Dashboard</h1>
            <p>Dashboard resource not found. Please restore <code>src/main/resources/dashboard/index.html</code>.</p>
        </body>
        </html>
        """;
        if (System.nanoTime() >= 0) {
            return minimalFallback;
        }
        return """
        <!DOCTYPE html>
        <html lang='en'>
        <head>
            <meta charset='UTF-8'>
            <meta name='viewport' content='width=device-width, initial-scale=1.0'>
            <title>KalliCloud Dashboard</title>
            <link rel='icon' type='image/svg+xml' href="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 64 64'%3E%3Crect width='64' height='64' rx='14' fill='%230f172a'/%3E%3Cpath d='M14 40l10-12 8 9 10-14 8 17' stroke='%2322d3ee' stroke-width='5' fill='none' stroke-linecap='round' stroke-linejoin='round'/%3E%3C/svg%3E">
            <script src='https://cdn.tailwindcss.com'></script>
            <link rel='stylesheet' href='https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.2/css/all.min.css'>
            <script src='https://cdn.jsdelivr.net/npm/sweetalert2@11'></script>
            <style>
                body {
                    background-image:
                        radial-gradient(circle at 15% 0%, rgba(34, 211, 238, 0.12), transparent 34%),
                        radial-gradient(circle at 85% 100%, rgba(59, 130, 246, 0.11), transparent 30%);
                }
                .theme-light body {
                    background: linear-gradient(180deg, #f8fbff 0%, #edf4ff 42%, #f7fbff 100%) !important;
                    color: #0f172a !important;
                }
                .theme-light .bg-slate-950 { background-color: #eef4ff !important; }
                .theme-light .bg-slate-950\\/90 { background-color: rgba(238, 244, 255, 0.93) !important; }
                .theme-light .bg-slate-900\\/60, .theme-light .bg-slate-900\\/40, .theme-light .bg-slate-900 { background-color: #ffffff !important; }
                .theme-light .bg-slate-950\\/70, .theme-light .bg-slate-950\\/60, .theme-light .bg-slate-950\\/40 { background-color: #f5f9ff !important; }
                .theme-light .text-slate-100, .theme-light .text-slate-300, .theme-light .text-slate-400 { color: #1e293b !important; }
                .theme-light .border-slate-800, .theme-light .border-slate-700, .theme-light .border-slate-800\\/80 { border-color: #c9d8ef !important; }
                .theme-light .from-slate-900, .theme-light .via-cyan-950, .theme-light .to-slate-900 {
                    background-image: linear-gradient(110deg, #ffffff, #ebf6ff 55%, #f6fbff) !important;
                }
                .hero-icon {
                    width: 0.95rem;
                    height: 0.95rem;
                    stroke: currentColor;
                    stroke-width: 1.8;
                    fill: none;
                }
                .nav-link {
                    display: inline-flex;
                    align-items: center;
                    gap: 0.45rem;
                    border-radius: 999px;
                    border: 1px solid rgb(51 65 85 / 0.9);
                    padding: 0.44rem 0.82rem;
                    font-size: 0.72rem;
                    letter-spacing: .02em;
                    color: rgb(203 213 225);
                    transition: all .16s ease;
                }
                .nav-link:hover {
                    border-color: rgb(34 211 238 / 0.9);
                    color: white;
                    background: linear-gradient(135deg, rgb(8 47 73 / 0.55), rgb(30 41 59 / 0.7));
                }
                .nav-link-active {
                    border-color: rgb(34 211 238 / 0.95) !important;
                    color: rgb(8 47 73) !important;
                    background: linear-gradient(135deg, rgb(165 243 252), rgb(186 230 253)) !important;
                    font-weight: 700;
                }
                .theme-light .nav-link {
                    color: #1e293b;
                    border-color: #c9d8ef;
                    background: #ffffff;
                }
                .theme-light .nav-link:hover {
                    border-color: #0ea5e9;
                    color: #0f172a;
                    background: linear-gradient(135deg, #eef7ff, #ffffff);
                }
                .theme-light .nav-link-active {
                    border-color: #0ea5e9 !important;
                    color: #0f172a !important;
                    background: linear-gradient(135deg, #d9f4ff, #e4eeff) !important;
                }
                .mobile-nav-panel {
                    border-top: 1px solid rgb(30 41 59 / 0.85);
                    background: linear-gradient(180deg, rgb(2 6 23 / 0.95), rgb(15 23 42 / 0.96));
                }
                .theme-light .mobile-nav-panel {
                    border-top: 1px solid #c9d8ef;
                    background: linear-gradient(180deg, #f8fbff, #eef5ff);
                }
                .mobile-nav-link {
                    width: 100%;
                    justify-content: flex-start;
                    padding: 0.62rem 0.78rem;
                    font-size: 0.8rem;
                }
                @media (max-width: 1024px) {
                    .nav-link {
                        font-size: 0.69rem;
                        padding: 0.38rem 0.72rem;
                    }
                }
                @media (max-width: 768px) {
                    body {
                        background-image:
                            radial-gradient(circle at 5% 0%, rgba(14, 165, 233, 0.14), transparent 40%),
                            radial-gradient(circle at 95% 100%, rgba(59, 130, 246, 0.14), transparent 40%);
                    }
                    #mobileNav .nav-link {
                        justify-content: flex-start;
                    }
                    #overviewSection {
                        grid-template-columns: repeat(2, minmax(0, 1fr));
                    }
                }
                @media (max-width: 520px) {
                    #overviewSection {
                        grid-template-columns: repeat(1, minmax(0, 1fr));
                    }
                    header .w-full {
                        flex-direction: column;
                    }
                    #apiKey {
                        width: 100%;
                    }
                    #connectBtn {
                        width: 100%;
                    }
                    .mobile-nav-link {
                        font-size: 0.78rem;
                    }
                }
            </style>
        </head>
        <body class='min-h-screen bg-slate-950 text-slate-100'>
            <nav class='sticky top-0 z-40 border-b border-slate-800/80 bg-slate-950/90 backdrop-blur'>
                <div class='mx-auto flex max-w-7xl items-center justify-between px-4 py-3 md:px-6'>
                    <div class='flex items-center gap-3'>
                        <div class='h-8 w-8 rounded-lg bg-gradient-to-br from-cyan-300 to-blue-500 p-[2px]'>
                            <div class='flex h-full w-full items-center justify-center rounded-md bg-slate-900 text-xs font-bold text-cyan-300'>
                                <svg class='hero-icon text-cyan-300' viewBox='0 0 24 24' aria-hidden='true'>
                                    <path stroke-linecap='round' stroke-linejoin='round' d='M3.75 16.5l4.5-7.5 4.5 4.5 4.5-7.5 3 9'/>
                                </svg>
                            </div>
                        </div>
                        <div>
                            <div class='text-sm font-semibold tracking-wide'>KalliCloud</div>
                            <div class='text-[10px] text-slate-400'>Operations Dashboard</div>
                        </div>
                    </div>
                    <div class='flex items-center gap-2'>
                        <button id='themeToggle' class='inline-flex items-center gap-1.5 rounded-md border border-slate-700 px-2.5 py-1 text-xs hover:border-cyan-400'>
                            <i class='fa-solid fa-circle-half-stroke text-[11px]'></i><span>Theme</span>
                        </button>
                        <button id='navToggle' aria-expanded='false' class='inline-flex items-center gap-1.5 rounded-md border border-slate-700 px-2.5 py-1 text-xs hover:border-cyan-400 md:hidden'>
                            <svg class='hero-icon' viewBox='0 0 24 24' aria-hidden='true'>
                                <path stroke-linecap='round' stroke-linejoin='round' d='M3.75 6.75h16.5m-16.5 5.25h16.5m-16.5 5.25h16.5'/>
                            </svg>
                            <span>Menu</span>
                        </button>
                    </div>
                    <div class='hidden items-center gap-2 md:flex'>
                        <a href='/dashboard/overview' data-page-link='overview' class='nav-link'><i class='fa-solid fa-gauge-high text-[11px]'></i><span>Overview</span></a>
                        <a href='/dashboard/servers' data-page-link='servers' class='nav-link'><i class='fa-solid fa-server text-[11px]'></i><span>Servers</span></a>
                        <a href='/dashboard/monitoring' data-page-link='monitoring' class='nav-link'><i class='fa-solid fa-heart-pulse text-[11px]'></i><span>Monitoring</span></a>
                        <a href='/dashboard/operations' data-page-link='operations' class='nav-link'><i class='fa-solid fa-gears text-[11px]'></i><span>Operations</span></a>
                        <a href='/dashboard/setup' data-page-link='setup' class='nav-link'><i class='fa-solid fa-screwdriver-wrench text-[11px]'></i><span>Setup</span></a>
                    </div>
                </div>
                <div id='mobileNav' class='mobile-nav-panel mx-auto hidden max-w-7xl gap-2 px-4 pb-3 md:hidden'>
                    <div class='grid grid-cols-1 gap-2 py-2'>
                        <a href='/dashboard/overview' data-page-link='overview' class='nav-link mobile-nav-link'><i class='fa-solid fa-gauge-high text-[12px]'></i><span>Overview</span></a>
                        <a href='/dashboard/servers' data-page-link='servers' class='nav-link mobile-nav-link'><i class='fa-solid fa-server text-[12px]'></i><span>Servers</span></a>
                        <a href='/dashboard/monitoring' data-page-link='monitoring' class='nav-link mobile-nav-link'><i class='fa-solid fa-heart-pulse text-[12px]'></i><span>Monitoring</span></a>
                        <a href='/dashboard/operations' data-page-link='operations' class='nav-link mobile-nav-link'><i class='fa-solid fa-gears text-[12px]'></i><span>Operations</span></a>
                        <a href='/dashboard/setup' data-page-link='setup' class='nav-link mobile-nav-link'><i class='fa-solid fa-screwdriver-wrench text-[12px]'></i><span>Setup</span></a>
                    </div>
                </div>
            </nav>
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
                        <span id='roleState' class='rounded-full border border-slate-700 px-3 py-1 text-slate-300'>Role: -</span>
                        <span id='wsState' class='rounded-full border border-slate-700 px-3 py-1 text-slate-300'>WS: disconnected</span>
                        <span id='lastUpdate' class='rounded-full border border-slate-700 px-3 py-1 text-slate-300'>Last update: -</span>
                    </div>
                </header>

                <section id='overviewSection' data-page='overview' class='dashboard-page grid grid-cols-2 gap-3 md:grid-cols-4'>
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

                <section id='serversSection' data-page='servers' class='dashboard-page grid gap-6 lg:grid-cols-3'>
                    <div class='lg:col-span-2 rounded-2xl border border-slate-800 bg-slate-900/60 p-4'>
                        <div class='mb-3 flex flex-wrap items-center justify-between gap-2'>
                            <h2 class='font-semibold'>Servers</h2>
                            <div class='flex gap-2'>
                                <input id='serverFilter' type='text' placeholder='Filter server/group' class='rounded-lg border border-slate-700 bg-slate-950/70 px-2 py-1 text-xs outline-none focus:border-cyan-400'>
                                <button id='refreshBtn' class='rounded-lg border border-slate-700 px-3 py-1 text-xs hover:border-cyan-400'>Refresh</button>
                            </div>
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
                        <h2 class='mb-3 font-semibold'>Quick Actions</h2>
                        <div class='grid grid-cols-2 gap-2 text-xs'>
                            <button id='btnWebhookTest' class='rounded-lg border border-slate-700 px-2 py-2 hover:border-cyan-400 disabled:opacity-30'>Webhook test</button>
                            <button id='btnClearAlerts' class='rounded-lg border border-slate-700 px-2 py-2 hover:border-rose-400 disabled:opacity-30'>Clear alerts</button>
                            <button id='btnScaleEval' class='rounded-lg border border-slate-700 px-2 py-2 hover:border-violet-400 disabled:opacity-30'>Scale eval</button>
                            <button id='btnReloadView' class='rounded-lg border border-slate-700 px-2 py-2 hover:border-emerald-400'>Reload view</button>
                        </div>

                        <h2 class='mb-3 mt-6 font-semibold'>Start Server</h2>
                        <div class='space-y-2 text-xs'>
                            <input id='startServerName' type='text' placeholder='Server name (e.g. Lobby-2)' class='w-full rounded-lg border border-slate-700 bg-slate-950/70 px-2 py-2 outline-none focus:border-cyan-400'>
                            <input id='startGroupName' type='text' placeholder='Group name (e.g. Lobby)' class='w-full rounded-lg border border-slate-700 bg-slate-950/70 px-2 py-2 outline-none focus:border-cyan-400'>
                            <button id='btnStartServer' class='w-full rounded-lg border border-slate-700 px-2 py-2 hover:border-emerald-400 disabled:opacity-30'>Start server</button>
                        </div>

                        <h2 class='mb-3 font-semibold'>Queue by Group</h2>
                        <div id='queueBox' class='space-y-2 text-sm'></div>

                        <h2 class='mb-3 mt-6 font-semibold'>Wrappers</h2>
                        <div id='wrappersBox' class='space-y-2 text-sm'></div>
                    </div>
                </section>

                <section id='opsSection' data-page='operations' class='dashboard-page grid gap-6 lg:grid-cols-3'>
                    <div class='rounded-2xl border border-slate-800 bg-slate-900/60 p-4 lg:col-span-2'>
                        <div class='mb-3 flex items-center justify-between'>
                            <h2 class='font-semibold'>Cluster & Scaling</h2>
                            <span id='clusterState' class='rounded-full border border-slate-700 px-3 py-1 text-xs text-slate-300'>cluster: -</span>
                        </div>
                        <div class='grid gap-4 md:grid-cols-2'>
                            <div class='rounded-xl border border-slate-800 bg-slate-950/70 p-3 text-xs'>
                                <div class='font-semibold'>Cluster Info</div>
                                <div id='clusterBox' class='mt-2 space-y-1 text-slate-300'></div>
                            </div>
                            <div class='rounded-xl border border-slate-800 bg-slate-950/70 p-3 text-xs'>
                                <div class='font-semibold'>Scaling Policies</div>
                                <div id='policiesBox' class='mt-2 space-y-1 text-slate-300'></div>
                            </div>
                        </div>
                        <div class='mt-4 grid gap-2 md:grid-cols-3'>
                            <input id='configKey' type='text' placeholder='Config key (e.g. CloudMaster.Network.ProxyOnlineMode)' class='rounded-lg border border-slate-700 bg-slate-950/70 px-2 py-2 text-xs outline-none focus:border-cyan-400 md:col-span-2'>
                            <button id='btnConfigGet' class='rounded-lg border border-slate-700 px-2 py-2 text-xs hover:border-cyan-400 disabled:opacity-30'>Get Config</button>
                            <input id='configValue' type='text' placeholder='Config value' class='rounded-lg border border-slate-700 bg-slate-950/70 px-2 py-2 text-xs outline-none focus:border-cyan-400 md:col-span-2'>
                            <button id='btnConfigSet' class='rounded-lg border border-slate-700 px-2 py-2 text-xs hover:border-emerald-400 disabled:opacity-30'>Set Config</button>
                        </div>
                    </div>
                    <div class='rounded-2xl border border-slate-800 bg-slate-900/60 p-4 lg:col-span-2'>
                        <div class='mb-3 flex items-center justify-between'>
                            <h2 class='font-semibold'>Recent Logs</h2>
                            <input id='logFilter' type='text' placeholder='Filter logs' class='rounded-lg border border-slate-700 bg-slate-950/70 px-2 py-1 text-xs outline-none focus:border-cyan-400'>
                        </div>
                        <pre id='logsBox' class='h-72 overflow-auto rounded-xl border border-slate-800 bg-slate-950/70 p-3 text-xs text-slate-300'></pre>
                    </div>
                    <div class='rounded-2xl border border-slate-800 bg-slate-900/60 p-4'>
                        <h2 class='mb-3 font-semibold'>Active Alerts</h2>
                        <div id='alertsBox' class='space-y-2 text-xs'></div>
                    </div>
                </section>

                <section id='monitoringSection' data-page='monitoring' class='dashboard-page rounded-2xl border border-slate-800 bg-slate-900/60 p-4'>
                    <div class='mb-3 flex items-center justify-between'>
                        <h2 class='font-semibold'>Metrics Trend</h2>
                        <span class='rounded-full border border-slate-700 px-3 py-1 text-xs text-slate-300'>last 180 points</span>
                    </div>
                    <canvas id='metricsCanvas' class='h-44 w-full rounded-xl border border-slate-800 bg-slate-950/70'></canvas>
                </section>

                <section id='setupSection' data-page='setup' class='dashboard-page rounded-2xl border border-slate-800 bg-slate-900/60 p-4'>
                    <div class='mb-3 flex items-center justify-between'>
                        <h2 class='font-semibold'>Setup Health</h2>
                        <span id='setupSummary' class='rounded-full border border-slate-700 px-3 py-1 text-xs text-slate-300'>-</span>
                    </div>
                    <div id='setupBox' class='grid gap-3 md:grid-cols-2 xl:grid-cols-3'></div>
                </section>
            </div>

            <script>
                const state = {
                    apiKey: sessionStorage.getItem('cloud_api_key') || '',
                    role: null,
                    ws: null,
                    wsUrl: null,
                    wsTicket: null,
                    overview: null,
                    logs: { lines: [] },
                    metricsHistory: [],
                    refreshInFlight: false,
                    refreshQueued: false,
                    lastAutoRefreshAt: 0
                };
                const apiKeyInput = document.getElementById('apiKey');
                apiKeyInput.value = state.apiKey;
                initTheme();
                applyDashboardPage();

                function showToast(icon, title, text = '') {
                    Swal.fire({
                        toast: true,
                        position: 'top-end',
                        icon,
                        title,
                        text,
                        showConfirmButton: false,
                        timer: 2500,
                        timerProgressBar: true
                    });
                }

                function initTheme() {
                    const saved = localStorage.getItem('cloud_theme') || 'dark';
                    if (saved === 'light') {
                        document.documentElement.classList.add('theme-light');
                    } else {
                        document.documentElement.classList.remove('theme-light');
                    }
                }

                function toggleTheme() {
                    const isLight = document.documentElement.classList.toggle('theme-light');
                    localStorage.setItem('cloud_theme', isLight ? 'light' : 'dark');
                    showToast('success', 'Theme switched', isLight ? 'Light mode active' : 'Dark mode active');
                }

                function getCurrentPage() {
                    const path = (window.location.pathname || '/dashboard').replace(/\\/+$/, '');
                    const parts = path.split('/').filter(Boolean);
                    const last = parts.length ? parts[parts.length - 1].toLowerCase() : 'dashboard';
                    const known = new Set(['overview', 'servers', 'monitoring', 'operations', 'setup']);
                    if (known.has(last)) return last;
                    return 'overview';
                }

                function applyDashboardPage() {
                    const current = getCurrentPage();
                    document.querySelectorAll('.dashboard-page').forEach(section => {
                        const page = section.getAttribute('data-page');
                        if (page === current) {
                            section.classList.remove('hidden');
                        } else {
                            section.classList.add('hidden');
                        }
                    });
                    document.querySelectorAll('[data-page-link]').forEach(link => {
                        if (link.getAttribute('data-page-link') === current) {
                            link.classList.add('nav-link-active');
                        } else {
                            link.classList.remove('nav-link-active');
                        }
                    });
                    const mobileNav = document.getElementById('mobileNav');
                    const navToggle = document.getElementById('navToggle');
                    if (mobileNav && navToggle && window.innerWidth < 768) {
                        mobileNav.classList.add('hidden');
                        navToggle.setAttribute('aria-expanded', 'false');
                    }
                }

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
                    sessionStorage.setItem('cloud_api_key', state.apiKey);
                    const auth = await apiGet('/api/v1/auth/me');
                    state.role = auth.role;
                    state.wsUrl = auth.wsUrl;
                    state.wsTicket = auth.wsTicket || null;
                    document.getElementById('authState').textContent = 'Authenticated as ' + auth.role;
                    document.getElementById('authState').className = 'rounded-full border border-emerald-500/40 bg-emerald-500/10 px-3 py-1 text-emerald-200';
                    document.getElementById('roleState').textContent = 'Role: ' + auth.role;
                    applyRoleState();
                    openWebSocket();
                    await refresh();
                }

                async function refresh() {
                    if (state.refreshInFlight) {
                        state.refreshQueued = true;
                        return;
                    }
                    state.refreshInFlight = true;
                    try {
                        const [overview, alerts, logs, setup, clusterInfo, policies, metricHistory] = await Promise.all([
                            apiGet('/api/v1/dashboard/overview'),
                            apiGet('/api/v1/alerts'),
                            apiGet('/api/v1/logs/recent'),
                            apiGet('/api/v1/setup/report'),
                            apiGet('/api/v1/cluster/info'),
                            apiGet('/api/v1/scaling/policies'),
                            apiGet('/api/v1/metrics/history')
                        ]);
                        state.overview = overview;
                        state.logs = logs;
                        state.metricsHistory = metricHistory.history || [];
                        renderOverview(overview);
                        renderAlerts(alerts);
                        renderLogs(logs);
                        renderSetup(setup);
                        renderCluster(clusterInfo);
                        renderPolicies(policies);
                        renderMetricsTrend(state.metricsHistory);
                    } finally {
                        state.refreshInFlight = false;
                        if (state.refreshQueued) {
                            state.refreshQueued = false;
                            setTimeout(() => refresh().catch(() => {}), 250);
                        }
                    }
                }

                function scheduleAutoRefresh() {
                    const now = Date.now();
                    if (now - state.lastAutoRefreshAt < 8000) {
                        return;
                    }
                    state.lastAutoRefreshAt = now;
                    refresh().catch(() => {});
                }

                function renderOverview(data) {
                    document.getElementById('runningServers').textContent = data.runningServers || 0;
                    document.getElementById('connectedWrappers').textContent = data.connectedWrappers || 0;
                    document.getElementById('queueTotal').textContent = data.queueTotal || 0;
                    document.getElementById('activeAlerts').textContent = (data.monitoring && data.monitoring.activeAlerts) || 0;
                    document.getElementById('lastUpdate').textContent = 'Last update: ' + new Date().toLocaleTimeString();

                    const filterText = (document.getElementById('serverFilter').value || '').trim().toLowerCase();
                    const filteredServers = (data.servers || []).filter(server => {
                        if (!filterText) return true;
                        return (server.serverName || '').toLowerCase().includes(filterText)
                            || (server.groupName || '').toLowerCase().includes(filterText);
                    });

                    const serverRows = filteredServers.map(server => {
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
                                        <button ${disabled} data-action='start-like' data-group='${encodeURIComponent(server.groupName || '')}' class='rounded border border-slate-700 px-2 py-1 text-xs hover:border-emerald-400 disabled:opacity-30'>start like</button>
                                        <button ${disabled} data-action='restart-server' data-server='${encodeURIComponent(server.serverName || '')}' class='rounded border border-slate-700 px-2 py-1 text-xs hover:border-cyan-400 disabled:opacity-30'>restart</button>
                                        <button ${disabled} data-action='stop-server' data-server='${encodeURIComponent(server.serverName || '')}' class='rounded border border-slate-700 px-2 py-1 text-xs hover:border-rose-400 disabled:opacity-30'>stop</button>
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
                        const canMutate = state.role === 'ADMIN';
                        const drainLabel = wrapper.draining ? 'undrain' : 'drain';
                        const disabled = canMutate ? '' : 'disabled';
                        return `<div class='rounded-lg border border-slate-800 bg-slate-950/60 p-3'>
                            <div class='font-medium'>${wrapper.wrapperId}</div>
                            <div class='text-xs text-slate-400'>${wrapper.hostname}</div>
                            <div class='mt-1 text-xs'>CPU ${Number(wrapper.cpuUsage || 0).toFixed(1)}% | RAM ${usedPct}% | Servers ${wrapper.activeServers}${drain}</div>
                            <div class='mt-2'>
                                <button ${disabled} data-action='toggle-wrapper-drain' data-wrapper='${encodeURIComponent(wrapper.wrapperId || '')}' data-draining='${wrapper.draining ? 'false' : 'true'}' class='rounded border border-slate-700 px-2 py-1 text-xs hover:border-amber-400 disabled:opacity-30'>${drainLabel}</button>
                            </div>
                        </div>`;
                    }).join('');
                    document.getElementById('wrappersBox').innerHTML = wrappers || "<p class='text-slate-400'>No wrappers connected</p>";
                }

                function renderAlerts(data) {
                    const active = data.active || [];
                    const items = active.slice(0, 20).map(alert => {
                        const sev = (alert.severity || 'INFO').toUpperCase();
                        const cls = sev === 'CRITICAL'
                            ? 'border-rose-600/50 bg-rose-950/40 text-rose-200'
                            : (sev === 'WARNING'
                                ? 'border-amber-600/50 bg-amber-950/40 text-amber-200'
                                : 'border-slate-700 bg-slate-950/40 text-slate-300');
                        return `<div class='rounded-lg border ${cls} p-2'>
                            <div class='font-semibold'>${sev}</div>
                            <div class='mt-1 text-xs'>${alert.component || '-'}</div>
                            <div class='mt-1 text-xs'>${alert.message || '-'}</div>
                        </div>`;
                    }).join('');
                    document.getElementById('alertsBox').innerHTML = items || "<p class='text-slate-400'>No active alerts</p>";
                }

                function renderLogs(data) {
                    const lines = (data.lines || []);
                    const filter = (document.getElementById('logFilter').value || '').trim().toLowerCase();
                    const filtered = filter ? lines.filter(line => line.toLowerCase().includes(filter)) : lines;
                    document.getElementById('logsBox').textContent = filtered.slice(-250).join('\\n');
                }

                function renderSetup(report) {
                    const summary = report.summary || {};
                    document.getElementById('setupSummary').textContent =
                        `groups ${summary.groupsOk || 0}/${summary.groupsTotal || 0} ok | issues ${summary.issuesTotal || 0}`;
                    const groups = report.groups || [];
                    const cards = groups.map(group => {
                        const healthy = !!group.healthy;
                        const cls = healthy
                            ? 'border-emerald-700/40 bg-emerald-950/20'
                            : 'border-rose-700/40 bg-rose-950/20';
                        const issues = (group.issues || []).map(issue => `<li>${issue}</li>`).join('');
                        return `<div class='rounded-xl border ${cls} p-3 text-xs'>
                            <div class='font-semibold text-sm'>${group.groupName}</div>
                            <div class='mt-1 text-slate-300'>dynamic: ${group.dynamic} | proxy: ${group.proxyGroup}</div>
                            <div class='mt-1 text-slate-300'>jar: ${group.jarFound ? 'ok' : 'missing'}</div>
                            <div class='mt-1 text-slate-300 break-all'>template: ${group.templateExists ? 'ok' : 'missing'}</div>
                            ${healthy ? "<div class='mt-2 text-emerald-300'>Healthy</div>" : `<ul class='mt-2 list-disc pl-5 text-rose-200'>${issues}</ul>`}
                        </div>`;
                    }).join('');
                    document.getElementById('setupBox').innerHTML = cards || "<p class='text-slate-400'>No groups</p>";
                }

                function renderCluster(info) {
                    document.getElementById('clusterState').textContent = `cluster: ${info.clusterState || 'UNKNOWN'}`;
                    document.getElementById('clusterBox').innerHTML = `
                        <div>Master ID: <span class='text-slate-100'>${info.masterId || '-'}</span></div>
                        <div>Primary: <span class='text-slate-100'>${info.isPrimary ? 'yes' : 'no'}</span></div>
                        <div>Primary ID: <span class='text-slate-100'>${info.primaryMasterId || '-'}</span></div>
                        <div>Nodes: <span class='text-slate-100'>${info.clusterNodes || 0}</span></div>`;
                }

                function renderPolicies(policies) {
                    const entries = Object.entries(policies || {});
                    const html = entries.map(([group, p]) => `
                        <div class='rounded-lg border border-slate-800 bg-slate-900/40 p-2'>
                            <div class='font-semibold text-slate-100'>${group}</div>
                            <div>min/max: ${p.minServers}/${p.maxServers}</div>
                            <div>up/down: ${p.scaleUpThreshold}/${p.scaleDownThreshold}</div>
                            <div>predictive: ${p.predictiveScaling ? 'on' : 'off'}</div>
                        </div>`).join('');
                    document.getElementById('policiesBox').innerHTML = html || "<p>No policies</p>";
                }

                function renderMetricsTrend(history) {
                    const canvas = document.getElementById('metricsCanvas');
                    if (!canvas) return;
                    const ctx = canvas.getContext('2d');
                    const parentWidth = canvas.parentElement.clientWidth;
                    canvas.width = Math.max(400, parentWidth - 16);
                    canvas.height = 176;
                    ctx.clearRect(0, 0, canvas.width, canvas.height);

                    const points = (history || []).slice(-120).map(h => Number(h.runningServers || 0));
                    if (points.length < 2) {
                        ctx.fillStyle = '#94a3b8';
                        ctx.fillText('Not enough metric data yet.', 12, 24);
                        return;
                    }
                    const min = Math.min(...points);
                    const max = Math.max(...points);
                    const range = Math.max(1, max - min);

                    ctx.strokeStyle = '#155e75';
                    ctx.lineWidth = 1;
                    for (let i = 0; i < 4; i++) {
                        const y = 16 + (i * (canvas.height - 32) / 3);
                        ctx.beginPath();
                        ctx.moveTo(8, y);
                        ctx.lineTo(canvas.width - 8, y);
                        ctx.stroke();
                    }

                    ctx.strokeStyle = '#22d3ee';
                    ctx.lineWidth = 2;
                    ctx.beginPath();
                    points.forEach((v, i) => {
                        const x = 8 + (i * (canvas.width - 16) / (points.length - 1));
                        const y = canvas.height - 16 - ((v - min) / range) * (canvas.height - 32);
                        if (i === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y);
                    });
                    ctx.stroke();
                }

                async function stopServer(serverName) {
                    try {
                        await apiPost('/api/v1/servers/stop', { serverName });
                        showToast('success', 'Server stop triggered', serverName);
                        await refresh();
                    } catch (e) {
                        showToast('error', 'Stop failed', e.message);
                    }
                }

                async function restartServer(serverName) {
                    try {
                        await apiPost('/api/v1/servers/restart', { serverName });
                        showToast('success', 'Server restart triggered', serverName);
                        await refresh();
                    } catch (e) {
                        showToast('error', 'Restart failed', e.message);
                    }
                }

                async function startLike(groupName) {
                    const now = Date.now().toString().slice(-5);
                    const generated = `${groupName}-${now}`;
                    document.getElementById('startServerName').value = generated;
                    document.getElementById('startGroupName').value = groupName;
                }

                async function startServerManual() {
                    const serverName = document.getElementById('startServerName').value.trim();
                    const groupName = document.getElementById('startGroupName').value.trim();
                    if (!serverName || !groupName) {
                        showToast('warning', 'Validation', 'serverName and groupName required');
                        return;
                    }
                    await apiPost('/api/v1/servers/start', { serverName, groupName });
                    showToast('success', 'Server start triggered', serverName + ' (' + groupName + ')');
                    await refresh();
                }

                async function toggleWrapperDrain(wrapperId, draining) {
                    try {
                        await apiPost('/api/v1/wrappers/drain', { wrapperId, draining });
                        showToast('success', 'Wrapper updated', wrapperId + ' draining=' + draining);
                        await refresh();
                    } catch (e) {
                        showToast('error', 'Drain failed', e.message);
                    }
                }

                async function clearAlerts() {
                    await apiPost('/api/v1/alerts/clear', {});
                    showToast('success', 'Alerts cleared');
                    await refresh();
                }

                async function triggerWebhookTest() {
                    await apiPost('/api/v1/webhook/test', {});
                    showToast('success', 'Webhook test sent');
                    await refresh();
                }

                async function triggerScaleEval() {
                    await apiPost('/api/v1/scaling/trigger', {});
                    showToast('success', 'Scaling evaluation triggered');
                    await refresh();
                }

                async function getConfigValue() {
                    const key = document.getElementById('configKey').value.trim();
                    if (!key) return;
                    const res = await apiGet('/api/v1/config/get?key=' + encodeURIComponent(key));
                    document.getElementById('configValue').value = (res.value ?? '');
                }

                async function setConfigValue() {
                    const key = document.getElementById('configKey').value.trim();
                    const value = document.getElementById('configValue').value;
                    if (!key) return;
                    await apiPost('/api/v1/config/set', { key, value });
                    showToast('success', 'Config updated', key);
                    await refresh();
                }

                function applyRoleState() {
                    const isAdmin = state.role === 'ADMIN';
                    ['btnWebhookTest', 'btnClearAlerts', 'btnScaleEval', 'btnStartServer', 'btnConfigSet'].forEach(id => {
                        const el = document.getElementById(id);
                        if (el) el.disabled = !isAdmin;
                    });
                }

                function openWebSocket() {
                    if (state.ws) {
                        state.ws.close();
                        state.ws = null;
                    }
                    const baseUrl = state.wsUrl || `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.hostname}:8090/live`;
                    const separator = baseUrl.includes('?') ? '&' : '?';
                    const wsToken = state.wsTicket || state.apiKey;
                    const wsUrl = `${baseUrl}${separator}token=${encodeURIComponent(wsToken)}`;
                    state.ws = new WebSocket(wsUrl);
                    document.getElementById('wsState').textContent = 'WS: connecting';
                    state.ws.onopen = () => {
                        document.getElementById('wsState').textContent = 'WS: connected';
                    };
                    state.ws.onmessage = () => { scheduleAutoRefresh(); };
                    state.ws.onclose = () => {
                        document.getElementById('wsState').textContent = 'WS: disconnected';
                    };
                }

                document.getElementById('connectBtn').addEventListener('click', () => authenticate().catch(err => {
                    document.getElementById('authState').textContent = 'Auth failed';
                    document.getElementById('authState').className = 'rounded-full border border-rose-500/40 bg-rose-500/10 px-3 py-1 text-rose-200';
                    sessionStorage.removeItem('cloud_api_key');
                    state.wsTicket = null;
                    showToast('error', 'Authentication failed', err.message || String(err));
                    console.error(err);
                }));
                document.getElementById('refreshBtn').addEventListener('click', () => refresh().catch(console.error));
                document.getElementById('btnReloadView').addEventListener('click', () => refresh().catch(console.error));
                document.getElementById('btnWebhookTest').addEventListener('click', () => triggerWebhookTest().catch(err => showToast('error', 'Webhook failed', err.message)));
                document.getElementById('btnClearAlerts').addEventListener('click', () => clearAlerts().catch(err => showToast('error', 'Clear alerts failed', err.message)));
                document.getElementById('btnScaleEval').addEventListener('click', () => triggerScaleEval().catch(err => showToast('error', 'Scale eval failed', err.message)));
                document.getElementById('btnStartServer').addEventListener('click', () => startServerManual().catch(err => showToast('error', 'Start failed', err.message)));
                document.getElementById('btnConfigGet').addEventListener('click', () => getConfigValue().catch(err => showToast('error', 'Config get failed', err.message)));
                document.getElementById('btnConfigSet').addEventListener('click', () => setConfigValue().catch(err => showToast('error', 'Config set failed', err.message)));
                document.getElementById('serverFilter').addEventListener('input', () => {
                    if (state.overview) renderOverview(state.overview);
                });
                document.getElementById('logFilter').addEventListener('input', () => {
                    renderLogs(state.logs);
                });
                document.getElementById('themeToggle').addEventListener('click', () => toggleTheme());
                document.addEventListener('click', (ev) => {
                    const button = ev.target && ev.target.closest ? ev.target.closest('button[data-action]') : null;
                    if (!button || button.disabled) return;
                    const action = (button.dataset.action || '').trim();
                    if (!action) return;

                    const decode = (value) => {
                        if (!value) return '';
                        try {
                            return decodeURIComponent(value);
                        } catch (_) {
                            return value;
                        }
                    };

                    const run = async () => {
                        if (action === 'start-like') {
                            await startLike(decode(button.dataset.group || ''));
                            return;
                        }
                        if (action === 'restart-server') {
                            await restartServer(decode(button.dataset.server || ''));
                            return;
                        }
                        if (action === 'stop-server') {
                            await stopServer(decode(button.dataset.server || ''));
                            return;
                        }
                        if (action === 'toggle-wrapper-drain') {
                            const nextDraining = (button.dataset.draining || '').toLowerCase() === 'true';
                            await toggleWrapperDrain(decode(button.dataset.wrapper || ''), nextDraining);
                        }
                    };

                    run().catch(err => {
                        showToast('error', 'Action failed', err && err.message ? err.message : String(err));
                    });
                });
                document.getElementById('navToggle').addEventListener('click', () => {
                    const mobileNav = document.getElementById('mobileNav');
                    const navToggle = document.getElementById('navToggle');
                    const opening = mobileNav.classList.contains('hidden');
                    mobileNav.classList.toggle('hidden');
                    navToggle.setAttribute('aria-expanded', opening ? 'true' : 'false');
                });
                document.querySelectorAll('#mobileNav a').forEach(el => {
                    el.addEventListener('click', () => {
                        document.getElementById('mobileNav').classList.add('hidden');
                        document.getElementById('navToggle').setAttribute('aria-expanded', 'false');
                    });
                });
                window.addEventListener('resize', () => {
                    if (window.innerWidth >= 768) {
                        document.getElementById('mobileNav').classList.add('hidden');
                        document.getElementById('navToggle').setAttribute('aria-expanded', 'false');
                    }
                });

                if (state.apiKey) {
                    authenticate().catch(() => {});
                }
            </script>
        </body>
        </html>
        """;
    }

    private int getEffectivePlayerCount(Master master, ServerInstance server, int sessionPlayers, int backendPlayers) {
        if (server == null) {
            return 0;
        }
        if (!isProxyServer(server)) {
            return Math.max(0, server.playerCount);
        }
        // Proxy metrics can lag behind. Use the best known online count for dashboard/API display.
        return Math.max(Math.max(0, server.playerCount), Math.max(sessionPlayers, backendPlayers));
    }

    private int getTrackedSessionPlayerCount(Master master) {
        if (master == null || master.getPlayerSessionManager() == null) {
            return 0;
        }
        return master.getPlayerSessionManager().getPlayerServerMapSnapshot().size();
    }

    private int getBackendOnlinePlayerCount(Master master) {
        if (master == null) {
            return 0;
        }
        return master.getRunningServers().values().stream()
                .filter(s -> !isProxyServer(s))
                .mapToInt(s -> Math.max(0, s.playerCount))
                .sum();
    }

    private boolean isProxyServer(ServerInstance server) {
        if (server == null) {
            return false;
        }
        String group = server.groupName == null ? "" : server.groupName;
        String name = server.serverName == null ? "" : server.serverName;
        return group.equalsIgnoreCase("Proxy")
                || group.toLowerCase(Locale.ROOT).contains("proxy")
                || name.toLowerCase(Locale.ROOT).startsWith("proxy");
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






