package de.kallifabio.cloud.pluginapi.service;

import com.google.gson.JsonObject;
import de.kallifabio.cloud.pluginapi.CloudApiClient;
import de.kallifabio.cloud.pluginapi.model.CloudOperationResult;
import de.kallifabio.cloud.pluginapi.model.CloudPlayerDataModel;
import de.kallifabio.cloud.pluginapi.model.CloudQueueStatus;
import de.kallifabio.cloud.pluginapi.request.FriendsSaveRequest;
import de.kallifabio.cloud.pluginapi.request.PartySaveRequest;
import de.kallifabio.cloud.pluginapi.request.PartySwitchRequest;
import de.kallifabio.cloud.pluginapi.request.PlayerDataSaveRequest;

import java.util.List;
import java.util.Map;

public final class PlayerService {

    private final CloudApiClient client;

    public PlayerService(CloudApiClient client) {
        this.client = client;
    }

    public JsonObject queueStatus() {
        return client.get("/api/v1/queue/status");
    }

    public CloudQueueStatus queueStatusModel() {
        return CloudQueueStatus.from(queueStatus());
    }

    public CloudPlayerDataModel getPlayerData(String playerUuid) {
        JsonObject response = client.get("/api/v1/player/data", Map.of("uuid", playerUuid));
        return CloudPlayerDataModel.from(response);
    }

    public JsonObject savePlayerData(String playerUuid, int coins, int kills, int deaths, int wins, int losses, String rank) {
        return client.post("/api/v1/player/data", Map.of(
                "playerUuid", playerUuid,
                "coins", coins,
                "kills", kills,
                "deaths", deaths,
                "wins", wins,
                "losses", losses,
                "rank", rank
        ));
    }

    public CloudOperationResult savePlayerData(PlayerDataSaveRequest request) {
        return CloudOperationResult.from(savePlayerData(
                request.playerUuid(),
                request.coins(),
                request.kills(),
                request.deaths(),
                request.wins(),
                request.losses(),
                request.rank()
        ));
    }

    public JsonObject getFriends(String playerUuid) {
        return client.get("/api/v1/player/friends", Map.of("uuid", playerUuid));
    }

    public JsonObject saveFriends(String playerUuid, List<String> friends) {
        return client.post("/api/v1/player/friends", Map.of(
                "playerUuid", playerUuid,
                "friends", friends
        ));
    }

    public CloudOperationResult saveFriends(FriendsSaveRequest request) {
        return CloudOperationResult.from(saveFriends(request.playerUuid(), request.friends()));
    }

    public JsonObject getParty(String partyId) {
        return client.get("/api/v1/player/party", Map.of("partyId", partyId));
    }

    public JsonObject saveParty(String partyId, String leaderUuid, List<String> members) {
        return client.post("/api/v1/player/party", Map.of(
                "partyId", partyId,
                "leaderUuid", leaderUuid,
                "members", members
        ));
    }

    public CloudOperationResult saveParty(PartySaveRequest request) {
        return CloudOperationResult.from(saveParty(request.partyId(), request.leaderUuid(), request.members()));
    }

    public JsonObject switchParty(String partyId, String targetServer) {
        return client.post("/api/v1/party/switch", Map.of(
                "partyId", partyId,
                "targetServer", targetServer
        ));
    }

    public CloudOperationResult switchParty(PartySwitchRequest request) {
        return CloudOperationResult.from(switchParty(request.partyId(), request.targetServer()));
    }
}
