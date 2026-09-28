/**
 * Erstellt von Gamer_Kidd_LP | kallifabio
 * am 09.01.2026 um 21:01
 * Projektname: KalliCloud
 * Packagename: de.kallifabio.cloud.api
 */

package de.kallifabio.cloud.api;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import de.kallifabio.cloud.data.ClanData;
import de.kallifabio.cloud.data.PlayerData;
import de.kallifabio.cloud.data.PunishmentData;
import de.kallifabio.cloud.libs.Message;
import de.kallifabio.cloud.libs.console.ConsoleColors;
import de.kallifabio.cloud.libs.console.ConsoleScreenManager;
import de.kallifabio.cloud.libs.logging.CentralLogger;
import de.kallifabio.cloud.master.Master;
import de.kallifabio.cloud.master.ServerInstance;
import de.kallifabio.cloud.master.capacity.CapacityPlannerService;
import de.kallifabio.cloud.master.permissions.PermissionGroup;
import de.kallifabio.cloud.master.permissions.PermissionProfile;
import de.kallifabio.cloud.setup.SetupValidator;
import de.kallifabio.cloud.setup.SystemDoctor;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.KeyStore;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

public class CloudHttpServer {

    private HttpServer server;
    private LiveWebSocketServer liveWebSocketServer;
    private ThreadPoolExecutor httpExecutor;
    private final Gson gson;
    private final long startedAt = System.currentTimeMillis();
    private volatile String cachedDashboardHtml;
    private static final int DEFAULT_PORT = 8081;
    private static final int DEFAULT_WS_PORT = 8090;
    private static final int PORT_FALLBACK_RANGE = 20;
    private static final int PORT_BIND_RETRIES = 3;
    private static final long PORT_RETRY_DELAY_MS = 300L;
    private final Map<String, String> apiKeys = new HashMap<>();
    private final Map<String, String> apiKeyRoles = new HashMap<>();
    private final Map<String, Long> liveWsTickets = new ConcurrentHashMap<>();
    private final Map<String, DashboardSession> dashboardSessions = new ConcurrentHashMap<>();
    private final Map<String, Deque<Long>> requestTimestampsByIp = new ConcurrentHashMap<>();
    private final Map<String, Long> blockedIps = new ConcurrentHashMap<>();
    private volatile Set<String> corsAllowedOrigins = Set.of("*");
    private volatile long lastRateLimitCleanupAt = 0L;
    private static final int RATE_LIMIT_PER_MINUTE = 600;
    private static final long BLOCK_DURATION_MS = 60 * 1000L;
    private static final long WS_TICKET_TTL_MS = 2 * 60 * 1000L;
    private static final long DASHBOARD_SESSION_TTL_MS = 30 * 60 * 1000L;
    private static final long RATE_LIMIT_CLEANUP_INTERVAL_MS = 60 * 1000L;
    private static final long MAX_FILE_READ_BYTES = 512L * 1024L;
    private static final long MAX_FILE_WRITE_BYTES = 1024L * 1024L;
    private static final long SYSTEM_DOCTOR_CACHE_TTL_MS = 30_000L;
    private static final long SELECTOR_HEARTBEAT_STALE_MS = 45_000L;
    private final Deque<Map<String, Object>> metricHistory = new ArrayDeque<>();
    private volatile Map<String, Object> cachedSystemDoctorPayload;
    private volatile long cachedSystemDoctorAt = 0L;
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

        String configuredOwner = Master.getInstance() != null
                ? Master.getInstance().getConfigManager().getMaster("CloudMaster.API.OwnerKey")
                : null;
        configuredOwner = sanitizeApiKey(configuredOwner);

        String configuredOperator = Master.getInstance() != null
                ? Master.getInstance().getConfigManager().getMaster("CloudMaster.API.OperatorKey")
                : null;
        configuredOperator = sanitizeApiKey(configuredOperator);

        apiKeys.put("admin", configuredAdmin);
        apiKeys.put("dashboard", configuredDashboard);
        if (configuredOwner != null && !configuredOwner.isBlank()) {
            apiKeys.put("owner", configuredOwner);
            apiKeyRoles.put(configuredOwner, "OWNER");
        }
        if (configuredOperator != null && !configuredOperator.isBlank()) {
            apiKeys.put("operator", configuredOperator);
            apiKeyRoles.put(configuredOperator, "OPERATOR");
        }
        apiKeyRoles.put(configuredAdmin, "ADMIN");
        apiKeyRoles.put(configuredDashboard, "VIEWER");
        ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " API Keys loaded (owner/operator/admin/dashboard)");
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
        httpExecutor = createHttpExecutor();
        server.setExecutor(httpExecutor);

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

    private ThreadPoolExecutor createHttpExecutor() {
        int cores = Math.max(2, Runtime.getRuntime().availableProcessors());
        int coreThreads = Math.min(8, Math.max(4, cores));
        int maxThreads = Math.min(32, Math.max(coreThreads, cores * 2));
        AtomicInteger counter = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "KalliCloud-REST-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return new ThreadPoolExecutor(
                coreThreads,
                maxThreads,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(512),
                factory,
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    private void setupEndpoints() {
        // Health & Status
        registerContext("/api/v1/health", this::handleHealth);
        registerContext("/api/v1/readiness", this::handleReadiness);
        registerContext("/api/v1/auth/session", this::handleAuthSession);
        registerContext("/api/v1/auth/logout", this::handleAuthLogout);
        registerContext("/api/v1/auth/me", this::handleAuthMe);
        registerContext("/api/v1/auth/debug", this::handleAuthDebug);
        registerContext("/api/v1/auth/rotate", this::handleRotateKey);
        registerContext("/api/v1/status", this::handleStatus);
        registerContext("/api/v1/dashboard/overview", this::handleDashboardOverview);
        registerContext("/api/v1/system/diagnostics", this::handleSystemDiagnostics);
        registerContext("/api/v1/system/doctor", this::handleSystemDoctor);
        registerContext("/api/v1/system/capacity", this::handleSystemCapacityPlanner);
        registerContext("/api/v1/system/report", this::handleSystemReport);
        registerContext("/api/v1/events/recent", this::handleEventsRecent);
        registerContext("/api/v1/bungeesystem", this::handleBungeeSystem);
        registerContext("/api/v1/lifecycle", this::handleLifecycle);
        registerContext("/api/v1/recovery/state", this::handleRecoveryState);
        registerContext("/api/v1/recovery/unquarantine", this::handleRecoveryUnquarantine);
        registerContext("/api/v1/incidents", this::handleIncidents);
        registerContext("/api/v1/backups", this::handleBackups);
        registerContext("/api/v1/backups/create", this::handleBackupCreate);
        registerContext("/api/v1/backups/restore-staging", this::handleBackupRestoreStaging);
        registerContext("/api/v1/rolling/restart", this::handleRollingRestart);
        registerContext("/api/v1/firewall/check", this::handleFirewallCheck);
        registerContext("/api/v1/motd", this::handleMotdStatus);
        registerContext("/api/v1/motd/update", this::handleMotdUpdate);
        registerContext("/openapi.yml", this::handleOpenApiSpec);

        // Cluster Management
        registerContext("/api/v1/cluster/info", this::handleClusterInfo);
        registerContext("/api/v1/cluster/nodes", this::handleClusterNodes);

        // Server Management
        registerContext("/api/v1/servers", this::handleServers);
        registerContext("/api/v1/servers/start", this::handleServerStart);
        registerContext("/api/v1/servers/stop", this::handleServerStop);
        registerContext("/api/v1/servers/restart", this::handleServerRestart);

        // Wrapper Management
        registerContext("/api/v1/wrappers", this::handleWrappers);
        registerContext("/api/v1/wrappers/drain", this::handleWrapperDrain);

        // Monitoring
        registerContext("/api/v1/metrics", this::handleMetrics);
        registerContext("/api/v1/metrics/history", this::handleMetricsHistory);
        registerContext("/api/v1/metrics/prometheus", this::handlePrometheusMetrics);
        registerContext("/api/v1/alerts", this::handleAlerts);
        registerContext("/api/v1/alerts/clear", this::handleAlertsClear);

        // Scaling
        registerContext("/api/v1/scaling/policies", this::handleScalingPolicies);
        registerContext("/api/v1/scaling/trigger", this::handleScalingTrigger);

        // Queue Management
        registerContext("/api/v1/queue/status", this::handleQueueStatus);
        registerContext("/api/v1/queue/join", this::handleQueueJoin);
        registerContext("/api/v1/player/data", this::handlePlayerData);
        registerContext("/api/v1/player/friends", this::handlePlayerFriends);
        registerContext("/api/v1/player/party", this::handlePlayerParty);
        registerContext("/api/v1/player/profile", this::handlePlayerProfile);
        registerContext("/api/v1/player/settings", this::handlePlayerSettings);
        registerContext("/api/v1/social/players", this::handleSocialPlayer);
        registerContext("/api/v1/party/create", this::handlePartyCreate);
        registerContext("/api/v1/party/invite", this::handlePartyInvite);
        registerContext("/api/v1/party/accept", this::handlePartyAccept);
        registerContext("/api/v1/party/kick", this::handlePartyKick);
        registerContext("/api/v1/party/leave", this::handlePartyLeave);
        registerContext("/api/v1/party/switch", this::handlePartySwitch);
        registerContext("/api/v1/punishments", this::handlePunishments);
        registerContext("/api/v1/punishments/upsert", this::handlePunishmentUpsert);
        registerContext("/api/v1/punishments/pardon", this::handlePunishmentPardon);
        registerContext("/api/v1/clans", this::handleClans);
        registerContext("/api/v1/clans/upsert", this::handleClanUpsert);
        registerContext("/api/v1/clans/delete", this::handleClanDelete);
        registerContext("/api/v1/permissions/group", this::handlePermissionGroup);
        registerContext("/api/v1/permissions/assign", this::handlePermissionAssign);
        registerContext("/api/v1/permissions/temp", this::handleTempPermission);
        registerContext("/api/v1/permissions/profile", this::handlePermissionProfile);
        registerContext("/api/v1/permissions/player", this::handlePermissionPlayerAlias);
        registerContext("/api/v1/permissions/check", this::handlePermissionCheck);
        registerContext("/api/v1/events/lobby", this::handleLobbyEvent);
        registerContext("/api/v1/signs", this::handleSigns);
        registerContext("/api/v1/signs/render", this::handleSignsRender);
        registerContext("/api/v1/signs/upsert", this::handleSignUpsert);
        registerContext("/api/v1/signs/delete", this::handleSignDelete);
        registerContext("/api/v1/signs/layouts", this::handleSignLayouts);
        registerContext("/api/v1/entity-selectors", this::handleEntitySelectors);
        registerContext("/api/v1/entity-selectors/render", this::handleEntitySelectorsRender);
        registerContext("/api/v1/entity-selectors/upsert", this::handleSignUpsert);
        registerContext("/api/v1/entity-selectors/delete", this::handleSignDelete);
        registerContext("/api/v1/entity-selectors/layouts", this::handleSignLayouts);
        registerContext("/api/v1/selectors", this::handleSelectors);
        registerContext("/api/v1/selectors/render", this::handleSelectorsRender);
        registerContext("/api/v1/selectors/upsert", this::handleSignUpsert);
        registerContext("/api/v1/selectors/delete", this::handleSignDelete);
        registerContext("/api/v1/selectors/layouts", this::handleSignLayouts);
        registerContext("/api/v1/selectors/templates", this::handleSelectorTemplates);
        registerContext("/api/v1/selectors/preview", this::handleSelectorPreview);
        registerContext("/api/v1/selectors/bulk", this::handleSelectorBulk);
        registerContext("/api/v1/selectors/cleanup", this::handleSelectorCleanup);
        registerContext("/api/v1/selectors/versions", this::handleSelectorVersions);
        registerContext("/api/v1/selectors/rollback", this::handleSelectorRollback);
        registerContext("/api/v1/selectors/heartbeat", this::handleSelectorHeartbeat);
        registerContext("/api/v1/selectors/resolve", this::handleSelectorResolve);

        // Safe managed file browser
        registerContext("/api/v1/files/list", this::handleFileList);
        registerContext("/api/v1/files/read", this::handleFileRead);
        registerContext("/api/v1/files/write", this::handleFileWrite);
        registerContext("/api/v1/files/mkdir", this::handleFileMkdir);
        registerContext("/api/v1/files/delete", this::handleFileDelete);

        // Load Balancer
        registerContext("/api/v1/loadbalancer/stats", this::handleLoadBalancerStats);

        // Group runtime management
        registerContext("/api/v1/groups", this::handleGroups);
        registerContext("/api/v1/groups/create", this::handleGroupCreate);
        registerContext("/api/v1/groups/delete", this::handleGroupDelete);
        registerContext("/api/v1/groups/update", this::handleGroupUpdate);

        // Template management
        registerContext("/api/v1/templates/diff", this::handleTemplateDiff);
        registerContext("/api/v1/templates/rollback", this::handleTemplateRollback);
        registerContext("/api/v1/templates/versions", this::handleTemplateVersions);

        // Operations
        registerContext("/api/v1/logs/recent", this::handleRecentLogs);
        registerContext("/api/v1/logs/search", this::handleLogSearch);
        registerContext("/api/v1/audit/recent", this::handleAuditRecent);
        registerContext("/api/v1/console/screens", this::handleConsoleScreens);
        registerContext("/api/v1/console/tail", this::handleConsoleTail);
        registerContext("/api/v1/console/send", this::handleConsoleSend);
        registerContext("/api/v1/setup/report", this::handleSetupReport);
        registerContext("/api/v1/config/get", this::handleConfigGet);
        registerContext("/api/v1/config/set", this::handleConfigSet);
        registerContext("/api/v1/webhook/test", this::handleWebhookTest);

        // Dashboard (serve static HTML)
        registerContext("/dashboard", this::handleDashboard);
        registerContext("/", this::handleRoot);
    }

    private void registerContext(String path, HttpHandler handler) {
        server.createContext(path, exchange -> {
            String requestId = resolveRequestId(exchange);
            exchange.setAttribute("requestId", requestId);
            try {
                if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                    applyCorsHeaders(exchange);
                    exchange.getResponseHeaders().set("X-Request-Id", requestId);
                    exchange.sendResponseHeaders(204, -1);
                    return;
                }
                handler.handle(exchange);
            } catch (IllegalArgumentException e) {
                sendError(exchange, 400, "BAD_REQUEST", safeErrorMessage(e), null, e);
            } catch (SecurityException e) {
                sendError(exchange, 403, "FORBIDDEN", safeErrorMessage(e), null, e);
            } catch (Exception e) {
                sendError(exchange, 500, "INTERNAL_ERROR",
                        "Unexpected server error. Check central logs with requestId=" + requestId,
                        null, e);
            } finally {
                exchange.close();
            }
        });
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        sendResponse(exchange, 200, buildHealthPayload());
    }

    private Map<String, Object> buildHealthPayload() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", System.currentTimeMillis());
        health.put("version", "1.0.2");
        health.put("uptimeMs", System.currentTimeMillis() - startedAt);
        health.put("apiPort", port);
        health.put("wsPort", wsPort);
        health.put("tls", tlsEnabled);
        health.put("apiRuntime", buildApiRuntimePayload());
        return health;
    }

    private Map<String, Object> buildApiRuntimePayload() {
        Map<String, Object> runtime = new LinkedHashMap<>();
        runtime.put("uptimeMs", System.currentTimeMillis() - startedAt);
        runtime.put("apiPort", port);
        runtime.put("wsPort", wsPort);
        runtime.put("tls", tlsEnabled);

        ThreadPoolExecutor executor = httpExecutor;
        if (executor == null) {
            runtime.put("status", "STOPPED");
            runtime.put("activeThreads", 0);
            runtime.put("poolSize", 0);
            runtime.put("maxThreads", 0);
            runtime.put("queuedRequests", 0);
            runtime.put("queueCapacity", 0);
            runtime.put("queueUtilization", 0.0);
            return runtime;
        }

        int queueSize = executor.getQueue().size();
        int queueRemaining = executor.getQueue().remainingCapacity();
        int queueCapacity = queueSize + queueRemaining;
        double queueUtilization = queueCapacity <= 0 ? 0.0 : queueSize / (double) queueCapacity;
        boolean saturated = executor.getActiveCount() >= executor.getMaximumPoolSize() && queueSize > 0;
        String status = queueUtilization >= 0.90 || saturated ? "CRITICAL"
                : queueUtilization >= 0.70 ? "WARNING"
                : "OK";

        runtime.put("status", status);
        runtime.put("coreThreads", executor.getCorePoolSize());
        runtime.put("activeThreads", executor.getActiveCount());
        runtime.put("poolSize", executor.getPoolSize());
        runtime.put("largestPoolSize", executor.getLargestPoolSize());
        runtime.put("maxThreads", executor.getMaximumPoolSize());
        runtime.put("queuedRequests", queueSize);
        runtime.put("queueRemaining", queueRemaining);
        runtime.put("queueCapacity", queueCapacity);
        runtime.put("queueUtilization", Math.round(queueUtilization * 10_000.0) / 10_000.0);
        runtime.put("completedRequests", executor.getCompletedTaskCount());
        runtime.put("totalScheduledRequests", executor.getTaskCount());
        return runtime;
    }

    private boolean isApiRuntimeHealthy(Map<String, Object> runtime) {
        Object status = runtime == null ? null : runtime.get("status");
        return status == null || (!"CRITICAL".equalsIgnoreCase(String.valueOf(status))
                && !"STOPPED".equalsIgnoreCase(String.valueOf(status)));
    }

    private void handleReadiness(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        sendResponse(exchange, 200, buildReadinessPayload());
    }

    private void handleAuthSession(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        if (!checkRateLimit(exchange)) {
            sendResponse(exchange, 429, Map.of("error", "Rate limit exceeded"));
            return;
        }
        Map<String, Object> body = readJsonBody(exchange);
        String presented = stringValue(body.get("apiKey"), readApiKey(exchange));
        String resolved = resolvePresentedApiKey(presented);
        if (resolved == null || !isKnownApiKey(resolved)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        String role = apiKeyRoles.getOrDefault(resolved, "VIEWER");
        String token = issueDashboardSession(role, getClientIp(exchange));
        String wsTicket = issueWsTicket();
        sendResponse(exchange, 200, Map.of(
                "authenticated", true,
                "role", role,
                "sessionToken", token,
                "expiresAt", dashboardSessions.get(token).expiresAt,
                "expiresInMs", DASHBOARD_SESSION_TTL_MS,
                "wsTicket", wsTicket,
                "wsUrl", buildWsUrl(exchange)
        ));
    }

    private void handleAuthLogout(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        String token = readBearerToken(exchange);
        if (token != null) {
            dashboardSessions.remove(token);
            apiKeyRoles.remove(token);
        }
        sendResponse(exchange, 200, Map.of("loggedOut", true));
    }

    private Map<String, Object> buildReadinessPayload() {
        Master master = Master.getInstance();
        Map<String, Object> diagnostics = master == null ? Map.of() : buildSystemDiagnostics();
        Map<String, Object> summary = diagnostics.get("summary") instanceof Map<?, ?> raw
                ? new LinkedHashMap<>((Map<String, Object>) raw)
                : new LinkedHashMap<>();
        String diagnosticsState = String.valueOf(diagnostics.getOrDefault("state", "UNKNOWN"));
        boolean masterReady = master != null;
        boolean hasWrapper = masterReady && master.getConnectedWrappers().values().stream().anyMatch(wrapper -> wrapper.isHealthy());
        boolean apiReady = server != null;
        boolean wsReady = liveWebSocketServer != null;
        Map<String, Object> apiRuntime = buildApiRuntimePayload();
        boolean apiRuntimeHealthy = isApiRuntimeHealthy(apiRuntime);
        boolean ready = masterReady && apiReady && wsReady && hasWrapper && apiRuntimeHealthy
                && !"CRITICAL".equalsIgnoreCase(diagnosticsState);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ready", ready);
        payload.put("status", ready ? "READY" : "DEGRADED");
        payload.put("timestamp", System.currentTimeMillis());
        payload.put("uptimeMs", System.currentTimeMillis() - startedAt);
        payload.put("components", Map.of(
                "master", masterReady,
                "api", apiReady,
                "apiRuntime", apiRuntimeHealthy,
                "websocket", wsReady,
                "healthyWrapper", hasWrapper
        ));
        payload.put("recommendedHttpStatus", ready ? 200 : 503);
        payload.put("diagnosticsState", diagnosticsState);
        payload.put("summary", summary);
        payload.put("apiRuntime", apiRuntime);
        return payload;
    }

    private void handleAuthMe(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        String apiKey = readApiKey(exchange);
        String role = apiKeyRoles.getOrDefault(apiKey, "UNKNOWN");
        String wsTicket = issueWsTicket();
        sendResponse(exchange, 200, Map.of(
                "authenticated", true,
                "role", role,
                "session", apiKey != null && dashboardSessions.containsKey(apiKey),
                "sessionExpiresAt", sessionExpiresAt(apiKey),
                "wsUrl", buildWsUrl(exchange),
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
            Map<String, Object> request = readJsonBody(exchange);
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

        sendResponse(exchange, 200, buildDashboardOverviewPayload());
    }

    private Map<String, Object> buildDashboardOverviewPayload() {
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
            serverInfo.put("lifecycleState", server.getLifecycleState().name());
            serverInfo.put("failureCount", server.failureCount);
            serverInfo.put("quarantined", server.quarantined);
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
            w.put("routeHost", wrapper.getRouteHost());
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
        return payload;
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

    private void handleSystemDoctor(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        sendResponse(exchange, 200, buildSystemDoctorPayload());
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

    private void handleSystemReport(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }

        Map<String, Object> report = new LinkedHashMap<>();
        Master master = Master.getInstance();
        report.put("generatedAt", System.currentTimeMillis());
        report.put("version", "1");
        report.put("health", buildHealthPayload());
        report.put("readiness", buildReadinessPayload());
        report.put("masterAvailable", master != null);
        report.put("apiRuntime", buildApiRuntimePayload());
        report.put("doctor", buildSystemDoctorPayload());
        if (master != null) {
            report.put("overview", buildDashboardOverviewPayload());
            report.put("diagnostics", buildSystemDiagnostics());
            report.put("capacity", buildSystemCapacityPlanner());
            report.put("setup", SetupValidator.buildReport(master.getConfigManager(), false));
        }
        report.put("logStats", buildLogStats(readLatestLogLines(2_000)));
        report.put("recentAudit", findLogLines("AUDIT", "INFO", 25));
        sendResponse(exchange, 200, report);
    }

    private Map<String, Object> buildSystemDoctorPayload() {
        long now = System.currentTimeMillis();
        Map<String, Object> cached = cachedSystemDoctorPayload;
        if (cached != null && now - cachedSystemDoctorAt < SYSTEM_DOCTOR_CACHE_TTL_MS) {
            return cached;
        }

        synchronized (this) {
            cached = cachedSystemDoctorPayload;
            if (cached != null && now - cachedSystemDoctorAt < SYSTEM_DOCTOR_CACHE_TTL_MS) {
                return cached;
            }
            Map<String, Object> fresh = buildSystemDoctorPayloadUncached(now);
            cachedSystemDoctorPayload = fresh;
            cachedSystemDoctorAt = now;
            return fresh;
        }
    }

    private Map<String, Object> buildSystemDoctorPayloadUncached(long now) {
        Master master = Master.getInstance();
        if (master != null) {
            return SystemDoctor.buildReport(master.getConfigManager());
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("state", "CRITICAL");
        summary.put("critical", 1);
        summary.put("warnings", 0);
        summary.put("info", 0);
        summary.put("findings", 1);
        summary.put("groups", 0);

        Map<String, Object> finding = new LinkedHashMap<>();
        finding.put("severity", "CRITICAL");
        finding.put("id", "master:unavailable");
        finding.put("message", "Master is not available.");
        finding.put("recommendation", "Start the Cloud master before running full system diagnostics.");

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("timestamp", now);
        report.put("summary", summary);
        report.put("findings", List.of(finding));
        return report;
    }

    private Map<String, Object> buildSystemDoctorLiveSummary() {
        Map<String, Object> doctor = buildSystemDoctorPayload();
        Map<String, Object> summary = new LinkedHashMap<>();
        Object rawSummary = doctor.get("summary");
        if (rawSummary instanceof Map<?, ?> map) {
            summary.put("state", String.valueOf(map.get("state") == null ? "UNKNOWN" : map.get("state")));
            summary.put("critical", map.get("critical") == null ? 0 : map.get("critical"));
            summary.put("warnings", map.get("warnings") == null ? 0 : map.get("warnings"));
            summary.put("info", map.get("info") == null ? 0 : map.get("info"));
            summary.put("findings", map.get("findings") == null ? 0 : map.get("findings"));
            summary.put("groups", map.get("groups") == null ? 0 : map.get("groups"));
        } else {
            summary.put("state", "UNKNOWN");
            summary.put("critical", 0);
            summary.put("warnings", 0);
            summary.put("info", 0);
            summary.put("findings", 0);
            summary.put("groups", 0);
        }
        summary.put("cachedAt", cachedSystemDoctorAt);
        return summary;
    }

    private void handleEventsRecent(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        Map<String, String> query = parseQueryParams(exchange.getRequestURI().getQuery());
        int limit = parseBoundedInt(query.get("limit"), 100, 10, 500);
        Master master = Master.getInstance();
        List<Map<String, Object>> events = master == null
                ? List.of()
                : master.getEventTimelineService().recent(limit, query.get("type"), query.get("severity"));
        sendResponse(exchange, 200, Map.of("limit", limit, "count", events.size(), "events", events));
    }

    private void handleBungeeSystem(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }

        Master master = Master.getInstance();
        Map<String, Object> overview = buildDashboardOverviewPayload();
        List<Map<String, Object>> proxyServers = new ArrayList<>();
        for (Object item : (List<?>) overview.getOrDefault("servers", List.of())) {
            if (item instanceof Map<?, ?> raw) {
                String group = String.valueOf(raw.get("groupName") == null ? "" : raw.get("groupName"));
                String name = String.valueOf(raw.get("serverName") == null ? "" : raw.get("serverName"));
                if (group.toLowerCase(Locale.ROOT).contains("proxy") || name.toLowerCase(Locale.ROOT).startsWith("proxy")) {
                    Map<String, Object> proxy = new LinkedHashMap<>();
                    raw.forEach((key, value) -> proxy.put(String.valueOf(key), value));
                    proxyServers.add(proxy);
                }
            }
        }

        List<Map<String, Object>> activePunishments = new ArrayList<>();
        for (PunishmentData punishment : master.getDataStore().getPunishments(null, true)) {
            activePunishments.add(punishmentState(punishment));
        }

        List<Map<String, Object>> clans = new ArrayList<>();
        for (ClanData clan : master.getDataStore().getClans()) {
            clans.add(clanState(clan));
        }

        List<Map<String, Object>> events = master.getEventTimelineService() == null
                ? List.of()
                : master.getEventTimelineService().recent(120, null, null);
        List<Map<String, Object>> bungeeEvents = events.stream()
                .filter(event -> {
                    String type = String.valueOf(event.getOrDefault("type", "")).toUpperCase(Locale.ROOT);
                    String source = String.valueOf(event.getOrDefault("source", "")).toUpperCase(Locale.ROOT);
                    return source.contains("PROXY")
                            || type.contains("PUNISHMENT")
                            || type.contains("CLAN")
                            || type.contains("PARTY")
                            || type.contains("FRIEND")
                            || type.contains("PROXY");
                })
                .limit(80)
                .toList();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("timestamp", System.currentTimeMillis());
        payload.put("proxies", proxyServers);
        payload.put("activePunishments", activePunishments);
        payload.put("activePunishmentCount", activePunishments.size());
        payload.put("clans", clans);
        payload.put("clanCount", clans.size());
        payload.put("events", bungeeEvents);
        payload.put("eventCount", bungeeEvents.size());
        payload.put("queue", overview.getOrDefault("queue", Map.of()));
        payload.put("liveSync", Map.of(
                "websocketPort", wsPort,
                "recentEventsInSnapshot", true,
                "snapshotIntervalSeconds", 5
        ));
        sendResponse(exchange, 200, payload);
    }

    private void handleLifecycle(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        Map<String, String> query = parseQueryParams(exchange.getRequestURI().getQuery());
        int limit = parseBoundedInt(query.get("limit"), 100, 10, 500);
        String serverName = query.get("serverName");
        Master master = Master.getInstance();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("serverName", serverName == null ? "" : serverName);
        payload.put("transitions", master == null
                ? List.of()
                : master.getLifecycleOrchestrator().recentTransitions(serverName, limit));
        if (master != null && (serverName == null || serverName.isBlank())) {
            List<Map<String, Object>> servers = new ArrayList<>();
            for (ServerInstance server : master.getRunningServers().values()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("serverName", server.serverName);
                item.put("groupName", server.groupName);
                item.put("status", server.status);
                item.put("lifecycleState", server.getLifecycleState().name());
                item.put("failureCount", server.failureCount);
                item.put("quarantined", server.quarantined);
                servers.add(item);
            }
            payload.put("servers", servers);
        }
        sendResponse(exchange, 200, payload);
    }

    private void handleRecoveryState(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        Master master = Master.getInstance();
        if (master == null) {
            sendResponse(exchange, 503, Map.of("error", "Master not available"));
            return;
        }
        sendResponse(exchange, 200, master.getRecoveryState());
    }

    private void handleRecoveryUnquarantine(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        Map<String, Object> request = readJsonBody(exchange);
        String serverName = stringValue(request.get("serverName"), "");
        if (serverName.isBlank()) {
            sendResponse(exchange, 400, Map.of("error", "serverName required"));
            return;
        }
        Master master = Master.getInstance();
        if (master == null) {
            sendResponse(exchange, 503, Map.of("error", "Master not available"));
            return;
        }
        boolean cleared = master.clearServerQuarantine(serverName);
        sendResponse(exchange, 200, Map.of(
                "serverName", serverName,
                "cleared", cleared,
                "recovery", master.getRecoveryState()
        ));
    }

    private void handleIncidents(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        Map<String, String> query = parseQueryParams(exchange.getRequestURI().getQuery());
        int limit = parseBoundedInt(query.get("limit"), 50, 10, 500);
        Master master = Master.getInstance();
        List<Map<String, Object>> incidents = master == null
                ? List.of()
                : master.getIncidentReportService().listIncidents(limit);
        sendResponse(exchange, 200, Map.of("limit", limit, "count", incidents.size(), "incidents", incidents));
    }

    private void handleBackups(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        Map<String, String> query = parseQueryParams(exchange.getRequestURI().getQuery());
        int limit = parseBoundedInt(query.get("limit"), 50, 10, 500);
        Master master = Master.getInstance();
        List<Map<String, Object>> backups = master == null ? List.of() : master.getBackupManager().listBackups(limit);
        sendResponse(exchange, 200, Map.of("limit", limit, "count", backups.size(), "backups", backups));
    }

    private void handleBackupCreate(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        Map<String, Object> request = readJsonBody(exchange);
        String name = stringValue(request.get("name"), "manual");
        boolean includeLogs = booleanValue(request.get("includeLogs"), false);
        Master master = Master.getInstance();
        if (master == null) {
            sendResponse(exchange, 503, Map.of("error", "Master not available"));
            return;
        }
        sendResponse(exchange, 200, Map.of("backup", master.getBackupManager().createBackup(name, includeLogs)));
    }

    private void handleBackupRestoreStaging(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        Map<String, Object> request = readJsonBody(exchange);
        String backupName = stringValue(request.get("backupName"), "");
        Master master = Master.getInstance();
        if (master == null) {
            sendResponse(exchange, 503, Map.of("error", "Master not available"));
            return;
        }
        sendResponse(exchange, 200, master.getBackupManager().restoreToStaging(backupName));
    }

    private void handleRollingRestart(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        Map<String, Object> request = readJsonBody(exchange);
        String groupName = stringValue(request.get("groupName"), "");
        int delaySeconds = parseBoundedInt(String.valueOf(request.getOrDefault("delaySeconds", "15")), 15, 5, 300);
        Master master = Master.getInstance();
        if (master == null) {
            sendResponse(exchange, 503, Map.of("error", "Master not available"));
            return;
        }
        List<String> targets = new ArrayList<>();
        for (ServerInstance server : master.getRunningServers().values()) {
            if (groupName.isBlank() || groupName.equalsIgnoreCase(server.groupName)) {
                targets.add(server.serverName);
            }
        }
        new Thread(() -> {
            for (String serverName : targets) {
                master.restartServer(serverName);
                try {
                    Thread.sleep(delaySeconds * 1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, "KalliCloud-RollingRestart").start();
        master.getEventTimelineService().publish("ROLLING_RESTART", "api", "INFO",
                "Rolling restart scheduled", Map.of("group", groupName, "targets", targets.size(), "delaySeconds", delaySeconds));
        sendResponse(exchange, 202, Map.of("message", "rolling restart scheduled", "targets", targets, "delaySeconds", delaySeconds));
    }

    private void handleFirewallCheck(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        Master master = Master.getInstance();
        List<Map<String, Object>> checks = new ArrayList<>();
        if (master != null) {
            for (ServerInstance server : master.getRunningServers().values()) {
                String host = "127.0.0.1";
                for (var wrapper : master.getConnectedWrappers().values()) {
                    if (wrapper.wrapperId.equalsIgnoreCase(server.wrapperId)) {
                        host = wrapper.routeHost == null || wrapper.routeHost.isBlank() ? wrapper.hostname : wrapper.routeHost;
                        break;
                    }
                }
                checks.add(checkTcp(server.serverName, host, server.port));
            }
        }
        sendResponse(exchange, 200, Map.of("checks", checks, "count", checks.size()));
    }

    private void handleMotdStatus(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        sendResponse(exchange, 200, buildMotdPayload());
    }

    private void handleMotdUpdate(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange, "OPERATOR")) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        Master master = Master.getInstance();
        if (master == null || master.getConfigManager() == null) {
            sendResponse(exchange, 503, Map.of("error", "Master not available"));
            return;
        }
        Map<String, Object> request = readJsonBody(exchange);
        setConfigIfPresent("CloudMaster.MOTD.Enabled", request.get("enabled"));
        setConfigIfPresent("CloudMaster.MOTD.Line1", request.get("line1"));
        setConfigIfPresent("CloudMaster.MOTD.Line2", request.get("line2"));
        setConfigIfPresent("CloudMaster.MOTD.MaintenanceLine1", request.get("maintenanceLine1"));
        setConfigIfPresent("CloudMaster.MOTD.MaintenanceLine2", request.get("maintenanceLine2"));
        setConfigIfPresent("CloudMaster.MOTD.FakeSlots.Enabled", request.get("fakeSlotsEnabled"));
        setConfigIfPresent("CloudMaster.MOTD.FakeSlots.Online",
                request.containsKey("fakeSlotsOnline") ? request.get("fakeSlotsOnline") : request.get("fakeOnline"));
        setConfigIfPresent("CloudMaster.MOTD.FakeSlots.Max",
                request.containsKey("fakeSlotsMax") ? request.get("fakeSlotsMax") : request.get("fakeMax"));
        try {
            master.getConfigManager().getMasterConfigData().save(master.getConfigManager().getMasterConfigFile());
            master.reloadConfiguration("api:motd-update");
        } catch (Exception e) {
            sendResponse(exchange, 500, Map.of("error", "Failed to save MOTD config: " + e.getMessage()));
            return;
        }
        master.getEventTimelineService().publish("MOTD_UPDATED", "api", "INFO",
                "MOTD/slots config updated", Map.of("by", "api"));
        sendResponse(exchange, 200, buildMotdPayload());
    }

    private Map<String, Object> buildMotdPayload() {
        Master master = Master.getInstance();
        Map<String, Object> payload = new LinkedHashMap<>();
        if (master == null || master.getConfigManager() == null) {
            payload.put("available", false);
            return payload;
        }
        boolean enabled = booleanConfig("CloudMaster.MOTD.Enabled", true);
        String line1 = stringConfig("CloudMaster.MOTD.Line1", "&bKalliCloud Network");
        String line2 = stringConfig("CloudMaster.MOTD.Line2", "&7Powered by KalliCloud");
        String maintenanceLine1 = stringConfig("CloudMaster.MOTD.MaintenanceLine1", "&cMaintenance");
        String maintenanceLine2 = stringConfig("CloudMaster.MOTD.MaintenanceLine2", "&7Please try again later");
        boolean fakeSlotsEnabled = booleanConfig("CloudMaster.MOTD.FakeSlots.Enabled", false);
        int fakeOnline = intConfig("CloudMaster.MOTD.FakeSlots.Online", -1);
        int fakeMax = intConfig("CloudMaster.MOTD.FakeSlots.Max", -1);
        payload.put("available", true);
        payload.put("motd", Map.of("enabled", enabled, "line1", line1, "line2", line2));
        payload.put("maintenance", Map.of("line1", maintenanceLine1, "line2", maintenanceLine2));
        payload.put("fakeSlots", Map.of("enabled", fakeSlotsEnabled, "online", fakeOnline, "max", fakeMax));
        payload.put("enabled", enabled);
        payload.put("line1", line1);
        payload.put("line2", line2);
        payload.put("maintenanceLine1", maintenanceLine1);
        payload.put("maintenanceLine2", maintenanceLine2);
        payload.put("fakeSlotsEnabled", fakeSlotsEnabled);
        payload.put("fakeOnline", fakeOnline);
        payload.put("fakeMax", fakeMax);
        payload.put("actualOnline", master.getPlayerSessionManager() == null ? 0
                : master.getPlayerSessionManager().getPlayerServerMapSnapshot().size());
        payload.put("actualMax", master.getRunningServers().values().stream()
                .filter(server -> !isProxyServer(server))
                .mapToInt(server -> Math.max(0, server.maxPlayers))
                .sum());
        return payload;
    }

    private void setConfigIfPresent(String key, Object value) {
        if (value == null || Master.getInstance() == null || Master.getInstance().getConfigManager() == null) {
            return;
        }
        Master.getInstance().getConfigManager().getMasterConfigData().set(key, value);
    }

    private String stringConfig(String key, String fallback) {
        try {
            String value = Master.getInstance().getConfigManager().getMaster(key);
            return value == null || value.isBlank() ? fallback : value;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private boolean booleanConfig(String key, boolean fallback) {
        return Boolean.parseBoolean(stringConfig(key, String.valueOf(fallback)));
    }

    private int intConfig(String key, int fallback) {
        try {
            return Integer.parseInt(stringConfig(key, String.valueOf(fallback)));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private Map<String, Object> buildSystemCapacityPlanner() {
        return CapacityPlannerService.build(Master.getInstance());
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
            serverInfo.put("port", server.port);
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
            Map<String, Object> request = readJsonBody(exchange);

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
            sendOperationException(exchange, e);
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
            Map<String, Object> request = readJsonBody(exchange);

            String serverName = (String) request.get("serverName");

            if (serverName == null) {
                sendResponse(exchange, 400, Map.of("error", "serverName required"));
                return;
            }

            Master.getInstance().stopServer(serverName);
            CentralLogger.audit("api", "server_stop", serverName);
            sendResponse(exchange, 200, Map.of("message", "Server stop initiated", "serverName", serverName));
        } catch (Exception e) {
            sendOperationException(exchange, e);
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
            Map<String, Object> request = readJsonBody(exchange);

            String serverName = (String) request.get("serverName");

            if (serverName == null) {
                sendResponse(exchange, 400, Map.of("error", "serverName required"));
                return;
            }

            Master.getInstance().restartServer(serverName);
            CentralLogger.audit("api", "server_restart", serverName);
            sendResponse(exchange, 200, Map.of("message", "Server restart initiated", "serverName", serverName));
        } catch (Exception e) {
            sendOperationException(exchange, e);
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
            wrapperInfo.put("routeHost", wrapper.getRouteHost());
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
            Map<String, Object> request = readJsonBody(exchange);
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
            sendOperationException(exchange, e);
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

    private void handleSigns(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        sendResponse(exchange, 200, buildSignCenterPayload(false, Set.of("SIGN")));
    }

    private void handleSignsRender(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        sendResponse(exchange, 200, buildSignCenterPayload(true, Set.of("SIGN")));
    }

    private void handleEntitySelectors(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        sendResponse(exchange, 200, buildSignCenterPayload(false, Set.of("NPC", "MOB")));
    }

    private void handleEntitySelectorsRender(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        sendResponse(exchange, 200, buildSignCenterPayload(true, Set.of("NPC", "MOB")));
    }

    private void handleSelectors(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        sendResponse(exchange, 200, buildSignCenterPayload(false));
    }

    private void handleSelectorsRender(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        sendResponse(exchange, 200, buildSignCenterPayload(true));
    }

    private void handleSelectorResolve(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange, "OPERATOR")) {
            sendResponse(exchange, 403, Map.of("error", "Forbidden"));
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        Master master = Master.getInstance();
        if (master == null) {
            sendResponse(exchange, 503, Map.of("error", "Master not available"));
            return;
        }

        Map<String, Object> request = readJsonBody(exchange);
        String selectorId = stringValue(request.get("id"), stringValue(request.get("selectorId"), ""));
        if (selectorId.isBlank()) {
            sendResponse(exchange, 400, Map.of("error", "id required"));
            return;
        }
        Map<String, Object> storedSelector = findSelectorForPath(master, selectorId, "/api/v1/selectors");
        if (storedSelector == null) {
            sendResponse(exchange, 404, Map.of("error", "selector not found", "id", selectorId));
            return;
        }

        Map<String, Object> selector = new LinkedHashMap<>(storedSelector);
        String playerUuid = stringValue(request.get("playerUuid"), "");
        String playerName = stringValue(request.get("playerName"), "Player");
        int priority = numberValue(request.get("priority"), 0);
        boolean enqueue = booleanValue(request.get("enqueue"), true);
        boolean assignSession = booleanValue(request.get("assignSession"), false);

        Map<String, Object> decision = resolveSelectorDecision(master, selector, playerUuid, playerName, priority, enqueue, assignSession);
        master.getEventTimelineService().publish("SELECTOR_RESOLVE", "selector:" + selectorId, "INFO",
                "Selector resolve executed", Map.of(
                        "selectorId", selectorId,
                        "action", String.valueOf(decision.get("action")),
                        "playerUuid", playerUuid,
                        "targetServer", String.valueOf(decision.getOrDefault("targetServer", ""))
                ));
        CentralLogger.audit("api", "selector_resolve", selectorId + " -> " + decision.get("action"));
        sendResponse(exchange, 200, decision);
    }

    private void handleSignUpsert(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        Master master = Master.getInstance();
        if (master == null) {
            sendResponse(exchange, 503, Map.of("error", "Master not available"));
            return;
        }
        Map<String, Object> request = readJsonBody(exchange);
        String path = exchange.getRequestURI().getPath();
        if (path.startsWith("/api/v1/signs")) {
            request.put("selectorType", "SIGN");
        } else if (path.startsWith("/api/v1/entity-selectors")) {
            String type = stringValue(request.get("selectorType"), stringValue(request.get("type"), "NPC")).toUpperCase(Locale.ROOT);
            if (!"NPC".equals(type) && !"MOB".equals(type)) {
                sendResponse(exchange, 400, Map.of("error", "selectorType must be NPC or MOB for entity-selectors"));
                return;
            }
            request.put("selectorType", type);
        }
        if (stringValue(request.get("serverName"), stringValue(request.get("server"), "")).isBlank()
                && stringValue(request.get("groupName"), stringValue(request.get("group"), "")).isBlank()) {
            sendResponse(exchange, 400, Map.of("error", "serverName or groupName required"));
            return;
        }
        Map<String, Object> sign = master.getConfigManager().upsertCloudSign(request);
        master.getEventTimelineService().publish("SELECTOR_UPSERT", "selector:" + sign.get("id"), "INFO",
                "Cloud selector upserted", Map.of("selectorId", String.valueOf(sign.get("id"))));
        CentralLogger.audit("api", "selector_upsert", String.valueOf(sign.get("id")));
        sendResponse(exchange, 200, Map.of("selector", sign, "sign", sign, "snapshot", buildSignCenterPayload(true)));
    }

    private void handleSignDelete(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod()) && !"DELETE".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        Master master = Master.getInstance();
        if (master == null) {
            sendResponse(exchange, 503, Map.of("error", "Master not available"));
            return;
        }
        String id;
        if ("DELETE".equals(exchange.getRequestMethod())) {
            id = parseQueryParams(exchange.getRequestURI().getQuery()).getOrDefault("id", "");
        } else {
            id = stringValue(readJsonBody(exchange).get("id"), "");
        }
        if (id.isBlank()) {
            sendResponse(exchange, 400, Map.of("error", "id required"));
            return;
        }
        String path = exchange.getRequestURI().getPath();
        Map<String, Object> existing = findSelectorForPath(master, id, path);
        if (existing == null) {
            sendResponse(exchange, 404, Map.of("error", "selector not found for this endpoint type", "id", id));
            return;
        }
        boolean deleted = master.getConfigManager().deleteCloudSign(id);
        master.getEventTimelineService().publish("SELECTOR_DELETE", "selector:" + id, "INFO",
                "Cloud selector deleted", Map.of("selectorId", id, "deleted", deleted));
        CentralLogger.audit("api", "selector_delete", id);
        sendResponse(exchange, 200, Map.of("id", id, "deleted", deleted, "snapshot", buildSignCenterPayload(true)));
    }

    private Map<String, Object> findSelectorForPath(Master master, String id, String path) {
        Set<String> allowedTypes = Set.of();
        if (path.startsWith("/api/v1/signs")) {
            allowedTypes = Set.of("SIGN");
        } else if (path.startsWith("/api/v1/entity-selectors")) {
            allowedTypes = Set.of("NPC", "MOB");
        }
        for (Map<String, Object> selector : master.getConfigManager().getCloudSigns()) {
            if (!id.equalsIgnoreCase(stringValue(selector.get("id"), ""))) {
                continue;
            }
            String type = stringValue(selector.get("selectorType"), "SIGN").toUpperCase(Locale.ROOT);
            if (allowedTypes.isEmpty() || allowedTypes.contains(type)) {
                return selector;
            }
        }
        return null;
    }

    private void handleSignLayouts(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        Master master = Master.getInstance();
        if (master == null) {
            sendResponse(exchange, 503, Map.of("error", "Master not available"));
            return;
        }
        if ("GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 200, Map.of(
                    "layouts", master.getConfigManager().getSignLayouts(),
                    "animationFrames", master.getConfigManager().getSignAnimationFrames()
            ));
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        Map<String, Object> request = readJsonBody(exchange);
        String name = stringValue(request.get("name"), "");
        List<String> lines = stringList(request.get("lines"));
        if (name.isBlank() || lines.isEmpty()) {
            sendResponse(exchange, 400, Map.of("error", "name and lines required"));
            return;
        }
        boolean saved = master.getConfigManager().upsertSignLayout(name, lines);
        master.getEventTimelineService().publish("SELECTOR_LAYOUT_UPSERT", "selector-layout:" + name, "INFO",
                "Cloud selector layout upserted", Map.of("layout", name, "saved", saved));
        CentralLogger.audit("api", "selector_layout_upsert", name);
        sendResponse(exchange, 200, Map.of("saved", saved, "layouts", master.getConfigManager().getSignLayouts()));
    }

    private void handleSelectorTemplates(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        Master master = Master.getInstance();
        if (master == null) {
            sendResponse(exchange, 503, Map.of("error", "Master not available"));
            return;
        }
        sendResponse(exchange, 200, Map.of("templates", master.getConfigManager().getSelectorTemplates()));
    }

    private void handleSelectorPreview(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        Master master = Master.getInstance();
        if (master == null) {
            sendResponse(exchange, 503, Map.of("error", "Master not available"));
            return;
        }
        Map<String, Object> selector;
        if ("GET".equals(exchange.getRequestMethod())) {
            String id = parseQueryParams(exchange.getRequestURI().getQuery()).getOrDefault("id", "");
            selector = findSelectorForPath(master, id, "/api/v1/selectors");
            if (selector == null) {
                sendResponse(exchange, 404, Map.of("error", "selector not found", "id", id));
                return;
            }
        } else if ("POST".equals(exchange.getRequestMethod())) {
            selector = readJsonBody(exchange);
        } else {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        sendResponse(exchange, 200, Map.of("preview", renderSelectorPreview(master, selector)));
    }

    private void handleSelectorBulk(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange, "ADMIN")) {
            sendResponse(exchange, 403, Map.of("error", "Forbidden"));
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        Master master = Master.getInstance();
        if (master == null) {
            sendResponse(exchange, 503, Map.of("error", "Master not available"));
            return;
        }
        Map<String, Object> result = master.getConfigManager().bulkUpdateSelectors(readJsonBody(exchange));
        master.getEventTimelineService().publish("SELECTOR_BULK", "selectors", "INFO",
                "Selector bulk action executed", result);
        CentralLogger.audit("api", "selector_bulk", String.valueOf(result));
        sendResponse(exchange, 200, Map.of("result", result, "snapshot", buildSignCenterPayload(true)));
    }

    private void handleSelectorCleanup(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange, "ADMIN")) {
            sendResponse(exchange, 403, Map.of("error", "Forbidden"));
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        Map<String, Object> request = readJsonBody(exchange);
        Master master = Master.getInstance();
        if (master == null) {
            sendResponse(exchange, 503, Map.of("error", "Master not available"));
            return;
        }
        Map<String, Object> result = master.getConfigManager().cleanupStaleSelectors(booleanValue(request.get("disableOnly"), true));
        master.getEventTimelineService().publish("SELECTOR_CLEANUP", "selectors", "INFO",
                "Selector cleanup executed", result);
        CentralLogger.audit("api", "selector_cleanup", String.valueOf(result));
        sendResponse(exchange, 200, Map.of("result", result, "snapshot", buildSignCenterPayload(true)));
    }

    private void handleSelectorVersions(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        Master master = Master.getInstance();
        if (master == null) {
            sendResponse(exchange, 503, Map.of("error", "Master not available"));
            return;
        }
        List<String> versions = master.getConfigManager().listSelectorVersions();
        sendResponse(exchange, 200, Map.of("versions", versions, "count", versions.size()));
    }

    private void handleSelectorRollback(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange, "ADMIN")) {
            sendResponse(exchange, 403, Map.of("error", "Forbidden"));
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        Master master = Master.getInstance();
        if (master == null) {
            sendResponse(exchange, 503, Map.of("error", "Master not available"));
            return;
        }
        String version = stringValue(readJsonBody(exchange).get("version"), "");
        boolean rolledBack = master.getConfigManager().rollbackSelectorVersion(version);
        CentralLogger.audit("api", "selector_rollback", version + " -> " + rolledBack);
        sendResponse(exchange, rolledBack ? 200 : 404, Map.of(
                "rolledBack", rolledBack,
                "version", version,
                "snapshot", buildSignCenterPayload(true)
        ));
    }

    private void handleSelectorHeartbeat(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange, "OPERATOR")) {
            sendResponse(exchange, 403, Map.of("error", "Forbidden"));
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        Master master = Master.getInstance();
        if (master == null) {
            sendResponse(exchange, 503, Map.of("error", "Master not available"));
            return;
        }
        Map<String, Object> request = readJsonBody(exchange);
        String id = stringValue(request.get("id"), "");
        Map<String, Object> selector = findSelectorForPath(master, id, "/api/v1/selectors");
        if (selector == null) {
            sendResponse(exchange, 404, Map.of("error", "selector not found", "id", id));
            return;
        }
        selector.put("spawned", booleanValue(request.get("spawned"), true));
        selector.put("lastSeenAt", System.currentTimeMillis());
        if (request.containsKey("world")) selector.put("world", stringValue(request.get("world"), "world"));
        if (request.containsKey("x")) selector.put("x", numberValue(request.get("x"), 0));
        if (request.containsKey("y")) selector.put("y", numberValue(request.get("y"), 0));
        if (request.containsKey("z")) selector.put("z", numberValue(request.get("z"), 0));
        if (request.containsKey("yaw")) selector.put("yaw", doubleValue(request.get("yaw"), 0.0));
        if (request.containsKey("pitch")) selector.put("pitch", doubleValue(request.get("pitch"), 0.0));
        if (request.containsKey("world") || request.containsKey("x") || request.containsKey("y") || request.containsKey("z")) {
            selector.put("locationMode", "FIXED");
            selector.put("autoLocation", false);
        }
        Map<String, Object> saved = master.getConfigManager().upsertCloudSign(selector);
        sendResponse(exchange, 200, Map.of("selector", saved));
    }

    private void handleQueueJoin(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }

        Map<String, Object> req = readJsonBody(exchange);
        String playerUuid = stringValue(req.get("playerUuid"));
        String playerName = stringValue(req.getOrDefault("playerName", "Player"));
        String group = stringValue(req.get("group"));
        int priority = numberValue(req.get("priority"), 0);
        if (playerUuid.isBlank() || group.isBlank()) {
            sendResponse(exchange, 400, Map.of("error", "playerUuid and group required"));
            return;
        }

        Master master = Master.getInstance();
        master.getPlayerQueueManager().addToQueue(playerUuid, playerName.isBlank() ? "Player" : playerName, group, priority);
        int position = master.getPlayerQueueManager().getQueuePosition(playerUuid, group);
        CentralLogger.audit("api", "queue_join", playerUuid + " -> " + group + " pos=" + position);
        if (master.getEventTimelineService() != null) {
            master.getEventTimelineService().publish("QUEUE_JOIN", "lobby-api", "INFO",
                    playerUuid + " queued for " + group,
                    Map.of("playerUuid", playerUuid, "playerName", playerName, "group", group, "position", position));
        }
        sendResponse(exchange, 200, Map.of(
                "message", "queued",
                "playerUuid", playerUuid,
                "group", group,
                "position", position,
                "totalQueued", master.getPlayerQueueManager().getTotalQueued()
        ));
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
            Map<String, Object> req = readJsonBody(exchange);
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

    private void handlePlayerProfile(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        Map<String, String> query = parseQueryParams(exchange.getRequestURI().getQuery());
        String uuid = firstNonBlank(query.get("uuid"), query.get("playerUuid"), trailingPath(exchange, "/api/v1/player/profile/"));
        if (uuid == null || uuid.isBlank()) {
            sendResponse(exchange, 400, Map.of("error", "uuid/playerUuid required"));
            return;
        }

        Master master = Master.getInstance();
        if ("GET".equals(exchange.getRequestMethod())) {
            PlayerData data = master.getDataStore().getPlayerData(uuid);
            PermissionProfile permissions = master.buildPermissionProfile(uuid);
            sendResponse(exchange, 200, Map.of(
                    "playerUuid", uuid,
                    "coins", data.coins,
                    "stats", data.stats,
                    "lastServer", data.lastServer,
                    "lastSeen", data.lastSeen,
                    "permissions", permissions.permissions,
                    "primaryGroup", permissions.primaryGroup,
                    "prefix", permissions.prefix,
                    "suffix", permissions.suffix
            ));
            return;
        }

        if ("POST".equals(exchange.getRequestMethod())) {
            Map<String, Object> req = readJsonBody(exchange);
            PlayerData data = master.getDataStore().getPlayerData(uuid);
            if (req.get("coins") != null) {
                data.coins = numberValue(req.get("coins"), data.coins);
            }
            if (req.get("lastServer") != null) {
                data.lastServer = stringValue(req.get("lastServer"));
            }
            if (req.get("stats") instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    data.stats.put(String.valueOf(entry.getKey()), numberValue(entry.getValue(), 0));
                }
            }
            data.lastSeen = System.currentTimeMillis();
            master.getDataStore().savePlayerData(data);
            CentralLogger.audit("api", "player_profile_save", uuid);
            sendResponse(exchange, 200, Map.of("message", "profile saved", "playerUuid", uuid));
            return;
        }

        sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
    }

    private void handlePlayerSettings(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        Map<String, String> query = parseQueryParams(exchange.getRequestURI().getQuery());
        String uuid = firstNonBlank(query.get("uuid"), query.get("playerUuid"), trailingPath(exchange, "/api/v1/player/settings/"));
        if (uuid == null || uuid.isBlank()) {
            sendResponse(exchange, 400, Map.of("error", "uuid/playerUuid required"));
            return;
        }

        Master master = Master.getInstance();
        PlayerData data = master.getDataStore().getPlayerData(uuid);
        if ("GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 200, Map.of(
                    "playerUuid", uuid,
                    "visibility", settingInt(data, "lobby.visibility", 0),
                    "scoreboard", settingInt(data, "lobby.scoreboard", 1) == 1,
                    "sounds", settingInt(data, "lobby.sounds", 1) == 1,
                    "language", settingInt(data, "lobby.language", 0) == 1 ? "en_US" : "de_DE"
            ));
            return;
        }

        if ("POST".equals(exchange.getRequestMethod())) {
            Map<String, Object> req = readJsonBody(exchange);
            if (req.get("visibility") != null) {
                data.stats.put("lobby.visibility", visibilityToInt(stringValue(req.get("visibility"))));
            }
            if (req.get("scoreboard") != null) {
                data.stats.put("lobby.scoreboard", booleanValue(req.get("scoreboard"), true) ? 1 : 0);
            }
            if (req.get("sounds") != null) {
                data.stats.put("lobby.sounds", booleanValue(req.get("sounds"), true) ? 1 : 0);
            }
            if (req.get("language") != null) {
                data.stats.put("lobby.language", "en_US".equalsIgnoreCase(stringValue(req.get("language"))) ? 1 : 0);
            }
            data.lastSeen = System.currentTimeMillis();
            master.getDataStore().savePlayerData(data);
            CentralLogger.audit("api", "player_settings_save", uuid);
            sendResponse(exchange, 200, Map.of("message", "settings saved", "playerUuid", uuid));
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
            Map<String, Object> req = readJsonBody(exchange);
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

    private void handleSocialPlayer(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        String uuid = firstNonBlank(
                parseQueryParams(exchange.getRequestURI().getQuery()).get("uuid"),
                parseQueryParams(exchange.getRequestURI().getQuery()).get("playerUuid"),
                trailingPath(exchange, "/api/v1/social/players/")
        );
        if (uuid == null || uuid.isBlank()) {
            sendResponse(exchange, 400, Map.of("error", "uuid/playerUuid required"));
            return;
        }
        Master master = Master.getInstance();
        List<String> friends = master.getDataStore().getFriends(uuid);
        String partyId = Optional.ofNullable(master.getDataStore().getPartyIdForPlayer(uuid)).orElse("");
        List<String> partyMembers = partyId.isBlank()
                ? List.of()
                : master.getDataStore().getPartyMembers(partyId);
        String leaderUuid = partyId.isBlank()
                ? ""
                : Optional.ofNullable(master.getDataStore().getPartyLeader(partyId)).orElse("");
        sendResponse(exchange, 200, Map.of(
                "playerUuid", uuid,
                "friends", friends,
                "partyId", partyId,
                "leaderUuid", leaderUuid,
                "partyMembers", partyMembers,
                "friendCount", friends.size()
        ));
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
            Map<String, Object> req = readJsonBody(exchange);
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

    private void handlePartyCreate(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }

        Map<String, Object> req = readJsonBody(exchange);
        String leaderUuid = firstNonBlank(stringValue(req.get("leaderUuid")), stringValue(req.get("playerUuid")));
        if (leaderUuid == null || leaderUuid.isBlank()) {
            sendResponse(exchange, 400, Map.of("error", "leaderUuid/playerUuid required"));
            return;
        }

        Master master = Master.getInstance();
        String partyId = firstNonBlank(stringValue(req.get("partyId")), master.getDataStore().getPartyIdForPlayer(leaderUuid));
        if (partyId == null || partyId.isBlank()) {
            partyId = "party-" + leaderUuid.replace("-", "").substring(0, Math.min(8, leaderUuid.replace("-", "").length()))
                    + "-" + Long.toUnsignedString(System.currentTimeMillis(), 36);
        }

        LinkedHashSet<String> members = new LinkedHashSet<>();
        members.add(leaderUuid);
        if (req.get("members") instanceof List<?> list) {
            for (Object member : list) {
                String value = stringValue(member);
                if (value != null && !value.isBlank()) {
                    members.add(value);
                }
            }
        }

        master.getDataStore().setPartyMembers(partyId, leaderUuid, new ArrayList<>(members));
        CentralLogger.audit("api", "party_create", partyId + " leader=" + leaderUuid + " size=" + members.size());
        sendResponse(exchange, 200, partyState(partyId));
    }

    private void handlePartyInvite(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }

        Map<String, Object> req = readJsonBody(exchange);
        String inviterUuid = firstNonBlank(stringValue(req.get("inviterUuid")), stringValue(req.get("fromUuid")), stringValue(req.get("playerUuid")));
        String targetUuid = firstNonBlank(stringValue(req.get("targetUuid")), stringValue(req.get("toUuid")));
        if (inviterUuid == null || targetUuid == null || inviterUuid.isBlank() || targetUuid.isBlank()) {
            sendResponse(exchange, 400, Map.of("error", "inviterUuid/fromUuid and targetUuid/toUuid required"));
            return;
        }
        if (inviterUuid.equalsIgnoreCase(targetUuid)) {
            sendResponse(exchange, 409, Map.of("error", "cannot invite yourself"));
            return;
        }

        Master master = Master.getInstance();
        String partyId = firstNonBlank(stringValue(req.get("partyId")), master.getDataStore().getPartyIdForPlayer(inviterUuid));
        String leaderUuid = master.getDataStore().getPartyLeader(partyId);
        if (partyId == null || partyId.isBlank() || leaderUuid == null || leaderUuid.isBlank()) {
            partyId = "party-" + inviterUuid.replace("-", "").substring(0, Math.min(8, inviterUuid.replace("-", "").length()))
                    + "-" + Long.toUnsignedString(System.currentTimeMillis(), 36);
            leaderUuid = inviterUuid;
            master.getDataStore().setPartyMembers(partyId, leaderUuid, List.of(inviterUuid));
        }

        List<String> members = master.getDataStore().getPartyMembers(partyId);
        if (!members.stream().anyMatch(member -> member.equalsIgnoreCase(inviterUuid))) {
            sendResponse(exchange, 403, Map.of("error", "inviter is not member of party"));
            return;
        }
        if (members.stream().anyMatch(member -> member.equalsIgnoreCase(targetUuid))) {
            sendResponse(exchange, 200, partyState(partyId, Map.of("message", "target already in party")));
            return;
        }

        long ttlMs = Math.max(60_000L, numberValue(req.get("ttlMs"), 10 * 60 * 1000));
        long expiresAt = System.currentTimeMillis() + ttlMs;
        master.getDataStore().createPartyInvite(partyId, inviterUuid, targetUuid, expiresAt);
        CentralLogger.audit("api", "party_invite", partyId + " " + inviterUuid + " -> " + targetUuid);
        sendResponse(exchange, 200, partyState(partyId, Map.of(
                "message", "party invite created",
                "targetUuid", targetUuid,
                "expiresAt", expiresAt
        )));
    }

    private void handlePartyAccept(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }

        Map<String, Object> req = readJsonBody(exchange);
        String playerUuid = firstNonBlank(stringValue(req.get("playerUuid")), stringValue(req.get("targetUuid")));
        if (playerUuid == null || playerUuid.isBlank()) {
            sendResponse(exchange, 400, Map.of("error", "playerUuid/targetUuid required"));
            return;
        }

        Master master = Master.getInstance();
        String partyId = firstNonBlank(master.getDataStore().consumePartyInvite(playerUuid), stringValue(req.get("partyId")));
        if (partyId == null || partyId.isBlank()) {
            sendResponse(exchange, 404, Map.of("error", "no pending party invite found"));
            return;
        }

        List<String> current = master.getDataStore().getPartyMembers(partyId);
        if (current.isEmpty()) {
            sendResponse(exchange, 404, Map.of("error", "party not found", "partyId", partyId));
            return;
        }
        LinkedHashSet<String> members = new LinkedHashSet<>(current);
        members.add(playerUuid);
        String leaderUuid = Optional.ofNullable(master.getDataStore().getPartyLeader(partyId)).orElse(current.get(0));
        master.getDataStore().setPartyMembers(partyId, leaderUuid, new ArrayList<>(members));
        CentralLogger.audit("api", "party_accept", partyId + " player=" + playerUuid);
        sendResponse(exchange, 200, partyState(partyId, Map.of("message", "party invite accepted")));
    }

    private void handlePartyKick(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }

        Map<String, Object> req = readJsonBody(exchange);
        String targetUuid = firstNonBlank(stringValue(req.get("targetUuid")), stringValue(req.get("playerUuid")));
        String actorUuid = firstNonBlank(stringValue(req.get("actorUuid")), stringValue(req.get("leaderUuid")));
        if (targetUuid == null || targetUuid.isBlank()) {
            sendResponse(exchange, 400, Map.of("error", "targetUuid/playerUuid required"));
            return;
        }

        Master master = Master.getInstance();
        String partyId = firstNonBlank(stringValue(req.get("partyId")), master.getDataStore().getPartyIdForPlayer(targetUuid));
        if (partyId == null || partyId.isBlank()) {
            sendResponse(exchange, 404, Map.of("error", "party not found"));
            return;
        }
        String leaderUuid = master.getDataStore().getPartyLeader(partyId);
        if (actorUuid != null && leaderUuid != null && !actorUuid.equalsIgnoreCase(leaderUuid)) {
            sendResponse(exchange, 403, Map.of("error", "only party leader can kick members"));
            return;
        }
        if (leaderUuid != null && targetUuid.equalsIgnoreCase(leaderUuid)) {
            sendResponse(exchange, 409, Map.of("error", "leader cannot be kicked; use leave to transfer leadership"));
            return;
        }

        List<String> members = new ArrayList<>(master.getDataStore().getPartyMembers(partyId));
        boolean removed = members.removeIf(member -> member.equalsIgnoreCase(targetUuid));
        if (!removed) {
            sendResponse(exchange, 404, Map.of("error", "target is not in party"));
            return;
        }
        master.getDataStore().setPartyMembers(partyId, leaderUuid == null ? "" : leaderUuid, members);
        CentralLogger.audit("api", "party_kick", partyId + " target=" + targetUuid);
        sendResponse(exchange, 200, partyState(partyId, Map.of("message", "party member kicked")));
    }

    private void handlePartyLeave(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }

        Map<String, Object> req = readJsonBody(exchange);
        String playerUuid = stringValue(req.get("playerUuid"));
        if (playerUuid == null || playerUuid.isBlank()) {
            sendResponse(exchange, 400, Map.of("error", "playerUuid required"));
            return;
        }

        Master master = Master.getInstance();
        String partyId = firstNonBlank(stringValue(req.get("partyId")), master.getDataStore().getPartyIdForPlayer(playerUuid));
        if (partyId == null || partyId.isBlank()) {
            sendResponse(exchange, 404, Map.of("error", "party not found"));
            return;
        }

        List<String> members = new ArrayList<>(master.getDataStore().getPartyMembers(partyId));
        boolean removed = members.removeIf(member -> member.equalsIgnoreCase(playerUuid));
        if (!removed) {
            sendResponse(exchange, 404, Map.of("error", "player is not in party"));
            return;
        }
        String oldLeader = master.getDataStore().getPartyLeader(partyId);
        String newLeader = members.isEmpty()
                ? ""
                : (playerUuid.equalsIgnoreCase(Optional.ofNullable(oldLeader).orElse("")) ? members.get(0) : oldLeader);
        master.getDataStore().setPartyMembers(partyId, newLeader == null ? "" : newLeader, members);
        CentralLogger.audit("api", "party_leave", partyId + " player=" + playerUuid + " remaining=" + members.size());
        sendResponse(exchange, 200, partyState(partyId, Map.of(
                "message", "party left",
                "removedUuid", playerUuid
        )));
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
            Map<String, Object> req = readJsonBody(exchange);
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

    private Map<String, Object> partyState(String partyId) {
        return partyState(partyId, Map.of());
    }

    private Map<String, Object> partyState(String partyId, Map<String, Object> extra) {
        Master master = Master.getInstance();
        List<String> members = partyId == null || partyId.isBlank()
                ? List.of()
                : master.getDataStore().getPartyMembers(partyId);
        String leaderUuid = partyId == null || partyId.isBlank()
                ? ""
                : Optional.ofNullable(master.getDataStore().getPartyLeader(partyId)).orElse("");
        Map<String, Object> response = new LinkedHashMap<>();
        if (extra != null) {
            response.putAll(extra);
        }
        response.put("partyId", partyId == null ? "" : partyId);
        response.put("leaderUuid", leaderUuid);
        response.put("members", members);
        response.put("memberCount", members.size());
        return response;
    }

    private void handlePunishments(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        Map<String, String> query = parseQueryParams(exchange.getRequestURI().getQuery());
        String targetUuid = firstNonBlank(query.get("targetUuid"), query.get("uuid"), trailingPath(exchange, "/api/v1/punishments/"));
        boolean activeOnly = Boolean.parseBoolean(firstNonBlank(query.get("activeOnly"), query.get("active"), "false"));
        List<Map<String, Object>> punishments = new ArrayList<>();
        for (PunishmentData punishment : Master.getInstance().getDataStore().getPunishments(targetUuid, activeOnly)) {
            punishments.add(punishmentState(punishment));
        }
        sendResponse(exchange, 200, Map.of("punishments", punishments, "count", punishments.size()));
    }

    private void handlePunishmentUpsert(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        Map<String, Object> req = readJsonBody(exchange);
        PunishmentData punishment = new PunishmentData();
        punishment.id = firstNonBlank(stringValue(req.get("id")),
                firstNonBlank(stringValue(req.get("type")), "punishment").toLowerCase(Locale.ROOT)
                        + "-" + Long.toUnsignedString(System.currentTimeMillis(), 36)
                        + "-" + UUID.randomUUID().toString().substring(0, 8));
        punishment.targetUuid = firstNonBlank(stringValue(req.get("targetUuid")), stringValue(req.get("uuid")));
        punishment.targetName = firstNonBlank(stringValue(req.get("targetName")), "");
        punishment.actorUuid = firstNonBlank(stringValue(req.get("actorUuid")), "");
        punishment.actorName = firstNonBlank(stringValue(req.get("actorName")), "API");
        punishment.type = firstNonBlank(stringValue(req.get("type")), "WARN").toUpperCase(Locale.ROOT);
        punishment.reason = firstNonBlank(stringValue(req.get("reason")), "No reason provided");
        punishment.proof = firstNonBlank(stringValue(req.get("proof")), "");
        punishment.notes = firstNonBlank(stringValue(req.get("notes")), "");
        punishment.address = firstNonBlank(stringValue(req.get("address")), "");
        punishment.createdAt = longValue(req.get("createdAt"), System.currentTimeMillis());
        punishment.expiresAt = longValue(req.get("expiresAt"), 0L);
        punishment.active = req.get("active") == null || Boolean.parseBoolean(String.valueOf(req.get("active")));
        if (punishment.targetUuid == null || punishment.targetUuid.isBlank()) {
            sendResponse(exchange, 400, Map.of("error", "targetUuid required"));
            return;
        }
        Master.getInstance().getDataStore().upsertPunishment(punishment);
        CentralLogger.audit("api", "punishment_upsert", punishment.type + " " + punishment.targetUuid + " id=" + punishment.id);
        publishApiEvent("PUNISHMENT_" + punishment.type, punishment.targetName + " " + punishment.reason);
        sendResponse(exchange, 200, punishmentState(punishment));
    }

    private void handlePunishmentPardon(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        Map<String, Object> req = readJsonBody(exchange);
        String id = stringValue(req.get("id"));
        String targetUuid = firstNonBlank(stringValue(req.get("targetUuid")), stringValue(req.get("uuid")));
        String typeLike = stringValue(req.get("typeLike"));
        boolean changed = id != null && !id.isBlank()
                ? Master.getInstance().getDataStore().deactivatePunishment(id)
                : Master.getInstance().getDataStore().deactivatePunishmentsForTarget(targetUuid, typeLike) > 0;
        CentralLogger.audit("api", "punishment_pardon", firstNonBlank(id, targetUuid, "-"));
        sendResponse(exchange, 200, Map.of("changed", changed));
    }

    private void handleClans(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        Map<String, String> query = parseQueryParams(exchange.getRequestURI().getQuery());
        String name = firstNonBlank(query.get("name"), trailingPath(exchange, "/api/v1/clans/"));
        if (name != null && !name.isBlank()) {
            ClanData clan = Master.getInstance().getDataStore().getClan(name);
            if (clan == null) {
                sendResponse(exchange, 404, Map.of("error", "clan not found"));
                return;
            }
            sendResponse(exchange, 200, clanState(clan));
            return;
        }
        List<Map<String, Object>> clans = new ArrayList<>();
        for (ClanData clan : Master.getInstance().getDataStore().getClans()) {
            clans.add(clanState(clan));
        }
        sendResponse(exchange, 200, Map.of("clans", clans, "count", clans.size()));
    }

    private void handleClanUpsert(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        Map<String, Object> req = readJsonBody(exchange);
        ClanData clan = new ClanData();
        clan.name = firstNonBlank(stringValue(req.get("name")), stringValue(req.get("clanName")));
        clan.tag = firstNonBlank(stringValue(req.get("tag")), "");
        clan.ownerUuid = firstNonBlank(stringValue(req.get("ownerUuid")), "");
        clan.homeServer = firstNonBlank(stringValue(req.get("homeServer")), "");
        clan.friendlyFire = req.get("friendlyFire") != null && Boolean.parseBoolean(String.valueOf(req.get("friendlyFire")));
        clan.createdAt = longValue(req.get("createdAt"), System.currentTimeMillis());
        clan.wins = numberValue(req.get("wins"), 0);
        clan.kills = numberValue(req.get("kills"), 0);
        clan.points = numberValue(req.get("points"), 0);
        clan.admins = stringList(req.get("admins"));
        clan.moderators = stringList(req.get("moderators"));
        clan.members = stringList(req.get("members"));
        if (clan.name == null || clan.name.isBlank() || clan.ownerUuid == null || clan.ownerUuid.isBlank()) {
            sendResponse(exchange, 400, Map.of("error", "name and ownerUuid required"));
            return;
        }
        if (!clan.members.contains(clan.ownerUuid)) {
            clan.members.add(clan.ownerUuid);
        }
        Master.getInstance().getDataStore().upsertClan(clan);
        CentralLogger.audit("api", "clan_upsert", clan.name + " owner=" + clan.ownerUuid);
        publishApiEvent("CLAN_UPSERT", clan.name);
        sendResponse(exchange, 200, clanState(clan));
    }

    private void handleClanDelete(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        Map<String, Object> req = readJsonBody(exchange);
        String name = firstNonBlank(stringValue(req.get("name")), stringValue(req.get("clanName")));
        boolean deleted = Master.getInstance().getDataStore().deleteClan(name);
        CentralLogger.audit("api", "clan_delete", firstNonBlank(name, "-"));
        sendResponse(exchange, 200, Map.of("deleted", deleted));
    }

    private Map<String, Object> punishmentState(PunishmentData punishment) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", punishment.id);
        map.put("targetUuid", punishment.targetUuid);
        map.put("targetName", punishment.targetName);
        map.put("actorUuid", punishment.actorUuid);
        map.put("actorName", punishment.actorName);
        map.put("type", punishment.type);
        map.put("reason", punishment.reason);
        map.put("proof", punishment.proof);
        map.put("notes", punishment.notes);
        map.put("address", punishment.address);
        map.put("createdAt", punishment.createdAt);
        map.put("expiresAt", punishment.expiresAt);
        map.put("active", punishment.active && !punishment.isExpired(System.currentTimeMillis()));
        return map;
    }

    private Map<String, Object> clanState(ClanData clan) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", clan.name);
        map.put("tag", clan.tag);
        map.put("ownerUuid", clan.ownerUuid);
        map.put("homeServer", clan.homeServer);
        map.put("friendlyFire", clan.friendlyFire);
        map.put("createdAt", clan.createdAt);
        map.put("wins", clan.wins);
        map.put("kills", clan.kills);
        map.put("points", clan.points);
        map.put("admins", clan.admins);
        map.put("moderators", clan.moderators);
        map.put("members", clan.members);
        map.put("memberCount", clan.members == null ? 0 : clan.members.size());
        return map;
    }

    private List<String> stringList(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                String text = stringValue(item);
                if (text != null && !text.isBlank()) {
                    result.add(text);
                }
            }
        }
        return result;
    }

    private Map<String, Object> buildSignCenterPayload(boolean renderLines) {
        return buildSignCenterPayload(renderLines, Set.of());
    }

    private Map<String, Object> buildSignCenterPayload(boolean renderLines, Set<String> selectorTypes) {
        Master master = Master.getInstance();
        if (master == null) {
            return Map.of("signs", List.of(), "layouts", Map.of(), "rendered", renderLines);
        }
        Map<String, Object> snapshot = new LinkedHashMap<>(master.getConfigManager().getSignCenterSnapshot());
        List<Map<String, Object>> enriched = new ArrayList<>();
        List<?> signs = (List<?>) snapshot.getOrDefault("signs", List.of());
        int animationIndex = (int) ((System.currentTimeMillis() / 500L)
                % Math.max(1, master.getConfigManager().getSignAnimationFrames().size()));
        String animation = master.getConfigManager().getSignAnimationFrames().get(animationIndex);
        for (Object rawSign : signs) {
            if (!(rawSign instanceof Map<?, ?> signMap)) {
                continue;
            }
            Map<String, Object> sign = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : signMap.entrySet()) {
                sign.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            String selectorType = stringValue(sign.get("selectorType"), "SIGN").toUpperCase(Locale.ROOT);
            if (selectorTypes != null && !selectorTypes.isEmpty() && !selectorTypes.contains(selectorType)) {
                continue;
            }
            ServerInstance target = resolveSignTarget(master, sign);
            sign.put("target", serverToSignTarget(target, sign));
            Map<String, Object> health = buildSelectorHealth(master, sign, target);
            sign.put("health", health);
            sign.put("actionDecision", health.getOrDefault("action", "CONNECT"));
            if (renderLines) {
                String layout = stringValue(sign.get("layout"), "Default");
                List<String> lines = renderSignLines(master.getConfigManager().getSignLayout(layout), sign, target, animation);
                sign.put("lines", lines);
                List<String> customHologram = stringList(sign.get("hologramLines"));
                sign.put("renderedHologramLines", customHologram.isEmpty()
                        ? lines
                        : renderTemplateLines(customHologram, sign, target, animation, 12, false));
                sign.put("renderedDisplayName", renderDisplayName(sign, target, animation));
            }
            enriched.add(sign);
        }
        snapshot.put("signs", enriched);
        snapshot.put("categories", buildSelectorCategorySummary(enriched));
        snapshot.put("healthSummary", buildSelectorHealthSummary(enriched));
        snapshot.put("rendered", renderLines);
        snapshot.put("generatedAt", System.currentTimeMillis());
        return snapshot;
    }

    private ServerInstance resolveSignTarget(Master master, Map<String, Object> sign) {
        String serverName = stringValue(sign.get("serverName"), "");
        if (!serverName.isBlank()) {
            ServerInstance exact = master.getRunningServers().get(serverName);
            if (exact != null) {
                return exact;
            }
        }
        String groupName = stringValue(sign.get("groupName"), "");
        if (groupName.isBlank()) {
            return null;
        }
        String selected = master.getLoadBalancerManager().getBestServerAllowFull(groupName,
                "selector:" + stringValue(sign.get("id"), groupName));
        if (selected != null) {
            ServerInstance best = master.getRunningServers().get(selected);
            if (best != null) {
                return best;
            }
        }
        return master.getRunningServers().values().stream()
                .filter(server -> groupName.equalsIgnoreCase(server.groupName))
                .sorted(Comparator
                        .comparing((ServerInstance server) -> !server.isOnline())
                        .thenComparing((ServerInstance server) -> !server.isHealthy())
                        .thenComparingDouble(server -> server.maxPlayers <= 0 ? 0.0
                                : (double) effectiveSelectorPlayerCount(master, server) / Math.max(1, server.maxPlayers))
                        .thenComparing((ServerInstance server) -> -server.tps)
                        .thenComparing(server -> server.serverName))
                .findFirst()
                .orElse(null);
    }

    private Map<String, Object> resolveSelectorDecision(Master master, Map<String, Object> selector,
                                                        String playerUuid, String playerName, int priority,
                                                        boolean enqueue, boolean assignSession) {
        Map<String, Object> decision = new LinkedHashMap<>();
        String selectorId = stringValue(selector.get("id"), "");
        String selectorType = stringValue(selector.get("selectorType"), "SIGN").toUpperCase(Locale.ROOT);
        String configuredGroup = stringValue(selector.get("groupName"), "");
        String fallbackGroup = stringValue(selector.get("fallbackGroup"), "");
        String clickAction = stringValue(selector.get("clickAction"), "CONNECT").toUpperCase(Locale.ROOT);
        boolean allowFull = priority >= 50 || booleanValue(selector.get("bypassFull"), false);

        ServerInstance target = resolveSelectorTargetForPlayer(master, selector, playerUuid, allowFull);
        Map<String, Object> health = buildSelectorHealth(master, selector, target);
        String healthAction = stringValue(health.get("action"), "CONNECT").toUpperCase(Locale.ROOT);
        String action = healthAction;
        ServerInstance resolvedTarget = target;
        boolean connect = false;
        boolean queued = false;
        int queuePosition = -1;
        String message = "Selector target resolved.";

        if ("SPAWN".equals(healthAction) || "RESPAWN".equals(healthAction) || "DISABLED".equals(healthAction)) {
            message = "Selector is not ready for player interaction.";
        } else if (isSelectorConnectAction(clickAction) && isSelectorTargetJoinable(master, target, allowFull)) {
            action = "CONNECT";
            connect = true;
            message = "Connect player to selected target.";
        } else {
            ServerInstance fallbackTarget = resolveFallbackSelectorTarget(master, fallbackGroup, playerUuid, allowFull);
            if (fallbackTarget != null) {
                action = "FALLBACK_CONNECT";
                resolvedTarget = fallbackTarget;
                connect = true;
                message = "Primary target unavailable; connect player to fallback target.";
            } else if (enqueue && shouldQueueSelector(selector, clickAction) && !playerUuid.isBlank()) {
                String queueGroup = configuredGroup.isBlank() ? fallbackGroup : configuredGroup;
                if (!queueGroup.isBlank()) {
                    master.getPlayerQueueManager().addToQueue(playerUuid, playerName.isBlank() ? "Player" : playerName, queueGroup, priority);
                    queuePosition = master.getPlayerQueueManager().getQueuePosition(playerUuid, queueGroup);
                    action = "QUEUE";
                    queued = true;
                    message = "Player added to queue.";
                } else {
                    action = "WAIT";
                    message = "No queue group configured for selector.";
                }
            } else {
                action = "WAIT";
                message = "No joinable target available.";
            }
        }

        if (connect && assignSession && !playerUuid.isBlank() && resolvedTarget != null) {
            master.getPlayerSessionManager().assignServer(playerUuid, resolvedTarget.serverName);
        }

        decision.put("selectorId", selectorId);
        decision.put("selectorType", selectorType);
        decision.put("action", action);
        decision.put("connect", connect);
        decision.put("queued", queued);
        decision.put("queuePosition", queuePosition);
        decision.put("message", message);
        decision.put("targetServer", resolvedTarget == null ? "" : resolvedTarget.serverName);
        decision.put("groupName", resolvedTarget == null ? configuredGroup : resolvedTarget.groupName);
        decision.put("target", serverToSignTarget(resolvedTarget, selector));
        decision.put("primaryTarget", serverToSignTarget(target, selector));
        decision.put("health", health);
        decision.put("permission", stringValue(selector.get("permission"), ""));
        decision.put("partyAware", booleanValue(selector.get("partyAware"), true));
        decision.put("fallbackGroup", fallbackGroup);
        decision.put("clickAction", clickAction);
        decision.put("resolvedAt", System.currentTimeMillis());
        return decision;
    }

    private ServerInstance resolveSelectorTargetForPlayer(Master master, Map<String, Object> selector,
                                                          String playerUuid, boolean allowFull) {
        String serverName = stringValue(selector.get("serverName"), "");
        if (!serverName.isBlank()) {
            ServerInstance exact = master.getRunningServers().get(serverName);
            if (exact != null) {
                return exact;
            }
        }
        String groupName = stringValue(selector.get("groupName"), "");
        if (groupName.isBlank()) {
            return null;
        }
        String selected = allowFull
                ? master.getLoadBalancerManager().getBestServerAllowFull(groupName, playerUuid)
                : master.getLoadBalancerManager().getBestServer(groupName, playerUuid);
        return selected == null ? null : master.getRunningServers().get(selected);
    }

    private ServerInstance resolveFallbackSelectorTarget(Master master, String fallbackGroup,
                                                         String playerUuid, boolean allowFull) {
        if (fallbackGroup == null || fallbackGroup.isBlank()) {
            return null;
        }
        String selected = allowFull
                ? master.getLoadBalancerManager().getBestServerAllowFull(fallbackGroup, playerUuid)
                : master.getLoadBalancerManager().getBestServer(fallbackGroup, playerUuid);
        ServerInstance fallback = selected == null ? null : master.getRunningServers().get(selected);
        return isSelectorTargetJoinable(master, fallback, allowFull) ? fallback : null;
    }

    private boolean isSelectorTargetJoinable(Master master, ServerInstance target, boolean allowFull) {
        if (target == null || !target.isOnline() || !target.isHealthy()) {
            return false;
        }
        if (master.getLoadBalancerManager().isWrapperDraining(target.wrapperId)) {
            return false;
        }
        if (master.getConfigManager().isMaintenanceMode(target.groupName)) {
            return false;
        }
        return allowFull || target.maxPlayers <= 0 || effectiveSelectorPlayerCount(master, target) < target.maxPlayers;
    }

    private boolean isSelectorConnectAction(String clickAction) {
        return clickAction == null
                || clickAction.isBlank()
                || "CONNECT".equalsIgnoreCase(clickAction)
                || "QUEUE_OR_CONNECT".equalsIgnoreCase(clickAction)
                || "FALLBACK".equalsIgnoreCase(clickAction);
    }

    private boolean shouldQueueSelector(Map<String, Object> selector, String clickAction) {
        if ("QUEUE".equalsIgnoreCase(clickAction) || "QUEUE_OR_CONNECT".equalsIgnoreCase(clickAction)) {
            return true;
        }
        return booleanValue(selector.get("queueOnFull"), true);
    }

    private Map<String, Object> serverToSignTarget(ServerInstance server, Map<String, Object> sign) {
        return serverToSignTarget(Master.getInstance(), server, sign);
    }

    private Map<String, Object> serverToSignTarget(Master master, ServerInstance server, Map<String, Object> sign) {
        Map<String, Object> target = new LinkedHashMap<>();
        String configuredServer = stringValue(sign.get("serverName"), "");
        String configuredGroup = stringValue(sign.get("groupName"), "");
        int effectivePlayers = effectiveSelectorPlayerCount(master, server);
        target.put("serverName", server == null ? configuredServer : server.serverName);
        target.put("groupName", server == null ? configuredGroup : server.groupName);
        target.put("status", server == null ? "OFFLINE" : server.status);
        target.put("online", server != null && "ONLINE".equalsIgnoreCase(server.status));
        target.put("playersOnline", effectivePlayers);
        target.put("rawPlayersOnline", server == null ? 0 : server.playerCount);
        target.put("maxPlayers", server == null ? 0 : server.maxPlayers);
        target.put("tps", server == null ? 0.0 : server.tps);
        target.put("port", server == null ? -1 : server.port);
        target.put("wrapperId", server == null ? "" : server.wrapperId);
        target.put("lifecycleState", server == null ? "OFFLINE" : server.getLifecycleState().name());
        return target;
    }

    private int effectiveSelectorPlayerCount(Master master, ServerInstance server) {
        if (server == null) {
            return 0;
        }
        if (master == null || master.getLoadBalancerManager() == null) {
            return Math.max(0, server.playerCount);
        }
        return master.getLoadBalancerManager().getEffectivePlayerCount(server);
    }

    private Map<String, Object> renderSelectorPreview(Master master, Map<String, Object> selector) {
        Map<String, Object> sign = new LinkedHashMap<>(selector);
        if (!sign.containsKey("selectorType")) {
            sign.put("selectorType", "SIGN");
        }
        ServerInstance target = resolveSignTarget(master, sign);
        String layout = stringValue(sign.get("layout"), defaultLayoutForSelector(sign));
        String animation = master.getConfigManager().getSignAnimationFrames().stream().findFirst().orElse("");
        List<String> signLines = renderSignLines(master.getConfigManager().getSignLayout(layout), sign, target, animation);
        List<String> customHologram = stringList(sign.get("hologramLines"));
        Map<String, Object> preview = new LinkedHashMap<>(sign);
        preview.put("target", serverToSignTarget(target, sign));
        preview.put("health", buildSelectorHealth(master, sign, target));
        preview.put("lines", signLines);
        preview.put("renderedHologramLines", customHologram.isEmpty()
                ? signLines
                : renderTemplateLines(customHologram, sign, target, animation, 12, false));
        preview.put("renderedDisplayName", renderDisplayName(sign, target, animation));
        return preview;
    }

    private Map<String, Object> buildSelectorHealth(Master master, Map<String, Object> sign, ServerInstance target) {
        Map<String, Object> health = new LinkedHashMap<>();
        boolean enabled = Boolean.parseBoolean(String.valueOf(sign.getOrDefault("enabled", true)));
        String serverName = stringValue(sign.get("serverName"), "");
        String groupName = stringValue(sign.get("groupName"), "");
        String effectiveGroupName = !groupName.isBlank() ? groupName : (target == null ? "" : target.groupName);
        boolean hasGroup = effectiveGroupName.isBlank()
                || master.getConfigManager().getAllServerGroups().stream().anyMatch(effectiveGroupName::equalsIgnoreCase);
        boolean maintenance = !effectiveGroupName.isBlank() && master.getConfigManager().isMaintenanceMode(effectiveGroupName);
        boolean full = target != null && target.maxPlayers > 0 && effectiveSelectorPlayerCount(master, target) >= target.maxPlayers;
        boolean online = target != null && "ONLINE".equalsIgnoreCase(target.status);
        String selectorType = stringValue(sign.get("selectorType"), "SIGN").toUpperCase(Locale.ROOT);
        boolean entitySelector = "NPC".equals(selectorType) || "MOB".equals(selectorType);
        boolean spawned = Boolean.parseBoolean(String.valueOf(sign.getOrDefault("spawned", false)));
        long lastSeenAt = longValue(sign.get("lastSeenAt"), 0L);
        long lastSeenAgeMs = lastSeenAt <= 0 ? -1L : System.currentTimeMillis() - lastSeenAt;
        boolean staleSpawn = entitySelector && spawned && lastSeenAgeMs > SELECTOR_HEARTBEAT_STALE_MS;
        String clickAction = stringValue(sign.get("clickAction"), "CONNECT").toUpperCase(Locale.ROOT);
        String status = "OK";
        String action = clickAction.isBlank() ? "CONNECT" : clickAction;
        List<String> issues = new ArrayList<>();
        if (!enabled) {
            status = "DISABLED";
            action = "DISABLED";
            issues.add("selector disabled");
        } else if ("DISABLED".equals(action)) {
            status = "DISABLED";
            issues.add("click action disabled");
        } else if (!hasGroup) {
            status = "MISSING_GROUP";
            action = "DISABLED";
            issues.add("target group missing");
        } else if (staleSpawn) {
            status = "SPAWN_STALE";
            action = "RESPAWN";
            issues.add("entity selector heartbeat stale");
        } else if (entitySelector && !spawned) {
            status = "NOT_SPAWNED";
            action = "SPAWN";
            issues.add("entity selector not spawned");
        } else if (!serverName.isBlank() && target == null) {
            status = "TARGET_OFFLINE";
            action = stringValue(sign.get("fallbackGroup"), "").isBlank() ? "QUEUE" : "FALLBACK";
            issues.add("target server offline");
        } else if (target == null) {
            status = "NO_TARGET";
            action = "QUEUE";
            issues.add("no live target available");
        } else if (maintenance) {
            status = "MAINTENANCE";
            action = "DISABLED";
            issues.add("target group in maintenance");
        } else if (!online) {
            status = "TARGET_NOT_READY";
            action = "QUEUE";
            issues.add("target not online");
        } else if (full) {
            status = "FULL";
            action = Boolean.parseBoolean(String.valueOf(sign.getOrDefault("queueOnFull", true))) ? "QUEUE" : "FALLBACK";
            issues.add("target full");
        }
        health.put("status", status);
        health.put("action", action);
        health.put("ok", "OK".equals(status));
        health.put("issues", issues);
        health.put("maintenance", maintenance);
        health.put("full", full);
        health.put("permissionRequired", !stringValue(sign.get("permission"), "").isBlank());
        health.put("spawned", spawned);
        health.put("spawnStale", staleSpawn);
        health.put("lastSeenAt", lastSeenAt);
        health.put("lastSeenAgeMs", lastSeenAgeMs);
        health.put("selectorType", selectorType);
        health.put("clickAction", clickAction);
        health.put("fallbackGroup", stringValue(sign.get("fallbackGroup"), ""));
        health.put("partyAware", Boolean.parseBoolean(String.valueOf(sign.getOrDefault("partyAware", true))));
        return health;
    }

    private Map<String, Object> buildSelectorCategorySummary(List<Map<String, Object>> selectors) {
        Map<String, Integer> counts = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Map<String, Object> selector : selectors) {
            String category = stringValue(selector.get("category"), "General");
            counts.put(category, counts.getOrDefault(category, 0) + 1);
        }
        return new LinkedHashMap<>(counts);
    }

    private Map<String, Object> buildSelectorHealthSummary(List<Map<String, Object>> selectors) {
        Map<String, Integer> byStatus = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        int ok = 0;
        int actionable = 0;
        for (Map<String, Object> selector : selectors) {
            Map<?, ?> health = selector.get("health") instanceof Map<?, ?> raw ? raw : Map.of();
            String status = stringValue(health.get("status"), "UNKNOWN");
            byStatus.put(status, byStatus.getOrDefault(status, 0) + 1);
            if ("OK".equalsIgnoreCase(status)) {
                ok++;
            }
            String action = stringValue(health.get("action"), "");
            if (!action.isBlank() && !"DISABLED".equalsIgnoreCase(action)) {
                actionable++;
            }
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", selectors.size());
        summary.put("ok", ok);
        summary.put("issues", Math.max(0, selectors.size() - ok));
        summary.put("actionable", actionable);
        summary.put("byStatus", byStatus);
        return summary;
    }

    private String defaultLayoutForSelector(Map<String, Object> selector) {
        String type = stringValue(selector.get("selectorType"), "SIGN");
        if ("NPC".equalsIgnoreCase(type)) return "Npc";
        if ("MOB".equalsIgnoreCase(type)) return "Mob";
        return "Default";
    }

    private String renderDisplayName(Map<String, Object> sign, ServerInstance target, String animation) {
        String displayName = stringValue(sign.get("displayName"), "");
        if (displayName.isBlank()) {
            String selectorType = stringValue(sign.get("selectorType"), "SIGN");
            String targetName = target == null
                    ? stringValue(sign.get("serverName"), stringValue(sign.get("groupName"), "Selector"))
                    : target.serverName;
            displayName = "SIGN".equalsIgnoreCase(selectorType) ? targetName : "&a" + targetName;
        }
        return renderSignLines(List.of(displayName), sign, target, animation).get(0);
    }

    private List<String> renderSignLines(List<String> templateLines, Map<String, Object> sign, ServerInstance target, String animation) {
        return renderTemplateLines(templateLines, sign, target, animation, 4, true);
    }

    private List<String> renderTemplateLines(List<String> templateLines, Map<String, Object> sign, ServerInstance target,
                                             String animation, int maxLines, boolean padToMax) {
        List<String> lines = new ArrayList<>();
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("{id}", stringValue(sign.get("id"), ""));
        placeholders.put("{world}", stringValue(sign.get("world"), "world"));
        placeholders.put("{selector_type}", stringValue(sign.get("selectorType"), "SIGN"));
        placeholders.put("{entity_type}", stringValue(sign.get("entityType"), ""));
        placeholders.put("{display_name}", stringValue(sign.get("displayName"), ""));
        placeholders.put("{category}", stringValue(sign.get("category"), "General"));
        placeholders.put("{permission}", stringValue(sign.get("permission"), ""));
        placeholders.put("{region}", stringValue(sign.get("region"), "GLOBAL"));
        placeholders.put("{action}", stringValue(sign.get("actionDecision"), "CONNECT"));
        if (sign.get("health") instanceof Map<?, ?> health) {
            placeholders.put("{health}", stringValue(health.get("status"), "UNKNOWN"));
        } else {
            placeholders.put("{health}", "UNKNOWN");
        }
        placeholders.put("{server_name}", target == null ? stringValue(sign.get("serverName"), "") : target.serverName);
        placeholders.put("{server}", placeholders.get("{server_name}"));
        placeholders.put("{group}", target == null ? stringValue(sign.get("groupName"), "") : target.groupName);
        placeholders.put("{status}", target == null ? "OFFLINE" : target.status);
        placeholders.put("{players_online}", String.valueOf(target == null ? 0 : target.playerCount));
        placeholders.put("{max_players}", String.valueOf(target == null ? 0 : target.maxPlayers));
        placeholders.put("{players}", (target == null ? 0 : target.playerCount) + "/" + (target == null ? 0 : target.maxPlayers));
        placeholders.put("{tps}", String.format(Locale.ROOT, "%.1f", target == null ? 0.0 : target.tps));
        placeholders.put("{port}", String.valueOf(target == null ? -1 : target.port));
        placeholders.put("{wrapper}", target == null ? "" : target.wrapperId);
        placeholders.put("{animation}", animation == null ? "" : animation);
        for (String line : templateLines) {
            if (maxLines > 0 && lines.size() >= maxLines) {
                break;
            }
            String rendered = line == null ? "" : line;
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                rendered = rendered.replace(entry.getKey(), entry.getValue());
            }
            lines.add(rendered);
        }
        while (padToMax && lines.size() < maxLines) {
            lines.add("");
        }
        return maxLines > 0 && lines.size() > maxLines ? new ArrayList<>(lines.subList(0, maxLines)) : lines;
    }

    private void publishApiEvent(String type, String message) {
        Master master = Master.getInstance();
        if (master != null && master.getEventTimelineService() != null) {
            master.getEventTimelineService().publish(type, "api", "INFO", message == null ? type : message, Map.of());
        }
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
            Map<String, Object> req = readJsonBody(exchange);
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
            Map<String, Object> req = readJsonBody(exchange);
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
            Map<String, Object> req = readJsonBody(exchange);
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

    private void handlePermissionProfile(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        Map<String, String> query = parseQueryParams(exchange.getRequestURI().getQuery());
        String playerUuid = query.get("playerUuid");
        if (playerUuid == null || playerUuid.isBlank()) {
            sendResponse(exchange, 400, Map.of("error", "playerUuid query required"));
            return;
        }
        PermissionProfile profile = Master.getInstance().buildPermissionProfile(playerUuid);
        List<String> permissions = new ArrayList<>(profile.permissions);
        permissions.sort(String.CASE_INSENSITIVE_ORDER);
        sendResponse(exchange, 200, Map.of(
                "playerUuid", profile.playerUuid,
                "primaryGroup", profile.primaryGroup,
                "prefix", profile.prefix,
                "suffix", profile.suffix,
                "permissions", permissions,
                "permissionCount", permissions.size()
        ));
    }

    private void handlePermissionPlayerAlias(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        String playerUuid = firstNonBlank(
                parseQueryParams(exchange.getRequestURI().getQuery()).get("playerUuid"),
                parseQueryParams(exchange.getRequestURI().getQuery()).get("uuid"),
                trailingPath(exchange, "/api/v1/permissions/player/")
        );
        if (playerUuid == null || playerUuid.isBlank()) {
            sendResponse(exchange, 400, Map.of("error", "playerUuid required"));
            return;
        }
        PermissionProfile profile = Master.getInstance().buildPermissionProfile(playerUuid);
        List<String> permissions = new ArrayList<>(profile.permissions);
        permissions.sort(String.CASE_INSENSITIVE_ORDER);
        sendResponse(exchange, 200, Map.of(
                "playerUuid", profile.playerUuid,
                "primaryGroup", profile.primaryGroup,
                "prefix", profile.prefix,
                "suffix", profile.suffix,
                "permissions", permissions,
                "permissionCount", permissions.size()
        ));
    }

    private void handleLobbyEvent(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        Map<String, Object> req = readJsonBody(exchange);
        String type = firstNonBlank(stringValue(req.get("type")), "LOBBY_EVENT");
        String playerUuid = stringValue(req.get("playerUuid"));
        String playerName = stringValue(req.get("playerName"));
        String serverName = stringValue(req.get("serverName"));
        String groupName = stringValue(req.get("groupName"));
        Map<String, Object> details = new LinkedHashMap<>();
        req.forEach((key, value) -> {
            if (key != null && value != null) {
                details.put(key, value);
            }
        });
        Master master = Master.getInstance();
        if (master != null && master.getEventTimelineService() != null) {
            master.getEventTimelineService().publish("LOBBY_" + type, "lobby-plugin", "INFO",
                    playerName + " " + type + " on " + serverName,
                    details);
        }
        CentralLogger.audit("lobby-plugin", "event_" + type,
                playerUuid + " " + playerName + " server=" + serverName + " group=" + groupName);
        sendResponse(exchange, 200, Map.of("message", "event accepted", "type", type));
    }

    private void handlePermissionCheck(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        Map<String, String> query = parseQueryParams(exchange.getRequestURI().getQuery());
        String playerUuid = query.get("playerUuid");
        String permission = query.get("permission");
        if (playerUuid == null || playerUuid.isBlank() || permission == null || permission.isBlank()) {
            sendResponse(exchange, 400, Map.of("error", "playerUuid and permission query required"));
            return;
        }
        PermissionProfile profile = Master.getInstance().buildPermissionProfile(playerUuid);
        boolean allowed = hasCloudPermission(profile.permissions, permission);
        sendResponse(exchange, 200, Map.of(
                "playerUuid", playerUuid,
                "permission", permission,
                "allowed", allowed,
                "primaryGroup", profile.primaryGroup,
                "prefix", profile.prefix,
                "suffix", profile.suffix
        ));
    }

    private void handleFileList(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        ManagedFileTarget target = resolveManagedFileTarget(parseQueryParams(exchange.getRequestURI().getQuery()));
        if (!Files.exists(target.target())) {
            sendResponse(exchange, 404, Map.of("error", "path not found", "path", target.relativePath()));
            return;
        }
        if (!Files.isDirectory(target.target())) {
            sendResponse(exchange, 400, Map.of("error", "path is not a directory", "path", target.relativePath()));
            return;
        }

        List<Map<String, Object>> entries = new ArrayList<>();
        try (Stream<Path> stream = Files.list(target.target())) {
            stream.sorted(Comparator
                            .comparing((Path p) -> !Files.isDirectory(p))
                            .thenComparing(p -> p.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                    .limit(500)
                    .forEach(path -> entries.add(buildFileEntry(target.root(), path)));
        }

        sendResponse(exchange, 200, Map.of(
                "scope", target.scope(),
                "root", target.rootLabel(),
                "path", target.relativePath(),
                "entries", entries,
                "count", entries.size()
        ));
    }

    private void handleFileRead(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        ManagedFileTarget target = resolveManagedFileTarget(parseQueryParams(exchange.getRequestURI().getQuery()));
        if (!Files.exists(target.target())) {
            sendResponse(exchange, 404, Map.of("error", "file not found", "path", target.relativePath()));
            return;
        }
        if (Files.isDirectory(target.target())) {
            sendResponse(exchange, 400, Map.of("error", "path is a directory", "path", target.relativePath()));
            return;
        }
        long size = Files.size(target.target());
        if (size > MAX_FILE_READ_BYTES) {
            sendResponse(exchange, 413, Map.of(
                    "error", "file too large for dashboard preview",
                    "maxBytes", MAX_FILE_READ_BYTES,
                    "size", size
            ));
            return;
        }
        String content = Files.readString(target.target(), StandardCharsets.UTF_8);
        sendResponse(exchange, 200, Map.of(
                "scope", target.scope(),
                "path", target.relativePath(),
                "size", size,
                "content", content
        ));
    }

    private void handleFileWrite(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        Map<String, Object> request = readJsonBody(exchange);
        ManagedFileTarget target = resolveManagedFileTargetFromBody(request);
        String content = request.get("content") == null ? "" : request.get("content").toString();
        if (content.getBytes(StandardCharsets.UTF_8).length > MAX_FILE_WRITE_BYTES) {
            sendResponse(exchange, 413, Map.of("error", "content too large", "maxBytes", MAX_FILE_WRITE_BYTES));
            return;
        }
        if (Files.exists(target.target()) && Files.isDirectory(target.target())) {
            sendResponse(exchange, 400, Map.of("error", "target is a directory", "path", target.relativePath()));
            return;
        }
        Path parent = target.target().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(target.target(), content, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        CentralLogger.audit("api", "file_write", target.scope() + ":" + target.relativePath());
        sendResponse(exchange, 200, Map.of("message", "file written", "path", target.relativePath(), "size", Files.size(target.target())));
    }

    private void handleFileMkdir(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        ManagedFileTarget target = resolveManagedFileTargetFromBody(readJsonBody(exchange));
        Files.createDirectories(target.target());
        CentralLogger.audit("api", "file_mkdir", target.scope() + ":" + target.relativePath());
        sendResponse(exchange, 200, Map.of("message", "directory created", "path", target.relativePath()));
    }

    private void handleFileDelete(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        ManagedFileTarget target = resolveManagedFileTargetFromBody(readJsonBody(exchange));
        if (target.relativePath().isBlank()) {
            sendResponse(exchange, 400, Map.of("error", "refusing to delete managed root"));
            return;
        }
        if (!Files.exists(target.target())) {
            sendResponse(exchange, 404, Map.of("error", "path not found", "path", target.relativePath()));
            return;
        }
        if (Files.isDirectory(target.target())) {
            try (Stream<Path> children = Files.list(target.target())) {
                if (children.findAny().isPresent()) {
                    sendResponse(exchange, 409, Map.of("error", "directory is not empty", "path", target.relativePath()));
                    return;
                }
            }
        }
        Files.delete(target.target());
        CentralLogger.audit("api", "file_delete", target.scope() + ":" + target.relativePath());
        sendResponse(exchange, 200, Map.of("message", "path deleted", "path", target.relativePath()));
    }

    private void handleLoadBalancerStats(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }

        Master master = Master.getInstance();
        Map<String, Object> stats = new HashMap<>();

        stats.put("serverLoads", master.getLoadBalancerManager().getServerLoads());
        stats.put("routing", master.getLoadBalancerManager().getRoutingDiagnostics());

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
            Map<String, Object> req = readJsonBody(exchange);
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
            Map<String, Object> req = readJsonBody(exchange);
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
            Map<String, Object> req = readJsonBody(exchange);
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
            Map<String, Object> req = readJsonBody(exchange);
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
        sendResponse(exchange, 200, Map.of("lines", readLatestLogLines(300)));
    }

    private void handleLogSearch(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }

        Map<String, String> query = parseQueryParams(exchange.getRequestURI().getQuery());
        String text = query.getOrDefault("query", "").trim();
        String level = query.getOrDefault("level", "all").trim();
        int limit = parseBoundedInt(query.get("limit"), 100, 10, 1000);
        List<String> lines = findLogLines(text, level, limit);

        sendResponse(exchange, 200, Map.of(
                "query", text,
                "level", level,
                "limit", limit,
                "count", lines.size(),
                "lines", lines
        ));
    }

    private void handleAuditRecent(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }

        Map<String, String> query = parseQueryParams(exchange.getRequestURI().getQuery());
        int limit = parseBoundedInt(query.get("limit"), 50, 10, 500);
        List<String> lines = findLogLines("AUDIT", "INFO", limit);
        sendResponse(exchange, 200, Map.of("limit", limit, "count", lines.size(), "lines", lines));
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
            Map<String, Object> request = readJsonBody(exchange);
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
        Master master = Master.getInstance();
        if (master == null) {
            sendResponse(exchange, 503, Map.of("error", "Master not available"));
            return;
        }
        Map<String, Object> report = SetupValidator.buildReport(master.getConfigManager(), false);
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
        Master master = Master.getInstance();
        if (master == null) {
            sendResponse(exchange, 503, Map.of("error", "Master not available"));
            return;
        }
        String value = master.getConfigManager().getMaster(key);
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
            Map<String, Object> req = readJsonBody(exchange);
        String key = req.get("key") == null ? null : req.get("key").toString();
        String value = req.get("value") == null ? null : req.get("value").toString();
        if (key == null || value == null) {
            sendResponse(exchange, 400, Map.of("error", "key and value required"));
            return;
        }
        Master master = Master.getInstance();
        if (master == null) {
            sendResponse(exchange, 503, Map.of("error", "Master not available"));
            return;
        }
        master.getConfigManager().getMasterConfigData().set(key, value);
        master.getConfigManager().getMasterConfigData().save(master.getConfigManager().getMasterConfigFile());
        master.reloadConfiguration("api:config-set");
        sendResponse(exchange, 200, Map.of("message", "config updated", "key", key));
    }

    private void handleWebhookTest(HttpExchange exchange) throws IOException {
        if (!authenticateRequest(exchange)) {
            sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
            return;
        }
        Master master = Master.getInstance();
        if (master == null) {
            sendResponse(exchange, 503, Map.of("error", "Master not available"));
            return;
        }
        master.getMonitoringService().publishEvent(
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
        snapshot.put("doctor", buildSystemDoctorLiveSummary());
        snapshot.put("apiRuntime", buildApiRuntimePayload());
        if (master == null) {
            snapshot.put("runningServers", 0);
            snapshot.put("connectedWrappers", 0);
            snapshot.put("queueTotal", 0);
            snapshot.put("masterAvailable", false);
            snapshot.put("recentEvents", List.of());
            return snapshot;
        }
        snapshot.put("masterAvailable", true);
        snapshot.put("runningServers", master.getRunningServers().size());
        snapshot.put("connectedWrappers", master.getConnectedWrappers().size());
        snapshot.put("queueTotal", master.getPlayerQueueManager().getTotalQueued());
        if (master.getEventTimelineService() != null) {
            snapshot.put("recentEvents", master.getEventTimelineService().recent(20, null, null));
        }
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

    private void handleOpenApiSpec(HttpExchange exchange) throws IOException {
        String spec = """
                openapi: 3.0.3
                info:
                  title: KalliCloud REST API
                  version: 1.0.2
                  description: Complete REST surface for KalliCloud master, dashboard, operations, files, permissions and monitoring.
                servers:
                  - url: /api/v1
                components:
                  securitySchemes:
                    ApiKeyAuth:
                      type: apiKey
                      in: header
                      name: X-API-Key
                  schemas:
                    GenericObject:
                      type: object
                      additionalProperties: true
                    ErrorResponse:
                      type: object
                      properties:
                        requestId: { type: string }
                        status: { type: integer }
                        error:
                          type: object
                          additionalProperties: true
                security:
                  - ApiKeyAuth: []
                paths:
                  /health:
                    get:
                      summary: API health
                      security: []
                      responses:
                        '200': { description: OK }
                  /readiness:
                    get:
                      summary: Cloud readiness
                      security: []
                      responses:
                        '200': { description: Readiness payload }
                  /auth/session:
                    post:
                      summary: Create short-lived dashboard session from API key
                      security: []
                      requestBody:
                        required: true
                        content:
                          application/json:
                            schema:
                              type: object
                              required: [apiKey]
                              properties:
                                apiKey: { type: string }
                      responses:
                        '200': { description: Session token and websocket ticket }
                        '401': { description: Unauthorized }
                  /auth/logout:
                    post:
                      summary: Revoke current dashboard session
                      responses:
                        '200': { description: Logout result }
                  /auth/me:
                    get:
                      summary: Validate current API key and issue dashboard WS ticket
                      responses: { '200': { description: Auth context }, '401': { description: Unauthorized } }
                  /auth/debug:
                    get:
                      summary: Local-only auth diagnostics
                      responses: { '200': { description: Auth debug payload } }
                  /auth/rotate:
                    post:
                      summary: Rotate admin or dashboard API key
                      responses: { '200': { description: Rotated key payload } }
                  /status:
                    get:
                      summary: Master status summary
                      responses: { '200': { description: Status payload } }
                  /dashboard/overview:
                    get:
                      summary: Dashboard overview
                      responses:
                        '200': { description: Overview payload }
                  /system/diagnostics:
                    get:
                      summary: System diagnostics score, issues and recommendations
                      responses: { '200': { description: Diagnostics payload } }
                  /system/doctor:
                    get:
                      summary: Deep configuration doctor for ports, groups, network, monitoring and recovery
                      responses: { '200': { description: System doctor report } }
                  /system/capacity:
                    get:
                      summary: Capacity planner for groups and wrappers
                      responses: { '200': { description: Capacity planner payload } }
                  /system/report:
                    get:
                      summary: Full operations report
                      responses:
                        '200': { description: Report payload }
                  /events/recent:
                    get:
                      summary: Recent event timeline
                      responses:
                        '200': { description: Event list }
                  /signs:
                    get:
                      summary: Central cloud sign registry with layouts, filtered to selectorType SIGN
                      responses: { '200': { description: Sign center snapshot } }
                  /signs/render:
                    get:
                      summary: Render registered signs with live server status placeholders, filtered to selectorType SIGN
                      responses: { '200': { description: Rendered sign payload } }
                  /signs/upsert:
                    post:
                      summary: Create or update a central cloud sign, forcing selectorType SIGN
                      responses: { '200': { description: Sign saved } }
                  /signs/delete:
                    post:
                      summary: Delete a central cloud sign by id
                      responses: { '200': { description: Sign deleted } }
                  /signs/layouts:
                    get:
                      summary: List central sign layouts
                      responses: { '200': { description: Sign layout map } }
                    post:
                      summary: Create or update a central sign layout
                      responses: { '200': { description: Sign layout saved } }
                  /entity-selectors:
                    get:
                      summary: Central NPC and mob selector registry
                      responses: { '200': { description: Entity selector center snapshot } }
                  /entity-selectors/render:
                    get:
                      summary: Render NPC and mob selectors with live server status placeholders
                      responses: { '200': { description: Rendered entity selector payload } }
                  /entity-selectors/upsert:
                    post:
                      summary: Create or update a central NPC or mob selector
                      responses: { '200': { description: Entity selector saved } }
                  /entity-selectors/delete:
                    post:
                      summary: Delete a central NPC or mob selector by id
                      responses: { '200': { description: Entity selector deleted } }
                  /entity-selectors/layouts:
                    get:
                      summary: List central entity selector layouts
                      responses: { '200': { description: Entity selector layout map } }
                    post:
                      summary: Create or update a central entity selector layout
                      responses: { '200': { description: Entity selector layout saved } }
                  /selectors:
                    get:
                      summary: Central selector registry for signs, NPCs and mobs
                      responses: { '200': { description: Selector center snapshot } }
                  /selectors/render:
                    get:
                      summary: Render signs, NPCs and mobs with live server status placeholders
                      responses: { '200': { description: Rendered selector payload } }
                  /selectors/upsert:
                    post:
                      summary: Create or update a central selector
                      responses: { '200': { description: Selector saved } }
                  /selectors/delete:
                    post:
                      summary: Delete a central selector by id
                      responses: { '200': { description: Selector deleted } }
                  /selectors/layouts:
                    get:
                      summary: List central selector layouts
                      responses: { '200': { description: Selector layout map } }
                    post:
                      summary: Create or update a central selector layout
                      responses: { '200': { description: Selector layout saved } }
                  /selectors/templates:
                    get:
                      summary: List selector presets for signs, NPCs and mobs
                      responses: { '200': { description: Selector template map } }
                  /selectors/preview:
                    get:
                      summary: Render preview for one saved selector by id
                      parameters:
                        - in: query
                          name: id
                          schema: { type: string }
                      responses: { '200': { description: Selector preview payload } }
                    post:
                      summary: Render preview for a selector draft
                      responses: { '200': { description: Selector preview payload } }
                  /selectors/bulk:
                    post:
                      summary: Bulk update selector state, layout or permissions
                      responses: { '200': { description: Bulk selector result } }
                  /selectors/cleanup:
                    post:
                      summary: Disable or delete stale selectors whose target no longer exists
                      responses: { '200': { description: Selector cleanup result } }
                  /selectors/versions:
                    get:
                      summary: List selector registry versions
                      responses: { '200': { description: Selector version list } }
                  /selectors/rollback:
                    post:
                      summary: Roll back selector registry to a previous version file
                      responses: { '200': { description: Selector rollback result } }
                  /selectors/heartbeat:
                    post:
                      summary: Report spawned selector health/location from a Lobby or Spigot plugin
                      responses: { '200': { description: Selector heartbeat result } }
                  /selectors/resolve:
                    post:
                      summary: Resolve a Sign/NPC/Mob selector click to connect, fallback, queue or spawn action
                      requestBody:
                        required: true
                        content:
                          application/json:
                            schema:
                              type: object
                              required: [id]
                              properties:
                                id: { type: string, example: lobby-selector-1 }
                                playerUuid: { type: string, example: 00000000-0000-0000-0000-000000000000 }
                                playerName: { type: string, example: kallifabio }
                                priority: { type: integer, example: 0 }
                                enqueue: { type: boolean, example: true }
                                assignSession: { type: boolean, example: false }
                      responses: { '200': { description: Selector resolve decision } }
                  /bungeesystem:
                    get:
                      summary: BungeeSystem proxy-layer overview with proxies, punishments, clans and live events
                      responses:
                        '200': { description: BungeeSystem dashboard payload }
                  /lifecycle:
                    get:
                      summary: Server lifecycle transitions
                      responses:
                        '200': { description: Lifecycle state }
                  /recovery/state:
                    get:
                      summary: Recovery, restart-lock and crash-quarantine state
                      responses:
                        '200': { description: Recovery state payload }
                  /recovery/unquarantine:
                    post:
                      summary: Clear crash quarantine and failure counters for one server
                      requestBody:
                        required: true
                        content:
                          application/json:
                            schema:
                              type: object
                              required: [serverName]
                              properties:
                                serverName: { type: string, example: Lobby-1 }
                      responses:
                        '200': { description: Quarantine cleared payload }
                  /incidents:
                    get:
                      summary: List generated incident reports
                      responses: { '200': { description: Incident list } }
                  /backups:
                    get:
                      summary: List backups
                      responses:
                        '200': { description: Backup list }
                  /backups/create:
                    post:
                      summary: Create backup
                      responses:
                        '200': { description: Backup created }
                  /backups/restore-staging:
                    post:
                      summary: Restore backup into staging folder
                      responses: { '200': { description: Restore staging payload } }
                  /rolling/restart:
                    post:
                      summary: Schedule rolling restart for group or explicit targets
                      responses: { '202': { description: Rolling restart scheduled } }
                  /firewall/check:
                    get:
                      summary: TCP reachability checks for proxy/backend/API ports
                      responses: { '200': { description: Firewall check result } }
                  /motd:
                    get:
                      summary: Network MOTD and slot display configuration
                      responses: { '200': { description: MOTD payload } }
                  /motd/update:
                    post:
                      summary: Update network MOTD and optional fake slot display
                      requestBody:
                        required: true
                        content:
                          application/json:
                            schema:
                              type: object
                              properties:
                                enabled: { type: boolean }
                                line1: { type: string }
                                line2: { type: string }
                                maintenanceLine1: { type: string }
                                maintenanceLine2: { type: string }
                                fakeSlotsEnabled: { type: boolean }
                                fakeSlotsOnline: { type: integer }
                                fakeSlotsMax: { type: integer }
                      responses: { '200': { description: Updated MOTD payload } }
                  /cluster/info:
                    get:
                      summary: Cluster primary and node summary
                      responses: { '200': { description: Cluster info } }
                  /cluster/nodes:
                    get:
                      summary: Cluster nodes
                      responses: { '200': { description: Cluster node list } }
                  /punishments:
                    get:
                      summary: List punishments, optionally filtered by targetUuid and activeOnly
                      parameters:
                        - in: query
                          name: targetUuid
                          schema: { type: string }
                        - in: query
                          name: activeOnly
                          schema: { type: boolean }
                      responses: { '200': { description: Punishment list } }
                  /punishments/upsert:
                    post:
                      summary: Create or update a punishment from proxy/plugin clients
                      requestBody:
                        required: true
                        content:
                          application/json:
                            schema: { $ref: '#/components/schemas/GenericObject' }
                      responses: { '200': { description: Stored punishment } }
                  /punishments/pardon:
                    post:
                      summary: Deactivate a punishment by id or by target/type filter
                      requestBody:
                        required: true
                        content:
                          application/json:
                            schema: { $ref: '#/components/schemas/GenericObject' }
                      responses: { '200': { description: Pardon result } }
                  /clans:
                    get:
                      summary: List clans or fetch one clan by name
                      parameters:
                        - in: query
                          name: name
                          schema: { type: string }
                      responses: { '200': { description: Clan payload } }
                  /clans/upsert:
                    post:
                      summary: Create or update a clan including members, roles and stats
                      requestBody:
                        required: true
                        content:
                          application/json:
                            schema: { $ref: '#/components/schemas/GenericObject' }
                      responses: { '200': { description: Stored clan } }
                  /clans/delete:
                    post:
                      summary: Delete a clan by name
                      requestBody:
                        required: true
                        content:
                          application/json:
                            schema: { $ref: '#/components/schemas/GenericObject' }
                      responses: { '200': { description: Delete result } }
                  /servers:
                    get:
                      summary: List running servers
                      responses: { '200': { description: Server list } }
                  /servers/start:
                    post:
                      summary: Start a server
                      requestBody:
                        required: true
                        content:
                          application/json:
                            schema:
                              type: object
                              required: [serverName, groupName]
                              properties:
                                serverName: { type: string }
                                groupName: { type: string }
                      responses: { '200': { description: Start initiated } }
                  /servers/stop:
                    post:
                      summary: Stop a server
                      responses: { '200': { description: Stop initiated } }
                  /servers/restart:
                    post:
                      summary: Restart a server
                      responses: { '200': { description: Restart initiated } }
                  /wrappers:
                    get:
                      summary: List connected wrappers
                      responses: { '200': { description: Wrapper list } }
                  /wrappers/drain:
                    post:
                      summary: Toggle wrapper drain state
                      responses: { '200': { description: Drain state updated } }
                  /metrics:
                    get:
                      summary: Current cloud metrics
                      responses: { '200': { description: Metrics snapshot } }
                  /metrics/history:
                    get:
                      summary: Recent metrics history
                      responses: { '200': { description: Metrics history } }
                  /metrics/prometheus:
                    get:
                      summary: Prometheus text exposition
                      responses: { '200': { description: Prometheus metrics } }
                  /alerts:
                    get:
                      summary: Active monitoring alerts
                      responses: { '200': { description: Alert list } }
                  /alerts/clear:
                    post:
                      summary: Clear active monitoring alerts
                      responses: { '200': { description: Alerts cleared } }
                  /scaling/policies:
                    get:
                      summary: List auto-scaling policies
                      responses: { '200': { description: Scaling policies } }
                  /scaling/trigger:
                    post:
                      summary: Trigger manual scaling evaluation
                      responses: { '200': { description: Scaling triggered } }
                  /queue/status:
                    get:
                      summary: Queue sizes and total queued players
                      responses: { '200': { description: Queue status } }
                  /player/data:
                    get:
                      summary: Read player data by uuid query
                      responses: { '200': { description: Player data } }
                    post:
                      summary: Save player data
                      responses: { '200': { description: Player data saved } }
                  /player/friends:
                    get:
                      summary: Read friend list by uuid query
                      responses: { '200': { description: Friend list } }
                    post:
                      summary: Save friend list
                      responses: { '200': { description: Friends saved } }
                  /player/party:
                    get:
                      summary: Read party members by partyId query
                      responses: { '200': { description: Party members } }
                    post:
                      summary: Save party
                      responses: { '200': { description: Party saved } }
                  /party/create:
                    post:
                      summary: Create or return a player's Cloud party
                      responses: { '200': { description: Party state } }
                  /party/invite:
                    post:
                      summary: Create a persistent party invite with timeout
                      responses: { '200': { description: Party invite created } }
                  /party/accept:
                    post:
                      summary: Consume a pending party invite and add the player
                      responses: { '200': { description: Party invite accepted } }
                  /party/kick:
                    post:
                      summary: Remove a party member with optional leader validation
                      responses: { '200': { description: Party member removed } }
                  /party/leave:
                    post:
                      summary: Leave a party and transfer leadership when required
                      responses: { '200': { description: Party left } }
                  /party/switch:
                    post:
                      summary: Switch all party members to target server
                      responses: { '200': { description: Party switch sent } }
                  /permissions/group:
                    post:
                      summary: Create or update cloud permission group
                      responses: { '200': { description: Permission group upserted } }
                  /permissions/assign:
                    post:
                      summary: Assign player to permission group
                      responses: { '200': { description: Permission group assigned } }
                  /permissions/temp:
                    post:
                      summary: Grant temporary permission
                      responses: { '200': { description: Temporary permission set } }
                  /permissions/profile:
                    get:
                      summary: Build effective cloud permission profile
                      parameters:
                        - in: query
                          name: playerUuid
                          schema: { type: string }
                          required: true
                      responses: { '200': { description: Effective profile } }
                  /permissions/check:
                    get:
                      summary: Check one permission against cloud profile
                      parameters:
                        - in: query
                          name: playerUuid
                          schema: { type: string }
                          required: true
                        - in: query
                          name: permission
                          schema: { type: string }
                          required: true
                      responses: { '200': { description: Permission decision } }
                  /files/list:
                    get:
                      summary: List managed files under server/template/wrapper scope
                      responses: { '200': { description: File entries } }
                  /files/read:
                    get:
                      summary: Read a managed UTF-8 text file
                      responses: { '200': { description: File content }, '413': { description: File too large } }
                  /files/write:
                    post:
                      summary: Write a managed UTF-8 text file
                      responses: { '200': { description: File written } }
                  /files/mkdir:
                    post:
                      summary: Create managed directory
                      responses: { '200': { description: Directory created } }
                  /files/delete:
                    post:
                      summary: Delete managed file or empty directory
                      responses: { '200': { description: Path deleted } }
                  /loadbalancer/stats:
                    get:
                      summary: Load balancer statistics with routing diagnostics and rejection reasons
                      responses: { '200': { description: Load balancer stats and candidate diagnostics } }
                  /groups:
                    get:
                      summary: List server groups
                      responses: { '200': { description: Group list } }
                  /groups/create:
                    post:
                      summary: Create server group
                      responses: { '200': { description: Group created } }
                  /groups/delete:
                    post:
                      summary: Delete server group
                      responses: { '200': { description: Group deleted } }
                  /groups/update:
                    post:
                      summary: Update server group setting
                      responses: { '200': { description: Group updated } }
                  /templates/diff:
                    get:
                      summary: Diff template against backup
                      responses: { '200': { description: Template diff } }
                  /templates/rollback:
                    post:
                      summary: Roll back template version
                      responses: { '200': { description: Template rolled back } }
                  /templates/versions:
                    get:
                      summary: List template versions
                      responses: { '200': { description: Template versions } }
                  /logs/recent:
                    get:
                      summary: Recent central log lines
                      responses: { '200': { description: Recent logs } }
                  /logs/search:
                    get:
                      summary: Search central logs
                      responses:
                        '200': { description: Log search result }
                  /audit/recent:
                    get:
                      summary: Recent audit lines
                      responses: { '200': { description: Audit lines } }
                  /console/screens:
                    get:
                      summary: List console screens
                      responses: { '200': { description: Console screen list } }
                  /console/tail:
                    get:
                      summary: Tail one server console screen
                      responses: { '200': { description: Console tail } }
                  /console/send:
                    post:
                      summary: Send command to server console
                      responses: { '200': { description: Command sent } }
                  /setup/report:
                    get:
                      summary: Setup validation report
                      responses: { '200': { description: Setup report } }
                  /config/get:
                    get:
                      summary: Read config value
                      responses: { '200': { description: Config value } }
                  /config/set:
                    post:
                      summary: Update config value
                      responses: { '200': { description: Config updated } }
                  /webhook/test:
                    post:
                      summary: Send alert webhook test
                      responses: { '200': { description: Webhook test sent } }
                """;
        byte[] payload = spec.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/yaml; charset=utf-8");
        exchange.sendResponseHeaders(200, payload.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(payload);
        }
    }

    private boolean authenticateRequest(HttpExchange exchange) {
        if (!checkRateLimit(exchange)) {
            exchange.setAttribute("authFailureStatus", 429);
            exchange.setAttribute("authFailureCode", "RATE_LIMITED");
            exchange.setAttribute("authFailureMessage", "Rate limit exceeded. Try again later.");
            return false;
        }
        String apiKey = readApiKey(exchange);
        if (apiKey == null || !isKnownApiKey(apiKey)) {
            exchange.setAttribute("authFailureStatus", 401);
            exchange.setAttribute("authFailureCode", "UNAUTHORIZED");
            exchange.setAttribute("authFailureMessage", "Missing or invalid API key.");
            return false;
        }

        String role = apiKeyRoles.getOrDefault(apiKey, "VIEWER");
        String requiredRole = requiredRoleFor(exchange);
        boolean allowed = hasRequiredRole(role, requiredRole);
        if (!allowed) {
            exchange.setAttribute("authFailureStatus", 403);
            exchange.setAttribute("authFailureCode", "FORBIDDEN");
            exchange.setAttribute("authFailureMessage", "Role " + role + " is not allowed for this request.");
        }
        return allowed;
    }

    private boolean authenticateRequest(HttpExchange exchange, String requiredRole) {
        if (!checkRateLimit(exchange)) {
            exchange.setAttribute("authFailureStatus", 429);
            exchange.setAttribute("authFailureCode", "RATE_LIMITED");
            exchange.setAttribute("authFailureMessage", "Rate limit exceeded. Try again later.");
            return false;
        }
        String apiKey = readApiKey(exchange);
        if (apiKey == null || !isKnownApiKey(apiKey)) {
            exchange.setAttribute("authFailureStatus", 401);
            exchange.setAttribute("authFailureCode", "UNAUTHORIZED");
            exchange.setAttribute("authFailureMessage", "Missing or invalid API key.");
            return false;
        }
        String role = apiKeyRoles.getOrDefault(apiKey, "VIEWER");
        boolean allowed = hasRequiredRole(role, requiredRole == null || requiredRole.isBlank() ? "VIEWER" : requiredRole);
        if (!allowed) {
            exchange.setAttribute("authFailureStatus", 403);
            exchange.setAttribute("authFailureCode", "FORBIDDEN");
            exchange.setAttribute("authFailureMessage", "Role " + role + " is not allowed for this request.");
        }
        return allowed;
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
        if (sanitized == null) {
            return false;
        }
        DashboardSession session = dashboardSessions.get(sanitized);
        if (session != null) {
            if (session.expiresAt < System.currentTimeMillis()) {
                dashboardSessions.remove(sanitized);
                apiKeyRoles.remove(sanitized);
                return false;
            }
            return true;
        }
        return apiKeyRoles.containsKey(sanitized);
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

    private String issueDashboardSession(String role, String clientIp) {
        cleanupDashboardSessions();
        String token = "kcs_" + UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        DashboardSession session = new DashboardSession(role == null ? "VIEWER" : role, clientIp,
                System.currentTimeMillis() + DASHBOARD_SESSION_TTL_MS);
        dashboardSessions.put(token, session);
        apiKeyRoles.put(token, session.role);
        return token;
    }

    private void cleanupDashboardSessions() {
        long now = System.currentTimeMillis();
        dashboardSessions.entrySet().removeIf(entry -> {
            boolean expired = entry.getValue().expiresAt < now;
            if (expired) {
                apiKeyRoles.remove(entry.getKey());
            }
            return expired;
        });
    }

    private long sessionExpiresAt(String token) {
        DashboardSession session = token == null ? null : dashboardSessions.get(token);
        return session == null ? 0L : session.expiresAt;
    }

    private String readBearerToken(HttpExchange exchange) {
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        return sanitizeApiKey(authorization.substring(7));
    }

    private String buildWsUrl(HttpExchange exchange) {
        String hostHeader = exchange.getRequestHeaders().getFirst("Host");
        String host = (hostHeader != null && !hostHeader.isBlank())
                ? hostHeader.split(":")[0]
                : (exchange.getLocalAddress() != null ? exchange.getLocalAddress().getHostString() : "localhost");
        String wsScheme = tlsEnabled ? "wss" : "ws";
        return wsScheme + "://" + host + ":" + wsPort + "/live";
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
        if ("owner".equalsIgnoreCase(key)) {
            return apiKeys.get("owner");
        }
        if ("operator".equalsIgnoreCase(key) || "ops".equalsIgnoreCase(key)) {
            return apiKeys.get("operator");
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

    private String requiredRoleFor(HttpExchange exchange) {
        if (!isMutatingRequest(exchange)) {
            return "VIEWER";
        }
        String path = exchange.getRequestURI().getPath().toLowerCase(Locale.ROOT);
        if (path.contains("/servers/")
                || path.contains("/wrappers/drain")
                || path.contains("/alerts/clear")
                || path.contains("/webhook/test")
                || path.contains("/console/send")
                || path.contains("/rolling/restart")
                || path.contains("/backups/create")) {
            return "OPERATOR";
        }
        return "ADMIN";
    }

    private boolean hasRequiredRole(String actualRole, String requiredRole) {
        int actual = roleLevel(actualRole);
        int required = roleLevel(requiredRole);
        return actual >= required;
    }

    private int roleLevel(String role) {
        if ("OWNER".equalsIgnoreCase(role)) {
            return 4;
        }
        if ("ADMIN".equalsIgnoreCase(role)) {
            return 3;
        }
        if ("OPERATOR".equalsIgnoreCase(role)) {
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

    private int parseBoundedInt(String raw, int fallback, int min, int max) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(raw.trim())));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String stringValue(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? fallback : text;
    }

    private boolean booleanValue(Object value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private boolean hasCloudPermission(Set<String> grantedPermissions, String requestedPermission) {
        if (grantedPermissions == null || requestedPermission == null || requestedPermission.isBlank()) {
            return false;
        }
        String requested = requestedPermission.trim().toLowerCase(Locale.ROOT);
        if (grantedPermissions.contains("*")) {
            return true;
        }
        for (String grantedRaw : grantedPermissions) {
            if (grantedRaw == null || grantedRaw.isBlank()) {
                continue;
            }
            String granted = grantedRaw.trim().toLowerCase(Locale.ROOT);
            if (granted.equals(requested)) {
                return true;
            }
            if (granted.endsWith(".*")) {
                String prefix = granted.substring(0, granted.length() - 1);
                if (requested.startsWith(prefix)) {
                    return true;
                }
            }
        }
        return false;
    }

    private ManagedFileTarget resolveManagedFileTargetFromBody(Map<String, Object> request) {
        Map<String, String> params = new HashMap<>();
        for (Map.Entry<String, Object> entry : request.entrySet()) {
            params.put(entry.getKey(), entry.getValue() == null ? "" : String.valueOf(entry.getValue()));
        }
        return resolveManagedFileTarget(params);
    }

    private ManagedFileTarget resolveManagedFileTarget(Map<String, String> params) {
        String scope = stringValue(params.get("scope"), "server").toLowerCase(Locale.ROOT);
        String serverName = stringValue(params.get("serverName"), "");
        String groupName = stringValue(params.get("groupName"), "");
        String wrapperId = stringValue(params.get("wrapperId"), "");
        String rawPath = stringValue(params.get("path"), "");

        Path root;
        String rootLabel;
        if ("template".equals(scope)) {
            if (groupName.isBlank()) {
                throw new IllegalArgumentException("groupName required for template file scope");
            }
            root = Path.of("templates", groupName);
            rootLabel = "templates/" + groupName;
        } else if ("wrapper".equals(scope)) {
            if (wrapperId.isBlank()) {
                throw new IllegalArgumentException("wrapperId required for wrapper file scope");
            }
            root = Path.of("servers");
            rootLabel = "servers (wrapper " + wrapperId + ")";
        } else {
            scope = "server";
            if (serverName.isBlank()) {
                throw new IllegalArgumentException("serverName required for server file scope");
            }
            ServerInstance server = Master.getInstance().getRunningServers().get(serverName);
            if (server != null && server.groupName != null && !server.groupName.isBlank()) {
                groupName = server.groupName;
            }
            if (groupName.isBlank()) {
                throw new IllegalArgumentException("groupName required when server is not running");
            }
            root = Path.of("servers", groupName, serverName);
            rootLabel = "servers/" + groupName + "/" + serverName;
        }

        Path rootAbs = root.toAbsolutePath().normalize();
        String relative = sanitizeRelativePath(rawPath);
        Path target = relative.isBlank() ? rootAbs : rootAbs.resolve(relative).normalize();
        if (!target.startsWith(rootAbs)) {
            throw new SecurityException("Path traversal blocked: " + rawPath);
        }
        String display = rootAbs.equals(target) ? "" : rootAbs.relativize(target).toString().replace('\\', '/');
        return new ManagedFileTarget(scope, rootLabel, rootAbs, target, display);
    }

    private String sanitizeRelativePath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return "";
        }
        String normalized = rawPath.replace('\\', '/').trim();
        if (normalized.indexOf('\0') >= 0) {
            throw new SecurityException("Invalid path");
        }
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private Map<String, Object> buildFileEntry(Path root, Path path) {
        Map<String, Object> entry = new LinkedHashMap<>();
        boolean directory = Files.isDirectory(path);
        entry.put("name", path.getFileName() == null ? "" : path.getFileName().toString());
        entry.put("path", root.relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/'));
        entry.put("directory", directory);
        try {
            entry.put("size", directory ? 0L : Files.size(path));
        } catch (IOException ignored) {
            entry.put("size", 0L);
        }
        try {
            entry.put("modifiedAt", Files.getLastModifiedTime(path).toMillis());
        } catch (IOException ignored) {
            entry.put("modifiedAt", 0L);
        }
        return entry;
    }

    private record ManagedFileTarget(String scope, String rootLabel, Path root, Path target, String relativePath) {
    }

    private Map<String, Object> checkTcp(String name, String host, int port) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", name);
        result.put("host", host);
        result.put("port", port);
        long start = System.nanoTime();
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new InetSocketAddress(host, port), 1_500);
            result.put("reachable", true);
        } catch (Exception e) {
            result.put("reachable", false);
            result.put("error", e.getMessage());
        }
        result.put("durationMs", (System.nanoTime() - start) / 1_000_000);
        return result;
    }

    private List<String> readLatestLogLines(int limit) {
        Path today = Path.of("logs", "cloud-" + java.time.LocalDate.now() + ".log");
        if (!Files.exists(today)) {
            return List.of();
        }
        try {
            List<String> lines = Files.readAllLines(today);
            int size = lines.size();
            int from = Math.max(0, size - Math.max(1, limit));
            return new ArrayList<>(lines.subList(from, size));
        } catch (IOException e) {
            CentralLogger.warn("API", "Unable to read recent logs: " + e.getMessage());
            return List.of();
        }
    }

    private List<String> findLogLines(String text, String level, int limit) {
        String normalizedText = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        String normalizedLevel = level == null ? "all" : level.trim().toUpperCase(Locale.ROOT);
        List<String> source = readLatestLogLines(5_000);
        List<String> matches = new ArrayList<>();

        for (int i = source.size() - 1; i >= 0 && matches.size() < limit; i--) {
            String line = source.get(i);
            String upper = line.toUpperCase(Locale.ROOT);
            if (!"ALL".equals(normalizedLevel) && !normalizedLevel.isBlank()
                    && !upper.contains("[" + normalizedLevel + "]")
                    && !upper.contains(normalizedLevel + ":")) {
                continue;
            }
            if (!normalizedText.isBlank() && !line.toLowerCase(Locale.ROOT).contains(normalizedText)) {
                continue;
            }
            matches.add(line);
        }

        Collections.reverse(matches);
        return matches;
    }

    private Map<String, Object> buildLogStats(List<String> lines) {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("sampleSize", lines.size());
        stats.put("info", countLogLevel(lines, "INFO"));
        stats.put("warn", countLogLevel(lines, "WARN"));
        stats.put("error", countLogLevel(lines, "ERROR"));
        stats.put("debug", countLogLevel(lines, "DEBUG"));
        stats.put("audit", lines.stream().filter(line -> line.contains("[AUDIT]")).count());
        stats.put("criticalMentions", lines.stream()
                .filter(line -> line.toLowerCase(Locale.ROOT).contains("critical"))
                .count());
        return stats;
    }

    private long countLogLevel(List<String> lines, String level) {
        String token = "[" + level + "]";
        return lines.stream().filter(line -> line.contains(token)).count();
    }

    private String trailingPath(HttpExchange exchange, String prefix) {
        String path = exchange.getRequestURI().getPath();
        if (path == null || prefix == null || !path.startsWith(prefix)) {
            return "";
        }
        String value = path.substring(prefix.length());
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private int numberValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private double doubleValue(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value == null ? fallback : Double.parseDouble(String.valueOf(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private long longValue(Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return value == null ? fallback : Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private int settingInt(PlayerData data, String key, int fallback) {
        return data == null || data.stats == null ? fallback : data.stats.getOrDefault(key, fallback);
    }

    private int visibilityToInt(String visibility) {
        if ("FRIENDS".equalsIgnoreCase(visibility)) {
            return 1;
        }
        if ("NONE".equalsIgnoreCase(visibility)) {
            return 2;
        }
        return 0;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readJsonBody(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).trim();
        if (body.isBlank()) {
            return new LinkedHashMap<>();
        }
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(body);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON body: " + e.getMessage(), e);
        }
        if (parsed == null || !parsed.isJsonObject()) {
            throw new IllegalArgumentException("JSON body must be an object.");
        }
        Map<String, Object> map = gson.fromJson(parsed, Map.class);
        return map == null ? new LinkedHashMap<>() : map;
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
        int effectiveStatusCode = resolveEffectiveStatusCode(exchange, statusCode);
        Object payloadData = effectiveStatusCode >= 400 ? normalizeErrorPayload(exchange, effectiveStatusCode, data) : data;
        String json = gson.toJson(payloadData);
        byte[] payload = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("X-Request-Id", resolveRequestId(exchange));
        applyCorsHeaders(exchange);
        exchange.sendResponseHeaders(effectiveStatusCode, payload.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(payload);
        }
    }

    private int resolveEffectiveStatusCode(HttpExchange exchange, int statusCode) {
        if (statusCode != 401) {
            return statusCode;
        }
        Object value = exchange.getAttribute("authFailureStatus");
        return value instanceof Number ? ((Number) value).intValue() : statusCode;
    }

    private void sendError(HttpExchange exchange, int statusCode, String code, String message,
                           Map<String, Object> details, Throwable throwable) throws IOException {
        if (throwable != null && statusCode >= 500) {
            CentralLogger.error("API", code + " " + exchange.getRequestMethod() + " "
                    + exchange.getRequestURI().getPath() + " requestId=" + resolveRequestId(exchange), throwable);
        } else if (throwable != null) {
            CentralLogger.warn("API", code + " " + exchange.getRequestMethod() + " "
                    + exchange.getRequestURI().getPath() + " requestId=" + resolveRequestId(exchange)
                    + " message=" + safeErrorMessage(throwable));
        }
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("code", code);
        raw.put("error", message);
        if (details != null && !details.isEmpty()) {
            raw.put("details", details);
        }
        sendResponse(exchange, statusCode, raw);
    }

    private void sendOperationException(HttpExchange exchange, Exception e) throws IOException {
        if (e instanceof IllegalArgumentException) {
            sendError(exchange, 400, "BAD_REQUEST", safeErrorMessage(e), null, e);
            return;
        }
        sendError(exchange, 500, "OPERATION_FAILED", safeErrorMessage(e), null, e);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeErrorPayload(HttpExchange exchange, int statusCode, Object data) {
        String code = defaultErrorCode(statusCode);
        String message = "Request failed";
        Map<String, Object> details = new LinkedHashMap<>();

        if (data instanceof Map<?, ?> rawMap) {
            Map<Object, Object> map = (Map<Object, Object>) rawMap;
            Object explicitCode = map.get("code");
            Object explicitMessage = map.get("error");
            if (explicitCode != null) {
                code = String.valueOf(explicitCode);
            }
            if (explicitMessage != null) {
                message = String.valueOf(explicitMessage);
            }
            for (Map.Entry<Object, Object> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                if (!"code".equals(key) && !"error".equals(key)) {
                    details.put(key, entry.getValue());
                }
            }
        } else if (data != null) {
            message = String.valueOf(data);
        }

        Object authCode = exchange.getAttribute("authFailureCode");
        Object authMessage = exchange.getAttribute("authFailureMessage");
        if (authCode != null) {
            code = String.valueOf(authCode);
        }
        if (authMessage != null) {
            message = String.valueOf(authMessage);
        }

        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        error.put("status", statusCode);
        if (!details.isEmpty()) {
            error.put("details", details);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", false);
        payload.put("requestId", resolveRequestId(exchange));
        payload.put("timestamp", System.currentTimeMillis());
        payload.put("path", exchange.getRequestURI().getPath());
        payload.put("error", error);
        return payload;
    }

    private String defaultErrorCode(int statusCode) {
        return switch (statusCode) {
            case 400 -> "BAD_REQUEST";
            case 401 -> "UNAUTHORIZED";
            case 403 -> "FORBIDDEN";
            case 404 -> "NOT_FOUND";
            case 405 -> "METHOD_NOT_ALLOWED";
            case 409 -> "CONFLICT";
            case 429 -> "RATE_LIMITED";
            default -> statusCode >= 500 ? "INTERNAL_ERROR" : "REQUEST_FAILED";
        };
    }

    private String resolveRequestId(HttpExchange exchange) {
        Object existing = exchange.getAttribute("requestId");
        if (existing != null) {
            return String.valueOf(existing);
        }
        String header = exchange.getRequestHeaders().getFirst("X-Request-Id");
        String requestId = header == null || header.isBlank()
                ? UUID.randomUUID().toString().replace("-", "")
                : header.trim();
        exchange.setAttribute("requestId", requestId);
        return requestId;
    }

    private String safeErrorMessage(Throwable throwable) {
        if (throwable == null || throwable.getMessage() == null || throwable.getMessage().isBlank()) {
            return "Unexpected error";
        }
        return throwable.getMessage();
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
        if (httpExecutor != null) {
            httpExecutor.shutdownNow();
            httpExecutor = null;
        }
    }

    public int getPort() {
        return port;
    }

    public int getWsPort() {
        return wsPort;
    }

    private static final class DashboardSession {
        private final String role;
        private final String clientIp;
        private final long expiresAt;

        private DashboardSession(String role, String clientIp, long expiresAt) {
            this.role = role;
            this.clientIp = clientIp;
            this.expiresAt = expiresAt;
        }
    }
}









