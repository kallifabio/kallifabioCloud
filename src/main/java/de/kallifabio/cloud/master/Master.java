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
import de.kallifabio.cloud.data.CloudDataStore;
import de.kallifabio.cloud.libs.console.ConsoleColors;
import de.kallifabio.cloud.libs.Message;
import de.kallifabio.cloud.config.ConfigManager;
import de.kallifabio.cloud.libs.console.ConsoleScreenManager;
import de.kallifabio.cloud.libs.logging.CentralLogger;
import de.kallifabio.cloud.master.cluster.ClusterManager;
import de.kallifabio.cloud.master.loadbalancer.LoadBalancerManager;
import de.kallifabio.cloud.master.monitoring.MonitoringService;
import de.kallifabio.cloud.master.player.PlayerSessionManager;
import de.kallifabio.cloud.master.permissions.PermissionGroup;
import de.kallifabio.cloud.master.permissions.PermissionEnforcer;
import de.kallifabio.cloud.master.permissions.PermissionSyncService;
import de.kallifabio.cloud.master.queue.PlayerQueueManager;
import de.kallifabio.cloud.master.scaling.AutoScalingManager;
import de.kallifabio.cloud.master.template.TemplateManager;
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
    private CloudDataStore dataStore;
    private PlayerSessionManager playerSessionManager;
    private TemplateManager templateManager;
    private PermissionSyncService permissionSyncService;
    private PermissionEnforcer permissionEnforcer;
    private ScheduledExecutorService executorService;

    // Connected Wrappers Management
    private final Map<Integer, WrapperConnection> connectedWrappers = new ConcurrentHashMap<>();
    private final Map<String, ServerInstance> runningServers = new ConcurrentHashMap<>();

    // Port Management
    private int nextAvailablePort;
    private final Set<Integer> usedPorts = ConcurrentHashMap.newKeySet();
    private final Map<String, Integer> serverPorts = new ConcurrentHashMap<>();

    // Feste Ports fuer erste Server
    private int FIRST_PROXY_PORT;
    private int FIRST_LOBBY_PORT;

    // Cluster State
    private String masterId = UUID.randomUUID().toString();
    private boolean isPrimaryMaster = true;
    private Set<String> clusterPeers = ConcurrentHashMap.newKeySet();
    private static final long SERVER_HEARTBEAT_TIMEOUT_MS = 30000;
    private static final long SERVER_STARTING_TIMEOUT_MS = 120000;
    private static final long WRAPPER_PONG_TIMEOUT_MS = 20000;

    public void start() {
        instance = this;
        executorService = Executors.newScheduledThreadPool(10);

        // Initialize Configuration ZUERST
        this.configManager = new ConfigManager();
        this.dataStore = new CloudDataStore();
        this.dataStore.initialize(configManager);
        this.playerSessionManager = new PlayerSessionManager(dataStore);
        this.templateManager = new TemplateManager();
        this.permissionSyncService = new PermissionSyncService(this, dataStore);
        this.permissionEnforcer = new PermissionEnforcer(permissionSyncService);

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
        ensureDefaultPermissionGroup();

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

        // Arrays fuer byte[]
        kryo.register(byte[].class);
    }

    // Port Management Methods
    public synchronized int assignPort(String serverName, String groupName) {
        // Feste Ports fuer erste Server (aus Config)
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

        // Fuer alle anderen Server: Dynamische Zuweisung
        int port = nextAvailablePort;
        while (usedPorts.contains(port) || !isPortAvailable(port)) {
            port++;
            if (port > 65535) {
                throw new RuntimeException("Keine verfuegbaren Ports mehr!");
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
        playerQueueManager = new PlayerQueueManager(this, loadBalancerManager, dataStore);

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
        } else if (object instanceof Message.Pong) {
            handlePong(connection, (Message.Pong) object);
        } else if (object instanceof Message.Ping) {
            handlePing(connection, (Message.Ping) object);
        } else if (object instanceof Message.ServerStatusMessage) {
            handleServerStatus(connection, (Message.ServerStatusMessage) object);
        } else if (object instanceof Message.ServerMetrics) {
            handleServerMetrics(connection, (Message.ServerMetrics) object);
        } else if (object instanceof Message.ServerLog) {
            handleServerLog((Message.ServerLog) object);
        } else if (object instanceof Message.ServerCommand) {
            handleServerCommand(connection, (Message.ServerCommand) object);
        } else if (object instanceof Message.PlayerJoinRequest) {
            handlePlayerJoinRequest(connection, (Message.PlayerJoinRequest) object);
        } else if (object instanceof Message.PlayerConnectRequest) {
            handlePlayerConnectRequest(connection, (Message.PlayerConnectRequest) object);
        } else if (object instanceof Message.QueueKeepAlive) {
            handleQueueKeepAlive((Message.QueueKeepAlive) object);
        } else if (object instanceof Message.ClusterSync) {
            handleClusterSync(connection, (Message.ClusterSync) object);
        } else if (object instanceof Message.ServerListRequest) {
            handleServerListRequest(connection, (Message.ServerListRequest) object);
        } else if (object instanceof Message.PluginHeartbeat) {
            handlePluginHeartbeat((Message.PluginHeartbeat) object);
        } else if (object instanceof Message.ConfigUpdate) {
            handleConfigUpdate((Message.ConfigUpdate) object);
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
            wrapper.updateHeartbeat(heartbeat.availableMemory, heartbeat.cpuUsage, heartbeat.activeServers);

            monitoringService.recordWrapperMetrics(wrapper);
        }
    }

    private void handlePong(Connection connection, Message.Pong pong) {
        WrapperConnection wrapper = connectedWrappers.get(connection.getID());
        if (wrapper != null) {
            wrapper.markPong(pong.pingTimestamp);
        }
    }

    private void handlePing(Connection connection, Message.Ping ping) {
        Message.Pong pong = new Message.Pong();
        pong.sourceId = masterId;
        pong.pingTimestamp = ping.timestamp;
        pong.timestamp = System.currentTimeMillis();
        connection.sendTCP(pong);
    }

    private void handleServerStatus(Connection connection, Message.ServerStatusMessage message) {
        ServerInstance instance = runningServers.get(message.serverName);
        boolean recoverableStatus = "ONLINE".equalsIgnoreCase(message.status) || "STARTING".equalsIgnoreCase(message.status);
        if (instance == null && recoverableStatus && message.groupName != null && !message.groupName.isBlank()) {
            WrapperConnection wrapper = connectedWrappers.get(connection.getID());
            String wrapperId = wrapper != null ? wrapper.wrapperId : "unknown";
            instance = new ServerInstance(
                    message.serverName,
                    message.groupName,
                    wrapperId,
                    configManager.getRamForGroup(message.groupName)
            );
            runningServers.put(message.serverName, instance);
            ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                    " Wiederhergestellt nach Reconnect: " + message.serverName + " (" + message.groupName + ")");
        }

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
        if (instance == null) {
            WrapperConnection wrapper = connectedWrappers.get(connection.getID());
            String group = (metrics.groupName != null && !metrics.groupName.isBlank())
                    ? metrics.groupName
                    : inferGroupFromServerName(metrics.serverName);
            String wrapperId = wrapper != null ? wrapper.wrapperId : "unknown";
            instance = new ServerInstance(metrics.serverName, group, wrapperId, configManager.getRamForGroup(group));
            runningServers.put(metrics.serverName, instance);
        }

        if (instance != null) {
            instance.playerCount = metrics.playerCount;
            instance.maxPlayers = metrics.maxPlayers;
            instance.tps = metrics.tps;
            instance.memoryUsage = metrics.memoryUsage;
            instance.cpuUsage = metrics.cpuUsage;
            instance.networkInBytes = metrics.networkInBytes;
            instance.networkOutBytes = metrics.networkOutBytes;
            instance.networkMode = metrics.networkMode == null ? "NONE" : metrics.networkMode;
            instance.diskReadBytes = metrics.diskReadBytes;
            instance.diskWriteBytes = metrics.diskWriteBytes;
            instance.lastUpdate = System.currentTimeMillis();

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
        if (request.playerUuid == null || request.playerUuid.isBlank()) {
            Message.PlayerJoinResponse response = new Message.PlayerJoinResponse();
            response.success = false;
            response.message = "Invalid player UUID";
            connection.sendTCP(response);
            return;
        }

        if (playerSessionManager.isDuplicateConnection(request.playerUuid)) {
            Message.PlayerJoinResponse response = new Message.PlayerJoinResponse();
            response.playerUuid = request.playerUuid;
            response.success = false;
            response.message = "Duplicate connection detected";
            connection.sendTCP(response);
            return;
        }

        playerSessionManager.touch(request.playerUuid);

        if (!permissionEnforcer.canJoinNetwork(request.playerUuid)) {
            Message.PlayerJoinResponse response = new Message.PlayerJoinResponse();
            response.playerUuid = request.playerUuid;
            response.success = false;
            response.message = "Missing permission: cloud.join";
            connection.sendTCP(response);
            return;
        }

        if (!permissionEnforcer.canJoinGroup(request.playerUuid, request.groupName)) {
            Message.PlayerJoinResponse response = new Message.PlayerJoinResponse();
            response.playerUuid = request.playerUuid;
            response.success = false;
            response.message = "Missing permission for group " + request.groupName;
            connection.sendTCP(response);
            return;
        }

        String lastServer = playerSessionManager.getLastServer(request.playerUuid);
        if (lastServer != null && !lastServer.isBlank()) {
            ServerInstance previous = runningServers.get(lastServer);
            if (previous != null && "ONLINE".equals(previous.status)) {
                Message.PlayerJoinResponse response = new Message.PlayerJoinResponse();
                response.playerUuid = request.playerUuid;
                response.targetServer = lastServer;
                response.success = true;
                response.message = "Reconnecting to last server";
                connection.sendTCP(response);
                playerSessionManager.assignServer(request.playerUuid, lastServer);
                return;
            }
        }

        boolean staffBypass = request.priority >= 100 || permissionEnforcer.canBypassMaintenance(request.playerUuid);
        boolean vipBypass = request.priority >= 50 || permissionEnforcer.canBypassServerFull(request.playerUuid);

        if (configManager.isMaintenanceMode(request.groupName) && !staffBypass) {
            List<String> whitelist = configManager.getGroupWhitelist(request.groupName);
            if (!whitelist.contains(request.playerUuid)) {
                Message.PlayerJoinResponse response = new Message.PlayerJoinResponse();
                response.playerUuid = request.playerUuid;
                response.success = false;
                response.message = "Group is in maintenance mode";
                connection.sendTCP(response);
                return;
            }
        }

        String targetServer = loadBalancerManager.getBestServer(request.groupName, request.playerUuid);
        if (targetServer == null && vipBypass) {
            targetServer = loadBalancerManager.getBestServerAllowFull(request.groupName, request.playerUuid);
        }

        if (targetServer != null) {
            Message.PlayerJoinResponse response = new Message.PlayerJoinResponse();
            response.playerUuid = request.playerUuid;
            response.targetServer = targetServer;
            response.success = true;
            connection.sendTCP(response);
            playerSessionManager.assignServer(request.playerUuid, targetServer);
            permissionSyncService.syncPlayerPermissions(request.playerUuid);
            notifyFriendsAboutLogin(request.playerUuid, targetServer);
        } else {
            if (permissionEnforcer.canBypassQueue(request.playerUuid)) {
                Message.PlayerJoinResponse response = new Message.PlayerJoinResponse();
                response.playerUuid = request.playerUuid;
                response.success = false;
                response.message = "No server available (queue bypass active)";
                connection.sendTCP(response);
                return;
            }
            playerQueueManager.addToQueue(request.playerUuid, request.groupName);

            Message.PlayerJoinResponse response = new Message.PlayerJoinResponse();
            response.playerUuid = request.playerUuid;
            response.success = false;
            response.queuePosition = playerQueueManager.getQueuePosition(request.playerUuid, request.groupName);
            response.message = "No server available, added to queue";
            connection.sendTCP(response);
        }
    }

    private void handleClusterSync(Connection connection, Message.ClusterSync sync) {
        clusterManager.handleSyncMessage(sync);
    }

    private void handleServerLog(Message.ServerLog log) {
        String source = "Server/" + log.serverName;
        if ("ERROR".equalsIgnoreCase(log.level)) {
            CentralLogger.error(source, log.message);
        } else if ("WARN".equalsIgnoreCase(log.level) || "WARNING".equalsIgnoreCase(log.level)) {
            CentralLogger.warn(source, log.message);
        } else if ("DEBUG".equalsIgnoreCase(log.level)) {
            CentralLogger.debug(source, log.message);
        } else {
            CentralLogger.info(source, log.message);
        }
    }

    private void handleConfigUpdate(Message.ConfigUpdate update) {
        reloadConfiguration("remote:" + update.configType);
    }

    private void handleQueueKeepAlive(Message.QueueKeepAlive keepAlive) {
        playerQueueManager.markQueueActivity(keepAlive.playerUuid, keepAlive.groupName);
    }

    private void handlePlayerConnectRequest(Connection connection, Message.PlayerConnectRequest request) {
        if (!permissionEnforcer.canSwitchServer(request.playerUuid)) {
            sendConnectResponse(connection, request.playerUuid, false, null, "Missing permission: cloud.server.switch");
            return;
        }

        String target = request.targetServer;
        if (target == null || target.isBlank()) {
            sendConnectResponse(connection, request.playerUuid, false, null, "targetServer required");
            return;
        }

        if ("hub".equalsIgnoreCase(target)) {
            if (!permissionEnforcer.canUseHub(request.playerUuid)) {
                sendConnectResponse(connection, request.playerUuid, false, null, "Missing permission: cloud.hub");
                return;
            }
            String lobby = loadBalancerManager.getBestServer("Lobby", request.playerUuid);
            if (lobby == null) {
                sendConnectResponse(connection, request.playerUuid, false, null, "No lobby available");
                return;
            }
            target = lobby;
        }

        ServerInstance instance = runningServers.get(target);
        if (instance == null || !"ONLINE".equals(instance.status)) {
            sendConnectResponse(connection, request.playerUuid, false, null, "Target server offline");
            return;
        }

        if (configManager.isMaintenanceMode(instance.groupName) && !permissionEnforcer.canBypassMaintenance(request.playerUuid)) {
            List<String> whitelist = configManager.getGroupWhitelist(instance.groupName);
            if (!whitelist.contains(request.playerUuid)) {
                sendConnectResponse(connection, request.playerUuid, false, null, "Target group in maintenance");
                return;
            }
        }

        if (instance.playerCount >= instance.maxPlayers && !permissionEnforcer.canBypassServerFull(request.playerUuid)) {
            sendConnectResponse(connection, request.playerUuid, false, null, "Target server full");
            return;
        }

        Message.PlayerTransfer transfer = new Message.PlayerTransfer();
        transfer.playerUuid = request.playerUuid;
        transfer.fromServer = request.fromServer;
        transfer.toServer = target;
        transfer.reason = "Manual switch";
        server.sendToAllTCP(transfer);
        playerSessionManager.assignServer(request.playerUuid, target);
        sendConnectResponse(connection, request.playerUuid, true, target, "Switch initiated");
    }

    private void sendConnectResponse(Connection connection, String playerUuid, boolean success, String targetServer, String message) {
        Message.PlayerConnectResponse response = new Message.PlayerConnectResponse();
        response.playerUuid = playerUuid;
        response.success = success;
        response.targetServer = targetServer;
        response.message = message;
        connection.sendTCP(response);
    }

    private void handleDisconnection(Connection connection) {
        WrapperConnection wrapper = connectedWrappers.remove(connection.getID());
        if (wrapper != null) {
            ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                    " Wrapper getrennt: " + wrapper.wrapperId);

            runningServers.values().stream()
                    .filter(s -> s.wrapperId.equals(wrapper.wrapperId))
                    .toList()
                    .forEach(s -> handleServerFailure(s, true));

            clusterManager.notifyWrapperLeft(wrapper);
        }
    }

    private void startEnterpriseServices() {
        executorService.scheduleAtFixedRate(() -> {
            checkWrapperHealth();
        }, 10, 10, TimeUnit.SECONDS);

        executorService.scheduleAtFixedRate(() -> {
            pingWrappers();
        }, 5, 5, TimeUnit.SECONDS);

        executorService.scheduleAtFixedRate(() -> {
            checkServerHealth();
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

        executorService.scheduleAtFixedRate(() -> {
            syncActivePermissionProfiles();
        }, 60, 60, TimeUnit.SECONDS);

        executorService.scheduleAtFixedRate(() -> {
            cleanupPendingSocialRequests();
        }, 60, 60, TimeUnit.SECONDS);

        // Auto-Start configured servers after 10 seconds
        int autoStartDelay = configManager.getAutoStartDelay();
        executorService.schedule(() -> {
            autoStartServers();
        }, autoStartDelay, TimeUnit.SECONDS);

        ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Enterprise-Services gestartet (Auto-Start in " + autoStartDelay + "s)");
    }

    private void autoStartServers() {
        // Pruefe ob Auto-Start aktiviert ist
        if (!configManager.isAutoStartEnabled()) {
            ConsoleScreenManager.printToTerminal(ConsoleColors.YELLOW + ConsoleColors.PREFIX +
                    ConsoleColors.getCurrentTime() + " Auto-Start ist deaktiviert");
            return;
        }

        // Warte bis mindestens ein Wrapper verbunden ist
        if (connectedWrappers.isEmpty()) {
            ConsoleScreenManager.printToTerminal(ConsoleColors.YELLOW + ConsoleColors.PREFIX +
                    ConsoleColors.getCurrentTime() + " Kein Wrapper verfuegbar fuer Auto-Start - Retry in 5s");

            executorService.schedule(() -> {
                autoStartServers();
            }, 5, TimeUnit.SECONDS);
            return;
        }

        ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Starte Auto-Start fuer konfigurierte Server...");

        // Aus Config laden
        List<String> autoStartGroups = configManager.getAutoStartGroups();

        for (String groupConfig : autoStartGroups) {
            String[] parts = groupConfig.split(":");
            String groupName = parts[0].trim();
            int count = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 1;

            for (int i = 1; i <= count; i++) {
                String serverName = groupName + "-" + i;

                // Nur starten wenn noch nicht laeuft
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
                ConsoleColors.getCurrentTime() + " [OK] Auto-Start abgeschlossen");
    }

    private void checkWrapperHealth() {
        long now = System.currentTimeMillis();
        connectedWrappers.values().forEach(wrapper -> {
            if (now - wrapper.lastHeartbeat > 30000) {
                ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                        " Wrapper " + wrapper.wrapperId + " antwortet nicht - wird als offline markiert");
                wrapper.connection.close();
            } else if (now - wrapper.lastPong > WRAPPER_PONG_TIMEOUT_MS) {
                ConsoleScreenManager.printToTerminal(ConsoleColors.YELLOW + ConsoleColors.PREFIX +
                        ConsoleColors.getCurrentTime() + " Wrapper " + wrapper.wrapperId +
                        " hat kein Pong gesendet - Verbindung wird geprueft");
                wrapper.connection.close();
            }
        });
    }

    private void pingWrappers() {
        long now = System.currentTimeMillis();
        for (WrapperConnection wrapper : connectedWrappers.values()) {
            Message.Ping ping = new Message.Ping();
            ping.sourceId = masterId;
            ping.timestamp = now;
            wrapper.markPingSent(now);
            wrapper.connection.sendTCP(ping);
        }
    }

    private void checkServerHealth() {
        long now = System.currentTimeMillis();
        for (ServerInstance server : new ArrayList<>(runningServers.values())) {
            if ("ONLINE".equals(server.status) && now - server.lastUpdate > SERVER_HEARTBEAT_TIMEOUT_MS) {
                ConsoleScreenManager.printToTerminal(ConsoleColors.RED + ConsoleColors.PREFIX +
                        ConsoleColors.getCurrentTime() + " Kein Heartbeat von " + server.serverName +
                        " seit " + ((now - server.lastUpdate) / 1000) + "s - markiere als CRASHED");
                handleServerFailure(server, true);
            } else if ("STARTING".equals(server.status) && now - server.startTime > SERVER_STARTING_TIMEOUT_MS) {
                ConsoleScreenManager.printToTerminal(ConsoleColors.RED + ConsoleColors.PREFIX +
                        ConsoleColors.getCurrentTime() + " STARTING-Timeout bei " + server.serverName +
                        " - stoppe und starte neu");
                forceStopServer(server.serverName);
                executorService.schedule(() -> startServer(server.serverName, server.groupName), 3, TimeUnit.SECONDS);
            }
        }
    }

    private void handleServerFailure(ServerInstance server, boolean recover) {
        ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Server-Ausfall erkannt: " + server.serverName + " - Initiiere Wiederherstellung");
        monitoringService.publishEvent("SERVER_CRASH", Map.of(
                "server", server.serverName,
                "group", server.groupName,
                "wrapper", server.wrapperId,
                "recover", recover
        ));

        // Release port
        Integer port = serverPorts.remove(server.serverName);
        if (port != null) {
            releasePort(port);
        }

        runningServers.remove(server.serverName);

        if (recover || server.isCritical) {
            transferPlayersFromFailedServer(server);
            autoScalingManager.replaceFailedServer(server);
        }
    }

    private void transferPlayersFromFailedServer(ServerInstance failedServer) {
        String fallback = loadBalancerManager.getBestServer("Lobby", "recovery-" + System.currentTimeMillis());
        if (fallback == null) {
            ConsoleScreenManager.printToTerminal(ConsoleColors.YELLOW + ConsoleColors.PREFIX +
                    ConsoleColors.getCurrentTime() + " Kein Fallback-Lobby-Server verfuegbar fuer " + failedServer.serverName);
            monitoringService.publishEvent("NO_FALLBACK_SERVER", Map.of(
                    "failedServer", failedServer.serverName,
                    "group", failedServer.groupName
            ));
            return;
        }

        Map<String, String> sessions = playerSessionManager.getPlayerServerMapSnapshot();
        List<String> affectedPlayers = sessions.entrySet().stream()
                .filter(entry -> failedServer.serverName.equalsIgnoreCase(entry.getValue()))
                .map(Map.Entry::getKey)
                .toList();

        for (String playerUuid : affectedPlayers) {
            Message.PlayerTransfer transfer = new Message.PlayerTransfer();
            transfer.playerUuid = playerUuid;
            transfer.fromServer = failedServer.serverName;
            transfer.toServer = fallback;
            transfer.reason = "Server crashed";
            server.sendToAllTCP(transfer);
            playerSessionManager.assignServer(playerUuid, fallback);
        }

        monitoringService.publishEvent("CRASH_PLAYER_TRANSFER", Map.of(
                "fromServer", failedServer.serverName,
                "toServer", fallback,
                "playersTransferred", affectedPlayers.size()
        ));
    }

    public void startServer(String serverName, String groupName) {
        WrapperConnection bestWrapper = loadBalancerManager.getBestWrapperForServer(groupName);

        if (bestWrapper == null) {
            ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                    " FEHLER: Kein verfuegbarer Wrapper fuer Server " + serverName);
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
        monitoringService.publishEvent("SERVER_START", Map.of(
                "server", serverName,
                "group", groupName,
                "wrapper", bestWrapper.wrapperId,
                "port", assignedPort
        ));

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
            command.command = "GRACEFUL_STOP";
            command.serverName = serverName;
            wrapper.connection.sendTCP(command);
            monitoringService.publishEvent("SERVER_STOP", Map.of(
                    "server", serverName,
                    "group", instance.groupName,
                    "wrapper", wrapper.wrapperId,
                    "mode", "graceful"
            ));
        }

        // Release port
        Integer port = serverPorts.remove(serverName);
        if (port != null) {
            releasePort(port);
        }

        runningServers.remove(serverName);
    }

    public void forceStopServer(String serverName) {
        ServerInstance instance = runningServers.get(serverName);
        if (instance == null) {
            return;
        }

        WrapperConnection wrapper = getWrapperById(instance.wrapperId);
        if (wrapper != null) {
            Message.ServerCommand command = new Message.ServerCommand();
            command.command = "FORCE_STOP";
            command.serverName = serverName;
            wrapper.connection.sendTCP(command);
            monitoringService.publishEvent("SERVER_STOP", Map.of(
                    "server", serverName,
                    "group", instance.groupName,
                    "wrapper", wrapper.wrapperId,
                    "mode", "force"
            ));
        }

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

        if (dataStore != null) {
            dataStore.shutdown();
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

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public CloudDataStore getDataStore() {
        return dataStore;
    }

    public TemplateManager getTemplateManager() {
        return templateManager;
    }

    public PlayerSessionManager getPlayerSessionManager() {
        return playerSessionManager;
    }

    public void syncPermissionsForPlayer(String playerUuid) {
        permissionSyncService.syncPlayerPermissions(playerUuid);
    }

    public void reloadConfiguration(String trigger) {
        configManager.backupConfigs(trigger);
        configManager.reloadAllConfigs();
        autoScalingManager.reloadPolicies();
        for (String group : configManager.getAllServerGroups()) {
            templateManager.createSnapshot(group);
            templateManager.applyIncrementalBackup(group);
        }
        CentralLogger.audit("system", "config_reload", trigger);
        ConsoleScreenManager.printToTerminal(ConsoleColors.GREEN + ConsoleColors.PREFIX +
                ConsoleColors.getCurrentTime() + " Konfiguration neu geladen (" + trigger + ")");
    }

    private void ensureDefaultPermissionGroup() {
        PermissionGroup existing = dataStore.getPermissionGroup("default");
        if (existing != null) {
            return;
        }
        PermissionGroup group = new PermissionGroup("default");
        group.weight = 0;
        group.prefix = "";
        group.suffix = "";
        group.permissions = List.of("cloud.join", "cloud.queue");
        dataStore.upsertPermissionGroup(group);
    }

    private void notifyFriendsAboutLogin(String playerUuid, String serverName) {
        List<String> friends = dataStore.getFriends(playerUuid);
        for (String friend : friends) {
            Message.PlayerNotification notification = new Message.PlayerNotification();
            notification.playerUuid = friend;
            notification.type = "FRIEND_ONLINE";
            notification.message = playerUuid + " is now on " + serverName;
            notification.timestamp = System.currentTimeMillis();
            server.sendToAllTCP(notification);
        }
    }

    private String inferGroupFromServerName(String serverName) {
        if (serverName == null || !serverName.contains("-")) {
            return "Lobby";
        }
        return serverName.substring(0, serverName.indexOf('-'));
    }

    private void syncActivePermissionProfiles() {
        for (String playerUuid : playerSessionManager.getPlayerServerMapSnapshot().keySet()) {
            permissionSyncService.syncPlayerPermissions(playerUuid);
        }
    }

    private void cleanupPendingSocialRequests() {
        int removed = dataStore.cleanupExpiredSocialPending(System.currentTimeMillis());
        if (removed > 0) {
            ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                    " Cleanup: " + removed + " abgelaufene Friend/Party Pending-Eintraege entfernt");
        }
    }
}
