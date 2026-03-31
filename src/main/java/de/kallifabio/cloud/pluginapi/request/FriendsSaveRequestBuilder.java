package de.kallifabio.cloud.pluginapi.request;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class FriendsSaveRequestBuilder {

    private String playerUuid;
    private final List<String> friends = new ArrayList<>();

    public static FriendsSaveRequestBuilder create() {
        return new FriendsSaveRequestBuilder();
    }

    public FriendsSaveRequestBuilder playerUuid(String playerUuid) {
        this.playerUuid = playerUuid;
        return this;
    }

    public FriendsSaveRequestBuilder addFriend(String friendUuid) {
        if (friendUuid != null && !friendUuid.isBlank()) {
            friends.add(friendUuid);
        }
        return this;
    }

    public FriendsSaveRequest build() {
        return new FriendsSaveRequest(
                Objects.requireNonNull(playerUuid, "playerUuid"),
                List.copyOf(friends)
        );
    }
}
