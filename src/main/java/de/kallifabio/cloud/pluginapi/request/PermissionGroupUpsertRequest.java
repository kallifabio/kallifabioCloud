package de.kallifabio.cloud.pluginapi.request;

import java.util.List;

public record PermissionGroupUpsertRequest(
        String name,
        String parent,
        int weight,
        String prefix,
        String suffix,
        List<String> permissions
) {
}
