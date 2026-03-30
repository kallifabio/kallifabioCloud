package de.kallifabio.cloud.commands.server;

import de.kallifabio.cloud.commands.BaseCloudCommand;

public class ForceStopServerCommand extends BaseCloudCommand {
    @Override
    public boolean execute(String sender, String[] args) {
        if (!ensureMaster()) return false;
        if (args.length < 1) {
            warn("Usage: " + getUsage());
            return false;
        }
        master().forceStopServer(args[0]);
        return true;
    }

    @Override
    public String getDescription() {
        return "Stoppt einen Server mit FORCE_STOP.";
    }

    @Override
    public String getUsage() {
        return "forcestopserver <serverName>";
    }
}
