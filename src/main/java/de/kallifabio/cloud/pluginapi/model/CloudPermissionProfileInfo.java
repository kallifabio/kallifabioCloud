package de.kallifabio.cloud.pluginapi.model;

import java.util.List;

public record CloudPermissionProfileInfo(
        String playerUuid,
        String primaryGroup,
        List<String> groups
) {
}
