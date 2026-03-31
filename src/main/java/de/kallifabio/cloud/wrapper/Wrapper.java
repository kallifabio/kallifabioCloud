/**
 * Erstellt von Gamer_Kidd_LP | kallifabio
 * am 03.10.2024 um 18:21
 * Projektname: CloudSystemTest
 * Packagename: de.kallifabio.cloudsystem
 */

package de.kallifabio.cloud.wrapper;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import de.kallifabio.cloud.config.ConfigManager;
import de.kallifabio.cloud.libs.console.ConsoleColors;
import de.kallifabio.cloud.libs.Message;
import de.kallifabio.cloud.libs.console.ConsoleScreenManager;
import de.kallifabio.cloud.libs.logging.CentralLogger;
import de.kallifabio.cloud.master.Master;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.*;

public class Wrapper {

    private Client client;
    private String wrapperId;
    private String hostname;
    private String routeHost;
    private int maxMemory;
    private int availableMemory;

    // Server Management
    private final Map<String, Serverprocess> managedServers = new ConcurrentHashMap<>();
    private final Set<String> restartInProgress = ConcurrentHashMap.newKeySet();
    private final Set<String> startInProgress = ConcurrentHashMap.newKeySet();
    private final Map<String, Integer> restartRetryCounts = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);

    // Connection State
    private volatile boolean connected = false;
    private volatile boolean reconnecting = false;
    private volatile boolean shuttingDown = false; // NEU: Shutdown-Flag
    private int reconnectAttempts = 0;
    private static final int MAX_RECONNECT_ATTEMPTS = 10;
    private static final long RECONNECT_DELAY_MS = 5000;

    // Heartbeat
    private ScheduledFuture<?> heartbeatTask;
    private static final long HEARTBEAT_INTERVAL_MS = 5000;
    private static final int START_RETRY_LIMIT = 8;

    // Metrics
    private final OperatingSystemMXBean osBean;

    public Wrapper() {
        this.wrapperId = generateWrapperId();
        this.hostname = detectHostname();
        this.routeHost = detectRouteHost();
        this.osBean = ManagementFactory.getOperatingSystemMXBean();
        this.maxMemory = detectUsableWrapperMemoryMb();
        this.availableMemory = calculateAvailableMemory();

        ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Wrapper-ID: " + wrapperId + " | usable RAM: " + maxMemory + "MB");
    }

    public void start() {
        ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Initialisiere Wrapper...");

        // Initialize client
        client = new Client(32768, 8192);
        registerKryoClasses(client.getKryo());

        // Setup network listener
        setupNetworkListener();

        // Start client in separate thread (WICHTIG!)
        new Thread(client, "KryoNet-Client").start();

        // Wait a bit for client to be ready
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Connect to master
        connectToMaster();

        // Start background tasks
        startBackgroundTasks();

        ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Wrapper initialisiert");
    }

    private void registerKryoClasses(Kryo kryo) {
        kryo.setReferences(false);
        kryo.setRegistrationRequired(false);

        // Basis-Klassen
        kryo.register(HashMap.class);
        kryo.register(ArrayList.class);
        kryo.register(String[].class);

        // Server Status & Listen
        kryo.register(Message.ServerStatusMessage.class);
        kryo.register(Message.ServerListRequest.class);
        kryo.register(Message.ServerListResponse.class);
        kryo.register(Message.ServerInfo.class);

        // Server Commands & Heartbeat
        kryo.register(Message.ServerCommand.class);
        kryo.register(Message.PluginHeartbeat.class);

        // Wrapper Management
        kryo.register(Message.WrapperRegister.class);
        kryo.register(Message.WrapperRegisterAck.class);
        kryo.register(Message.WrapperHeartbeat.class);
        kryo.register(Message.WrapperCommand.class);
        kryo.register(Message.Ping.class);
        kryo.register(Message.Pong.class);

        // Server Metrics
        kryo.register(Message.ServerMetrics.class);
        kryo.register(Message.ServerLoadUpdate.class);

        // Player Management
        kryo.register(Message.PlayerJoinRequest.class);
        kryo.register(Message.PlayerJoinResponse.class);
        kryo.register(Message.PlayerConnectRequest.class);
        kryo.register(Message.PlayerConnectResponse.class);
        kryo.register(Message.PlayerTransfer.class);

        // Queue Management
        kryo.register(Message.QueueUpdate.class);
        kryo.register(Message.QueueKeepAlive.class);
        kryo.register(Message.ServerLog.class);
        kryo.register(Message.PermissionSync.class);
        kryo.register(Message.PlayerNotification.class);

        // Cluster Management
        kryo.register(Message.ClusterSync.class);
        kryo.register(Message.ClusterHeartbeat.class);
        kryo.register(Message.MasterElection.class);

        // Auto-Scaling
        kryo.register(Message.ScalingDecision.class);
        kryo.register(Message.ResourceRequest.class);

        // Monitoring
        kryo.register(Message.HealthCheck.class);
        kryo.register(Message.Alert.class);

        // Template Sync
        kryo.register(Message.TemplateSyncRequest.class);
        kryo.register(Message.TemplateSyncResponse.class);

        // Configuration
        kryo.register(Message.ConfigUpdate.class);

        // Backup & Recovery
        kryo.register(Message.BackupRequest.class);
        kryo.register(Message.RestoreRequest.class);

        // Performance
        kryo.register(Message.PerformanceProfile.class);

        // Cross-Master
        kryo.register(Message.CrossMasterRequest.class);
        kryo.register(Message.CrossMasterResponse.class);

        // Database
        kryo.register(Message.DatabaseSync.class);

        // API
        kryo.register(Message.APIRequest.class);
        kryo.register(Message.APIResponse.class);

        // Arrays fÃ¼r byte[]
        kryo.register(byte[].class);

        ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Kryo-Klassen registriert");
    }

    private void setupNetworkListener() {
        client.addListener(new Listener() {
            @Override
            public void connected(Connection connection) {
                handleConnected(connection);
            }

            @Override
            public void disconnected(Connection connection) {
                handleDisconnected(connection);
            }

            @Override
            public void received(Connection connection, Object object) {
                handleMessage(connection, object);
            }

            public void exceptionCaught(Connection connection, Throwable cause) {
                if (!(cause instanceof IOException)) {
                    ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                            " Wrapper-Fehler: " + cause.getMessage());
                }
            }
        });
    }

    private void connectToMaster() {
        String masterHost;
        int tcpPort;
        int udpPort;
        ConfigManager cfg = new ConfigManager();

        // Combined-Mode: bevorzuge konfigurierten ConnectHost statt hartem Localhost.
        if (Master.getInstance() != null) {
            String configured = Master.getInstance().getConfigManager().getMasterConnectHost();
            masterHost = (configured == null || configured.isBlank()) ? "127.0.0.1" : configured.trim();
            tcpPort = Master.getInstance().getMasterPort();
            udpPort = Master.getInstance().getMasterUdpPort();
        } else {
            masterHost = cfg.getMasterConnectHost();
            tcpPort = cfg.getMasterTcpPort();
            udpPort = cfg.getMasterUdpPort();
        }

        try {
            ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                    " Verbinde zu Master: " + masterHost + ":" + tcpPort);

            client.connect(10000, masterHost, tcpPort, udpPort);

        } catch (IOException e) {
            ConsoleScreenManager.printToTerminal(ConsoleColors.RED + ConsoleColors.PREFIX +
                    ConsoleColors.getCurrentTime() + " Verbindung zu Master fehlgeschlagen: " + e.getMessage());
            e.printStackTrace(); // Zeige vollstaendigen Stacktrace fÃ¼r Debugging

            // Schedule reconnect
            scheduleReconnect();
        }
    }

    private void handleConnected(Connection connection) {
        connected = true;
        reconnectAttempts = 0;

        ConsoleScreenManager.printToTerminal(ConsoleColors.GREEN + ConsoleColors.PREFIX +
                ConsoleColors.getCurrentTime() + " [OK] Mit Master verbunden");

        // Send registration
        registerWithMaster();

        // Start heartbeat
        startHeartbeat();
        syncManagedServersAfterReconnect();
    }

    private void handleDisconnected(Connection connection) {
        if (connected) {
            connected = false;

            // NEU: Nur Reconnect wenn nicht am Herunterfahren
            if (!shuttingDown) {
                ConsoleScreenManager.printToTerminal(ConsoleColors.YELLOW + ConsoleColors.PREFIX +
                        ConsoleColors.getCurrentTime() + " Verbindung zu Master verloren");

                // Stop heartbeat
                stopHeartbeat();

                // Warte 1 Sekunde vor Reconnect
                scheduler.schedule(() -> {
                    if (!reconnecting && !shuttingDown) {
                        scheduleReconnect();
                    }
                }, 1000, TimeUnit.MILLISECONDS);
            }
        }
    }

    private void registerWithMaster() {
        Message.WrapperRegister register = new Message.WrapperRegister();
        register.wrapperId = wrapperId;
        register.hostname = hostname;
        register.routeHost = routeHost;
        register.maxMemory = maxMemory;
        register.availableMemory = calculateAvailableMemory();
        register.version = "1.0.2";

        client.sendTCP(register);

        ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Registrierung gesendet");
    }

    private void handleMessage(Connection connection, Object object) {
        if (object instanceof Message.WrapperRegisterAck) {
            handleRegisterAck((Message.WrapperRegisterAck) object);
        } else if (object instanceof Message.ServerCommand) {
            handleServerCommand((Message.ServerCommand) object);
        } else if (object instanceof Message.Ping) {
            handlePing((Message.Ping) object);
        } else if (object instanceof Message.ConfigUpdate) {
            reloadLocalConfig((Message.ConfigUpdate) object);
        } else if (object instanceof Message.PlayerTransfer) {
            handlePlayerTransfer((Message.PlayerTransfer) object);
        } else if (object instanceof Message.PermissionSync) {
            handlePermissionSync((Message.PermissionSync) object);
        }
    }

    private void handlePing(Message.Ping ping) {
        Message.Pong pong = new Message.Pong();
        pong.sourceId = wrapperId;
        pong.pingTimestamp = ping.timestamp;
        pong.timestamp = System.currentTimeMillis();
        client.sendTCP(pong);
    }

    private void handleRegisterAck(Message.WrapperRegisterAck ack) {
        if (ack.success) {
            ConsoleScreenManager.printToTerminal(ConsoleColors.GREEN + ConsoleColors.PREFIX +
                    ConsoleColors.getCurrentTime() + " [OK] Registrierung bestÃ¤tigt von Master: " + ack.masterId);
        } else {
            ConsoleScreenManager.printToTerminal(ConsoleColors.RED + ConsoleColors.PREFIX +
                    ConsoleColors.getCurrentTime() + " Registrierung abgelehnt");
        }
    }

    private void handleServerCommand(Message.ServerCommand command) {
        switch (command.command.toUpperCase()) {
            case "START" -> startServer(command.serverName, command.groupName, command.port);
            case "STOP", "GRACEFUL_STOP" -> stopServer(command.serverName, true);
            case "FORCE_STOP" -> stopServer(command.serverName, false);
            case "RESTART" -> restartServer(command.serverName);
            case "REGISTER_BACKEND_ROUTE" -> registerBackendRoute(command.serverName, command.targetHost, command.port);
            case "UNREGISTER_BACKEND_ROUTE" -> unregisterBackendRoute(command.serverName);
            default -> ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX +
                    ConsoleColors.getCurrentTime() + " Unbekannter Befehl: " + command.command);
        }
    }

    private void registerBackendRoute(String backendServerName, String targetHost, int targetPort) {
        if (backendServerName == null || backendServerName.isBlank() || targetPort <= 0) {
            return;
        }
        for (Serverprocess process : managedServers.values()) {
            if (process == null || !isProxyGroup(process.getGroupName())) {
                continue;
            }
            process.registerBackendRouteInProxyConfig(backendServerName, targetHost, targetPort);
        }
    }

    private void unregisterBackendRoute(String backendServerName) {
        if (backendServerName == null || backendServerName.isBlank()) {
            return;
        }
        for (Serverprocess process : managedServers.values()) {
            if (process == null || !isProxyGroup(process.getGroupName())) {
                continue;
            }
            process.unregisterBackendRouteInProxyConfig(backendServerName);
        }
    }

    private void handlePlayerTransfer(Message.PlayerTransfer transfer) {
        ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Spieler-Transfer: " + transfer.playerUuid + " von " + transfer.fromServer +
                " zu " + transfer.toServer);

        // Forward to appropriate server
        Serverprocess server = managedServers.get(transfer.toServer);
        if (server != null) {
            server.sendCommand("transfer " + transfer.playerUuid);
        }
    }

    private void handlePermissionSync(Message.PermissionSync sync) {
        if (sync == null || sync.playerUuid == null || sync.playerUuid.isBlank()) {
            return;
        }
        for (Serverprocess server : managedServers.values()) {
            if (sync.targetServer != null && !sync.targetServer.isBlank()
                    && !sync.targetServer.equalsIgnoreCase(server.getServerName())) {
                continue;
            }
            server.applyPermissionSync(sync);
        }
    }

    private void startServer(String serverName, String groupName, int port) {
        startServer(serverName, groupName, port, 1);
    }

    private void startServer(String serverName, String groupName, int port, int attempt) {
        if (shuttingDown) {
            return;
        }
        if (managedServers.containsKey(serverName)) {
            ConsoleScreenManager.printToTerminal(ConsoleColors.YELLOW + ConsoleColors.PREFIX +
                    ConsoleColors.getCurrentTime() + " Server " + serverName + " lÃ¤uft bereits");
            return;
        }
        if (!startInProgress.add(serverName)) {
            return;
        }

        ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Starte Server: " + serverName + " (" + groupName + ") auf Port " + port);

        try {
            if (!isPortAvailable(port)) {
                scheduleStartRetry(serverName, groupName, port, attempt, "Port belegt");
                return;
            }

            Serverprocess serverProcess = new Serverprocess(serverName, groupName, port, this);
            serverProcess.start();
            managedServers.put(serverName, serverProcess);
            availableMemory = calculateAvailableMemory();
            restartInProgress.remove(serverName);
            restartRetryCounts.remove(serverName);

            ConsoleScreenManager.printToTerminal(ConsoleColors.GREEN + ConsoleColors.PREFIX +
                    ConsoleColors.getCurrentTime() + " [OK] Server " + serverName +
                    " erfolgreich gestartet auf Port " + port);

        } catch (IOException e) {
            scheduleStartRetry(serverName, groupName, port, attempt, e.getMessage());
        } catch (Exception e) {
            ConsoleScreenManager.printToTerminal(ConsoleColors.RED + ConsoleColors.PREFIX +
                    ConsoleColors.getCurrentTime() + " Unerwarteter Fehler bei " + serverName + ": " + e.getMessage());
            e.printStackTrace();
            restartInProgress.remove(serverName);
            restartRetryCounts.remove(serverName);
        } finally {
            startInProgress.remove(serverName);
        }
    }

    private void stopServer(String serverName) {
        stopServer(serverName, true);
    }

    private void stopServer(String serverName, boolean graceful) {
        Serverprocess server = managedServers.get(serverName);
        if (server == null) {
            ConsoleScreenManager.printToTerminal(ConsoleColors.YELLOW + ConsoleColors.PREFIX +
                    ConsoleColors.getCurrentTime() + " Server " + serverName + " nicht gefunden");
            return;
        }
        if (shuttingDown) {
            graceful = false;
        }

        ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Stoppe Server: " + serverName);

        server.stop(graceful);
        managedServers.remove(serverName);

        availableMemory = calculateAvailableMemory();
    }

    private void restartServer(String serverName) {
        if (shuttingDown) {
            return;
        }
        if (!restartInProgress.add(serverName)) {
            ConsoleScreenManager.printToTerminal(ConsoleColors.YELLOW + ConsoleColors.PREFIX +
                    ConsoleColors.getCurrentTime() + " Neustart bereits in Arbeit: " + serverName);
            return;
        }

        Serverprocess server = managedServers.get(serverName);
        if (server == null) {
            ConsoleScreenManager.printToTerminal(ConsoleColors.YELLOW + ConsoleColors.PREFIX +
                    ConsoleColors.getCurrentTime() + " Server " + serverName + " nicht gefunden");
            restartInProgress.remove(serverName);
            restartRetryCounts.remove(serverName);
            return;
        }

        ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Starte Server neu: " + serverName);

        String groupName = server.getGroupName();
        int serverPort = server.getPort();
        stopServer(serverName);

        scheduler.schedule(() -> startServer(serverName, groupName, serverPort, 1), 3, TimeUnit.SECONDS);
    }

    private void startHeartbeat() {
        heartbeatTask = scheduler.scheduleAtFixedRate(() -> {
            sendHeartbeat();
        }, HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void stopHeartbeat() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
            heartbeatTask = null;
        }
    }

    private void sendHeartbeat() {
        if (!connected) return;

        Message.WrapperHeartbeat heartbeat = new Message.WrapperHeartbeat();
        heartbeat.wrapperId = wrapperId;
        heartbeat.availableMemory = calculateAvailableMemory();
        heartbeat.cpuUsage = getCpuUsage();
        heartbeat.activeServers = managedServers.size();
        heartbeat.timestamp = System.currentTimeMillis();

        client.sendTCP(heartbeat);
    }

    private void scheduleReconnect() {
        if (reconnecting || shuttingDown) return; // NEU: PrÃ¼fe shutdown-Flag

        reconnecting = true;
        reconnectAttempts++;

        if (reconnectAttempts > MAX_RECONNECT_ATTEMPTS) {
            ConsoleScreenManager.printToTerminal(ConsoleColors.RED + ConsoleColors.PREFIX +
                    ConsoleColors.getCurrentTime() + " Maximale Anzahl an Verbindungsversuchen erreicht");
            return;
        }

        ConsoleScreenManager.printToTerminal(ConsoleColors.YELLOW + ConsoleColors.PREFIX +
                ConsoleColors.getCurrentTime() + " Verbindungsversuch in " +
                (RECONNECT_DELAY_MS / 1000) + " Sekunden... (Versuch " + reconnectAttempts +
                "/" + MAX_RECONNECT_ATTEMPTS + ")");

        scheduler.schedule(() -> {
            if (shuttingDown) return; // NEU: Abbrechen wenn Shutdown lÃ¤uft

            reconnecting = false;

            // Client komplett neu erstellen
            if (client != null) {
                try {
                    client.stop();
                    Thread.sleep(200);
                } catch (Exception e) {}
            }

            // Neuer Client
            client = new Client(32768, 8192);
            registerKryoClasses(client.getKryo());
            setupNetworkListener();
            new Thread(client, "KryoNet-Client-" + reconnectAttempts).start();

            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            connectToMaster();
        }, RECONNECT_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private void startBackgroundTasks() {
        // Metrics collection
        scheduler.scheduleAtFixedRate(() -> {
            collectAndSendMetrics();
        }, 10, 10, TimeUnit.SECONDS);

        // Server health check
        scheduler.scheduleAtFixedRate(() -> {
            checkServerHealth();
        }, 30, 30, TimeUnit.SECONDS);

        scheduler.scheduleAtFixedRate(() -> {
            checkWrapperHealth();
        }, 15, 15, TimeUnit.SECONDS);
    }

    private void collectAndSendMetrics() {
        if (shuttingDown || !connected) return;

        for (Serverprocess server : managedServers.values()) {
            Message.ServerMetrics metrics = server.collectMetrics();
            if (metrics != null) {
                client.sendTCP(metrics);
            }
        }
    }

    private void checkServerHealth() {
        if (shuttingDown) {
            return;
        }
        List<String> unhealthyServers = new ArrayList<>();

        for (Map.Entry<String, Serverprocess> entry : managedServers.entrySet()) {
            if (!entry.getValue().isHealthy()) {
                unhealthyServers.add(entry.getKey());
            }
        }

        for (String serverName : unhealthyServers) {
            if (restartInProgress.contains(serverName)) {
                continue;
            }
            ConsoleScreenManager.printToTerminal(ConsoleColors.YELLOW + ConsoleColors.PREFIX +
                    ConsoleColors.getCurrentTime() + " Server " + serverName + " ist unhealthy - Neustart wird eingeleitet");
            restartServer(serverName);
        }
    }

    private void scheduleStartRetry(String serverName, String groupName, int port, int attempt, String reason) {
        restartRetryCounts.put(serverName, attempt);
        if (attempt >= START_RETRY_LIMIT) {
            ConsoleScreenManager.printToTerminal(ConsoleColors.RED + ConsoleColors.PREFIX +
                    ConsoleColors.getCurrentTime() + " FEHLER beim Starten von " + serverName +
                    ": " + reason + " (Retry-Limit erreicht)");
            restartInProgress.remove(serverName);
            restartRetryCounts.remove(serverName);
            return;
        }
        long backoffSeconds = Math.min(5L, attempt);
        ConsoleScreenManager.printToTerminal(ConsoleColors.YELLOW + ConsoleColors.PREFIX +
                ConsoleColors.getCurrentTime() + " Start von " + serverName + " verschoben (" + reason +
                "), Retry " + attempt + "/" + START_RETRY_LIMIT + " in " + backoffSeconds + "s");
        scheduler.schedule(() -> startServer(serverName, groupName, port, attempt + 1),
                backoffSeconds,
                TimeUnit.SECONDS);
    }

    private boolean isPortAvailable(int port) {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(port)) {
            socket.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private int calculateAvailableMemory() {
        int usedMemory = managedServers.values().stream()
                .mapToInt(Serverprocess::getAllocatedMemory)
                .sum();
        int estimated = maxMemory - usedMemory;
        if (estimated < 0) {
            estimated = 0;
        }
        return Math.min(maxMemory, estimated);
    }

    private double getCpuUsage() {
        if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOs) {
            double system = sunOs.getSystemCpuLoad() * 100.0;
            if (Double.isNaN(system) || system < 0) {
                return 0.0;
            }
            return Math.min(100.0, system);
        }
        return 0.0;
    }

    private int detectUsableWrapperMemoryMb() {
        long totalMb = readOsMemoryMb("getTotalMemorySize");
        if (totalMb <= 0) {
            totalMb = readOsMemoryMb("getTotalPhysicalMemorySize");
        }
        if (totalMb <= 0) {
            totalMb = Runtime.getRuntime().maxMemory() / 1024 / 1024;
        }

        long reserveMb = Math.max(768L, (long) (totalMb * 0.20));
        long usable = totalMb - reserveMb;
        if (usable < 512L) {
            usable = Math.max(512L, totalMb);
        }
        return (int) Math.min(Integer.MAX_VALUE, usable);
    }

    private long readOsMemoryMb(String methodName) {
        try {
            Method method = osBean.getClass().getMethod(methodName);
            Object value = method.invoke(osBean);
            if (value instanceof Number number) {
                long bytes = number.longValue();
                if (bytes > 0) {
                    return bytes / 1024 / 1024;
                }
            }
        } catch (Exception ignored) {
        }
        return -1L;
    }

    private String generateWrapperId() {
        return "Wrapper-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String detectHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown";
        }
    }

    private String detectMasterHost() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            return "127.0.0.1";
        }
    }

    private String detectRouteHost() {
        ConfigManager cfg = new ConfigManager();
        String configured = cfg.getMaster("CloudMaster.Network.GameHost");
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        return detectMasterHost();
    }

    private boolean isProxyGroup(String groupName) {
        if (groupName == null) {
            return false;
        }
        String group = groupName.toLowerCase(Locale.ROOT);
        return group.contains("proxy") || group.contains("bungee") || group.contains("waterfall") || group.contains("velocity");
    }

    public void sendServerStatus(String serverName, String status) {
        if (!connected) return;

        Message.ServerStatusMessage message = new Message.ServerStatusMessage();
        message.serverName = serverName;
        message.status = status;
        Serverprocess process = managedServers.get(serverName);
        if (process != null) {
            message.groupName = process.getGroupName();
            message.playerCount = process.getPlayerCount();
            message.maxPlayers = process.getMaxPlayers();
        }

        client.sendTCP(message);
    }

    public void sendServerLog(String serverName, String level, String line) {
        if (!connected) {
            return;
        }

        Message.ServerLog log = new Message.ServerLog();
        log.serverName = serverName;
        log.level = level;
        log.message = line;
        log.timestamp = System.currentTimeMillis();
        client.sendTCP(log);
    }

    private void syncManagedServersAfterReconnect() {
        scheduler.schedule(() -> {
            if (!connected) {
                return;
            }

            for (Serverprocess process : managedServers.values()) {
                sendServerStatus(process.getServerName(), process.isRunning() ? "ONLINE" : "OFFLINE");
                Message.ServerMetrics metrics = process.collectMetrics();
                if (metrics != null) {
                    client.sendTCP(metrics);
                }
            }
        }, 2, TimeUnit.SECONDS);
    }

    private void checkWrapperHealth() {
        availableMemory = calculateAvailableMemory();
        double cpu = getCpuUsage();
        if (cpu > 95.0) {
            CentralLogger.warn("Wrapper/" + wrapperId, "CPU kritisch: " + String.format("%.2f", cpu) + "%");
        }
        if (availableMemory < 512) {
            CentralLogger.warn("Wrapper/" + wrapperId, "Wenig freier RAM: " + availableMemory + "MB");
        }
    }

    private void reloadLocalConfig(Message.ConfigUpdate update) {
        try {
            ConfigManager manager = new ConfigManager();
            manager.reloadAllConfigs();
            CentralLogger.audit("wrapper:" + wrapperId, "config_reload", update.configType);
        } catch (Exception e) {
            CentralLogger.error("Wrapper/" + wrapperId, "Config-Reload fehlgeschlagen", e);
        }
    }

    public void shutdown() {
        if (shuttingDown) {
            return;
        }
        shuttingDown = true;

        ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Wrapper wird heruntergefahren...");

        // Stop heartbeat and background tasks first to avoid restarts during shutdown.
        stopHeartbeat();
        scheduler.shutdownNow();

        // Stop all servers
        List<Serverprocess> servers = new ArrayList<>(managedServers.values());
        managedServers.clear();
        for (Serverprocess server : servers) {
            try {
                server.stop(false);
            } catch (Exception ignored) {
            }
        }

        availableMemory = calculateAvailableMemory();

        // Disconnect from master
        if (client != null) {
            client.stop();
        }
        connected = false;
        reconnecting = false;

        ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Wrapper heruntergefahren");
    }

    // Getters
    public Client getClient() {
        return client;
    }

    public String getWrapperId() {
        return wrapperId;
    }

    public boolean isConnected() {
        return connected;
    }

    public Map<String, Serverprocess> getManagedServers() {
        return new HashMap<>(managedServers);
    }

    public Set<String> getRestartInProgress() {
        return Set.copyOf(restartInProgress);
    }

    public Map<String, Integer> getRestartRetryCounts() {
        return new HashMap<>(restartRetryCounts);
    }
}


