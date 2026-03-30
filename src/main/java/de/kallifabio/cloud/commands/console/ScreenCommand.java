package de.kallifabio.cloud.commands.console;

import de.kallifabio.cloud.commands.BaseCloudCommand;
import de.kallifabio.cloud.libs.console.ConsoleScreenManager;

import java.util.Set;

public class ScreenCommand extends BaseCloudCommand {
    @Override
    public boolean execute(String sender, String[] args) {
        if (args.length == 0 || "list".equalsIgnoreCase(args[0])) {
            Set<String> names = ConsoleScreenManager.getScreenNames();
            info("Screens: " + String.join(", ", names));
            return true;
        }

        String target = args[0];
        if ("main".equalsIgnoreCase(target)) {
            ConsoleScreenManager.switchToMainScreen();
            info("Auf Main-Screen gewechselt.");
            return true;
        }

        if (!ConsoleScreenManager.switchToScreen(target)) {
            warn("Screen nicht gefunden: " + target);
            warn("Usage: " + getUsage());
            return false;
        }

        info("Zu Screen gewechselt: " + target);
        for (String line : ConsoleScreenManager.getScreenMessages(target, 20)) {
            info(line);
        }
        return true;
    }

    @Override
    public String getDescription() {
        return "Wechselt auf einen Console-Screen oder listet Screens.";
    }

    @Override
    public String getUsage() {
        return "screen <list|main|serverName>";
    }
}

