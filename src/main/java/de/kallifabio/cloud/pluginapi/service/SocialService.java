package de.kallifabio.cloud.pluginapi.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.kallifabio.cloud.pluginapi.CloudApiClient;
import de.kallifabio.cloud.pluginapi.model.CloudOperationResult;
import de.kallifabio.cloud.pluginapi.request.FriendsSaveRequest;
import de.kallifabio.cloud.pluginapi.request.PartySaveRequest;
import de.kallifabio.cloud.pluginapi.request.PartySwitchRequest;

import java.util.ArrayList;
import java.util.List;

public final class SocialService {

    private final PlayerService playerService;

    public SocialService(CloudApiClient client) {
        this.playerService = new PlayerService(client);
    }

    public List<String> friendList(String playerUuid) {
        JsonObject response = playerService.getFriends(playerUuid);
        List<String> friends = new ArrayList<>();
        JsonArray array = response.has("friends") ? response.getAsJsonArray("friends") : new JsonArray();
        for (JsonElement element : array) {
            friends.add(element.getAsString());
        }
        return friends;
    }

    public CloudOperationResult saveFriends(FriendsSaveRequest request) {
        return playerService.saveFriends(request);
    }

    public List<String> partyMembers(String partyId) {
        JsonObject response = playerService.getParty(partyId);
        List<String> members = new ArrayList<>();
        JsonArray array = response.has("members") ? response.getAsJsonArray("members") : new JsonArray();
        for (JsonElement element : array) {
            members.add(element.getAsString());
        }
        return members;
    }

    public CloudOperationResult saveParty(PartySaveRequest request) {
        return playerService.saveParty(request);
    }

    public CloudOperationResult switchParty(PartySwitchRequest request) {
        return playerService.switchParty(request);
    }
}
