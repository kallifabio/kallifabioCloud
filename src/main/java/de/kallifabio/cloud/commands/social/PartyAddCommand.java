package de.kallifabio.cloud.commands.social;

import de.kallifabio.cloud.commands.BaseCloudCommand;

import java.util.ArrayList;
import java.util.List;

public class PartyAddCommand extends BaseCloudCommand {
    @Override
    public boolean execute(String sender, String[] args) {
        if (!ensureMaster()) return false;
        if (args.length < 2) {
            warn("Usage: " + getUsage());
            return false;
        }
        String partyId = args[0];
        String player = args[1];
        List<String> members = new ArrayList<>(master().getDataStore().getPartyMembers(partyId));
        String leader = master().getDataStore().getPartyLeader(partyId);
        if (leader == null) {
            error("Party existiert nicht: " + partyId);
            return false;
        }
        if (!members.contains(player)) {
            members.add(player);
            master().getDataStore().setPartyMembers(partyId, leader, members);
        }
        info("Spieler zur Party hinzugefuegt: " + player + " -> " + partyId);
        return true;
    }

    @Override
    public String getDescription() {
        return "Fuegt Spieler zu Party hinzu.";
    }

    @Override
    public String getUsage() {
        return "partyadd <partyId> <playerUuid>";
    }
}
