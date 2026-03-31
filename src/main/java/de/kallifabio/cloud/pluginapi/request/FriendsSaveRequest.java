package de.kallifabio.cloud.pluginapi.request;

import java.util.List;

public record FriendsSaveRequest(String playerUuid, List<String> friends) {
}
