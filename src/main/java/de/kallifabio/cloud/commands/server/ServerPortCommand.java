package de.kallifabio.cloud.commands.server;

import de.kallifabio.cloud.commands.BaseCloudCommand;

public class ServerPortCommand extends BaseCloudCommand {
    @Override
    public boolean execute(String sender, String[] args) {
        if (!ensureMaster()) return false;
        if (args.length < 1) {
            warn("Usage: " + getUsage());
            return false;
        }
        int port = master().getServerPort(args[0]);
        if (port < 0) {
            error("Kein Port für Server " + args[0] + " gefunden.");
            return false;
        }
        info(args[0] + " läuft auf Port " + port);
        return true;
    }

    @Override
    public String getDescription() {
        return "Zeigt den Port eines Servers.";
    }

    @Override
    public String getUsage() {
        return "serverport <serverName>";
    }
}
