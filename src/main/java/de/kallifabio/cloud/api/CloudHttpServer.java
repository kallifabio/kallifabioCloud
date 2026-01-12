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
import de.kallifabio.cloud.libs.console.ConsoleColors;
import de.kallifabio.cloud.libs.console.ConsoleScreenManager;
import de.kallifabio.cloud.master.Master;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;

public class CloudHttpServer {

    private HttpServer server;
    private final Gson gson;
    private static final int PORT = 8080;
    private final Map<String, String> apiKeys = new HashMap<>();

    public CloudHttpServer() {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        initializeApiKeys();
        try {
            startServer();
        } catch (IOException e) {
            ConsoleScreenManager.logToMainScreen(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                    " FEHLER beim Starten des HTTP-Servers: " + e.getMessage());
        }
    }

    private void initializeApiKeys() {
        // Generate default API key (in production, load from config)
        apiKeys.put("admin", UUID.randomUUID().toString());
        ConsoleScreenManager.logToMainScreen(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Admin API Key: " + apiKeys.get("admin"));
    }

    private void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.setExecutor(Executors.newFixedThreadPool(10));

        // API Endpoints
        setupEndpoints();

        server.start();
        ConsoleScreenManager.logToMainScreen(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " REST API gestartet auf Port " + PORT);
    }

    private void setupEndpoints() {
        // Health & Status
        server.createContext("/api/v1/health", this::handleHealth);
        server.createContext("/api/v1/status", this::handleStatus);

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
        server.createContext("/api/v1/alerts", this::handleAlerts);

        // Scaling
        server.createContext("/api/v1/scaling/policies", this::handleScalingPolicies);
        server.createContext("/api/v1/scaling/trigger", this::handleScalingTrigger);

        // Queue Management
        server.createContext("/api/v1/queue/status", this::handleQueueStatus);

        // Load Balancer
        server.createContext("/api/v1/loadbalancer/stats", this::handleLoadBalancerStats);

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
            serverInfo.put("ram", server.allocatedRam);  // FIX: war server.ram
            serverInfo.put("port", server.port);  // NEU: Port hinzugefügt
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

        sendResponse(exchange, 200, metrics);
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

    private void handleDashboard(HttpExchange exchange) throws IOException {
        String html = generateDashboardHTML();
        exchange.getResponseHeaders().set("Content-Type", "text/html");
        exchange.sendResponseHeaders(200, html.length());
        OutputStream os = exchange.getResponseBody();
        os.write(html.getBytes());
        os.close();
    }

    private void handleRoot(HttpExchange exchange) throws IOException {
        String response = "KalliCloud API - Use /dashboard for web interface or /api/v1/* for REST API";
        exchange.sendResponseHeaders(200, response.length());
        OutputStream os = exchange.getResponseBody();
        os.write(response.getBytes());
        os.close();
    }

    private boolean authenticateRequest(HttpExchange exchange) {
        String authHeader = exchange.getRequestHeaders().getFirst("X-API-Key");
        return authHeader != null && apiKeys.containsValue(authHeader);
    }

    private void sendResponse(HttpExchange exchange, int statusCode, Object data) throws IOException {
        String json = gson.toJson(data);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(statusCode, json.length());
        OutputStream os = exchange.getResponseBody();
        os.write(json.getBytes());
        os.close();
    }

    private String generateDashboardHTML() {
        return """
        <!DOCTYPE html>
        <html>
        <head>
            <title>KalliCloud Dashboard</title>
            <style>
                * { margin: 0; padding: 0; box-sizing: border-box; }
                body { 
                    font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                    background: #0f172a;
                    color: #e2e8f0;
                    padding: 20px;
                }
                .header {
                    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                    padding: 30px;
                    border-radius: 10px;
                    margin-bottom: 20px;
                }
                h1 { color: white; margin-bottom: 10px; }
                .stats-grid {
                    display: grid;
                    grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
                    gap: 20px;
                    margin-bottom: 20px;
                }
                .stat-card {
                    background: #1e293b;
                    padding: 20px;
                    border-radius: 8px;
                    border-left: 4px solid #667eea;
                }
                .stat-value { font-size: 2em; font-weight: bold; color: #667eea; }
                .stat-label { color: #94a3b8; margin-top: 5px; }
                .section {
                    background: #1e293b;
                    padding: 20px;
                    border-radius: 8px;
                    margin-bottom: 20px;
                }
                .section h2 { margin-bottom: 15px; color: #667eea; }
                table { width: 100%; border-collapse: collapse; }
                th, td { padding: 12px; text-align: left; border-bottom: 1px solid #334155; }
                th { background: #334155; font-weight: 600; }
                .status { 
                    padding: 4px 12px;
                    border-radius: 12px;
                    font-size: 0.85em;
                }
                .status-online { background: #065f46; color: #a7f3d0; }
                .status-offline { background: #7f1d1d; color: #fecaca; }
                .refresh-btn {
                    background: #667eea;
                    color: white;
                    padding: 10px 20px;
                    border: none;
                    border-radius: 5px;
                    cursor: pointer;
                    font-size: 1em;
                }
                .refresh-btn:hover { background: #5568d3; }
            </style>
        </head>
        <body>
            <div class="header">
                <h1>🚀 KalliCloud Enterprise Dashboard</h1>
                <p>Real-time Minecraft Cloud System Monitoring</p>
            </div>
            
            <button class="refresh-btn" onclick="loadData()">🔄 Refresh</button>
            
            <div class="stats-grid">
                <div class="stat-card">
                    <div class="stat-value" id="totalServers">-</div>
                    <div class="stat-label">Running Servers</div>
                </div>
                <div class="stat-card">
                    <div class="stat-value" id="totalPlayers">-</div>
                    <div class="stat-label">Online Players</div>
                </div>
                <div class="stat-card">
                    <div class="stat-value" id="totalWrappers">-</div>
                    <div class="stat-label">Connected Wrappers</div>
                </div>
                <div class="stat-card">
                    <div class="stat-value" id="avgTps">-</div>
                    <div class="stat-label">Average TPS</div>
                </div>
            </div>
            
            <div class="section">
                <h2>📊 Servers</h2>
                <table id="serversTable">
                    <thead>
                        <tr>
                            <th>Server</th>
                            <th>Group</th>
                            <th>Status</th>
                            <th>Players</th>
                            <th>TPS</th>
                            <th>Wrapper</th>
                        </tr>
                    </thead>
                    <tbody></tbody>
                </table>
            </div>
            
            <div class="section">
                <h2>🖥️ Wrappers</h2>
                <table id="wrappersTable">
                    <thead>
                        <tr>
                            <th>Wrapper ID</th>
                            <th>Hostname</th>
                            <th>Memory</th>
                            <th>CPU Usage</th>
                            <th>Active Servers</th>
                        </tr>
                    </thead>
                    <tbody></tbody>
                </table>
            </div>
            
            <script>
                async function loadData() {
                    try {
                        const [serversRes, wrappersRes, metricsRes] = await Promise.all([
                            fetch('/api/v1/servers'),
                            fetch('/api/v1/wrappers'),
                            fetch('/api/v1/metrics')
                        ]);
                        
                        const servers = await serversRes.json();
                        const wrappers = await wrappersRes.json();
                        const metrics = await metricsRes.json();
                        
                        updateStats(servers, metrics);
                        updateServersTable(servers);
                        updateWrappersTable(wrappers);
                    } catch (error) {
                        console.error('Failed to load data:', error);
                    }
                }
                
                function updateStats(servers, metrics) {
                    document.getElementById('totalServers').textContent = servers.count || 0;
                    document.getElementById('totalWrappers').textContent = metrics.wrapperMetrics ? Object.keys(metrics.wrapperMetrics).length : 0;
                    
                    let totalPlayers = 0;
                    let totalTps = 0;
                    let onlineCount = 0;
                    
                    (servers.servers || []).forEach(s => {
                        totalPlayers += s.playerCount || 0;
                        if (s.status === 'ONLINE') {
                            totalTps += s.tps || 0;
                            onlineCount++;
                        }
                    });
                    
                    document.getElementById('totalPlayers').textContent = totalPlayers;
                    document.getElementById('avgTps').textContent = onlineCount > 0 ? (totalTps / onlineCount).toFixed(1) : '0';
                }
                
                function updateServersTable(data) {
                    const tbody = document.querySelector('#serversTable tbody');
                    tbody.innerHTML = '';
                    
                    (data.servers || []).forEach(server => {
                        const row = tbody.insertRow();
                        row.innerHTML = `
                            <td>${server.serverName}</td>
                            <td>${server.groupName}</td>
                            <td><span class="status status-${server.status === 'ONLINE' ? 'online' : 'offline'}">${server.status}</span></td>
                            <td>${server.playerCount}/${server.maxPlayers}</td>
                            <td>${server.tps ? server.tps.toFixed(1) : '-'}</td>
                            <td>${server.wrapperId}</td>
                        `;
                    });
                }
                
                function updateWrappersTable(data) {
                    const tbody = document.querySelector('#wrappersTable tbody');
                    tbody.innerHTML = '';
                    
                    (data.wrappers || []).forEach(wrapper => {
                        const row = tbody.insertRow();
                        const memUsage = ((wrapper.maxMemory - wrapper.availableMemory) / wrapper.maxMemory * 100).toFixed(1);
                        row.innerHTML = `
                            <td>${wrapper.wrapperId}</td>
                            <td>${wrapper.hostname}</td>
                            <td>${wrapper.availableMemory}MB / ${wrapper.maxMemory}MB (${memUsage}%)</td>
                            <td>${wrapper.cpuUsage ? wrapper.cpuUsage.toFixed(1) : '0'}%</td>
                            <td>${wrapper.activeServers}</td>
                        `;
                    });
                }
                
                // Load data on page load and every 5 seconds
                loadData();
                setInterval(loadData, 5000);
            </script>
        </body>
        </html>
        """;
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            ConsoleScreenManager.logToMainScreen(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                    " HTTP-Server gestoppt");
        }
    }
}
