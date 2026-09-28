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
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class LoadBalancerManager {

    private final Master master;

    // Load balancing strategies
    private final Map<String, LoadBalancingStrategy> strategyMap = new ConcurrentHashMap<>();

    // Server load tracking
    private final Map<String, ServerLoad> serverLoads = new ConcurrentHashMap<>();

    // Player affinity (sticky sessions)
    private final Map<String, String> playerAffinity = new ConcurrentHashMap<>();
    private final Map<String, Long> drainingWrappers = new ConcurrentHashMap<>();
    private final Map<String, WrapperReservation> wrapperReservationsByServer = new ConcurrentHashMap<>();

    // Request counter for round-robin
    private final AtomicInteger requestCounter = new AtomicInteger(0);
    private static final long LOAD_STALE_MS = 30000;
    private static final long WRAPPER_RESERVATION_TTL_MS = 5 * 60 * 1000L;
    private static final double MAX_ACCEPTING_CPU = 95.0;
    private static final double MIN_ACCEPTING_TPS = 16.0;

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
        if (groupName == null || groupName.isBlank()) {
            return null;
        }
        if (master.getConfigManager().isMaintenanceMode(groupName)) {
            return null;
        }
        cleanupStaleTracking();
        List<ServerInstance> servers = getAvailableServers(groupName);

        if (servers.isEmpty()) {
            return null;
        }

        // Check player affinity first
        String affinityKey = normalizePlayerUuid(playerUuid);
        String affinityServer = affinityKey == null ? null : playerAffinity.get(affinityKey);
        if (affinityServer != null && isServerAvailableForGroup(affinityServer, groupName)) {
            return affinityServer;
        } else if (affinityKey != null && affinityServer != null) {
            playerAffinity.remove(affinityKey);
        }

        // Use appropriate strategy
        LoadBalancingStrategy strategy = strategyMap.getOrDefault(groupName, LoadBalancingStrategy.LEAST_LOADED);
        if (strategy == LoadBalancingStrategy.ROUND_ROBIN && servers.size() > 1) {
            servers.sort(Comparator.comparing(s -> s.serverName));
        }
        String selectedServer = selectServerByStrategy(servers, strategy);

        // Store affinity
        if (selectedServer != null && affinityKey != null) {
            playerAffinity.put(affinityKey, selectedServer);
        }

        return selectedServer;
    }

    public String getBestServerAllowFull(String groupName, String playerUuid) {
        if (groupName == null || groupName.isBlank()) {
            return null;
        }
        if (master.getConfigManager().isMaintenanceMode(groupName)) {
            return null;
        }
        cleanupStaleTracking();
        List<ServerInstance> servers = master.getRunningServers().values().stream()
                .filter(s -> s.groupName.equalsIgnoreCase(groupName))
                .filter(ServerInstance::isOnline)
                .filter(ServerInstance::isHealthy)
                .filter(s -> !isWrapperDraining(s.wrapperId))
                .filter(this::isAcceptingIgnoringSlots)
                .toList();
        if (servers.isEmpty()) {
            return null;
        }
        LoadBalancingStrategy strategy = strategyMap.getOrDefault(groupName, LoadBalancingStrategy.LEAST_LOADED);
        if (strategy == LoadBalancingStrategy.ROUND_ROBIN && servers.size() > 1) {
            servers = new ArrayList<>(servers);
            servers.sort(Comparator.comparing(s -> s.serverName));
        }
        return selectServerByStrategy(servers, strategy);
    }

    private List<ServerInstance> getAvailableServers(String groupName) {
        if (groupName == null || groupName.isBlank()) {
            return List.of();
        }
        if (master.getConfigManager().isMaintenanceMode(groupName)) {
            return List.of();
        }
        return master.getRunningServers().values().stream()
                .filter(s -> s.groupName.equalsIgnoreCase(groupName))
                .filter(ServerInstance::isOnline)
                .filter(ServerInstance::isHealthy)
                .filter(s -> !isWrapperDraining(s.wrapperId))
                .filter(s -> {
                    ServerLoad load = serverLoads.get(s.serverName);
                    return load == null || load.acceptingPlayers;
                })
                .filter(s -> s.maxPlayers <= 0 || getEffectivePlayerCount(s) < s.maxPlayers)
                .toList();
    }

    private boolean isServerAvailable(String serverName) {
        ServerInstance server = master.getRunningServers().get(serverName);
        if (server == null || !server.isOnline() || !server.isHealthy()) {
            return false;
        }
        if (master.getConfigManager().isMaintenanceMode(server.groupName)) {
            return false;
        }
        if (isWrapperDraining(server.wrapperId)) {
            return false;
        }

        ServerLoad load = serverLoads.get(serverName);
        return (load == null || load.acceptingPlayers)
                && (server.maxPlayers <= 0 || getEffectivePlayerCount(server) < server.maxPlayers);
    }

    private boolean isServerAvailableForGroup(String serverName, String groupName) {
        ServerInstance server = master.getRunningServers().get(serverName);
        return server != null
                && groupName != null
                && server.groupName != null
                && server.groupName.equalsIgnoreCase(groupName)
                && isServerAvailable(serverName);
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
        int index = Math.floorMod(requestCounter.getAndIncrement(), servers.size());
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
                .min(Comparator.comparingInt(this::getEffectivePlayerCount))
                .map(s -> s.serverName)
                .orElse(null);
    }

    private String selectWeighted(List<ServerInstance> servers) {
        // Weight based on available capacity
        double totalWeight = 0;
        Map<ServerInstance, Double> weights = new HashMap<>();

        for (ServerInstance server : servers) {
            double capacity = Math.max(1, server.maxPlayers - getEffectivePlayerCount(server));
            double weight = capacity / (double) Math.max(1, server.maxPlayers);
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
        return servers.get(ThreadLocalRandom.current().nextInt(servers.size())).serverName;
    }

    private double calculateLoadScore(ServerInstance server) {
        // Lower score = less loaded
        if (server.maxPlayers <= 0 || server.allocatedRam <= 0) {
            return 1.0;
        }

        double playerLoad = clamp01((double) Math.max(0, getEffectivePlayerCount(server)) / Math.max(1, server.maxPlayers));
        double tpsLoad = clamp01((20.0 - Math.max(0.0, Math.min(20.0, server.tps))) / 20.0);
        double memoryLoad = clamp01(server.getMemoryUsagePercentage() / 100.0);
        double cpuLoad = clamp01(server.cpuUsage / 100.0);

        return (playerLoad * 0.45) + (tpsLoad * 0.25) + (memoryLoad * 0.20) + (cpuLoad * 0.10);
    }

    public void updateServerLoad(Message.ServerMetrics metrics) {
        ServerInstance instance = master.getRunningServers().get(metrics.serverName);
        if (instance == null) {
            return;
        }

        ServerLoad load = serverLoads.computeIfAbsent(metrics.serverName, k -> new ServerLoad(metrics.serverName));

        load.playerCount = metrics.playerCount;
        load.maxPlayers = metrics.maxPlayers;
        load.tps = metrics.tps;
        load.memoryUsage = metrics.memoryUsage;
        load.loadScore = calculateLoadScore(instance);
        load.lastUpdate = System.currentTimeMillis();

        boolean proxy = instance.groupName != null && instance.groupName.toLowerCase(Locale.ROOT).contains("proxy");
        boolean tpsOk = proxy || metrics.tps >= MIN_ACCEPTING_TPS;
        boolean cpuOk = metrics.cpuUsage < MAX_ACCEPTING_CPU;
        boolean slotsOk = metrics.maxPlayers <= 0 || getEffectivePlayerCount(instance) < metrics.maxPlayers;
        boolean memoryOk = instance.getMemoryUsagePercentage() < 96.0;
        load.acceptingPlayers = tpsOk && cpuOk && slotsOk && memoryOk;
    }

    private boolean isAcceptingIgnoringSlots(ServerInstance instance) {
        if (instance == null) {
            return false;
        }
        boolean proxy = instance.groupName != null && instance.groupName.toLowerCase(Locale.ROOT).contains("proxy");
        boolean tpsOk = proxy || instance.tps >= MIN_ACCEPTING_TPS;
        boolean cpuOk = instance.cpuUsage < MAX_ACCEPTING_CPU;
        boolean memoryOk = instance.getMemoryUsagePercentage() < 96.0;
        return tpsOk && cpuOk && memoryOk;
    }

    public WrapperConnection getBestWrapperForServer(String groupName) {
        int requiredMemory = Math.max(256, master.getConfigManager().getRamForGroup(groupName));
        Optional<WrapperConnection> candidate = master.getConnectedWrappers().values().stream()
                .filter(w -> canScheduleOnWrapper(w, requiredMemory))
                .filter(w -> !isWrapperDraining(w.getWrapperId()))
                .min(Comparator.comparingDouble(w -> calculateWrapperScore(w, requiredMemory)));
        if (candidate.isPresent()) {
            return candidate.get();
        }

        // Fallback if all wrappers are draining, to avoid deadlock.
        return master.getConnectedWrappers().values().stream()
                .filter(w -> canScheduleOnWrapper(w, requiredMemory))
                .min(Comparator.comparingDouble(w -> calculateWrapperScore(w, requiredMemory) + 0.25))
                .orElse(null);
    }

    private boolean canScheduleOnWrapper(WrapperConnection wrapper, int requiredMemory) {
        return wrapper != null
                && wrapper.isHealthy()
                && getEffectiveAvailableMemory(wrapper) >= requiredMemory
                && wrapper.getCpuUsage() < 98.0;
    }

    private double calculateWrapperScore(WrapperConnection wrapper, int requiredMemory) {
        double memoryUsage = clamp01(wrapper.getMemoryUsagePercentage() / 100.0);
        double cpuUsage = clamp01(wrapper.getCpuUsage() / 100.0);
        double serverLoad = clamp01(wrapper.getActiveServers() / 20.0);
        double rttLoad = wrapper.getLastRttMs() < 0 ? 0.05 : clamp01(wrapper.getLastRttMs() / 500.0);
        int effectiveAvailable = getEffectiveAvailableMemory(wrapper);
        double headroomPenalty = effectiveAvailable <= 0
                ? 1.0
                : clamp01(requiredMemory / (double) Math.max(1, effectiveAvailable));
        return (memoryUsage * 0.30)
                + (cpuUsage * 0.30)
                + (serverLoad * 0.20)
                + (rttLoad * 0.10)
                + (headroomPenalty * 0.10);
    }

    public void reserveWrapperCapacity(String serverName, String wrapperId, int memoryMb) {
        if (serverName == null || serverName.isBlank() || wrapperId == null || wrapperId.isBlank() || memoryMb <= 0) {
            return;
        }
        wrapperReservationsByServer.put(serverName, new WrapperReservation(wrapperId, memoryMb, System.currentTimeMillis()));
    }

    public void releaseServerReservation(String serverName) {
        if (serverName != null && !serverName.isBlank()) {
            wrapperReservationsByServer.remove(serverName);
        }
    }

    public void setWrapperDraining(String wrapperId, boolean draining) {
        if (wrapperId == null || wrapperId.isBlank()) {
            return;
        }
        if (draining) {
            drainingWrappers.put(wrapperId, System.currentTimeMillis());
        } else {
            drainingWrappers.remove(wrapperId);
        }
    }

    public boolean isWrapperDraining(String wrapperId) {
        return wrapperId != null && drainingWrappers.containsKey(wrapperId);
    }

    public Map<String, Long> getDrainingWrappers() {
        return new HashMap<>(drainingWrappers);
    }

    public void removePlayerAffinity(String playerUuid) {
        String uuid = normalizePlayerUuid(playerUuid);
        if (uuid != null) {
            playerAffinity.remove(uuid);
        }
    }

    public int removeServerTracking(String serverName) {
        if (serverName == null || serverName.isBlank()) {
            return 0;
        }
        serverLoads.remove(serverName);
        releaseServerReservation(serverName);
        int removedAffinities = 0;
        for (Map.Entry<String, String> entry : playerAffinity.entrySet()) {
            if (serverName.equalsIgnoreCase(entry.getValue())
                    && playerAffinity.remove(entry.getKey(), entry.getValue())) {
                removedAffinities++;
            }
        }
        return removedAffinities;
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

    public Map<String, Object> getRoutingDiagnostics() {
        cleanupStaleTracking();
        Map<String, Object> diagnostics = new LinkedHashMap<>();
        List<Map<String, Object>> candidates = new ArrayList<>();
        Map<String, Integer> availableByGroup = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Map<String, Integer> blockedByGroup = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        master.getRunningServers().values().stream()
                .sorted(Comparator.comparing(server -> server.serverName))
                .forEach(server -> {
                    List<String> reasons = getRoutingRejectionReasons(server);
                    boolean available = reasons.isEmpty();
                    String group = server.groupName == null || server.groupName.isBlank() ? "unknown" : server.groupName;
                    if (available) {
                        availableByGroup.merge(group, 1, Integer::sum);
                    } else {
                        blockedByGroup.merge(group, 1, Integer::sum);
                    }

                    Map<String, Object> candidate = new LinkedHashMap<>();
                    candidate.put("serverName", server.serverName);
                    candidate.put("groupName", group);
                    candidate.put("status", server.status);
                    candidate.put("lifecycleState", server.getLifecycleState().name());
                    candidate.put("wrapperId", server.wrapperId);
                    candidate.put("available", available);
                    candidate.put("reasons", reasons);
                    candidate.put("effectivePlayers", getEffectivePlayerCount(server));
                    candidate.put("rawPlayers", Math.max(0, server.playerCount));
                    candidate.put("maxPlayers", server.maxPlayers);
                    candidate.put("full", server.maxPlayers > 0 && getEffectivePlayerCount(server) >= server.maxPlayers);
                    candidate.put("tps", server.tps);
                    candidate.put("cpuUsage", server.cpuUsage);
                    candidate.put("memoryUsagePercent", server.getMemoryUsagePercentage());
                    candidate.put("loadScore", calculateLoadScore(server));
                    ServerLoad load = serverLoads.get(server.serverName);
                    candidate.put("hasFreshLoad", load != null);
                    candidate.put("acceptingPlayers", load == null || load.acceptingPlayers);
                    candidates.add(candidate);
                });

        diagnostics.put("candidates", candidates);
        diagnostics.put("availableByGroup", new LinkedHashMap<>(availableByGroup));
        diagnostics.put("blockedByGroup", new LinkedHashMap<>(blockedByGroup));
        diagnostics.put("affinities", playerAffinity.size());
        diagnostics.put("drainingWrappers", new LinkedHashMap<>(drainingWrappers));
        diagnostics.put("reservations", wrapperReservationsByServer.size());
        diagnostics.put("generatedAt", System.currentTimeMillis());
        return diagnostics;
    }

    private List<String> getRoutingRejectionReasons(ServerInstance server) {
        List<String> reasons = new ArrayList<>();
        if (server == null) {
            return List.of("missing_server");
        }
        if (!server.isOnline()) {
            reasons.add("not_online");
        }
        if (!server.isHealthy()) {
            reasons.add("unhealthy");
        }
        if (master.getConfigManager().isMaintenanceMode(server.groupName)) {
            reasons.add("maintenance");
        }
        if (isWrapperDraining(server.wrapperId)) {
            reasons.add("wrapper_draining");
        }
        ServerLoad load = serverLoads.get(server.serverName);
        if (load != null && !load.acceptingPlayers) {
            reasons.add("not_accepting_players");
        }
        if (server.maxPlayers > 0 && getEffectivePlayerCount(server) >= server.maxPlayers) {
            reasons.add("full");
        }
        return reasons;
    }

    private void cleanupStaleTracking() {
        long now = System.currentTimeMillis();
        serverLoads.entrySet().removeIf(entry -> now - entry.getValue().lastUpdate > LOAD_STALE_MS);
        playerAffinity.entrySet().removeIf(entry -> !isServerAvailable(entry.getValue()));
        wrapperReservationsByServer.entrySet().removeIf(entry ->
                now - entry.getValue().reservedAt > WRAPPER_RESERVATION_TTL_MS
                        || !master.getRunningServers().containsKey(entry.getKey()));
        drainingWrappers.entrySet().removeIf(entry ->
                master.getConnectedWrappers().values().stream().noneMatch(w -> w.getWrapperId().equals(entry.getKey())));
    }

    public int getEffectivePlayerCount(ServerInstance server) {
        if (server == null) {
            return 0;
        }
        int metricPlayers = Math.max(0, server.playerCount);
        if (master.getPlayerSessionManager() == null) {
            return metricPlayers;
        }
        int sessionPlayers = master.getPlayerSessionManager().getTrackedPlayerCount(server.serverName);
        return Math.max(metricPlayers, sessionPlayers);
    }

    private int getEffectiveAvailableMemory(WrapperConnection wrapper) {
        if (wrapper == null) {
            return 0;
        }
        int reserved = wrapperReservationsByServer.values().stream()
                .filter(reservation -> wrapper.getWrapperId().equals(reservation.wrapperId))
                .mapToInt(reservation -> reservation.memoryMb)
                .sum();
        return Math.max(0, wrapper.getAvailableMemory() - reserved);
    }

    private String normalizePlayerUuid(String playerUuid) {
        if (playerUuid == null || playerUuid.isBlank()) {
            return null;
        }
        return playerUuid.trim().toLowerCase(Locale.ROOT);
    }

    private double clamp01(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 1.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
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

class WrapperReservation {
    final String wrapperId;
    final int memoryMb;
    final long reservedAt;

    WrapperReservation(String wrapperId, int memoryMb, long reservedAt) {
        this.wrapperId = wrapperId;
        this.memoryMb = memoryMb;
        this.reservedAt = reservedAt;
    }
}

enum LoadBalancingStrategy {
    ROUND_ROBIN,
    LEAST_LOADED,
    LEAST_CONNECTIONS,
    WEIGHTED,
    RANDOM
}

