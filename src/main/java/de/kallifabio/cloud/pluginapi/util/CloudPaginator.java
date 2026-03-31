package de.kallifabio.cloud.pluginapi.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CloudPaginator {

    private CloudPaginator() {
    }

    public static <T> List<T> page(List<T> source, int page, int pageSize) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, pageSize);
        int from = (safePage - 1) * safeSize;
        if (from >= source.size()) {
            return List.of();
        }
        int to = Math.min(source.size(), from + safeSize);
        return Collections.unmodifiableList(new ArrayList<>(source.subList(from, to)));
    }
}
