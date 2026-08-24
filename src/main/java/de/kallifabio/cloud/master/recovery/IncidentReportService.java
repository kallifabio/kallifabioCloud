package de.kallifabio.cloud.master.recovery;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.kallifabio.cloud.libs.logging.CentralLogger;
import de.kallifabio.cloud.master.Master;
import de.kallifabio.cloud.master.ServerInstance;
import de.kallifabio.cloud.master.events.EventTimelineService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class IncidentReportService {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final EventTimelineService events;

    public IncidentReportService(EventTimelineService events) {
        this.events = events;
    }

    public Path createIncident(Master master, ServerInstance server, String reason) {
        try {
            Files.createDirectories(Path.of("logs", "incidents"));
            String safeServer = safe(server == null ? "unknown" : server.serverName);
            Path target = Path.of("logs", "incidents",
                    FORMAT.format(LocalDateTime.now()) + "-" + safeServer + ".json");
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("generatedAt", System.currentTimeMillis());
            payload.put("reason", reason == null ? "unknown" : reason);
            payload.put("server", serverToMap(server));
            payload.put("masterId", master == null ? "unknown" : master.getMasterId());
            payload.put("wrappers", master == null ? 0 : master.getConnectedWrappers().size());
            payload.put("runningServers", master == null ? 0 : master.getRunningServers().size());
            payload.put("recentEvents", master == null ? List.of() : master.getEventTimelineService().recent(50, null, null));
            payload.put("recentLogLines", readRecentLogLines(120));
            Files.writeString(target, gson.toJson(payload), StandardCharsets.UTF_8);
            events.publish("INCIDENT_REPORT", "incident:" + safeServer, "WARNING",
                    "Incident report created: " + target, Map.of("server", safeServer, "path", target.toString()));
            return target;
        } catch (Exception e) {
            CentralLogger.error("Incident", "Failed to create incident report", e);
            return null;
        }
    }

    public List<Map<String, Object>> listIncidents(int limit) {
        Path dir = Path.of("logs", "incidents");
        if (!Files.exists(dir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.reverseOrder())
                    .limit(Math.max(1, limit))
                    .map(this::incidentFileToMap)
                    .toList();
        } catch (IOException e) {
            CentralLogger.warn("Incident", "Failed to list incident reports: " + e.getMessage());
            return List.of();
        }
    }

    private Map<String, Object> serverToMap(ServerInstance server) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (server == null) {
            return map;
        }
        map.put("serverName", server.serverName);
        map.put("groupName", server.groupName);
        map.put("wrapperId", server.wrapperId);
        map.put("status", server.status);
        map.put("lifecycleState", server.getLifecycleState().name());
        map.put("port", server.port);
        map.put("players", server.playerCount + "/" + server.maxPlayers);
        map.put("tps", server.tps);
        map.put("cpuUsage", server.cpuUsage);
        map.put("memoryUsage", server.memoryUsage);
        map.put("failureCount", server.failureCount);
        map.put("quarantined", server.quarantined);
        return map;
    }

    private Map<String, Object> incidentFileToMap(Path path) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("file", path.toString());
        map.put("name", path.getFileName().toString());
        try {
            map.put("size", Files.size(path));
            map.put("modified", Files.getLastModifiedTime(path).toMillis());
        } catch (IOException ignored) {
        }
        return map;
    }

    private List<String> readRecentLogLines(int limit) {
        Path today = Path.of("logs", "cloud-" + java.time.LocalDate.now() + ".log");
        if (!Files.exists(today)) {
            return List.of();
        }
        try {
            List<String> lines = Files.readAllLines(today, StandardCharsets.UTF_8);
            int from = Math.max(0, lines.size() - limit);
            return lines.subList(from, lines.size());
        } catch (IOException e) {
            return List.of();
        }
    }

    private String safe(String value) {
        return value == null ? "unknown" : value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
