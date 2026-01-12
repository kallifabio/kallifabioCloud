/**
 * Erstellt von Gamer_Kidd_LP | kallifabio
 * am 28.12.2024 um 22:39
 * Projektname: CloudSystemTest
 * Packagename: de.kallifabio.cloudsystem.libs
 */

package de.kallifabio.cloud.libs.console;

import java.util.LinkedList;
import java.util.Queue;

public class ConsoleScreen {

    private final String name;
    private final Queue<String> messages;
    private static final int MESSAGE_LIMIT = 50;
    private boolean customPrefix = false; // Flag für benutzerdefinierten Prefix

    public ConsoleScreen(String name) {
        this.name = name;
        this.messages = new LinkedList<>();
    }

    // Setzt das Prefix für den Bildschirm
    public void setPrefix(String prefix) {
        this.customPrefix = true;
    }

    public void addMessage(String message) {
        synchronized (messages) {
            if (messages.size() >= MESSAGE_LIMIT) {
                messages.poll();
            }
            messages.offer(message);
        }
        if (ConsoleScreenManager.getCurrentScreen() == this) {
            System.out.println(message);
        }
    }

    public void writeToServer(String input) {
        // Wenn wir auf dem Hauptbildschirm sind, einfach die Eingabe anzeigen
        if (this.name.equals("main")) {
            addMessage(ConsoleColors.PREFIX + input);
        } else {
            // Wenn auf einem Server-Bildschirm, leite die Eingabe an den Server weiter
            addMessage(ConsoleColors.PREFIX + "[SERVER-INPUT] " + input);
            // Hier kannst du die Logik zum Senden der Eingabe an den Server einbauen
        }
    }

    @Override
    public String toString() {
        return name;
    }
}
