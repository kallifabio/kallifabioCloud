package de.kallifabio.cloud.master.capacity;

import de.kallifabio.cloud.master.Master;
import de.kallifabio.cloud.master.ServerInstance;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CapacityPlannerService {

    private CapacityPlannerService() {
    }

    public static Map<String, Object> build(Master master) {
        long now = System.currentTimeMillis();
        List<Map<String, Object>> wrappers = new ArrayList<>();
        List<Map<String, Object>> groups = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();
        int healthyWrappers = 0;
        int drainingWrappers = 0;
        int totalWrapperMemory = 0;
        int availableWrapperMemory = 0;
        double cpuCritical = master.getConfigManager().getMonitoringCpuCritical();

        for (var wrapper : master.getConnectedWrappers().values()) {
            boolean draining = master.getLoadBalancerManager().isWrapperDraining(wrapper.getWrapperId());
            boolean healthy = wrapper.isHealthy();
            int maxMemory = Math.max(0, wrapper.getMaxMemory());
            int availableMemory = Math.max(0, wrapper.getAvailableMemory());
            totalWrapperMemory += maxMemory;
            availableWrapperMemory += availableMemory;
            if (healthy) {
                healthyWrappers++;
            }
            if (draining) {
                drainingWrappers++;
            }

            Map<String, Object> wrapperInfo = new LinkedHashMap<>();
            wrapperInfo.put("wrapperId", wrapper.getWrapperId());
            wrapperInfo.put("hostname", wrapper.getHostname());
            wrapperInfo.put("routeHost", wrapper.getRouteHost());
            wrapperInfo.put("healthy", healthy);
            wrapperInfo.put("draining", draining);
            wrapperInfo.put("maxMemoryMb", maxMemory);
            wrapperInfo.put("availableMemoryMb", availableMemory);
            wrapperInfo.put("usedMemoryMb", Math.max(0, maxMemory - availableMemory));
            wrapperInfo.put("cpuUsage", Math.round(wrapper.getCpuUsage() * 10.0) / 10.0);
            wrapperInfo.put("activeServers", wrapper.getActiveServers());
            wrappers.add(wrapperInfo);
        }

        Map<String, Object> queueStats = new LinkedHashMap<>(master.getPlayerQueueManager().getQueueStats());
        List<String> configuredGroups = new ArrayList<>(master.getConfigManager().getAllServerGroups());
        for (String groupName : configuredGroups) {
            int requiredRam = Math.max(1, safeInteger(master.getConfigManager().getRamForGroup(groupName), 1024));
            int maxPlayers = Math.max(1, safeInteger(master.getConfigManager().getMaxPlayersForGroup(groupName), 100));
            int minServers = Math.max(0, master.getConfigManager().getMinServersForGroup(groupName));
            int maxServers = Math.max(minServers, master.getConfigManager().getMaxServersForGroup(groupName));
            boolean maintenance = master.getConfigManager().isMaintenanceMode(groupName);
            boolean dynamic = master.getConfigManager().isDynamicGroup(groupName);
            int queuedPlayers = parseQueueSize(queueStats.get(groupName));

            int runningServers = 0;
            int onlineServers = 0;
            int startingServers = 0;
            int totalPlayers = 0;
            for (ServerInstance server : master.getRunningServers().values()) {
                if (!groupName.equalsIgnoreCase(String.valueOf(server.groupName))) {
                    continue;
                }
                runningServers++;
                totalPlayers += Math.max(0, server.playerCount);
                String status = String.valueOf(server.status == null ? "" : server.status);
                if ("ONLINE".equalsIgnoreCase(status)) {
                    onlineServers++;
                }
                if ("STARTING".equalsIgnoreCase(status)) {
                    startingServers++;
                }
            }

            List<String> startableWrappers = new ArrayList<>();
            String bestWrapperId = null;
            int bestAvailableMemory = -1;
            int smallestShortfall = Integer.MAX_VALUE;
            for (var wrapper : master.getConnectedWrappers().values()) {
                boolean healthy = wrapper.isHealthy();
                boolean draining = master.getLoadBalancerManager().isWrapperDraining(wrapper.getWrapperId());
                int availableMemory = Math.max(0, wrapper.getAvailableMemory());
                boolean cpuOk = wrapper.getCpuUsage() < cpuCritical;
                if (healthy && !draining) {
                    smallestShortfall = Math.min(smallestShortfall, Math.max(0, requiredRam - availableMemory));
                }
                if (healthy && !draining && cpuOk && availableMemory >= requiredRam) {
                    startableWrappers.add(wrapper.getWrapperId());
                    if (availableMemory > bestAvailableMemory) {
                        bestAvailableMemory = availableMemory;
                        bestWrapperId = wrapper.getWrapperId();
                    }
                }
            }
            int shortfallMb = startableWrappers.isEmpty()
                    ? (smallestShortfall == Integer.MAX_VALUE ? requiredRam : smallestShortfall)
                    : 0;

            int demandFromQueue = (int) Math.ceil((double) queuedPlayers / Math.max(1, maxPlayers));
            int recommendedServers = Math.max(minServers, runningServers + demandFromQueue);
            if (queuedPlayers > 0 && runningServers == 0) {
                recommendedServers = Math.max(recommendedServers, 1);
            }
            recommendedServers = Math.min(maxServers, recommendedServers);
            boolean canStartNow = !maintenance && runningServers < maxServers && !startableWrappers.isEmpty();

            String recommendation = buildGroupRecommendation(
                    maintenance,
                    runningServers,
                    maxServers,
                    startableWrappers.isEmpty(),
                    shortfallMb,
                    recommendedServers
            );

            if (!canStartNow && queuedPlayers > 0) {
                recommendations.add(groupName + ": queued players exist, but planner cannot start more capacity now.");
            } else if (recommendedServers > runningServers) {
                recommendations.add(groupName + ": start " + (recommendedServers - runningServers)
                        + " more server(s), best wrapper: " + (bestWrapperId == null ? "none" : bestWrapperId) + ".");
            }

            Map<String, Object> groupInfo = new LinkedHashMap<>();
            groupInfo.put("groupName", groupName);
            groupInfo.put("ramMb", requiredRam);
            groupInfo.put("maxPlayers", maxPlayers);
            groupInfo.put("minServers", minServers);
            groupInfo.put("maxServers", maxServers);
            groupInfo.put("runningServers", runningServers);
            groupInfo.put("onlineServers", onlineServers);
            groupInfo.put("startingServers", startingServers);
            groupInfo.put("players", totalPlayers);
            groupInfo.put("queuedPlayers", queuedPlayers);
            groupInfo.put("maintenance", maintenance);
            groupInfo.put("dynamic", dynamic);
            groupInfo.put("tags", master.getConfigManager().getGroupTags(groupName));
            groupInfo.put("canStartNow", canStartNow);
            groupInfo.put("startableWrappers", startableWrappers);
            groupInfo.put("bestWrapperId", bestWrapperId);
            groupInfo.put("capacityShortfallMb", shortfallMb);
            groupInfo.put("recommendedServers", recommendedServers);
            groupInfo.put("recommendation", recommendation);
            groups.add(groupInfo);
        }

        if (master.getConnectedWrappers().isEmpty()) {
            recommendations.add("No wrapper connected. Start or reconnect a wrapper before planning capacity.");
        }
        if (drainingWrappers > 0) {
            recommendations.add(drainingWrappers + " wrapper(s) are draining and excluded from new scheduling.");
        }
        if (recommendations.isEmpty()) {
            recommendations.add("Planner found no immediate capacity action.");
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("groups", groups.size());
        summary.put("healthyWrappers", healthyWrappers);
        summary.put("drainingWrappers", drainingWrappers);
        summary.put("totalWrapperMemoryMb", totalWrapperMemory);
        summary.put("availableWrapperMemoryMb", availableWrapperMemory);
        summary.put("queueTotal", master.getPlayerQueueManager().getTotalQueued());
        summary.put("startableGroups", groups.stream().filter(group -> Boolean.TRUE.equals(group.get("canStartNow"))).count());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("generatedAt", now);
        payload.put("summary", summary);
        payload.put("wrappers", wrappers);
        payload.put("groups", groups);
        payload.put("recommendations", recommendations);
        return payload;
    }

    private static String buildGroupRecommendation(boolean maintenance, int runningServers, int maxServers,
                                                   boolean noStartableWrapper, int shortfallMb,
                                                   int recommendedServers) {
        if (maintenance) {
            return "Maintenance is enabled. Keep capacity stable unless staff bypass is intended.";
        }
        if (runningServers >= maxServers) {
            return "Group already reached MaxServers. Increase MaxServers before scaling further.";
        }
        if (noStartableWrapper) {
            return shortfallMb > 0
                    ? "No wrapper has enough free RAM. Need at least " + shortfallMb + "MB more free RAM on one wrapper."
                    : "No healthy non-draining wrapper is available for scheduling.";
        }
        if (recommendedServers > runningServers) {
            return "Scale up recommended: queue/demand suggests " + recommendedServers + " server(s).";
        }
        return "Capacity looks balanced. No immediate scale-up required.";
    }

    private static int safeInteger(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private static int parseQueueSize(Object value) {
        if (value instanceof Number) {
            return Math.max(0, ((Number) value).intValue());
        }
        if (value == null) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(String.valueOf(value)));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
