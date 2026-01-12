/**
 * Erstellt von Gamer_Kidd_LP | kallifabio
 * am 09.01.2026 um 20:43
 * Projektname: CloudSystemTest
 * Packagename: de.kallifabio.cloud.master.cluster
 */

package de.kallifabio.cloud.master.cluster;

import de.kallifabio.cloud.libs.console.ConsoleColors;
import de.kallifabio.cloud.libs.Message;
import de.kallifabio.cloud.libs.console.ConsoleScreenManager;
import de.kallifabio.cloud.master.Master;
import de.kallifabio.cloud.master.WrapperConnection;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ClusterManager {

    private final Master master;
    private final String masterId;
    private final Map<String, ClusterNode> clusterNodes = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);

    private ClusterState currentState = ClusterState.INITIALIZING;
    private String primaryMasterId;
    private long lastHeartbeatSent;
    private long lastHeartbeatReceived;

    // Cluster configuration
    private static final long HEARTBEAT_INTERVAL_MS = 5000;
    private static final long HEARTBEAT_TIMEOUT_MS = 15000;
    private static final long STATE_SYNC_INTERVAL_MS = 10000;
    private static final int ELECTION_TIMEOUT_MS = 5000;

    // Split-brain prevention
    private int electionTerm = 0;
    private String votedFor = null;
    private final Set<String> receivedVotes = ConcurrentHashMap.newKeySet();

    public ClusterManager(Master master, String masterId) {
        this.master = master;
        this.masterId = masterId;
        this.primaryMasterId = masterId;

        initializeCluster();
        startClusterServices();
    }

    private void initializeCluster() {
        // Register self as cluster node
        ClusterNode selfNode = new ClusterNode(
                masterId,
                master.getMasterHost(),
                master.getMasterPort(),
                true,
                System.currentTimeMillis()
        );
        clusterNodes.put(masterId, selfNode);

        // Check for existing cluster
        discoverClusterPeers();

        ConsoleScreenManager.logToMainScreen(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Cluster Manager initialisiert - Master ID: " + masterId);
    }

    private void discoverClusterPeers() {
        // Try to discover other masters in the network
        // This could use multicast, consul, etcd, or configured peer list
        List<String> configuredPeers = loadConfiguredPeers();

        for (String peerAddress : configuredPeers) {
            try {
                // Attempt to connect to peer
                connectToPeer(peerAddress);
            } catch (Exception e) {
                ConsoleScreenManager.logToMainScreen(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                        " Konnte nicht zu Peer verbinden: " + peerAddress);
            }
        }

        if (clusterNodes.size() == 1) {
            // No other masters found, become primary
            becomePrimary();
        } else {
            // Join existing cluster
            joinCluster();
        }
    }

    private List<String> loadConfiguredPeers() {
        // Load from configuration
        // For now, return empty list - could be extended to read from config
        return new ArrayList<>();
    }

    private void connectToPeer(String peerAddress) {
        // Implementation for connecting to peer master
        // Would use KryoNet or HTTP for inter-master communication
    }

    private void startClusterServices() {
        // Heartbeat sender
        scheduler.scheduleAtFixedRate(() -> {
            sendHeartbeat();
        }, 0, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);

        // Heartbeat monitor
        scheduler.scheduleAtFixedRate(() -> {
            checkHeartbeats();
        }, HEARTBEAT_TIMEOUT_MS, HEARTBEAT_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        // State synchronization
        scheduler.scheduleAtFixedRate(() -> {
            syncClusterState();
        }, STATE_SYNC_INTERVAL_MS, STATE_SYNC_INTERVAL_MS, TimeUnit.MILLISECONDS);

        ConsoleScreenManager.logToMainScreen(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Cluster-Services gestartet");
    }

    private void sendHeartbeat() {
        Message.ClusterHeartbeat heartbeat = new Message.ClusterHeartbeat();
        heartbeat.masterId = masterId;
        heartbeat.isPrimary = master.isPrimaryMaster();
        heartbeat.connectedWrappers = master.getConnectedWrappers().size();
        heartbeat.runningServers = master.getRunningServers().size();
        heartbeat.uptime = System.currentTimeMillis() - clusterNodes.get(masterId).startTime;

        // Broadcast to all cluster peers
        broadcastToPeers(heartbeat);

        // Auch als ClusterSync Message für handleHeartbeat
        Message.ClusterSync syncHeartbeat = new Message.ClusterSync();
        syncHeartbeat.masterId = masterId;
        syncHeartbeat.messageType = "HEARTBEAT";
        syncHeartbeat.timestamp = System.currentTimeMillis();
        syncHeartbeat.data.put("connectedWrappers", heartbeat.connectedWrappers);
        syncHeartbeat.data.put("runningServers", heartbeat.runningServers);
        syncHeartbeat.data.put("isPrimary", heartbeat.isPrimary);

        broadcastToPeers(syncHeartbeat);

        lastHeartbeatSent = System.currentTimeMillis();
    }

    private void checkHeartbeats() {
        long now = System.currentTimeMillis();
        List<String> failedNodes = new ArrayList<>();

        for (Map.Entry<String, ClusterNode> entry : clusterNodes.entrySet()) {
            if (entry.getKey().equals(masterId)) continue;

            ClusterNode node = entry.getValue();
            if (now - node.lastHeartbeat > HEARTBEAT_TIMEOUT_MS) {
                ConsoleScreenManager.logToMainScreen(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                        " Cluster-Knoten " + node.masterId + " antwortet nicht mehr");
                failedNodes.add(entry.getKey());
            }
        }

        // Handle failed nodes
        for (String failedNodeId : failedNodes) {
            handleNodeFailure(failedNodeId);
        }
    }

    private void handleNodeFailure(String failedNodeId) {
        ClusterNode failedNode = clusterNodes.remove(failedNodeId);
        if (failedNode == null) return;

        ConsoleScreenManager.logToMainScreen(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Master-Knoten ausgefallen: " + failedNodeId);

        // If primary master failed, trigger election
        if (failedNodeId.equals(primaryMasterId)) {
            ConsoleScreenManager.logToMainScreen(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                    " Primary Master ausgefallen - starte Wahl");
            startElection();
        }
    }

    public void syncClusterState() {
        if (!master.isPrimaryMaster()) {
            return; // Only primary syncs state
        }

        Message.ClusterSync sync = new Message.ClusterSync();
        sync.masterId = masterId;
        sync.messageType = "STATE";
        sync.timestamp = System.currentTimeMillis();

        // Add cluster state data
        sync.data.put("runningServers", new HashMap<>(master.getRunningServers()));
        sync.data.put("connectedWrappers", new HashMap<>(master.getConnectedWrappers()));
        sync.data.put("clusterNodes", new HashMap<>(clusterNodes));

        broadcastToPeers(sync);
    }

    private void startElection() {
        if (currentState == ClusterState.ELECTION) {
            return; // Already in election
        }

        currentState = ClusterState.ELECTION;
        electionTerm++;
        votedFor = masterId; // Vote for self
        receivedVotes.clear();
        receivedVotes.add(masterId);

        ConsoleScreenManager.logToMainScreen(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Starte Master-Wahl - Term: " + electionTerm);

        // Send election request to all peers
        Message.MasterElection election = new Message.MasterElection();
        election.candidateId = masterId;
        election.priority = calculateElectionPriority();
        election.uptime = System.currentTimeMillis() - clusterNodes.get(masterId).startTime;
        election.connectedWrappers = master.getConnectedWrappers().size();

        broadcastToPeers(election);

        // Wait for votes
        scheduler.schedule(() -> {
            evaluateElection();
        }, ELECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    private int calculateElectionPriority() {
        int priority = 0;

        // Factors for election priority
        priority += master.getConnectedWrappers().size() * 10; // Connected wrappers
        priority += master.getRunningServers().size() * 5; // Running servers
        priority += (clusterNodes.get(masterId).isPrimary ? 100 : 0); // Current primary bonus

        return priority;
    }

    private void evaluateElection() {
        int quorum = (clusterNodes.size() / 2) + 1;

        if (receivedVotes.size() >= quorum) {
            ConsoleScreenManager.logToMainScreen(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                    " Wahl gewonnen mit " + receivedVotes.size() + " Stimmen (Quorum: " + quorum + ")");
            becomePrimary();
        } else {
            ConsoleScreenManager.logToMainScreen(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                    " Wahl verloren - nicht genug Stimmen erhalten");
            currentState = ClusterState.FOLLOWER;
        }
    }

    private void becomePrimary() {
        master.setPrimaryMaster(true);
        primaryMasterId = masterId;
        currentState = ClusterState.PRIMARY;

        ConsoleScreenManager.logToMainScreen(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Dieser Master ist jetzt PRIMARY");

        // Notify all cluster nodes
        Message.ClusterSync takeover = new Message.ClusterSync();
        takeover.masterId = masterId;
        takeover.messageType = "TAKEOVER";
        takeover.data.put("term", electionTerm);
        takeover.timestamp = System.currentTimeMillis();

        broadcastToPeers(takeover);

        // Take over responsibilities
        takeOverPrimaryResponsibilities();
    }

    private void takeOverPrimaryResponsibilities() {
        // Rebalance wrappers if needed
        // Sync state from other masters
        // Resume auto-scaling decisions
        ConsoleScreenManager.logToMainScreen(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Primary-Verantwortlichkeiten übernommen");
    }

    private void joinCluster() {
        currentState = ClusterState.FOLLOWER;
        master.setPrimaryMaster(false);

        ConsoleScreenManager.logToMainScreen(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Cluster beigetreten als FOLLOWER");

        // Request state sync from primary
        requestStateSync();
    }

    private void requestStateSync() {
        Message.ClusterSync request = new Message.ClusterSync();
        request.masterId = masterId;
        request.messageType = "STATE_REQUEST";
        request.timestamp = System.currentTimeMillis();

        broadcastToPeers(request);
    }

    public void handleSyncMessage(Message.ClusterSync sync) {
        switch (sync.messageType) {
            case "STATE":
                handleStateSync(sync);
                break;
            case "HEARTBEAT":
                handleHeartbeat(sync);
                break;
            case "TAKEOVER":
                handleTakeover(sync);
                break;
            case "STATE_REQUEST":
                handleStateRequest(sync);
                break;
        }
    }

    private void handleStateSync(Message.ClusterSync sync) {
        if (currentState != ClusterState.FOLLOWER) {
            return; // Only followers accept state sync
        }

        // Update local state from primary
        ConsoleScreenManager.logToMainScreen(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " State-Sync von Primary Master " + sync.masterId + " empfangen");

        // Sync would update local maps with received data
        // In a real implementation, merge strategies would be needed
    }

    private void handleHeartbeat(Message.ClusterSync sync) {
        String senderId = sync.masterId;
        ClusterNode node = clusterNodes.get(senderId);

        if (node == null) {
            // New node discovered - mit 5 Parametern
            node = new ClusterNode(
                    senderId,
                    "", // hostname wird später aktualisiert
                    0,  // port wird später aktualisiert
                    false, // nicht primary
                    System.currentTimeMillis()
            );
            clusterNodes.put(senderId, node);
            ConsoleScreenManager.logToMainScreen(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                    " Neuer Cluster-Knoten entdeckt: " + senderId);
        }

        // Update heartbeat mit Metriken falls verfügbar
        if (sync.data.containsKey("connectedWrappers") && sync.data.containsKey("runningServers")) {
            int wrappers = (Integer) sync.data.getOrDefault("connectedWrappers", 0);
            int servers = (Integer) sync.data.getOrDefault("runningServers", 0);
            node.updateHeartbeat(wrappers, servers);
        } else {
            node.updateHeartbeat();
        }
    }

    private void handleTakeover(Message.ClusterSync sync) {
        Integer newTerm = (Integer) sync.data.get("term");
        if (newTerm != null && newTerm > electionTerm) {
            // Accept new primary
            electionTerm = newTerm;
            primaryMasterId = sync.masterId;
            currentState = ClusterState.FOLLOWER;
            master.setPrimaryMaster(false);

            ConsoleScreenManager.logToMainScreen(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                    " Neuer Primary Master: " + sync.masterId + " (Term: " + newTerm + ")");
        }
    }

    private void handleStateRequest(Message.ClusterSync sync) {
        if (master.isPrimaryMaster()) {
            // Send current state to requesting node
            syncClusterState();
        }
    }

    private void broadcastToPeers(Object message) {
        // Send message to all known cluster peers
        // In a real implementation, this would use network communication
        for (ClusterNode node : clusterNodes.values()) {
            if (!node.masterId.equals(masterId)) {
                // Send message to peer
                // Would use KryoNet, HTTP, or gRPC
            }
        }
    }

    public void notifyWrapperJoined(WrapperConnection wrapper) {
        if (master.isPrimaryMaster()) {
            // Broadcast wrapper join to cluster
            Message.ClusterSync sync = new Message.ClusterSync();
            sync.masterId = masterId;
            sync.messageType = "WRAPPER_JOIN";
            sync.data.put("wrapperId", wrapper.getWrapperId());
            sync.data.put("hostname", wrapper.getHostname());
            sync.timestamp = System.currentTimeMillis();

            broadcastToPeers(sync);
        }
    }

    public void notifyWrapperLeft(WrapperConnection wrapper) {
        if (master.isPrimaryMaster()) {
            // Broadcast wrapper leave to cluster
            Message.ClusterSync sync = new Message.ClusterSync();
            sync.masterId = masterId;
            sync.messageType = "WRAPPER_LEAVE";
            sync.data.put("wrapperId", wrapper.getWrapperId());
            sync.timestamp = System.currentTimeMillis();

            broadcastToPeers(sync);
        }
    }



    public boolean isPrimary() {
        return currentState == ClusterState.PRIMARY;
    }

    public String getCurrentState() {
        return isPrimary() ? "PRIMARY" : "SECONDARY";
    }

    public String getPrimaryMasterId() {
        return primaryMasterId;
    }

    public Map<String, ClusterNode> getClusterNodes() {
        return new HashMap<>(clusterNodes);
    }

    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
    }


}

// Cluster State Enum
enum ClusterState {
    INITIALIZING,
    PRIMARY,
    FOLLOWER,
    ELECTION,
    DISCONNECTED
}

