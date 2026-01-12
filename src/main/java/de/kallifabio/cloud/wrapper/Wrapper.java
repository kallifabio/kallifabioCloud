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
import de.kallifabio.cloud.libs.console.ConsoleColors;
import de.kallifabio.cloud.libs.Message;
import de.kallifabio.cloud.libs.console.ConsoleScreenManager;
import de.kallifabio.cloud.master.Master;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.*;

public class Wrapper {

    private Client client;
    private String wrapperId;
    private String hostname;
    private int maxMemory;
    private int availableMemory;

    // Server Management
    private final Map<String, Serverprocess> managedServers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);

    // Connection State
    private volatile boolean connected = false;
    private volatile boolean reconnecting = false;
    private int reconnectAttempts = 0;
    private static final int MAX_RECONNECT_ATTEMPTS = 10;
    private static final long RECONNECT_DELAY_MS = 5000;

    // Heartbeat
    private ScheduledFuture<?> heartbeatTask;
    private static final long HEARTBEAT_INTERVAL_MS = 5000;

    // Metrics
    private final OperatingSystemMXBean osBean;

    public Wrapper() {
        this.wrapperId = generateWrapperId();
        this.hostname = detectHostname();
        this.maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024 / 1024);
        this.availableMemory = calculateAvailableMemory();
        this.osBean = ManagementFactory.getOperatingSystemMXBean();

        ConsoleScreenManager.logToMainScreen(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Wrapper-ID: " + wrapperId);
    }

    public void start() {
        ConsoleScreenManager.logToMainScreen(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
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

        ConsoleScreenManager.logToMainScreen(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
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

        ConsoleScreenManager.logToMainScreen(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
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
                    ConsoleScreenManager.logToMainScreen(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                            " Wrapper-Fehler: " + cause.getMessage());
                }
            }
        });
    }

    private void connectToMaster() {
        // Im COMBINED Mode verwende localhost
        String masterHost = "127.0.0.1";
        int tcpPort = 54555;
        int udpPort = 54777;

        // Wenn Master-Instanz existiert, verwende dessen Werte
        if (Master.getInstance() != null) {
            // Im Combined-Mode immer localhost verwenden
            masterHost = "127.0.0.1";
            tcpPort = 54555;
            udpPort = 54777;
        }

        try {
            ConsoleScreenManager.logToMainScreen(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                    " Verbinde zu Master: " + masterHost + ":" + tcpPort);

            client.connect(10000, masterHost, tcpPort, udpPort);

        } catch (IOException e) {
            ConsoleScreenManager.logToMainScreen(ConsoleColors.RED + ConsoleColors.PREFIX +
                    ConsoleColors.getCurrentTime() + " Verbindung zu Master fehlgeschlagen: " + e.getMessage());
            e.printStackTrace(); // Zeige vollständigen Stacktrace für Debugging

            // Schedule reconnect
            scheduleReconnect();
        }
    }

    private void handleConnected(Connection connection) {
        connected = true;
        reconnectAttempts = 0;

        ConsoleScreenManager.logToMainScreen(ConsoleColors.GREEN + ConsoleColors.PREFIX +
                ConsoleColors.getCurrentTime() + " ✓ Mit Master verbunden");

        // Send registration
        registerWithMaster();

        // Start heartbeat
        startHeartbeat();
    }

    private void handleDisconnected(Connection connection) {
        if (connected) {
            connected = false;

            ConsoleScreenManager.logToMainScreen(ConsoleColors.YELLOW + ConsoleColors.PREFIX +
                    ConsoleColors.getCurrentTime() + " Verbindung zu Master verloren");

            // Stop heartbeat
            stopHeartbeat();

            // FIX: Warte 1 Sekunde vor Reconnect
            scheduler.schedule(() -> {
                if (!reconnecting) {
                    scheduleReconnect();
                }
            }, 1000, TimeUnit.MILLISECONDS);
        }
    }

    private void registerWithMaster() {
        Message.WrapperRegister register = new Message.WrapperRegister();
        register.wrapperId = wrapperId;
        register.hostname = hostname;
        register.maxMemory = maxMemory;
        register.availableMemory = calculateAvailableMemory();
        register.version = "1.0.0";

        client.sendTCP(register);

        ConsoleScreenManager.logToMainScreen(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Registrierung gesendet");
    }

    private void handleMessage(Connection connection, Object object) {
        if (object instanceof Message.WrapperRegisterAck) {
            handleRegisterAck((Message.WrapperRegisterAck) object);
        } else if (object instanceof Message.ServerCommand) {
            handleServerCommand((Message.ServerCommand) object);
        } else if (object instanceof Message.PlayerTransfer) {
            handlePlayerTransfer((Message.PlayerTransfer) object);
        }
    }

    private void handleRegisterAck(Message.WrapperRegisterAck ack) {
        if (ack.success) {
            ConsoleScreenManager.logToMainScreen(ConsoleColors.GREEN + ConsoleColors.PREFIX +
                    ConsoleColors.getCurrentTime() + " ✓ Registrierung bestätigt von Master: " + ack.masterId);
        } else {
            ConsoleScreenManager.logToMainScreen(ConsoleColors.RED + ConsoleColors.PREFIX +
                    ConsoleColors.getCurrentTime() + " Registrierung abgelehnt");
        }
    }

    private void handleServerCommand(Message.ServerCommand command) {
        switch (command.command.toUpperCase()) {
            case "START" -> startServer(command.serverName, command.groupName, command.port);
            case "STOP" -> stopServer(command.serverName);
            case "RESTART" -> restartServer(command.serverName);
            default -> ConsoleScreenManager.logToMainScreen(ConsoleColors.PREFIX +
                    ConsoleColors.getCurrentTime() + " Unbekannter Befehl: " + command.command);
        }
    }

    private void handlePlayerTransfer(Message.PlayerTransfer transfer) {
        ConsoleScreenManager.logToMainScreen(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Spieler-Transfer: " + transfer.playerUuid + " von " + transfer.fromServer +
                " zu " + transfer.toServer);

        // Forward to appropriate server
        Serverprocess server = managedServers.get(transfer.toServer);
        if (server != null) {
            server.sendCommand("transfer " + transfer.playerUuid);
        }
    }

    private void startServer(String serverName, String groupName, int port) {
        if (managedServers.containsKey(serverName)) {
            ConsoleScreenManager.logToMainScreen(ConsoleColors.YELLOW + ConsoleColors.PREFIX +
                    ConsoleColors.getCurrentTime() + " Server " + serverName + " läuft bereits");
            return;
        }

        ConsoleScreenManager.logToMainScreen(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Starte Server: " + serverName + " (" + groupName + ") auf Port " + port);

        try {
            // Create and start server process with assigned port
            Serverprocess serverProcess = new Serverprocess(serverName, groupName, port, this);
            serverProcess.start();
            managedServers.put(serverName, serverProcess);

            // Update available memory
            availableMemory = calculateAvailableMemory();

            ConsoleScreenManager.logToMainScreen(ConsoleColors.GREEN + ConsoleColors.PREFIX +
                    ConsoleColors.getCurrentTime() + " ✓ Server " + serverName +
                    " erfolgreich gestartet auf Port " + port);

        } catch (IOException e) {
            ConsoleScreenManager.logToMainScreen(ConsoleColors.RED + ConsoleColors.PREFIX +
                    ConsoleColors.getCurrentTime() + " FEHLER beim Starten von " + serverName + ": " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            ConsoleScreenManager.logToMainScreen(ConsoleColors.RED + ConsoleColors.PREFIX +
                    ConsoleColors.getCurrentTime() + " Unerwarteter Fehler bei " + serverName + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void stopServer(String serverName) {
        Serverprocess server = managedServers.get(serverName);
        if (server == null) {
            ConsoleScreenManager.logToMainScreen(ConsoleColors.YELLOW + ConsoleColors.PREFIX +
                    ConsoleColors.getCurrentTime() + " Server " + serverName + " nicht gefunden");
            return;
        }

        ConsoleScreenManager.logToMainScreen(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Stoppe Server: " + serverName);

        server.stop();
        managedServers.remove(serverName);

        // Update available memory
        availableMemory = calculateAvailableMemory();
    }

    private void restartServer(String serverName) {
        Serverprocess server = managedServers.get(serverName);
        if (server == null) {
            ConsoleScreenManager.logToMainScreen(ConsoleColors.YELLOW + ConsoleColors.PREFIX +
                    ConsoleColors.getCurrentTime() + " Server " + serverName + " nicht gefunden");
            return;
        }

        ConsoleScreenManager.logToMainScreen(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Starte Server neu: " + serverName);

        String groupName = server.getGroupName();
        stopServer(serverName);

        // Wait a bit before restarting
        scheduler.schedule(() -> {
            startServer(serverName, groupName, server.getPort());
        }, 3, TimeUnit.SECONDS);
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
        if (reconnecting) return;

        reconnecting = true;
        reconnectAttempts++;

        if (reconnectAttempts > MAX_RECONNECT_ATTEMPTS) {
            ConsoleScreenManager.logToMainScreen(ConsoleColors.RED + ConsoleColors.PREFIX +
                    ConsoleColors.getCurrentTime() + " Maximale Anzahl an Verbindungsversuchen erreicht");
            return;
        }

        ConsoleScreenManager.logToMainScreen(ConsoleColors.YELLOW + ConsoleColors.PREFIX +
                ConsoleColors.getCurrentTime() + " Verbindungsversuch in " +
                (RECONNECT_DELAY_MS / 1000) + " Sekunden... (Versuch " + reconnectAttempts +
                "/" + MAX_RECONNECT_ATTEMPTS + ")");

        scheduler.schedule(() -> {
            reconnecting = false;

            // FIX: Client komplett neu erstellen
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
    }

    private void collectAndSendMetrics() {
        if (!connected) return;

        for (Serverprocess server : managedServers.values()) {
            Message.ServerMetrics metrics = server.collectMetrics();
            if (metrics != null) {
                client.sendTCP(metrics);
            }
        }
    }

    private void checkServerHealth() {
        List<String> unhealthyServers = new ArrayList<>();

        for (Map.Entry<String, Serverprocess> entry : managedServers.entrySet()) {
            if (!entry.getValue().isHealthy()) {
                unhealthyServers.add(entry.getKey());
            }
        }

        for (String serverName : unhealthyServers) {
            ConsoleScreenManager.logToMainScreen(ConsoleColors.YELLOW + ConsoleColors.PREFIX +
                    ConsoleColors.getCurrentTime() + " Server " + serverName + " ist unhealthy - Neustart wird eingeleitet");
            restartServer(serverName);
        }
    }

    private int calculateAvailableMemory() {
        int usedMemory = managedServers.values().stream()
                .mapToInt(Serverprocess::getAllocatedMemory)
                .sum();
        return maxMemory - usedMemory;
    }

    private double getCpuUsage() {
        if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
            return ((com.sun.management.OperatingSystemMXBean) osBean).getProcessCpuLoad() * 100;
        }
        return 0.0;
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

    public void sendServerStatus(String serverName, String status) {
        if (!connected) return;

        Message.ServerStatusMessage message = new Message.ServerStatusMessage();
        message.serverName = serverName;
        message.status = status;

        client.sendTCP(message);
    }

    public void shutdown() {
        ConsoleScreenManager.logToMainScreen(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Wrapper wird heruntergefahren...");

        // Stop all servers
        List<String> serverNames = new ArrayList<>(managedServers.keySet());
        for (String serverName : serverNames) {
            stopServer(serverName);
        }

        // Stop heartbeat
        stopHeartbeat();

        // Stop scheduler
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }

        // Disconnect from master
        if (client != null) {
            client.stop();
        }

        ConsoleScreenManager.logToMainScreen(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
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
}
