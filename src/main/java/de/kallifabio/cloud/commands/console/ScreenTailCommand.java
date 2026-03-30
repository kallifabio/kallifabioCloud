package de.kallifabio.cloud.commands.console;

import de.kallifabio.cloud.commands.BaseCloudCommand;
import de.kallifabio.cloud.libs.console.ConsoleScreenManager;

import java.util.List;

public class ScreenTailCommand extends BaseCloudCommand {
    @Override
    public boolean execute(String sender, String[] args) {
        if (args.length < 1) {
            warn("Usage: " + getUsage());
            return false;
        }

        String serverName = args[0];
        int lines = 20;
        if (args.length >= 2) {
            try {
                lines = Math.max(1, Integer.parseInt(args[1]));
            } catch (NumberFormatException ignored) {
                warn("Ungueltige Anzahl, verwende 20.");
            }
        }

        if (!ConsoleScreenManager.hasScreen(serverName)) {
            warn("Screen nicht gefunden: " + serverName);
            return false;
        }

        List<String> tail = ConsoleScreenManager.getScreenMessages(serverName, lines);
        info("Screen-Tail " + serverName + " (" + tail.size() + " Zeilen):");
        for (String line : tail) {
            info(line);
        }
        return true;
    }

    @Override
    public String getDescription() {
        return "Zeigt die letzten Zeilen eines Server-Screens.";
    }

    @Override
    public String getUsage() {
        return "screentail <serverName> [lines]";
    }
}

