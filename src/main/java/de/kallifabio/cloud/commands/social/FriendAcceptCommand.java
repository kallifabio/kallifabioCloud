package de.kallifabio.cloud.commands.social;

import java.util.ArrayList;
import java.util.List;

public class FriendAcceptCommand extends FriendAddCommand {
    @Override
    public boolean execute(String sender, String[] args) {
        if (!ensureMaster()) return false;
        if (args.length < 1) {
            warn("Usage: " + getUsage());
            return false;
        }
        String accepter = args[0];
        String requester = master().getDataStore().consumeFriendRequest(accepter);
        if (requester == null) {
            error("Keine offene Friend-Request fuer " + accepter);
            return false;
        }

        List<String> accepterFriends = new ArrayList<>(master().getDataStore().getFriends(accepter));
        if (!accepterFriends.contains(requester)) {
            accepterFriends.add(requester);
            master().getDataStore().setFriends(accepter, accepterFriends);
        }
        List<String> requesterFriends = new ArrayList<>(master().getDataStore().getFriends(requester));
        if (!requesterFriends.contains(accepter)) {
            requesterFriends.add(accepter);
            master().getDataStore().setFriends(requester, requesterFriends);
        }
        info("Friend-Request angenommen: " + requester + " <-> " + accepter);
        return true;
    }

    @Override
    public String getDescription() {
        return "Akzeptiert eine offene Friend-Request.";
    }

    @Override
    public String getUsage() {
        return "friendaccept <playerUuid>";
    }
}
