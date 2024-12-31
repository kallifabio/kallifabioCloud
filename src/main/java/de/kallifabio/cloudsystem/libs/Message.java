/**
 * Erstellt von Gamer_Kidd_LP | kallifabio
 * am 28.12.2024 um 20:18
 * Projektname: CloudSystemTest
 * Packagename: de.kallifabio.cloudsystem.libs
 */

package de.kallifabio.cloudsystem.libs;

public class Message {

    public static class ServerStatusMessage {
        public String status;
        public String serverName;

        // Standardkonstruktor
        public ServerStatusMessage() {}
    }

    public static class ServerCommand {
        public String command;
        public String serverName;

        // Standardkonstruktor
        public ServerCommand() {}
    }
}
