package de.kallifabio.cloud.libs.console;

import de.kallifabio.cloud.Launcher;
import de.kallifabio.cloud.commands.CommandHandler;
import de.kallifabio.cloud.libs.logging.CentralLogger;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.util.HashMap;
import java.util.Map;

public class ConsoleScreenManager {

    private static final Map<String, ConsoleScreen> screens = new HashMap<>();
    private static final ConsoleScreen mainScreen = new ConsoleScreen("main");
    private static final Object OUTPUT_LOCK = new Object();

    private static ConsoleScreen currentScreen;
    private static CommandHandler commandHandler;
    private static volatile boolean running = true;

    private static Terminal terminal;
    private static LineReader lineReader;

    public static void setCommandHandler(CommandHandler handler) {
        commandHandler = handler;
    }

    public static CommandHandler getCommandHandler() {
        return commandHandler;
    }

    public static void startConsole() {
        running = true;
        screens.put("main", mainScreen);
        currentScreen = mainScreen;

        try {
            terminal = TerminalBuilder.builder().system(true).build();
            lineReader = LineReaderBuilder.builder().terminal(terminal).build();

            Thread inputThread = new Thread(() -> {
                while (running) {
                    try {
                        String input = lineReader.readLine(ConsoleColors.PREFIX);
                        if (input == null || input.trim().isEmpty()) {
                            continue;
                        }

                        if (input.startsWith("/switch")) {
                            handleSwitchCommand(input);
                        } else if (input.equals("/screens")) {
                            printToTerminal(ConsoleColors.YELLOW +
                                    "Verfuegbare Screens: " + String.join(", ", screens.keySet()));
                        } else if (input.equals("/exit")) {
                            handleExitCommand();
                        } else if (input.equalsIgnoreCase("exit") || input.equalsIgnoreCase("stop")) {
                            printToTerminal("Fahre System herunter...");
                            System.exit(0);
                        } else if (currentScreen != mainScreen) {
                            sendInputToCurrentServer(input);
                        } else {
                            if (commandHandler != null) {
                                commandHandler.executeCommand(input, "Console");
                            } else {
                                printToTerminal(ConsoleColors.RED + "Command-Handler nicht initialisiert!");
                            }
                        }
                    } catch (UserInterruptException e) {
                        printToTerminal("Verwende 'exit' zum Beenden.");
                    } catch (EndOfFileException e) {
                        break;
                    } catch (Exception e) {
                        if (running) {
                            printToTerminal(ConsoleColors.RED + "Console-Fehler: " + e.getMessage());
                        }
                        // Kein Break: Console bleibt aktiv.
                    }
                }
            }, "Console-Input");

            inputThread.setDaemon(false);
            inputThread.start();

        } catch (Exception e) {
            System.err.println("Fehler beim Initialisieren der Console: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void stopConsole() {
        running = false;
        if (terminal != null) {
            try {
                terminal.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static void handleSwitchCommand(String input) {
        String[] parts = input.split(" ");
        if (parts.length <= 1) {
            printToTerminal(ConsoleColors.RED + "Bitte gib einen Bildschirmnamen an. Beispiel: /switch Proxy-1");
            return;
        }

        String screenName = parts[1];
        if (screens.containsKey(screenName)) {
            currentScreen = screens.get(screenName);
            printToTerminal(ConsoleColors.GREEN + "Wechsel zu Bildschirm: " + screenName);
        } else {
            printToTerminal(ConsoleColors.RED + "Bildschirm '" + screenName + "' existiert nicht.");
            printToTerminal(ConsoleColors.YELLOW + "Verfuegbare Screens: " + String.join(", ", screens.keySet()));
        }
    }

    private static void handleExitCommand() {
        if (currentScreen == mainScreen) {
            printToTerminal(ConsoleColors.YELLOW + "Du bist bereits auf dem Hauptbildschirm.");
            return;
        }
        currentScreen = mainScreen;
        printToTerminal(ConsoleColors.GREEN + "Zurueck zum Hauptbildschirm.");
    }

    public static void createServerScreen(String serverName) {
        if (!screens.containsKey(serverName)) {
            ConsoleScreen serverScreen = new ConsoleScreen(serverName);
            screens.put(serverName, serverScreen);
            printToTerminal("Bildschirm fuer Server '" + serverName + "' erstellt.");
        }
    }

    public static void logToServerScreen(String serverName, String message) {
        ConsoleScreen serverScreen = screens.get(serverName);
        if (serverScreen != null) {
            synchronized (OUTPUT_LOCK) {
                serverScreen.addMessageDirect(message);
            }
        } else {
            printToTerminal("Server-Bildschirm '" + serverName + "' nicht gefunden. Nachricht: " + message);
        }
    }

    public static void switchToMainScreen() {
        currentScreen = mainScreen;
    }

    public static ConsoleScreen getCurrentScreen() {
        return currentScreen;
    }

    public static String padRight(String s, int n) {
        return String.format("%-" + n + "s", s);
    }

    public static Object getOutputLock() {
        return OUTPUT_LOCK;
    }

    public static void printToTerminal(String message) {
        CentralLogger.info("Console", message);
        if (terminal != null && lineReader != null) {
            synchronized (OUTPUT_LOCK) {
                lineReader.printAbove(message);
            }
        } else {
            System.out.println(message);
            System.out.flush();
        }
    }

    private static void sendInputToCurrentServer(String input) {
        if (currentScreen == null || "main".equalsIgnoreCase(currentScreen.getName())) {
            return;
        }

        String serverName = currentScreen.getName();
        boolean delivered = false;

        if (Launcher.getWrapper() != null) {
            var process = Launcher.getWrapper().getManagedServers().get(serverName);
            if (process != null && process.isRunning()) {
                process.sendCommand(input);
                delivered = true;
            }
        }

        if (!delivered) {
            currentScreen.writeToServer(input);
            printToTerminal(ConsoleColors.YELLOW +
                    "Server '" + serverName + "' ist nicht lokal verfuegbar (nur Log-Echo).");
        }
    }
}
