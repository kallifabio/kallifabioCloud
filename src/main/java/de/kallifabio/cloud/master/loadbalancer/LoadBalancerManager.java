/**
 * Erstellt von Gamer_Kidd_LP | kallifabio
 * am 09.01.2026 um 20:48
 * Projektname: CloudSystemTest
 * Packagename: de.kallifabio.cloud.master.loadbalancer
 */

package de.kallifabio.cloud.master.loadbalancer;

import de.kallifabio.cloud.libs.console.ConsoleColors;
import de.kallifabio.cloud.libs.Message;
import de.kallifabio.cloud.libs.console.ConsoleScreenManager;
import de.kallifabio.cloud.master.Master;
import de.kallifabio.cloud.master.ServerInstance;
import de.kallifabio.cloud.master.WrapperConnection;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class LoadBalancerManager {

    private final Master master;

    // Load balancing strategies
    private final Map<String, LoadBalancingStrategy> strategyMap = new ConcurrentHashMap<>();

    // Server load tracking
    private final Map<String, ServerLoad> serverLoads = new ConcurrentHashMap<>();

    // Player affinity (sticky sessions)
    private final Map<String, String> playerAffinity = new ConcurrentHashMap<>();

    // Request counter for round-robin
    private final AtomicInteger requestCounter = new AtomicInteger(0);

    public LoadBalancerManager(Master master) {
        this.master = master;
        initializeStrategies();
    }

    private void initializeStrategies() {
        strategyMap.put("Lobby", LoadBalancingStrategy.LEAST_LOADED);
        strategyMap.put("Proxy", LoadBalancingStrategy.ROUND_ROBIN);

        ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Load Balancer initialisiert");
    }

    public String getBestServer(String groupName, String playerUuid) {
        List<ServerInstance> servers = getAvailableServers(groupName);

        if (servers.isEmpty()) {
            return null;
        }

        // Check player affinity first
        String affinityServer = playerAffinity.get(playerUuid);
        if (affinityServer != null && isServerAvailable(affinityServer)) {
            return affinityServer;
        }

        // Use appropriate strategy
        LoadBalancingStrategy strategy = strategyMap.getOrDefault(groupName, LoadBalancingStrategy.LEAST_LOADED);
        String selectedServer = selectServerByStrategy(servers, strategy);

        // Store affinity
        if (selectedServer != null) {
            playerAffinity.put(playerUuid, selectedServer);
        }

        return selectedServer;
    }

    private List<ServerInstance> getAvailableServers(String groupName) {
        return master.getRunningServers().values().stream()
                .filter(s -> s.groupName.equalsIgnoreCase(groupName))
                .filter(s -> "ONLINE".equals(s.status))
                .filter(s -> {
                    ServerLoad load = serverLoads.get(s.serverName);
                    return load == null || load.acceptingPlayers;
                })
                .filter(s -> s.playerCount < s.maxPlayers)
                .toList();
    }

    private boolean isServerAvailable(String serverName) {
        ServerInstance server = master.getRunningServers().get(serverName);
        if (server == null || !"ONLINE".equals(server.status)) {
            return false;
        }

        ServerLoad load = serverLoads.get(serverName);
        return (load == null || load.acceptingPlayers) && server.playerCount < server.maxPlayers;
    }

    private String selectServerByStrategy(List<ServerInstance> servers, LoadBalancingStrategy strategy) {
        return switch (strategy) {
            case ROUND_ROBIN -> selectRoundRobin(servers);
            case LEAST_LOADED -> selectLeastLoaded(servers);
            case LEAST_CONNECTIONS -> selectLeastConnections(servers);
            case WEIGHTED -> selectWeighted(servers);
            case RANDOM -> selectRandom(servers);
        };
    }

    private String selectRoundRobin(List<ServerInstance> servers) {
        if (servers.isEmpty()) return null;
        int index = requestCounter.getAndIncrement() % servers.size();
        return servers.get(index).serverName;
    }

    private String selectLeastLoaded(List<ServerInstance> servers) {
        return servers.stream()
                .min(Comparator.comparingDouble(s -> calculateLoadScore(s)))
                .map(s -> s.serverName)
                .orElse(null);
    }

    private String selectLeastConnections(List<ServerInstance> servers) {
        return servers.stream()
                .min(Comparator.comparingInt(s -> s.playerCount))
                .map(s -> s.serverName)
                .orElse(null);
    }

    private String selectWeighted(List<ServerInstance> servers) {
        // Weight based on available capacity
        double totalWeight = 0;
        Map<ServerInstance, Double> weights = new HashMap<>();

        for (ServerInstance server : servers) {
            double capacity = server.maxPlayers - server.playerCount;
            double weight = capacity / (double) server.maxPlayers;
            weights.put(server, weight);
            totalWeight += weight;
        }

        double random = Math.random() * totalWeight;
        double cumulative = 0;

        for (Map.Entry<ServerInstance, Double> entry : weights.entrySet()) {
            cumulative += entry.getValue();
            if (random <= cumulative) {
                return entry.getKey().serverName;
            }
        }

        return servers.get(0).serverName;
    }

    private String selectRandom(List<ServerInstance> servers) {
        if (servers.isEmpty()) return null;
        return servers.get(new Random().nextInt(servers.size())).serverName;
    }

    private double calculateLoadScore(ServerInstance server) {
        // Lower score = less loaded
        double playerLoad = (double) server.playerCount / server.maxPlayers;
        double tpsLoad = server.tps < 18 ? 1.0 : (20.0 - server.tps) / 20.0;
        double memoryLoad = server.memoryUsage / (double) (server.allocatedRam * 1024 * 1024);

        return (playerLoad * 0.5) + (tpsLoad * 0.3) + (memoryLoad * 0.2);
    }

    public void updateServerLoad(Message.ServerMetrics metrics) {
        ServerLoad load = serverLoads.computeIfAbsent(metrics.serverName, k -> new ServerLoad(metrics.serverName));

        load.playerCount = metrics.playerCount;
        load.maxPlayers = metrics.maxPlayers;
        load.tps = metrics.tps;
        load.memoryUsage = metrics.memoryUsage;
        load.loadScore = calculateLoadScore(master.getRunningServers().get(metrics.serverName));
        load.lastUpdate = System.currentTimeMillis();

        // Auto-disable if overloaded
        if (metrics.tps < 15.0 || metrics.playerCount >= metrics.maxPlayers) {
            load.acceptingPlayers = false;
        } else {
            load.acceptingPlayers = true;
        }
    }

    public WrapperConnection getBestWrapperForServer(String groupName) {
        return master.getConnectedWrappers().values().stream()
                .filter(w -> w.getAvailableMemory() >= 1024) // At least 1GB available
                .min(Comparator.comparingInt(w -> w.getActiveServers()))
                .orElse(null);
    }

    public void removePlayerAffinity(String playerUuid) {
        playerAffinity.remove(playerUuid);
    }

    public Map<String, Double> getServerLoads() {
        Map<String, Double> loads = new HashMap<>();
        serverLoads.forEach((serverName, serverLoad) -> {
            loads.put(serverName, serverLoad.loadScore);
        });
        return loads;
    }

    public Map<String, ServerLoad> getServerLoadDetails() {
        return new HashMap<>(serverLoads);
    }
}

class ServerLoad {
    String serverName;
    int playerCount;
    int maxPlayers;
    double tps;
    long memoryUsage;
    double loadScore;
    boolean acceptingPlayers;
    long lastUpdate;

    public ServerLoad(String serverName) {
        this.serverName = serverName;
        this.acceptingPlayers = true;
        this.lastUpdate = System.currentTimeMillis();
    }
}

enum LoadBalancingStrategy {
    ROUND_ROBIN,
    LEAST_LOADED,
    LEAST_CONNECTIONS,
    WEIGHTED,
    RANDOM
}

