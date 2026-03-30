package de.kallifabio.cloud.commands.console;

import de.kallifabio.cloud.commands.BaseCloudCommand;
import de.kallifabio.cloud.libs.console.ConsoleScreenManager;

public class ScreenSendCommand extends BaseCloudCommand {
    @Override
    public boolean execute(String sender, String[] args) {
        if (args.length < 2) {
            warn("Usage: " + getUsage());
            return false;
        }

        String serverName = args[0];
        String command = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));

        if (!ConsoleScreenManager.sendCommandToServer(serverName, command)) {
            error("Konnte Befehl nicht senden. Server nicht lokal/running: " + serverName);
            return false;
        }

        info("Befehl an " + serverName + " gesendet: " + command);
        return true;
    }

    @Override
    public String getDescription() {
        return "Sendet einen Konsolenbefehl an einen laufenden Server-Screen.";
    }

    @Override
    public String getUsage() {
        return "screencmd <serverName> <command...>";
    }
}

