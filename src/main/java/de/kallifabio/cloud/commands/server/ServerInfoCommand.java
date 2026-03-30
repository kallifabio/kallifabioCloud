package de.kallifabio.cloud.commands.server;

import de.kallifabio.cloud.commands.BaseCloudCommand;
import de.kallifabio.cloud.master.ServerInstance;

public class ServerInfoCommand extends BaseCloudCommand {
    @Override
    public boolean execute(String sender, String[] args) {
        if (!ensureMaster()) return false;
        if (args.length < 1) {
            warn("Usage: " + getUsage());
            return false;
        }
        ServerInstance server = master().getRunningServers().get(args[0]);
        if (server == null) {
            error("Server nicht gefunden: " + args[0]);
            return false;
        }
        info("Server: " + server.serverName);
        info("Group: " + server.groupName + " | Wrapper: " + server.wrapperId + " | Status: " + server.status);
        info("Players: " + server.playerCount + "/" + server.maxPlayers + " | TPS: " + String.format("%.2f", server.tps));
        info("RAM: " + server.memoryUsage + " bytes | CPU: " + String.format("%.2f", server.cpuUsage) + "% | Port: " + server.port);
        return true;
    }

    @Override
    public String getDescription() {
        return "Zeigt Details zu einem Server.";
    }

    @Override
    public String getUsage() {
        return "serverinfo <serverName>";
    }
}
