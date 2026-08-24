package de.kallifabio.cloud.master.events;

import de.kallifabio.cloud.libs.logging.CentralLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;

public final class EventTimelineService {

    private static final int MAX_EVENTS = 2_000;
    private final Deque<CloudEvent> events = new ConcurrentLinkedDeque<>();

    public CloudEvent publish(String type, String source, String severity, String message, Map<String, Object> details) {
        CloudEvent event = new CloudEvent(
                System.currentTimeMillis(),
                normalize(type, "UNKNOWN"),
                normalize(source, "system"),
                normalize(severity, "INFO"),
                message == null ? "" : message,
                details == null ? Map.of() : Map.copyOf(details)
        );
        events.addLast(event);
        while (events.size() > MAX_EVENTS) {
            events.pollFirst();
        }
        CentralLogger.info("EVENT", event.type() + " | " + event.source() + " | " + event.message());
        return event;
    }

    public List<Map<String, Object>> recent(int limit, String typeFilter, String severityFilter) {
        String type = normalizeFilter(typeFilter);
        String severity = normalizeFilter(severityFilter);
        List<Map<String, Object>> result = new ArrayList<>();
        List<CloudEvent> snapshot = new ArrayList<>(events);
        Collections.reverse(snapshot);

        for (CloudEvent event : snapshot) {
            if (result.size() >= limit) {
                break;
            }
            if (type != null && !type.equalsIgnoreCase(event.type())) {
                continue;
            }
            if (severity != null && !severity.equalsIgnoreCase(event.severity())) {
                continue;
            }
            result.add(toMap(event));
        }
        Collections.reverse(result);
        return result;
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("totalBuffered", events.size());
        payload.put("recent", recent(100, null, null));
        return payload;
    }

    private Map<String, Object> toMap(CloudEvent event) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("timestamp", event.timestamp());
        map.put("type", event.type());
        map.put("source", event.source());
        map.put("severity", event.severity());
        map.put("message", event.message());
        map.put("details", event.details());
        return map;
    }

    private String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeFilter(String value) {
        if (value == null || value.isBlank() || "all".equalsIgnoreCase(value)) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
