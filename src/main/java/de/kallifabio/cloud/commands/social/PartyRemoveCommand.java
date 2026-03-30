package de.kallifabio.cloud.commands.social;

import de.kallifabio.cloud.commands.BaseCloudCommand;

import java.util.List;

public class PartyRemoveCommand extends BaseCloudCommand {
    @Override
    public boolean execute(String sender, String[] args) {
        if (!ensureMaster()) return false;
        if (args.length < 1) {
            warn("Usage: " + getUsage());
            return false;
        }
        String partyId = args[0];
        String leader = master().getDataStore().getPartyLeader(partyId);
        if (leader == null) {
            error("Party nicht gefunden: " + partyId);
            return false;
        }
        master().getDataStore().setPartyMembers(partyId, leader, List.of());
        info("Party entfernt (geleert): " + partyId);
        return true;
    }

    @Override
    public String getDescription() {
        return "Entfernt/Leert eine Party.";
    }

    @Override
    public String getUsage() {
        return "partyremove <partyId>";
    }
}
