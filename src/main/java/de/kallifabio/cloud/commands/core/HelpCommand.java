package de.kallifabio.cloud.commands.core;

import de.kallifabio.cloud.commands.BaseCloudCommand;
import de.kallifabio.cloud.commands.Command;
import de.kallifabio.cloud.commands.CommandHandler;
import de.kallifabio.cloud.libs.console.ConsoleColors;
import de.kallifabio.cloud.libs.console.ConsoleScreenManager;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

public class HelpCommand extends BaseCloudCommand {

    private final CommandHandler commandHandler;

    public HelpCommand(CommandHandler commandHandler) {
        this.commandHandler = commandHandler;
    }

    @Override
    public boolean execute(String sender, String[] args) {
        Map<String, Command> all = commandHandler.getCommands();
        Map<Class<?>, String> canonicalNames = new LinkedHashMap<>();
        Map<Class<?>, Command> canonicalCommands = new LinkedHashMap<>();
        for (Map.Entry<String, Command> entry : all.entrySet()) {
            canonicalNames.putIfAbsent(entry.getValue().getClass(), entry.getKey());
            canonicalCommands.putIfAbsent(entry.getValue().getClass(), entry.getValue());
        }

        ConsoleScreenManager.printToTerminal(ConsoleColors.CYAN + "=== Verfuegbare Commands ===");
        canonicalNames.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getValue))
                .forEach(entry -> {
                    Command command = canonicalCommands.get(entry.getKey());
                    ConsoleScreenManager.printToTerminal(ConsoleColors.YELLOW + entry.getValue() +
                            ConsoleColors.WHITE + " - " + command.getDescription());
                });
        return true;
    }

    @Override
    public String getDescription() {
        return "Zeigt alle verfuegbaren Commands.";
    }

    @Override
    public String getUsage() {
        return "help";
    }
}
