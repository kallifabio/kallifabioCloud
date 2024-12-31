/**
 * Erstellt von Gamer_Kidd_LP | kallifabio
 * am 29.12.2024 um 03:44
 * Projektname: CloudSystemTest
 * Packagename: de.kallifabio.cloudsystem.commands
 */

package de.kallifabio.cloudsystem.commands;

import java.util.HashMap;
import java.util.Map;

public class CommandHandler {

    private final Map<String, Command> commands = new HashMap<>();

    // Registriere einen neuen Befehl
    public void registerCommand(String name, Command command) {
        commands.put(name.toLowerCase(), command);
    }

    // Führe einen Befehl aus
    public boolean executeCommand(String input, String sender) {
        String[] args = input.split(" ");
        if (args.length == 0) {
            return false; // Keine Eingabe
        }

        String commandName = args[0].toLowerCase();
        Command command = commands.get(commandName);

        if (command == null) {
            System.out.println("Unbekannter Befehl: " + commandName);
            return false;
        }

        String[] commandArgs = new String[args.length - 1];
        System.arraycopy(args, 1, commandArgs, 0, commandArgs.length);

        return command.execute(sender, commandArgs);
    }
}
