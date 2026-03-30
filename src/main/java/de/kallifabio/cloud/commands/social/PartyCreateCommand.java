package de.kallifabio.cloud.commands.social;

import de.kallifabio.cloud.commands.BaseCloudCommand;

import java.util.List;

public class PartyCreateCommand extends BaseCloudCommand {
    @Override
    public boolean execute(String sender, String[] args) {
        if (!ensureMaster()) return false;
        if (args.length < 2) {
            warn("Usage: " + getUsage());
            return false;
        }
        String partyId = args[0];
        String leader = args[1];
        master().getDataStore().setPartyMembers(partyId, leader, List.of(leader));
        info("Party erstellt: " + partyId + " leader=" + leader);
        return true;
    }

    @Override
    public String getDescription() {
        return "Erstellt eine Party.";
    }

    @Override
    public String getUsage() {
        return "partycreate <partyId> <leaderUuid>";
    }
}
