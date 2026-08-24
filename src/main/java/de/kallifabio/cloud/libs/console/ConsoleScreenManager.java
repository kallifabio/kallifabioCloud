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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ConsoleScreenManager {

    private static final Map<String, ConsoleScreen> screens = new ConcurrentHashMap<>();
    private static final ConsoleScreen mainScreen = new ConsoleScreen("main");
    private static final Object OUTPUT_LOCK = new Object();

    private static ConsoleScreen currentScreen;
    private static CommandHandler commandHandler;
    private static volatile boolean running = true;

    private static Terminal terminal;
    private static LineReader lineReader;
    private static Thread inputThread;

    public static void setCommandHandler(CommandHandler handler) {
        commandHandler = handler;
    }

    public static CommandHandler getCommandHandler() {
        return commandHandler;
    }

    public static void startConsole() {
        if (inputThread != null && inputThread.isAlive()) {
            return;
        }
        running = true;
        screens.put("main", mainScreen);
        currentScreen = mainScreen;

        try {
            terminal = TerminalBuilder.builder().system(true).build();
            lineReader = LineReaderBuilder.builder().terminal(terminal).build();

            inputThread = new Thread(() -> {
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
                                    "Verfügbare Screens: " + String.join(", ", screens.keySet()));
                        } else if (input.equals("/exit")) {
                            handleExitCommand();
                        } else if (input.equalsIgnoreCase("exit") || input.equalsIgnoreCase("stop")) {
                            running = false;
                            printToTerminal("Fahre System herunter...");
                            Launcher.requestShutdown();
                            break;
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
                        if (!running) {
                            break;
                        }
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

            inputThread.setDaemon(true);
            inputThread.start();

        } catch (Exception e) {
            System.err.println("Fehler beim Initialisieren der Console: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void stopConsole() {
        running = false;
        Thread thread = inputThread;
        if (thread != null && thread != Thread.currentThread()) {
            thread.interrupt();
        }
        if (terminal != null) {
            try {
                terminal.writer().println();
                terminal.flush();
                terminal.close();
            } catch (Exception ignored) {
            }
        }
        if (thread != null && thread != Thread.currentThread()) {
            try {
                thread.join(1000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        lineReader = null;
        terminal = null;
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
            printToTerminal(ConsoleColors.YELLOW + "Verfügbare Screens: " + String.join(", ", screens.keySet()));
        }
    }

    private static void handleExitCommand() {
        if (currentScreen == mainScreen) {
            printToTerminal(ConsoleColors.YELLOW + "Du bist bereits auf dem Hauptbildschirm.");
            return;
        }
        currentScreen = mainScreen;
        printToTerminal(ConsoleColors.GREEN + "Zurück zum Hauptbildschirm.");
    }

    public static void createServerScreen(String serverName) {
        if (!screens.containsKey(serverName)) {
            ConsoleScreen serverScreen = new ConsoleScreen(serverName);
            screens.put(serverName, serverScreen);
            printToTerminal("Bildschirm für Server '" + serverName + "' erstellt.");
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

    public static boolean switchToScreen(String screenName) {
        if (screenName == null) {
            return false;
        }
        ConsoleScreen target = screens.get(screenName);
        if (target == null) {
            return false;
        }
        currentScreen = target;
        return true;
    }

    public static boolean hasScreen(String screenName) {
        return screenName != null && screens.containsKey(screenName);
    }

    public static Set<String> getScreenNames() {
        return Set.copyOf(screens.keySet());
    }

    public static List<String> getScreenMessages(String screenName, int limit) {
        ConsoleScreen screen = screens.get(screenName);
        if (screen == null) {
            return List.of();
        }
        List<String> snapshot = screen.getMessagesSnapshot();
        if (limit <= 0 || snapshot.size() <= limit) {
            return snapshot;
        }
        return snapshot.subList(snapshot.size() - limit, snapshot.size());
    }

    public static boolean sendCommandToServer(String serverName, String command) {
        if (serverName == null || serverName.isBlank() || command == null || command.isBlank()) {
            return false;
        }
        if (Launcher.getWrapper() == null) {
            return false;
        }
        var process = Launcher.getWrapper().getManagedServers().get(serverName);
        if (process == null || !process.isRunning()) {
            return false;
        }
        process.sendCommand(command);
        return true;
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
                try {
                    lineReader.printAbove(message);
                } catch (Exception ignored) {
                    System.out.println(message);
                    System.out.flush();
                }
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
                    "Server '" + serverName + "' ist nicht lokal verfügbar (nur Log-Echo).");
        }
    }
}
