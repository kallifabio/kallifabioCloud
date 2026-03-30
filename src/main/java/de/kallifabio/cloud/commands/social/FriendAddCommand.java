package de.kallifabio.cloud.commands.social;

import de.kallifabio.cloud.commands.BaseCloudCommand;

import java.util.ArrayList;
import java.util.List;

public class FriendAddCommand extends BaseCloudCommand {
    @Override
    public boolean execute(String sender, String[] args) {
        if (!ensureMaster()) return false;
        if (args.length < 2) {
            warn("Usage: " + getUsage());
            return false;
        }
        String player = args[0];
        String friend = args[1];

        List<String> pFriends = new ArrayList<>(master().getDataStore().getFriends(player));
        if (!pFriends.contains(friend)) {
            pFriends.add(friend);
            master().getDataStore().setFriends(player, pFriends);
        }

        List<String> fFriends = new ArrayList<>(master().getDataStore().getFriends(friend));
        if (!fFriends.contains(player)) {
            fFriends.add(player);
            master().getDataStore().setFriends(friend, fFriends);
        }

        info("Friendship erstellt: " + player + " <-> " + friend);
        return true;
    }

    @Override
    public String getDescription() {
        return "Fuegt zwei Spieler als Freunde hinzu.";
    }

    @Override
    public String getUsage() {
        return "friendadd <playerUuid> <friendUuid>";
    }
}
