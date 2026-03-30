package de.kallifabio.cloud.commands.server;

import de.kallifabio.cloud.commands.BaseCloudCommand;

public class StopServerCommand extends BaseCloudCommand {
    @Override
    public boolean execute(String sender, String[] args) {
        if (!ensureMaster()) return false;
        if (args.length < 1) {
            warn("Usage: " + getUsage());
            return false;
        }
        master().stopServer(args[0]);
        return true;
    }

    @Override
    public String getDescription() {
        return "Stoppt einen Server graceful.";
    }

    @Override
    public String getUsage() {
        return "stopserver <serverName>";
    }
}
