package de.kallifabio.cloud.master.events;

import java.util.Map;

public record CloudEvent(
        long timestamp,
        String type,
        String source,
        String severity,
        String message,
        Map<String, Object> details
) {
}
