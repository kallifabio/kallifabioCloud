package de.kallifabio.cloud.commands.social;

import de.kallifabio.cloud.commands.BaseCloudCommand;

public class FriendOnlineCommand extends BaseCloudCommand {
    @Override
    public boolean execute(String sender, String[] args) {
        if (!ensureMaster()) return false;
        if (args.length < 1) {
            warn("Usage: " + getUsage());
            return false;
        }
        String server = master().getPlayerSessionManager().getCurrentServer(args[0]);
        if (server == null) {
            info(args[0] + " ist offline.");
            return true;
        }
        info(args[0] + " ist online auf " + server);
        return true;
    }

    @Override
    public String getDescription() {
        return "Prüft Online-Status eines Spielers.";
    }

    @Override
    public String getUsage() {
        return "friendonline <playerUuid>";
    }
}
