package de.kallifabio.cloud.commands.social;

import de.kallifabio.cloud.commands.BaseCloudCommand;

import java.util.List;

public class FriendListCommand extends BaseCloudCommand {
    @Override
    public boolean execute(String sender, String[] args) {
        if (!ensureMaster()) return false;
        if (args.length < 1) {
            warn("Usage: " + getUsage());
            return false;
        }
        List<String> friends = master().getDataStore().getFriends(args[0]);
        info("Friends von " + args[0] + ": " + friends);
        return true;
    }

    @Override
    public String getDescription() {
        return "Zeigt Friend-Liste eines Spielers.";
    }

    @Override
    public String getUsage() {
        return "friendlist <playerUuid>";
    }
}
