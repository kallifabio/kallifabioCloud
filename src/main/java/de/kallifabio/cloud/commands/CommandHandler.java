/**
 * Erstellt von Gamer_Kidd_LP | kallifabio
 * am 29.12.2024 um 03:44
 * Projektname: CloudSystemTest
 * Packagename: de.kallifabio.cloudsystem.commands
 */

package de.kallifabio.cloud.commands;

import de.kallifabio.cloud.libs.console.ConsoleColors;
import de.kallifabio.cloud.libs.console.ConsoleScreenManager;

import java.util.HashMap;
import java.util.Map;

public class CommandHandler {

    private final Map<String, Command> commands = new HashMap<>();

    public CommandHandler() {
        // Registriere Default-Commands
        registerDefaultCommands();
    }

    private void registerDefaultCommands() {
        registerCommand("startserver", new startserverCommand());
        registerCommand("stopserver", new StopServerCommand());
        registerCommand("restartserver", new RestartServerCommand());
        registerCommand("list", new ListCommand());
        registerCommand("status", new StatusCommand());
        registerCommand("help", new HelpCommand(this));

        // Aliases
        registerCommand("start", new startserverCommand());
        registerCommand("stop", new StopServerCommand());
        registerCommand("restart", new RestartServerCommand());
        registerCommand("ls", new ListCommand());

        ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX +
                ConsoleColors.getCurrentTime() + " Commands registriert: " + commands.size());
    }

    /**
     * Registriere einen neuen Befehl
     */
    public void registerCommand(String name, Command command) {
        commands.put(name.toLowerCase(), command);
    }

    /**
     * Führe einen Befehl aus
     */
    public boolean executeCommand(String input, String sender) {
        if (input == null || input.trim().isEmpty()) {
            return false;
        }

        // Console-spezifische Commands (mit /)
        if (input.startsWith("/")) {
            return false; // Wird von ConsoleScreenManager gehandhabt
        }

        String[] args = input.trim().split(" ");
        if (args.length == 0) {
            return false;
        }

        String commandName = args[0].toLowerCase();
        Command command = commands.get(commandName);

        if (command == null) {
            ConsoleScreenManager.printToTerminal(ConsoleColors.RED +
                    "Unbekannter Befehl: " + commandName);
            ConsoleScreenManager.printToTerminal(ConsoleColors.YELLOW +
                    "Tippe 'help' für eine Liste aller Commands");
            return false;
        }

        // Extract command arguments
        String[] commandArgs = new String[args.length - 1];
        System.arraycopy(args, 1, commandArgs, 0, commandArgs.length);

        try {
            return command.execute(sender, commandArgs);
        } catch (Exception e) {
            ConsoleScreenManager.printToTerminal(ConsoleColors.RED +
                    "Fehler beim Ausführen von '" + commandName + "': " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Gibt alle registrierten Commands zurück
     */
    public Map<String, Command> getCommands() {
        return new HashMap<>(commands);
    }

    /**
     * Prüft ob ein Command existiert
     */
    public boolean hasCommand(String name) {
        return commands.containsKey(name.toLowerCase());
    }

    /**
     * Entfernt einen Command
     */
    public void unregisterCommand(String name) {
        commands.remove(name.toLowerCase());
    }
}
