/**
 * Erstellt von Gamer_Kidd_LP | kallifabio
 * am 29.12.2024 um 03:47
 * Projektname: CloudSystemTest
 * Packagename: de.kallifabio.cloudsystem.commands
 */

package de.kallifabio.cloudsystem.commands;

import de.kallifabio.cloudsystem.libs.ConsoleColors;
import de.kallifabio.cloudsystem.libs.Message;
import de.kallifabio.cloudsystem.managers.ConsoleScreenManager;
import de.kallifabio.cloudsystem.master.Master;
import de.kallifabio.cloudsystem.wrapper.Wrapper;

public class startserverCommand implements Command {

    @Override
    public boolean execute(String sender, String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: startserver <groupName> <serverName>");
            return false;
        }

        String serverName = args[0];
        String groupName = serverName.startsWith("Proxy") ? "Proxy" : "Lobby";

        try {
            // Erzeuge und sende den Startbefehl an den Wrapper
            Message.ServerCommand startCommand = new Message.ServerCommand();
            startCommand.command = "START";
            startCommand.serverName = serverName;

            Wrapper wrapper = Master.getInstance().getWrapper(); // Hole die Wrapper-Instanz aus dem Master
            if (wrapper == null || wrapper.getClient() == null) {
                System.out.println(ConsoleColors.RED + "Wrapper ist nicht verbunden!");
                return false;
            }

            wrapper.getClient().sendTCP(startCommand);
            System.out.println(ConsoleColors.GREEN + "Server '" + serverName + "' in Gruppe '" + groupName + "' wird gestartet...");
            return true;
        } catch (Exception e) {
            System.out.println(ConsoleColors.RED + "Fehler beim Starten des Servers: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}
