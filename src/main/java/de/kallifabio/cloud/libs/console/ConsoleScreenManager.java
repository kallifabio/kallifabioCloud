/**
 * Erstellt von Gamer_Kidd_LP | kallifabio
 * am 28.12.2024 um 21:23
 * Projektname: CloudSystemTest
 * Packagename: de.kallifabio.cloudsystem.managers
 */

package de.kallifabio.cloud.libs.console;

import de.kallifabio.cloud.commands.CommandHandler;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Scanner;

public class ConsoleScreenManager {

    private static final Map<String, ConsoleScreen> screens = new HashMap<>();
    private static ConsoleScreen currentScreen;
    private static final ConsoleScreen mainScreen = new ConsoleScreen("main");
    private static CommandHandler commandHandler;

    private static String lastInput = "";
    private static volatile boolean running = true;
    private static final Object OUTPUT_LOCK = new Object();

    // JLine Terminal und Reader
    private static Terminal terminal;
    private static LineReader lineReader;

    public static void setCommandHandler(CommandHandler handler) {
        commandHandler = handler;
    }

    public static CommandHandler getCommandHandler() {
        return commandHandler;
    }

    public static void startConsole() {
        screens.put("main", mainScreen);
        currentScreen = mainScreen;

        try {
            // Initialize JLine Terminal
            terminal = TerminalBuilder.builder()
                    .system(true)
                    .build();

            // Initialize LineReader
            lineReader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .build();

            Thread inputThread = new Thread(() -> {
                while (running) {
                    try {
                        // JLine zeigt automatisch das Prompt und handhabt Input sauber
                        String input = lineReader.readLine(ConsoleColors.PREFIX);

                        if (input == null || input.trim().isEmpty()) {
                            continue;
                        }

                        if (input.equals(lastInput)) {
                            printToTerminal("Der Input ist der gleiche wie der vorherige.");
                            continue;
                        }

                        lastInput = input;

                        // Command handling
                        if (input.startsWith("/switch")) {
                            handleSwitchCommand(input);
                        } else if (input.equals("/exit")) {
                            handleExitCommand();
                        } else if (input.equalsIgnoreCase("exit") || input.equalsIgnoreCase("stop")) {
                            printToTerminal("Fahre System herunter...");
                            System.exit(0);
                        } else if (currentScreen != mainScreen) {
                            currentScreen.writeToServer(input);
                        } else {
                            if (commandHandler != null) {
                                commandHandler.executeCommand(input, "Console");
                            } else {
                                printToTerminal(ConsoleColors.RED +
                                        "Command-Handler nicht initialisiert!");
                            }
                        }
                    } catch (UserInterruptException e) {
                        // Ctrl+C
                        printToTerminal("Verwende 'exit' zum Beenden.");
                    } catch (EndOfFileException e) {
                        // Ctrl+D
                        break;
                    } catch (Exception e) {
                        if (running) {
                            printToTerminal("Fehler: " + e.getMessage());
                        }
                        break;
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
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    private static void handleSwitchCommand(String input) {
        String[] parts = input.split(" ");
        if (parts.length > 1) {
            String screenName = parts[1];
            if (screens.containsKey(screenName)) {
                currentScreen = screens.get(screenName);
                printToTerminal(ConsoleColors.GREEN +
                        "Wechsel zu Bildschirm: " + screenName);
            } else {
                printToTerminal(ConsoleColors.RED +
                        "Bildschirm '" + screenName + "' existiert nicht.");
                printToTerminal(ConsoleColors.YELLOW +
                        "Verfügbare Screens: " + String.join(", ", screens.keySet()));
            }
        } else {
            printToTerminal(ConsoleColors.RED +
                    "Bitte gib einen Bildschirmnamen an. Beispiel: /switch Proxy-1");
        }
    }

    private static void handleExitCommand() {
        if (currentScreen == mainScreen) {
            printToTerminal(ConsoleColors.YELLOW +
                    "Du bist bereits auf dem Hauptbildschirm.");
        } else {
            currentScreen = mainScreen;
            printToTerminal(ConsoleColors.GREEN +
                    "Zurück zum Hauptbildschirm.");
        }
    }

    /*public static void printToTerminal(String message) {
        synchronized (OUTPUT_LOCK) {
            mainScreen.addMessageDirect(message);
        }
    }*/

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
        if (terminal != null && lineReader != null) {
            // Verwende LineReader's printAbove für saubere Ausgabe über der Eingabezeile
            lineReader.printAbove(message);
        } else {
            // Fallback wenn Terminal nicht verfügbar
            System.out.println(message);
            System.out.flush();
        }
    }
}
