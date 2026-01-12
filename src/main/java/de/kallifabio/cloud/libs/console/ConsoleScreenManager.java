/**
 * Erstellt von Gamer_Kidd_LP | kallifabio
 * am 28.12.2024 um 21:23
 * Projektname: CloudSystemTest
 * Packagename: de.kallifabio.cloudsystem.managers
 */

package de.kallifabio.cloud.libs.console;

import de.kallifabio.cloud.commands.CommandHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class ConsoleScreenManager {

    private static final Map<String, ConsoleScreen> screens = new HashMap<>();
    private static ConsoleScreen currentScreen;
    private static final ConsoleScreen mainScreen = new ConsoleScreen("main");
    private static CommandHandler commandHandler;

    private static boolean waitingForInput = false;
    private static String lastInput = ""; // Variable, um den letzten Input zu speichern

    public static void setCommandHandler(CommandHandler handler) {
        commandHandler = handler;
    }

    public static CommandHandler getCommandHandler() {
        return commandHandler;
    }

    public static void startConsole() {
        // Main Screen immer sofort starten
        screens.put("main", mainScreen);
        currentScreen = mainScreen;

        // Start der Eingabeschleife in einem neuen Thread
        new Thread(() -> {
            Scanner scanner = new Scanner(System.in);

            // Direktes Anzeigen des Prefixes mit blinkendem Cursor
            while (true) {
                // Zeige sofort das Prefix und den Cursor an
                System.out.print(ConsoleColors.PREFIX);  // Neues Prefix für Eingabeaufforderung

                // Warte auf Eingabe und verarbeite diese sofort
                if (scanner.hasNextLine()) {
                    String input = scanner.nextLine();

                    // Prüfen, ob der Input leer ist oder der gleiche wie der letzte Input
                    if (input.trim().isEmpty()) {
                        continue;  // Falls der Input leer ist, überspringen
                    }
                    if (input.equals(lastInput)) {
                        System.out.println("Der Input ist der gleiche wie der vorherige. Bitte geben Sie etwas anderes ein.");
                        continue;  // Falls der Input der gleiche wie der letzte ist, überspringen
                    }

                    // Speichern des aktuellen Inputs als letzten Input
                    lastInput = input;

                    // GEÄNDERT: Console-Commands zuerst prüfen
                    if (input.startsWith("/switch")) {
                        handleSwitchCommand(input);
                    } else if (input.equals("/exit")) {
                        handleExitCommand();
                    } else if (currentScreen != mainScreen) {
                        // Auf Server-Screen: Befehl an Server senden
                        currentScreen.writeToServer(input);
                    } else {
                        // NEU: Auf Main-Screen: Cloud-Command ausführen
                        if (commandHandler != null) {
                            commandHandler.executeCommand(input, "Console");
                        } else {
                            logToMainScreen(ConsoleColors.RED +
                                    "Command-Handler nicht initialisiert!");
                        }
                    }
                }
            }
        }, "Console-Input").start();
    }

    // NEU: Switch-Command Handler
    private static void handleSwitchCommand(String input) {
        String[] parts = input.split(" ");
        if (parts.length > 1) {
            String screenName = parts[1];
            if (screens.containsKey(screenName)) {
                currentScreen = screens.get(screenName);
                logToMainScreen(ConsoleColors.GREEN +
                        "Wechsel zu Bildschirm: " + screenName);
            } else {
                logToMainScreen(ConsoleColors.RED +
                        "Bildschirm '" + screenName + "' existiert nicht.");
                logToMainScreen(ConsoleColors.YELLOW +
                        "Verfügbare Screens: " + String.join(", ", screens.keySet()));
            }
        } else {
            logToMainScreen(ConsoleColors.RED +
                    "Bitte gib einen Bildschirmnamen an. Beispiel: /switch Proxy-1");
        }
    }

    // NEU: Exit-Command Handler
    private static void handleExitCommand() {
        if (currentScreen == mainScreen) {
            logToMainScreen(ConsoleColors.YELLOW +
                    "Du bist bereits auf dem Hauptbildschirm.");
        } else {
            currentScreen = mainScreen;
            logToMainScreen(ConsoleColors.GREEN +
                    "Zurück zum Hauptbildschirm.");
        }
    }

    // Log-Nachricht im Hauptbildschirm
    public static void logToMainScreen(String message) {
        mainScreen.addMessage(message);

        // Wenn die Nachricht die letzte Serverstartnachricht war, das Prefix ändern
        if (message.contains("Server Lobby-1 gestartet") || message.contains("Server Proxy-1 gestartet")) {
            waitingForInput = true;  // Aktiviert das Warten auf Eingabe
        }

        // Immer sofort auf Eingabeaufforderung warten
        waitingForInput = true;
    }

    // Einen neuen Bildschirm für einen Server erstellen
    public static void createServerScreen(String serverName) {
        if (!screens.containsKey(serverName)) {
            ConsoleScreen serverScreen = new ConsoleScreen(serverName);
            screens.put(serverName, serverScreen);
            logToMainScreen("Bildschirm für Server '" + serverName + "' erstellt.");
        }
    }

    // Log-Nachricht an den jeweiligen Server-Bildschirm senden
    public static void logToServerScreen(String serverName, String message) {
        ConsoleScreen serverScreen = screens.get(serverName);
        if (serverScreen != null) {
            serverScreen.addMessage(message);
        } else {
            logToMainScreen("Server-Bildschirm '" + serverName + "' nicht gefunden. Nachricht: " + message);
        }
    }

    // Zurück zum Hauptbildschirm wechseln
    public static void switchToMainScreen() {
        currentScreen = mainScreen;
    }

    // Zugriff auf den aktuellen Bildschirm
    public static ConsoleScreen getCurrentScreen() {
        return currentScreen;
    }

    public static String padRight(String s, int n) {
        return String.format("%-" + n + "s", s);
    }

}
