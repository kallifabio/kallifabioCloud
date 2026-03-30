package de.kallifabio.cloud.commands.social;

import de.kallifabio.cloud.commands.BaseCloudCommand;

import java.util.List;

public class PartySwitchCommand extends BaseCloudCommand {
    @Override
    public boolean execute(String sender, String[] args) {
        if (!ensureMaster()) return false;
        if (args.length < 2) {
            warn("Usage: " + getUsage());
            return false;
        }
        String partyId = args[0];
        String targetServer = args[1];
        List<String> members = master().getDataStore().getPartyMembers(partyId);
        if (members.isEmpty()) {
            error("Party leer oder nicht gefunden: " + partyId);
            return false;
        }
        for (String member : members) {
            master().getPlayerSessionManager().assignServer(member, targetServer);
        }
        info("Party " + partyId + " auf Server " + targetServer + " umgestellt (" + members.size() + " Spieler).");
        return true;
    }

    @Override
    public String getDescription() {
        return "Verschiebt alle Party-Mitglieder auf Zielserver.";
    }

    @Override
    public String getUsage() {
        return "partyswitch <partyId> <targetServer>";
    }
}
