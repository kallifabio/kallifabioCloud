package de.kallifabio.cloud.pluginapi.model;

import java.util.List;

public record CloudPermissionProfileInfo(
        String playerUuid,
        String primaryGroup,
        String prefix,
        String suffix,
        List<String> permissions
) {
}
