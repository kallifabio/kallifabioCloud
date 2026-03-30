package de.kallifabio.cloud.commands.social;

import de.kallifabio.cloud.commands.BaseCloudCommand;

import java.util.ArrayList;
import java.util.List;

public class PartyAcceptCommand extends BaseCloudCommand {
    @Override
    public boolean execute(String sender, String[] args) {
        if (!ensureMaster()) return false;
        if (args.length < 1) {
            warn("Usage: " + getUsage());
            return false;
        }
        String playerUuid = args[0];
        String partyId = master().getDataStore().consumePartyInvite(playerUuid);
        if (partyId == null) {
            error("Keine offene Party-Invite fuer " + playerUuid);
            return false;
        }

        String leader = master().getDataStore().getPartyLeader(partyId);
        if (leader == null) {
            error("Party nicht gefunden: " + partyId);
            return false;
        }
        List<String> members = new ArrayList<>(master().getDataStore().getPartyMembers(partyId));
        if (!members.contains(playerUuid)) {
            members.add(playerUuid);
            master().getDataStore().setPartyMembers(partyId, leader, members);
        }
        info("Party-Invite angenommen: " + playerUuid + " -> " + partyId);
        return true;
    }

    @Override
    public String getDescription() {
        return "Nimmt eine offene Party-Invite an.";
    }

    @Override
    public String getUsage() {
        return "partyaccept <playerUuid>";
    }
}
