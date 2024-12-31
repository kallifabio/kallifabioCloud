/**
 * Erstellt von Gamer_Kidd_LP | kallifabio
 * am 28.12.2024 um 21:23
 * Projektname: CloudSystemTest
 * Packagename: de.kallifabio.cloudsystem.managers
 */

package de.kallifabio.cloudsystem.managers;

import de.kallifabio.cloudsystem.libs.ConsoleColors;
import de.kallifabio.cloudsystem.libs.ConsoleScreen;

import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class ConsoleScreenManager {

    private static final Map<String, ConsoleScreen> screens = new HashMap<>();
    private static ConsoleScreen currentScreen;
    private static final ConsoleScreen mainScreen = new ConsoleScreen("main");

    private static boolean waitingForInput = false;
    private static String lastInput = ""; // Variable, um den letzten Input zu speichern

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

                    // Verarbeiten von /switch und /exit Kommandos
                    if (input.startsWith("/switch")) {
                        String[] parts = input.split(" ");
                        if (parts.length > 1) {
                            String screenName = parts[1];
                            if (screens.containsKey(screenName)) {
                                currentScreen = screens.get(screenName);
                                System.out.println("Wechsel zu Bildschirm: " + screenName);
                            } else {
                                System.out.println("Bildschirm '" + screenName + "' existiert nicht.");
                            }
                        } else {
                            System.out.println("Bitte geben Sie einen Bildschirmnamen an. Beispiel: /switch Proxy-1");
                        }
                    } else if (input.equals("/exit")) {
                        currentScreen = mainScreen;
                        System.out.println("Zurück zum Hauptbildschirm.");
                    } else {
                        // Eingabe an den aktuellen Bildschirm weitergeben
                        currentScreen.writeToServer(input);
                    }
                }
            }
        }).start();
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
}
