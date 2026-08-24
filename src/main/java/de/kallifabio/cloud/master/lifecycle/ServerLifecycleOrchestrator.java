package de.kallifabio.cloud.master.lifecycle;

import de.kallifabio.cloud.master.ServerInstance;
import de.kallifabio.cloud.master.events.EventTimelineService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public final class ServerLifecycleOrchestrator {

    private static final int MAX_TRANSITIONS_PER_SERVER = 80;
    private final EventTimelineService events;
    private final Map<String, Deque<LifecycleTransition>> history = new ConcurrentHashMap<>();

    public ServerLifecycleOrchestrator(EventTimelineService events) {
        this.events = events;
    }

    public synchronized LifecycleTransition transition(ServerInstance server, ServerLifecycleState target, String reason) {
        if (server == null || target == null) {
            return null;
        }
        ServerLifecycleState from = server.getLifecycleState();
        if (from == target) {
            server.lastUpdate = System.currentTimeMillis();
            return new LifecycleTransition(System.currentTimeMillis(), server.serverName, from, target, reason);
        }

        server.setLifecycleState(target);
        LifecycleTransition transition = new LifecycleTransition(
                System.currentTimeMillis(),
                server.serverName,
                from,
                target,
                reason == null ? "" : reason
        );
        Deque<LifecycleTransition> transitions = history.computeIfAbsent(server.serverName, key -> new ConcurrentLinkedDeque<>());
        transitions.addLast(transition);
        while (transitions.size() > MAX_TRANSITIONS_PER_SERVER) {
            transitions.pollFirst();
        }
        events.publish("SERVER_LIFECYCLE", "server:" + server.serverName,
                target == ServerLifecycleState.FAILED || target == ServerLifecycleState.QUARANTINED ? "WARNING" : "INFO",
                from + " -> " + target,
                Map.of("server", server.serverName, "from", from.name(), "to", target.name(), "reason", transition.reason()));
        return transition;
    }

    public List<Map<String, Object>> recentTransitions(String serverName, int limit) {
        List<LifecycleTransition> source = new ArrayList<>();
        if (serverName != null && !serverName.isBlank()) {
            source.addAll(history.getOrDefault(serverName, new ConcurrentLinkedDeque<>()));
        } else {
            for (Deque<LifecycleTransition> transitions : history.values()) {
                source.addAll(transitions);
            }
        }
        source.sort((a, b) -> Long.compare(b.timestamp(), a.timestamp()));
        List<Map<String, Object>> result = new ArrayList<>();
        for (LifecycleTransition transition : source) {
            if (result.size() >= limit) {
                break;
            }
            result.add(toMap(transition));
        }
        Collections.reverse(result);
        return result;
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("trackedServers", history.size());
        payload.put("recentTransitions", recentTransitions(null, 100));
        return payload;
    }

    private Map<String, Object> toMap(LifecycleTransition transition) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("timestamp", transition.timestamp());
        map.put("serverName", transition.serverName());
        map.put("from", transition.from().name());
        map.put("to", transition.to().name());
        map.put("reason", transition.reason());
        return map;
    }
}
