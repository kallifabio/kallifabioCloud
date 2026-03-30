package de.kallifabio.cloud.commands.social;

import de.kallifabio.cloud.commands.BaseCloudCommand;

import java.util.concurrent.TimeUnit;

public class FriendRequestCommand extends BaseCloudCommand {
    private static final long REQUEST_TTL_MINUTES = 30;

    @Override
    public boolean execute(String sender, String[] args) {
        if (!ensureMaster()) return false;
        if (args.length < 2) {
            warn("Usage: " + getUsage());
            return false;
        }
        String from = args[0];
        String to = args[1];
        long expiresAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(REQUEST_TTL_MINUTES);
        master().getDataStore().createFriendRequest(from, to, expiresAt);
        info("Friend-Request gespeichert: " + from + " -> " + to + " (TTL " + REQUEST_TTL_MINUTES + "m)");
        return true;
    }

    @Override
    public String getDescription() {
        return "Erstellt eine offene Friend-Request (pending).";
    }

    @Override
    public String getUsage() {
        return "friendrequest <fromUuid> <toUuid>";
    }
}
