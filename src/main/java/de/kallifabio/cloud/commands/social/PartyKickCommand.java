package de.kallifabio.cloud.commands.social;

import de.kallifabio.cloud.commands.BaseCloudCommand;

import java.util.ArrayList;
import java.util.List;

public class PartyKickCommand extends BaseCloudCommand {
    @Override
    public boolean execute(String sender, String[] args) {
        if (!ensureMaster()) return false;
        if (args.length < 2) {
            warn("Usage: " + getUsage());
            return false;
        }
        String partyId = args[0];
        String player = args[1];
        String leader = master().getDataStore().getPartyLeader(partyId);
        List<String> members = new ArrayList<>(master().getDataStore().getPartyMembers(partyId));
        if (leader == null) {
            error("Party existiert nicht: " + partyId);
            return false;
        }
        members.remove(player);
        master().getDataStore().setPartyMembers(partyId, leader, members);
        info("Spieler aus Party entfernt: " + player);
        return true;
    }

    @Override
    public String getDescription() {
        return "Entfernt Spieler aus Party.";
    }

    @Override
    public String getUsage() {
        return "partykick <partyId> <playerUuid>";
    }
}
