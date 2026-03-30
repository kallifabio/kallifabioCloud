package de.kallifabio.cloud.commands.server;

import de.kallifabio.cloud.commands.BaseCloudCommand;

public class StartServerCommand extends BaseCloudCommand {
    @Override
    public boolean execute(String sender, String[] args) {
        if (!ensureMaster()) return false;
        if (args.length < 2) {
            warn("Usage: " + getUsage());
            return false;
        }
        master().startServer(args[0], args[1]);
        return true;
    }

    @Override
    public String getDescription() {
        return "Startet einen Server.";
    }

    @Override
    public String getUsage() {
        return "startserver <serverName> <group>";
    }
}
