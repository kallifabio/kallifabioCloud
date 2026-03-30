package de.kallifabio.cloud.commands;

import de.kallifabio.cloud.libs.console.ConsoleColors;
import de.kallifabio.cloud.libs.console.ConsoleScreenManager;
import de.kallifabio.cloud.master.Master;

public abstract class BaseCloudCommand implements Command {

    protected Master master() {
        return Master.getInstance();
    }

    protected boolean ensureMaster() {
        if (master() == null) {
            ConsoleScreenManager.printToTerminal(ConsoleColors.RED + "Master ist nicht gestartet.");
            return false;
        }
        return true;
    }

    protected void info(String message) {
        ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() + " " + message);
    }

    protected void warn(String message) {
        ConsoleScreenManager.printToTerminal(ConsoleColors.YELLOW + message);
    }

    protected void error(String message) {
        ConsoleScreenManager.printToTerminal(ConsoleColors.RED + message);
    }
}
