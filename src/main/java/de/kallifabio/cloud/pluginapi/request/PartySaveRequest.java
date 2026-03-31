package de.kallifabio.cloud.pluginapi.request;

import java.util.List;

public record PartySaveRequest(String partyId, String leaderUuid, List<String> members) {
}
