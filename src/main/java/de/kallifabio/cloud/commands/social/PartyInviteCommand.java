package de.kallifabio.cloud.commands.social;

import de.kallifabio.cloud.commands.BaseCloudCommand;

import java.util.concurrent.TimeUnit;

public class PartyInviteCommand extends BaseCloudCommand {
    private static final long INVITE_TTL_MINUTES = 15;

    @Override
    public boolean execute(String sender, String[] args) {
        if (!ensureMaster()) return false;
        if (args.length < 2) {
            warn("Usage: " + getUsage());
            return false;
        }
        String partyId = args[0];
        String playerUuid = args[1];
        long expiresAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(INVITE_TTL_MINUTES);
        master().getDataStore().createPartyInvite(partyId, sender, playerUuid, expiresAt);
        info("Party-Invite gespeichert: party=" + partyId + " -> player=" + playerUuid + " (TTL " + INVITE_TTL_MINUTES + "m)");
        return true;
    }

    @Override
    public String getDescription() {
        return "Legt eine offene Party-Einladung an (pending).";
    }

    @Override
    public String getUsage() {
        return "partyinvite <partyId> <playerUuid>";
    }
}
