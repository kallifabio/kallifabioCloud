package de.kallifabio.cloud.commands.social;

import de.kallifabio.cloud.commands.BaseCloudCommand;

import java.util.ArrayList;
import java.util.List;

public class FriendRemoveCommand extends BaseCloudCommand {
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
        pFriends.remove(friend);
        master().getDataStore().setFriends(player, pFriends);

        List<String> fFriends = new ArrayList<>(master().getDataStore().getFriends(friend));
        fFriends.remove(player);
        master().getDataStore().setFriends(friend, fFriends);

        info("Friendship entfernt: " + player + " x " + friend);
        return true;
    }

    @Override
    public String getDescription() {
        return "Entfernt Freundschaft zwischen zwei Spielern.";
    }

    @Override
    public String getUsage() {
        return "friendremove <playerUuid> <friendUuid>";
    }
}
