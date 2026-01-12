/**
 * Erstellt von Gamer_Kidd_LP | kallifabio
 * am 29.12.2024 um 03:47
 * Projektname: CloudSystemTest
 * Packagename: de.kallifabio.cloudsystem.commands
 */

package de.kallifabio.cloud.commands;

import de.kallifabio.cloud.libs.console.ConsoleColors;
import de.kallifabio.cloud.libs.Message;
import de.kallifabio.cloud.libs.console.ConsoleScreenManager;
import de.kallifabio.cloud.master.Master;
import de.kallifabio.cloud.master.ServerInstance;
import de.kallifabio.cloud.master.WrapperConnection;
import de.kallifabio.cloud.wrapper.Wrapper;

import java.util.Map;

public class startserverCommand implements Command {

    @Override
    public boolean execute(String sender, String[] args) {
        if (args.length < 1) {
            ConsoleScreenManager.logToMainScreen(ConsoleColors.RED +
                    "Usage: startserver <groupName> [serverName]");
            ConsoleScreenManager.logToMainScreen(ConsoleColors.YELLOW +
                    "Beispiel: startserver Lobby");
            ConsoleScreenManager.logToMainScreen(ConsoleColors.YELLOW +
                    "Beispiel: startserver Proxy Proxy-2");
            return false;
        }

        String groupName = args[0];
        String serverName;

        // Wenn kein Server-Name angegeben, generiere einen
        if (args.length >= 2) {
            serverName = args[1];
        } else {
            serverName = generateServerName(groupName);
        }

        try {
            Master master = Master.getInstance();
            if (master == null) {
                ConsoleScreenManager.logToMainScreen(ConsoleColors.RED +
                        "Master ist nicht verfügbar!");
                return false;
            }

            // Nutze die Master-Methode zum Starten
            master.startServer(serverName, groupName);

            ConsoleScreenManager.logToMainScreen(ConsoleColors.GREEN +
                    "Server '" + serverName + "' wird gestartet...");
            return true;

        } catch (Exception e) {
            ConsoleScreenManager.logToMainScreen(ConsoleColors.RED +
                    "Fehler beim Starten: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private String generateServerName(String groupName) {
        Master master = Master.getInstance();
        long count = master.getRunningServers().values().stream()
                .filter(s -> s.groupName.equalsIgnoreCase(groupName))
                .count();
        return groupName + "-" + (count + 1);
    }


    public String getDescription() {
        return "Startet einen neuen Server";
    }


    public String getUsage() {
        return "startserver <groupName> [serverName]";
    }
}

/**
 * Command: stopserver <serverName>
 * Stoppt einen laufenden Server
 */
class StopServerCommand implements Command {

    @Override
    public boolean execute(String sender, String[] args) {
        if (args.length < 1) {
            ConsoleScreenManager.logToMainScreen(ConsoleColors.RED +
                    "Usage: stopserver <serverName>");
            return false;
        }

        String serverName = args[0];
        Master master = Master.getInstance();

        if (master == null) {
            ConsoleScreenManager.logToMainScreen(ConsoleColors.RED +
                    "Master ist nicht verfügbar!");
            return false;
        }

        ServerInstance server = master.getRunningServers().get(serverName);
        if (server == null) {
            ConsoleScreenManager.logToMainScreen(ConsoleColors.RED +
                    "Server '" + serverName + "' nicht gefunden!");
            return false;
        }

        master.stopServer(serverName);
        ConsoleScreenManager.logToMainScreen(ConsoleColors.GREEN +
                "Server '" + serverName + "' wird gestoppt...");

        return true;
    }


    public String getDescription() {
        return "Stoppt einen laufenden Server";
    }


    public String getUsage() {
        return "stopserver <serverName>";
    }
}

/**
 * Command: restartserver <serverName>
 * Startet einen Server neu
 */
class RestartServerCommand implements Command {

    @Override
    public boolean execute(String sender, String[] args) {
        if (args.length < 1) {
            ConsoleScreenManager.logToMainScreen(ConsoleColors.RED +
                    "Usage: restartserver <serverName>");
            return false;
        }

        String serverName = args[0];
        Master master = Master.getInstance();

        if (master == null) {
            ConsoleScreenManager.logToMainScreen(ConsoleColors.RED +
                    "Master ist nicht verfügbar!");
            return false;
        }

        master.restartServer(serverName);
        ConsoleScreenManager.logToMainScreen(ConsoleColors.GREEN +
                "Server '" + serverName + "' wird neugestartet...");

        return true;
    }


    public String getDescription() {
        return "Startet einen Server neu";
    }


    public String getUsage() {
        return "restartserver <serverName>";
    }
}

/**
 * Command: list [servers|wrappers]
 * Listet Server oder Wrapper auf
 */
class ListCommand implements Command {

    @Override
    public boolean execute(String sender, String[] args) {
        String type = args.length > 0 ? args[0].toLowerCase() : "servers";
        Master master = Master.getInstance();

        if (master == null) {
            ConsoleScreenManager.logToMainScreen(ConsoleColors.RED +
                    "Master ist nicht verfügbar!");
            return false;
        }

        switch (type) {
            case "servers" -> listServers(master);
            case "wrappers" -> listWrappers(master);
            default -> {
                ConsoleScreenManager.logToMainScreen(ConsoleColors.RED +
                        "Usage: list [servers|wrappers]");
                return false;
            }
        }

        return true;
    }

    private void listServers(Master master) {
        Map<String, ServerInstance> servers = master.getRunningServers();

        ConsoleScreenManager.logToMainScreen(ConsoleColors.CYAN +
                "═══════════════════════════════════════════════════════");
        ConsoleScreenManager.logToMainScreen(ConsoleColors.CYAN +
                "                    Running Servers                    ");
        ConsoleScreenManager.logToMainScreen(ConsoleColors.CYAN +
                "═══════════════════════════════════════════════════════");

        if (servers.isEmpty()) {
            ConsoleScreenManager.logToMainScreen(ConsoleColors.YELLOW +
                    "Keine Server online");
        } else {
            for (ServerInstance server : servers.values()) {
                String statusColor = "ONLINE".equals(server.status) ?
                        ConsoleColors.GREEN : ConsoleColors.YELLOW;

                ConsoleScreenManager.logToMainScreen(String.format(
                        ConsoleColors.WHITE + "%-15s " + statusColor + "%-10s " +
                                ConsoleColors.CYAN + "%-10s " + ConsoleColors.WHITE + "%d/%d Spieler  TPS: %.1f",
                        server.serverName,
                        server.status,
                        server.groupName,
                        server.playerCount,
                        server.maxPlayers,
                        server.tps
                ));
            }
        }

        ConsoleScreenManager.logToMainScreen(ConsoleColors.CYAN +
                "═══════════════════════════════════════════════════════");
        ConsoleScreenManager.logToMainScreen(ConsoleColors.WHITE +
                "Gesamt: " + servers.size() + " Server");
    }

    private void listWrappers(Master master) {
        Map<Integer, WrapperConnection> wrappers = master.getConnectedWrappers();

        ConsoleScreenManager.logToMainScreen(ConsoleColors.CYAN +
                "═══════════════════════════════════════════════════════");
        ConsoleScreenManager.logToMainScreen(ConsoleColors.CYAN +
                "                  Connected Wrappers                   ");
        ConsoleScreenManager.logToMainScreen(ConsoleColors.CYAN +
                "═══════════════════════════════════════════════════════");

        if (wrappers.isEmpty()) {
            ConsoleScreenManager.logToMainScreen(ConsoleColors.YELLOW +
                    "Keine Wrapper verbunden");
        } else {
            for (WrapperConnection wrapper : wrappers.values()) {
                int usedMemory = wrapper.getMaxMemory() - wrapper.getAvailableMemory();
                double memoryPercent = (usedMemory / (double) wrapper.getMaxMemory()) * 100;

                ConsoleScreenManager.logToMainScreen(String.format(
                        ConsoleColors.WHITE + "%-20s " + ConsoleColors.CYAN + "RAM: %dMB/%dMB (%.1f%%) " +
                                ConsoleColors.WHITE + "CPU: %.1f%% " + ConsoleColors.YELLOW + "Server: %d",
                        wrapper.getWrapperId(),
                        usedMemory,
                        wrapper.getMaxMemory(),
                        memoryPercent,
                        wrapper.getCpuUsage(),
                        wrapper.getActiveServers()
                ));
            }
        }

        ConsoleScreenManager.logToMainScreen(ConsoleColors.CYAN +
                "═══════════════════════════════════════════════════════");
        ConsoleScreenManager.logToMainScreen(ConsoleColors.WHITE +
                "Gesamt: " + wrappers.size() + " Wrapper");
    }


    public String getDescription() {
        return "Listet Server oder Wrapper auf";
    }


    public String getUsage() {
        return "list [servers|wrappers]";
    }
}

/**
 * Command: status
 * Zeigt System-Status
 */
class StatusCommand implements Command {

    @Override
    public boolean execute(String sender, String[] args) {
        Master master = Master.getInstance();

        if (master == null) {
            ConsoleScreenManager.logToMainScreen(ConsoleColors.RED +
                    "Master ist nicht verfügbar!");
            return false;
        }

        ConsoleScreenManager.logToMainScreen(ConsoleColors.CYAN +
                "═══════════════════════════════════════════════════════");
        ConsoleScreenManager.logToMainScreen(ConsoleColors.CYAN +
                "                    System Status                      ");
        ConsoleScreenManager.logToMainScreen(ConsoleColors.CYAN +
                "═══════════════════════════════════════════════════════");

        // Master Info
        ConsoleScreenManager.logToMainScreen(ConsoleColors.YELLOW + "Master:");
        ConsoleScreenManager.logToMainScreen(ConsoleColors.WHITE +
                "  ID: " + master.getMasterId());
        ConsoleScreenManager.logToMainScreen(ConsoleColors.WHITE +
                "  Primary: " + (master.isPrimaryMaster() ? "Yes" : "No"));
        ConsoleScreenManager.logToMainScreen(ConsoleColors.WHITE +
                "  Cluster State: " + master.getClusterManager().getCurrentState());

        // Statistics
        ConsoleScreenManager.logToMainScreen(ConsoleColors.YELLOW + "Statistics:");
        int totalPlayers = master.getRunningServers().values().stream()
                .mapToInt(s -> s.playerCount).sum();
        int totalCapacity = master.getRunningServers().values().stream()
                .mapToInt(s -> s.maxPlayers).sum();

        ConsoleScreenManager.logToMainScreen(ConsoleColors.WHITE +
                "  Servers: " + master.getRunningServers().size());
        ConsoleScreenManager.logToMainScreen(ConsoleColors.WHITE +
                "  Wrappers: " + master.getConnectedWrappers().size());
        ConsoleScreenManager.logToMainScreen(ConsoleColors.WHITE +
                "  Players: " + totalPlayers + "/" + totalCapacity);

        // Alerts
        int activeAlerts = master.getMonitoringService().getActiveAlerts().size();
        if (activeAlerts > 0) {
            ConsoleScreenManager.logToMainScreen(ConsoleColors.RED +
                    "  Active Alerts: " + activeAlerts);
        }

        ConsoleScreenManager.logToMainScreen(ConsoleColors.CYAN +
                "═══════════════════════════════════════════════════════");

        return true;
    }


    public String getDescription() {
        return "Zeigt System-Status";
    }


    public String getUsage() {
        return "status";
    }
}

/**
 * Command: help
 * Zeigt verfügbare Commands
 */
class HelpCommand implements Command {
    private final CommandHandler handler;

    public HelpCommand(CommandHandler handler) {
        this.handler = handler;
    }

    @Override
    public boolean execute(String sender, String[] args) {
        ConsoleScreenManager.logToMainScreen(ConsoleColors.CYAN +
                "═══════════════════════════════════════════════════════");
        ConsoleScreenManager.logToMainScreen(ConsoleColors.CYAN +
                "                  Available Commands                   ");
        ConsoleScreenManager.logToMainScreen(ConsoleColors.CYAN +
                "═══════════════════════════════════════════════════════");

        handler.getCommands().forEach((name, cmd) -> {
            ConsoleScreenManager.logToMainScreen(ConsoleColors.YELLOW +
                    name + ConsoleColors.WHITE + " - " + cmd.getDescription());
            ConsoleScreenManager.logToMainScreen(ConsoleColors.YELLOW +
                    "  Usage: " + cmd.getUsage());
        });

        ConsoleScreenManager.logToMainScreen(ConsoleColors.CYAN +
                "═══════════════════════════════════════════════════════");
        ConsoleScreenManager.logToMainScreen(ConsoleColors.WHITE +
                "Console Commands:");
        ConsoleScreenManager.logToMainScreen(ConsoleColors.YELLOW +
                "  /switch <serverName> - Wechsel zu Server-Screen");
        ConsoleScreenManager.logToMainScreen(ConsoleColors.YELLOW +
                "  /exit - Zurück zum Main-Screen");

        return true;
    }


    public String getDescription() {
        return "Zeigt diese Hilfe";
    }


    public String getUsage() {
        return "help";
    }
}
