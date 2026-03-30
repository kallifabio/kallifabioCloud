/**
 * Erstellt von Gamer_Kidd_LP | kallifabio
 * am 03.10.2024 um 18:21
 * Projektname: CloudSystemTest
 * Packagename: de.kallifabio.cloudsystem
 */

package de.kallifabio.cloud.master;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;
import de.kallifabio.cloud.libs.console.ConsoleColors;
import de.kallifabio.cloud.libs.Message;
import de.kallifabio.cloud.config.ConfigManager;
import de.kallifabio.cloud.libs.console.ConsoleScreenManager;
import de.kallifabio.cloud.master.cluster.ClusterManager;
import de.kallifabio.cloud.master.loadbalancer.LoadBalancerManager;
import de.kallifabio.cloud.master.monitoring.MonitoringService;
import de.kallifabio.cloud.master.queue.PlayerQueueManager;
import de.kallifabio.cloud.master.scaling.AutoScalingManager;
import de.kallifabio.cloud.wrapper.Wrapper;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Master {

    private static Master instance;
    private Server server;
    private String masterHost = detectIp();
    private Integer masterPort = 54555;
    private ConfigManager configManager;
    private Wrapper wrapper;

    // Enterprise Features
    private ClusterManager clusterManager;
    private AutoScalingManager autoScalingManager;
    private MonitoringService monitoringService;
    private LoadBalancerManager loadBalancerManager;
    private PlayerQueueManager playerQueueManager;
    private ScheduledExecutorService executorService;

    // Connected Wrappers Management
    private final Map<Integer, WrapperConnection> connectedWrappers = new ConcurrentHashMap<>();
    private final Map<String, ServerInstance> runningServers = new ConcurrentHashMap<>();

    // Port Management
    private int nextAvailablePort;
    private final Set<Integer> usedPorts = ConcurrentHashMap.newKeySet();
    private final Map<String, Integer> serverPorts = new ConcurrentHashMap<>();

    // Feste Ports für erste Server
    private int FIRST_PROXY_PORT;
    private int FIRST_LOBBY_PORT;

    // Cluster State
    private String masterId = UUID.randomUUID().toString();
    private boolean isPrimaryMaster = true;
    private Set<String> clusterPeers = ConcurrentHashMap.newKeySet();

    public void start() {
        instance = this;
        executorService = Executors.newScheduledThreadPool(10);

        // Initialize Configuration ZUERST
        this.configManager = new ConfigManager();

        // Lade Port-Konfiguration aus Config
        this.FIRST_PROXY_PORT = configManager.getFirstProxyPort();
        this.FIRST_LOBBY_PORT = configManager.getFirstLobbyPort();
        this.nextAvailablePort = configManager.getDynamicPortStart();

        // Initialize Server
        server = new Server(32768, 8192);
        Kryo kryo = server.getKryo();

        // Register all message types
        registerKryoClasses(kryo);

        // Initialize Enterprise Components
        initializeEnterpriseComponents();

        // Setup Network Listener
        setupNetworkListener();

        // Bind and Start Server
        try {
            server.bind(54555, 54777);
        } catch (IOException e) {
            ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                    " FEHLER: Konnte Server nicht binden: " + e.getMessage());
            throw new RuntimeException(e);
        }
        server.start();

        // Start Enterprise Services
        startEnterpriseServices();

        // Add Shutdown Hook
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));

        ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Cloud-Master gestartet auf IP: " + masterHost + " | Master-ID: " + masterId);
        ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Port-Konfiguration:");
        ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + "  - Proxy-1: " + FIRST_PROXY_PORT);
        ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + "  - Lobby-1: " + FIRST_LOBBY_PORT);
        ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + "  - Dynamisch ab: " + nextAvailablePort);
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

        // Arrays für byte[]
        kryo.register(byte[].class);
    }

    // Port Management Methods
    public synchronized int assignPort(String serverName, String groupName) {
        // Feste Ports für erste Server (aus Config)
        if (serverName.equals("Proxy-1")) {
            usedPorts.add(FIRST_PROXY_PORT);
            ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                    " Fester Port zugewiesen: " + serverName + " -> " + FIRST_PROXY_PORT);
            return FIRST_PROXY_PORT;
        }

        if (serverName.equals("Lobby-1")) {
            usedPorts.add(FIRST_LOBBY_PORT);
            ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                    " Fester Port zugewiesen: " + serverName + " -> " + FIRST_LOBBY_PORT);
            return FIRST_LOBBY_PORT;
        }

        // Für alle anderen Server: Dynamische Zuweisung
        int port = nextAvailablePort;
        while (usedPorts.contains(port) || !isPortAvailable(port)) {
            port++;
            if (port > 65535) {
                throw new RuntimeException("Keine verfügbaren Ports mehr!");
            }
        }
        usedPorts.add(port);
        nextAvailablePort = port + 1;

        ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Dynamischer Port zugewiesen: " + serverName + " -> " + port);
        return port;
    }

    public synchronized void releasePort(int port) {
        usedPorts.remove(port);
        ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Port freigegeben: " + port);
    }

    private boolean isPortAvailable(int port) {
        try (ServerSocket socket = new ServerSocket(port)) {
            socket.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public int getServerPort(String serverName) {
        return serverPorts.getOrDefault(serverName, -1);
    }

    private void initializeEnterpriseComponents() {
        ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Initialisiere Enterprise-Komponenten...");

        clusterManager = new ClusterManager(this, masterId);
        autoScalingManager = new AutoScalingManager(this, configManager);
        monitoringService = new MonitoringService(this);
        loadBalancerManager = new LoadBalancerManager(this);
        playerQueueManager = new PlayerQueueManager(this, loadBalancerManager);

        ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Enterprise-Komponenten initialisiert");
    }

    private void setupNetworkListener() {
        server.addListener(new Listener() {
            @Override
            public void received(Connection connection, Object object) {
                handleMessage(connection, object);
            }

            @Override
            public void connected(Connection connection) {
                ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                        " Neue Verbindung: " + connection.getRemoteAddressTCP());
            }

            @Override
            public void disconnected(Connection connection) {
                handleDisconnection(connection);
            }

            @Override
            public void idle(Connection connection) {
                WrapperConnection wrapper = connectedWrappers.get(connection.getID());
                if (wrapper != null && System.currentTimeMillis() - wrapper.lastHeartbeat > 30000) {
                    ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                            " Wrapper " + wrapper.wrapperId + " ist idle - Trennung wird eingeleitet");
                    connection.close();
                }
            }

            public void exceptionCaught(Connection connection, Throwable cause) {
                if (cause instanceof IOException) {
                    ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                            " IO-Fehler: " + cause.getMessage());
                } else {
                    ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                            " Unerwarteter Fehler: " + cause.getMessage());
                    cause.printStackTrace();
                }
            }
        });
    }

    private void handleMessage(Connection connection, Object object) {
        if (object instanceof Message.WrapperRegister) {
            handleWrapperRegister(connection, (Message.WrapperRegister) object);
        } else if (object instanceof Message.WrapperHeartbeat) {
            handleWrapperHeartbeat(connection, (Message.WrapperHeartbeat) object);
        } else if (object instanceof Message.ServerStatusMessage) {
            handleServerStatus(connection, (Message.ServerStatusMessage) object);
        } else if (object instanceof Message.ServerMetrics) {
            handleServerMetrics(connection, (Message.ServerMetrics) object);
        } else if (object instanceof Message.ServerCommand) {
            handleServerCommand(connection, (Message.ServerCommand) object);
        } else if (object instanceof Message.PlayerJoinRequest) {
            handlePlayerJoinRequest(connection, (Message.PlayerJoinRequest) object);
        } else if (object instanceof Message.ClusterSync) {
            handleClusterSync(connection, (Message.ClusterSync) object);
        } else if (object instanceof Message.ServerListRequest) {
            handleServerListRequest(connection, (Message.ServerListRequest) object);
        } else if (object instanceof Message.PluginHeartbeat) {
            handlePluginHeartbeat((Message.PluginHeartbeat) object);
        }
    }

    private void handlePluginStatus(Message.ServerStatusMessage msg) {
        ServerInstance server = runningServers.get(msg.serverName);
        if (server != null) {
            server.status = msg.status;
            server.playerCount = msg.playerCount;
            server.maxPlayers = msg.maxPlayers;
        }
    }

    private void handleServerListRequest(Connection connection, Message.ServerListRequest request) {
        Message.ServerListResponse response = new Message.ServerListResponse();
        response.requestId = request.requestId;
        response.servers = new ArrayList<>();

        for (ServerInstance server : runningServers.values()) {
            if (request.groupName == null || server.groupName.equals(request.groupName)) {
                Message.ServerInfo info = new Message.ServerInfo();
                info.serverName = server.serverName;
                info.groupName = server.groupName;
                info.status = server.status;
                info.playerCount = server.playerCount;
                info.maxPlayers = server.maxPlayers;
                info.tps = server.tps;
                info.lastUpdate = server.lastUpdate;
                response.servers.add(info);
            }
        }

        connection.sendTCP(response);
    }

    private void handlePluginHeartbeat(Message.PluginHeartbeat heartbeat) {
        ServerInstance server = runningServers.get(heartbeat.serverName);
        if (server != null) {
            server.playerCount = heartbeat.playerCount;
            server.maxPlayers = heartbeat.maxPlayers;
            server.tps = heartbeat.tps;
            server.lastUpdate = System.currentTimeMillis();
        }
    }

    private void handleWrapperRegister(Connection connection, Message.WrapperRegister message) {
        WrapperConnection wrapper = new WrapperConnection(
                message.wrapperId,
                connection,
                message.hostname,
                message.maxMemory,
                message.availableMemory
        );

        connectedWrappers.put(connection.getID(), wrapper);

        ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Wrapper registriert: " + message.wrapperId + " | RAM: " +
                message.availableMemory + "MB / " + message.maxMemory + "MB");

        Message.WrapperRegisterAck ack = new Message.WrapperRegisterAck();
        ack.masterId = masterId;
        ack.success = true;
        ack.assignedId = message.wrapperId;
        connection.sendTCP(ack);

        clusterManager.notifyWrapperJoined(wrapper);
    }

    private void handleWrapperHeartbeat(Connection connection, Message.WrapperHeartbeat heartbeat) {
        WrapperConnection wrapper = connectedWrappers.get(connection.getID());
        if (wrapper != null) {
            wrapper.lastHeartbeat = System.currentTimeMillis();
            wrapper.availableMemory = heartbeat.availableMemory;
            wrapper.cpuUsage = heartbeat.cpuUsage;
            wrapper.activeServers = heartbeat.activeServers;

            monitoringService.recordWrapperMetrics(wrapper);
        }
    }

    private void handleServerStatus(Connection connection, Message.ServerStatusMessage message) {
        ServerInstance instance = runningServers.get(message.serverName);
        if (instance != null) {
            instance.status = message.status;
            instance.lastUpdate = System.currentTimeMillis();

            ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                    " Server Status: " + message.serverName + " -> " + message.status);

            if ("ONLINE".equals(message.status)) {
                autoScalingManager.checkScalingNeeded(message.serverName);
            }
        }
    }

    private void handleServerMetrics(Connection connection, Message.ServerMetrics metrics) {
        ServerInstance instance = runningServers.get(metrics.serverName);
        if (instance != null) {
            instance.playerCount = metrics.playerCount;
            instance.maxPlayers = metrics.maxPlayers;
            instance.tps = metrics.tps;
            instance.memoryUsage = metrics.memoryUsage;

            monitoringService.recordServerMetrics(instance);
            loadBalancerManager.updateServerLoad(metrics);
            autoScalingManager.evaluateMetrics(metrics);
        }
    }

    private void handleServerCommand(Connection connection, Message.ServerCommand command) {
        if ("START".equalsIgnoreCase(command.command)) {
            ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                    " Starte Server: " + command.serverName);
            startServer(command.serverName, command.groupName);
        } else if ("STOP".equalsIgnoreCase(command.command)) {
            ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                    " Stoppe Server: " + command.serverName);
            stopServer(command.serverName);
        } else if ("RESTART".equalsIgnoreCase(command.command)) {
            ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                    " Starte Server neu: " + command.serverName);
            restartServer(command.serverName);
        }
    }

    private void handlePlayerJoinRequest(Connection connection, Message.PlayerJoinRequest request) {
        String targetServer = loadBalancerManager.getBestServer(request.groupName, request.playerUuid);

        if (targetServer != null) {
            Message.PlayerJoinResponse response = new Message.PlayerJoinResponse();
            response.playerUuid = request.playerUuid;
            response.targetServer = targetServer;
            response.success = true;
            connection.sendTCP(response);
        } else {
            playerQueueManager.addToQueue(request.playerUuid, request.groupName);

            Message.PlayerJoinResponse response = new Message.PlayerJoinResponse();
            response.playerUuid = request.playerUuid;
            response.success = false;
            response.queuePosition = playerQueueManager.getQueuePosition(request.playerUuid);
            connection.sendTCP(response);
        }
    }

    private void handleClusterSync(Connection connection, Message.ClusterSync sync) {
        clusterManager.handleSyncMessage(sync);
    }

    private void handleDisconnection(Connection connection) {
        WrapperConnection wrapper = connectedWrappers.remove(connection.getID());
        if (wrapper != null) {
            ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                    " Wrapper getrennt: " + wrapper.wrapperId);

            runningServers.values().stream()
                    .filter(s -> s.wrapperId.equals(wrapper.wrapperId))
                    .forEach(s -> handleServerFailure(s));

            clusterManager.notifyWrapperLeft(wrapper);
        }
    }

    private void startEnterpriseServices() {
        executorService.scheduleAtFixedRate(() -> {
            checkWrapperHealth();
        }, 10, 10, TimeUnit.SECONDS);

        executorService.scheduleAtFixedRate(() -> {
            autoScalingManager.evaluate();
        }, 30, 30, TimeUnit.SECONDS);

        executorService.scheduleAtFixedRate(() -> {
            monitoringService.aggregateMetrics();
        }, 60, 60, TimeUnit.SECONDS);

        executorService.scheduleAtFixedRate(() -> {
            clusterManager.syncClusterState();
        }, 5, 5, TimeUnit.SECONDS);

        executorService.scheduleAtFixedRate(() -> {
            playerQueueManager.processQueue();
        }, 1, 1, TimeUnit.SECONDS);

        // Auto-Start configured servers after 10 seconds
        int autoStartDelay = configManager.getAutoStartDelay();
        executorService.schedule(() -> {
            autoStartServers();
        }, autoStartDelay, TimeUnit.SECONDS);

        ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Enterprise-Services gestartet (Auto-Start in " + autoStartDelay + "s)");
    }

    private void autoStartServers() {
        // Prüfe ob Auto-Start aktiviert ist
        if (!configManager.isAutoStartEnabled()) {
            ConsoleScreenManager.printToTerminal(ConsoleColors.YELLOW + ConsoleColors.PREFIX +
                    ConsoleColors.getCurrentTime() + " Auto-Start ist deaktiviert");
            return;
        }

        // Warte bis mindestens ein Wrapper verbunden ist
        if (connectedWrappers.isEmpty()) {
            ConsoleScreenManager.printToTerminal(ConsoleColors.YELLOW + ConsoleColors.PREFIX +
                    ConsoleColors.getCurrentTime() + " Kein Wrapper verfügbar für Auto-Start - Retry in 5s");

            executorService.schedule(() -> {
                autoStartServers();
            }, 5, TimeUnit.SECONDS);
            return;
        }

        ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Starte Auto-Start für konfigurierte Server...");

        // Aus Config laden
        List<String> autoStartGroups = configManager.getAutoStartGroups();

        for (String groupConfig : autoStartGroups) {
            String[] parts = groupConfig.split(":");
            String groupName = parts[0].trim();
            int count = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 1;

            for (int i = 1; i <= count; i++) {
                String serverName = groupName + "-" + i;

                // Nur starten wenn noch nicht läuft
                if (!runningServers.containsKey(serverName)) {
                    try {
                        startServer(serverName, groupName);

                        // Warte 2 Sekunden zwischen Starts
                        Thread.sleep(2000);
                    } catch (Exception e) {
                        ConsoleScreenManager.printToTerminal(ConsoleColors.RED + ConsoleColors.PREFIX +
                                ConsoleColors.getCurrentTime() + " Fehler beim Auto-Start von " +
                                serverName + ": " + e.getMessage());
                    }
                }
            }
        }

        ConsoleScreenManager.printToTerminal(ConsoleColors.GREEN + ConsoleColors.PREFIX +
                ConsoleColors.getCurrentTime() + " ✓ Auto-Start abgeschlossen");
    }

    private void checkWrapperHealth() {
        long now = System.currentTimeMillis();
        connectedWrappers.values().forEach(wrapper -> {
            if (now - wrapper.lastHeartbeat > 30000) {
                ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                        " Wrapper " + wrapper.wrapperId + " antwortet nicht - wird als offline markiert");
                wrapper.connection.close();
            }
        });
    }

    private void handleServerFailure(ServerInstance server) {
        ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Server-Ausfall erkannt: " + server.serverName + " - Initiiere Wiederherstellung");

        // Release port
        Integer port = serverPorts.remove(server.serverName);
        if (port != null) {
            releasePort(port);
        }

        runningServers.remove(server.serverName);

        if (server.isCritical) {
            autoScalingManager.replaceFailedServer(server);
        }
    }

    public void startServer(String serverName, String groupName) {
        WrapperConnection bestWrapper = loadBalancerManager.getBestWrapperForServer(groupName);

        if (bestWrapper == null) {
            ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                    " FEHLER: Kein verfügbarer Wrapper für Server " + serverName);
            return;
        }

        // Assign port for this server - MIT SERVER-NAMEN
        int assignedPort = assignPort(serverName, groupName);
        serverPorts.put(serverName, assignedPort);

        ServerInstance instance = new ServerInstance(
                serverName,
                groupName,
                bestWrapper.wrapperId,
                configManager.getRamForGroup(groupName)
        );
        instance.port = assignedPort;

        runningServers.put(serverName, instance);

        // Send start command to wrapper
        Message.ServerCommand command = new Message.ServerCommand();
        command.command = "START";
        command.serverName = serverName;
        command.groupName = groupName;
        command.port = assignedPort;  // Port wird jetzt korrekt gesetzt!

        bestWrapper.connection.sendTCP(command);

        ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Server " + serverName + " wird auf Wrapper " + bestWrapper.wrapperId +
                " gestartet (Port: " + assignedPort + ")");
    }

    public void stopServer(String serverName) {
        ServerInstance instance = runningServers.get(serverName);
        if (instance == null) {
            ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                    " FEHLER: Server " + serverName + " nicht gefunden");
            return;
        }

        WrapperConnection wrapper = getWrapperById(instance.wrapperId);
        if (wrapper != null) {
            Message.ServerCommand command = new Message.ServerCommand();
            command.command = "STOP";
            command.serverName = serverName;
            wrapper.connection.sendTCP(command);
        }

        // Release port
        Integer port = serverPorts.remove(serverName);
        if (port != null) {
            releasePort(port);
        }

        runningServers.remove(serverName);
    }

    public void restartServer(String serverName) {
        ServerInstance instance = runningServers.get(serverName);
        if (instance != null) {
            String groupName = instance.groupName;
            stopServer(serverName);
            executorService.schedule(() -> {
                startServer(serverName, groupName);
            }, 5, TimeUnit.SECONDS);
        }
    }

    private WrapperConnection getWrapperById(String wrapperId) {
        return connectedWrappers.values().stream()
                .filter(w -> w.wrapperId.equals(wrapperId))
                .findFirst()
                .orElse(null);
    }

    public void shutdown() {
        ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Cloud-System wird heruntergefahren...");

        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
            }
        }

        if (server != null) {
            server.stop();
            ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                    " Server erfolgreich geschlossen");
        }
    }

    public String detectIp() {
        try {
            InetAddress localHost = InetAddress.getLocalHost();
            return localHost.getHostAddress();
        } catch (UnknownHostException e) {
            e.printStackTrace();
            return "127.0.0.1";
        }
    }

    // Getters
    public static Master getInstance() { return instance; }
    public Integer getMasterPort() { return masterPort; }
    public String getMasterHost() { return masterHost; }
    public Server getServer() { return server; }
    public Wrapper getWrapper() { return wrapper; }
    public void setWrapper(Wrapper wrapper) { this.wrapper = wrapper; }
    public ClusterManager getClusterManager() { return clusterManager; }
    public AutoScalingManager getAutoScalingManager() { return autoScalingManager; }
    public MonitoringService getMonitoringService() { return monitoringService; }
    public LoadBalancerManager getLoadBalancerManager() { return loadBalancerManager; }
    public PlayerQueueManager getPlayerQueueManager() { return playerQueueManager; }
    public Map<String, ServerInstance> getRunningServers() { return runningServers; }
    public Map<Integer, WrapperConnection> getConnectedWrappers() { return connectedWrappers; }
    public String getMasterId() { return masterId; }
    public boolean isPrimaryMaster() { return isPrimaryMaster; }
    public void setPrimaryMaster(boolean primary) { this.isPrimaryMaster = primary; }

    public int getFIRST_LOBBY_PORT() {
        return FIRST_LOBBY_PORT;
    }

    public int getFIRST_PROXY_PORT() {
        return FIRST_PROXY_PORT;
    }
}
