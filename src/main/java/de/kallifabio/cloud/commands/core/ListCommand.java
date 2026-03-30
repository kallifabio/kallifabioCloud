package de.kallifabio.cloud.commands.core;

import de.kallifabio.cloud.commands.BaseCloudCommand;
import de.kallifabio.cloud.master.ServerInstance;

public class ListCommand extends BaseCloudCommand {

    @Override
    public boolean execute(String sender, String[] args) {
        if (!ensureMaster()) return false;

        if (master().getRunningServers().isEmpty()) {
            info("Keine Server aktiv.");
            return true;
        }

        for (ServerInstance server : master().getRunningServers().values()) {
            info(server.serverName + " [" + server.groupName + "] " + server.status +
                    " " + server.playerCount + "/" + server.maxPlayers + " port=" + server.port);
        }
        return true;
    }

    @Override
    public String getDescription() {
        return "Listet alle laufenden Server.";
    }

    @Override
    public String getUsage() {
        return "list";
    }
}
