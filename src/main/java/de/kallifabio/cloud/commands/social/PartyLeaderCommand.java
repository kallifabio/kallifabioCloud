package de.kallifabio.cloud.commands.social;

import de.kallifabio.cloud.commands.BaseCloudCommand;

public class PartyLeaderCommand extends BaseCloudCommand {
    @Override
    public boolean execute(String sender, String[] args) {
        if (!ensureMaster()) return false;
        if (args.length < 1) {
            warn("Usage: " + getUsage());
            return false;
        }
        String leader = master().getDataStore().getPartyLeader(args[0]);
        if (leader == null) {
            error("Party nicht gefunden: " + args[0]);
            return false;
        }
        info("Party-Leader von " + args[0] + ": " + leader);
        return true;
    }

    @Override
    public String getDescription() {
        return "Zeigt Party-Leader.";
    }

    @Override
    public String getUsage() {
        return "partyleader <partyId>";
    }
}
