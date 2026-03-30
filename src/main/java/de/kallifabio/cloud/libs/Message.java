/**
 * Erstellt von Gamer_Kidd_LP | kallifabio
 * am 28.12.2024 um 20:18
 * Projektname: CloudSystemTest
 * Packagename: de.kallifabio.cloudsystem.libs
 */

package de.kallifabio.cloud.libs;

import java.util.HashMap;
import java.util.Map;

public class Message {

    // Existing Messages
    public static class ServerStatusMessage {
        public String serverName;
        public String groupName;
        public String status;
        public int playerCount;
        public int maxPlayers;

        public ServerStatusMessage() {}
    }

    public static class ServerListRequest {
        public String requestId;
        public String groupName;

        public ServerListRequest() {}
    }

    // ServerListResponse.java
    public static class ServerListResponse {
        public String requestId;
        public java.util.List<ServerInfo> servers;

        public ServerListResponse() {}
    }

    // ServerInfo.java
    public static class ServerInfo {
        public String serverName;
        public String groupName;
        public String status;
        public int playerCount;
        public int maxPlayers;
        public double tps;
        public long lastUpdate;

        public ServerInfo() {}

        public boolean isOnline() {
            return "ONLINE".equals(status);
        }

        public boolean isFull() {
            return playerCount >= maxPlayers;
        }

        public int getFreeSlots() {
            return Math.max(0, maxPlayers - playerCount);
        }
    }

    // PluginHeartbeat.java
    public static class PluginHeartbeat {
        public String serverName;
        public int playerCount;
        public int maxPlayers;
        public double tps;
        public long timestamp;

        public PluginHeartbeat() {}
    }

    // PlayerConnectRequest.java
    public static class PlayerConnectRequest {
        public String playerUuid;
        public String playerName;
        public String targetServer;
        public String fromServer;

        public PlayerConnectRequest() {}
    }

    // PlayerConnectResponse.java
    public static class PlayerConnectResponse {
        public String playerUuid;
        public String targetServer;
        public boolean success;
        public String message;
        public int queuePosition;

        public PlayerConnectResponse() {}
    }

    public static class ServerCommand {
        public String command;
        public String serverName;
        public String groupName;
        public int port;
        public ServerCommand() {}
    }

    // Wrapper Registration
    public static class WrapperRegister {
        public String wrapperId;
        public String hostname;
        public int maxMemory;
        public int availableMemory;
        public String version;
        public WrapperRegister() {}
    }

    public static class WrapperRegisterAck {
        public String masterId;
        public boolean success;
        public String assignedId;
        public WrapperRegisterAck() {}
    }

    // Heartbeat
    public static class WrapperHeartbeat {
        public String wrapperId;
        public int availableMemory;
        public double cpuUsage;
        public int activeServers;
        public long timestamp;
        public WrapperHeartbeat() {}
    }

    public static class Ping {
        public String sourceId;
        public long timestamp;
        public Ping() {}
    }

    public static class Pong {
        public String sourceId;
        public long pingTimestamp;
        public long timestamp;
        public Pong() {}
    }

    // Server Metrics
    public static class ServerMetrics {
        public String serverName;
        public String groupName;
        public int playerCount;
        public int maxPlayers;
        public double tps;
        public long memoryUsage;
        public double cpuUsage;
        public long networkInBytes;
        public long networkOutBytes;
        public String networkMode;
        public long diskReadBytes;
        public long diskWriteBytes;
        public long timestamp;
        public ServerMetrics() {}
    }

    // Player Management
    public static class PlayerJoinRequest {
        public String playerUuid;
        public String playerName;
        public String groupName;
        public int priority; // VIP priority
        public PlayerJoinRequest() {}
    }

    public static class PlayerJoinResponse {
        public String playerUuid;
        public String targetServer;
        public boolean success;
        public int queuePosition;
        public String message;
        public PlayerJoinResponse() {}
    }

    public static class PlayerTransfer {
        public String playerUuid;
        public String fromServer;
        public String toServer;
        public String reason;
        public PlayerTransfer() {}
    }

    // Cluster Communication
    public static class ClusterSync {
        public String masterId;
        public String messageType; // STATE, HEARTBEAT, ELECTION, TAKEOVER
        public Map<String, Object> data;
        public long timestamp;
        public ClusterSync() {
            data = new HashMap<>();
        }
    }

    public static class ClusterHeartbeat {
        public String masterId;
        public boolean isPrimary;
        public int connectedWrappers;
        public int runningServers;
        public long uptime;
        public ClusterHeartbeat() {}
    }

    public static class MasterElection {
        public String candidateId;
        public int priority;
        public long uptime;
        public int connectedWrappers;
        public MasterElection() {}
    }

    // Auto-Scaling
    public static class ScalingDecision {
        public String groupName;
        public String action; // SCALE_UP, SCALE_DOWN
        public int targetCount;
        public String reason;
        public long timestamp;
        public ScalingDecision() {}
    }

    public static class ResourceRequest {
        public String groupName;
        public int requiredRam;
        public int requiredCpu;
        public int serverCount;
        public ResourceRequest() {}
    }

    // Monitoring
    public static class HealthCheck {
        public String componentId;
        public String componentType; // MASTER, WRAPPER, SERVER
        public String status; // HEALTHY, DEGRADED, UNHEALTHY
        public Map<String, String> metrics;
        public HealthCheck() {
            metrics = new HashMap<>();
        }
    }

    public static class Alert {
        public String alertId;
        public String severity; // INFO, WARNING, CRITICAL
        public String component;
        public String message;
        public long timestamp;
        public Map<String, String> details;
        public Alert() {
            details = new HashMap<>();
        }
    }

    // Load Balancing
    public static class ServerLoadUpdate {
        public String serverName;
        public int playerCount;
        public double loadScore;
        public boolean acceptingPlayers;
        public ServerLoadUpdate() {}
    }

    // Queue Management
    public static class QueueUpdate {
        public String playerUuid;
        public int position;
        public int totalInQueue;
        public String estimatedWait;
        public QueueUpdate() {}
    }

    public static class QueueKeepAlive {
        public String playerUuid;
        public String groupName;
        public long timestamp;
        public QueueKeepAlive() {}
    }

    public static class ServerLog {
        public String serverName;
        public String level;
        public String message;
        public long timestamp;
        public ServerLog() {}
    }

    public static class PermissionSync {
        public String playerUuid;
        public java.util.List<String> permissions;
        public String primaryGroup;
        public String prefix;
        public String suffix;
        public String targetServer;
        public long timestamp;
        public PermissionSync() {}
    }

    public static class PlayerNotification {
        public String playerUuid;
        public String type;
        public String message;
        public long timestamp;
        public PlayerNotification() {}
    }

    // Server Template Sync
    public static class TemplateSyncRequest {
        public String groupName;
        public String templateVersion;
        public TemplateSyncRequest() {}
    }

    public static class TemplateSyncResponse {
        public String groupName;
        public byte[] templateData;
        public String checksum;
        public TemplateSyncResponse() {}
    }

    // Configuration Updates
    public static class ConfigUpdate {
        public String configType; // MASTER, GROUP, SIGN
        public String configName;
        public Map<String, Object> configData;
        public ConfigUpdate() {
            configData = new HashMap<>();
        }
    }

    // Backup & Recovery
    public static class BackupRequest {
        public String serverName;
        public String backupType; // FULL, INCREMENTAL
        public BackupRequest() {}
    }

    public static class RestoreRequest {
        public String serverName;
        public String backupId;
        public RestoreRequest() {}
    }

    // Performance Optimization
    public static class PerformanceProfile {
        public String profileName;
        public int minRam;
        public int maxRam;
        public int targetTPS;
        public Map<String, String> jvmFlags;
        public PerformanceProfile() {
            jvmFlags = new HashMap<>();
        }
    }

    // Cross-Master Communication
    public static class CrossMasterRequest {
        public String sourceMasterId;
        public String targetMasterId;
        public String requestType;
        public Map<String, Object> payload;
        public CrossMasterRequest() {
            payload = new HashMap<>();
        }
    }

    public static class CrossMasterResponse {
        public String requestId;
        public boolean success;
        public Map<String, Object> responseData;
        public String errorMessage;
        public CrossMasterResponse() {
            responseData = new HashMap<>();
        }
    }

    // Database Sync (for shared state)
    public static class DatabaseSync {
        public String operation; // INSERT, UPDATE, DELETE
        public String table;
        public Map<String, Object> data;
        public long timestamp;
        public DatabaseSync() {
            data = new HashMap<>();
        }
    }

    // Wrapper Commands
    public static class WrapperCommand {
        public String command; // SHUTDOWN, RESTART, UPDATE
        public Map<String, String> params;
        public WrapperCommand() {
            params = new HashMap<>();
        }
    }

    // API Request Routing
    public static class APIRequest {
        public String requestId;
        public String endpoint;
        public String method;
        public Map<String, String> headers;
        public String body;
        public APIRequest() {
            headers = new HashMap<>();
        }
    }

    public static class APIResponse {
        public String requestId;
        public int statusCode;
        public Map<String, String> headers;
        public String body;
        public APIResponse() {
            headers = new HashMap<>();
        }
    }
}
