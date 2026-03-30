package de.kallifabio.cloud.commands.social;

import de.kallifabio.cloud.commands.BaseCloudCommand;

import java.util.List;

public class PartyListCommand extends BaseCloudCommand {
    @Override
    public boolean execute(String sender, String[] args) {
        if (!ensureMaster()) return false;
        if (args.length < 1) {
            warn("Usage: " + getUsage());
            return false;
        }
        String partyId = args[0];
        String leader = master().getDataStore().getPartyLeader(partyId);
        List<String> members = master().getDataStore().getPartyMembers(partyId);
        if (leader == null) {
            error("Party nicht gefunden: " + partyId);
            return false;
        }
        info("Party " + partyId + " leader=" + leader + " members=" + members);
        return true;
    }

    @Override
    public String getDescription() {
        return "Listet Party-Mitglieder.";
    }

    @Override
    public String getUsage() {
        return "partylist <partyId>";
    }
}
