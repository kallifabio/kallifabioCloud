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
    private boolean customPrefix = false;

    public ConsoleScreen(String name) {
        this.name = name;
        this.messages = new LinkedList<>();
    }

    public void setPrefix(String prefix) {
        this.customPrefix = true;
    }

    // Normale addMessage Methode (mit Lock)
    public void addMessage(String message) {
        synchronized (ConsoleScreenManager.getOutputLock()) {
            addMessageDirect(message);
        }
    }

    // Direct version ohne extra Lock (fuer wenn bereits locked)
    public void addMessageDirect(String message) {
        synchronized (messages) {
            if (messages.size() >= MESSAGE_LIMIT) {
                messages.poll();
            }
            messages.offer(message);
        }

        // NUR ausgeben wenn dieser Screen aktuell angezeigt wird
        if (ConsoleScreenManager.getCurrentScreen() == this) {
            ConsoleScreenManager.printToTerminal(message);
        }
    }

    public void writeToServer(String input) {
        synchronized (ConsoleScreenManager.getOutputLock()) {
            if (this.name.equals("main")) {
                addMessageDirect(ConsoleColors.PREFIX + input);
            } else {
                addMessageDirect(ConsoleColors.PREFIX + "[SERVER-INPUT] " + input);
            }
        }
    }

    @Override
    public String toString() {
        return name;
    }

    public String getName() {
        return name;
    }
}
