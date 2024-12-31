/**
 * Erstellt von Gamer_Kidd_LP | kallifabio
 * am 04.10.2024 um 03:22
 * Projektname: CloudSystemTest
 * Packagename: de.kallifabio.cloudsystem.managers
 */

package de.kallifabio.cloudsystem.managers;

import java.util.HashMap;
import java.util.Map;

public class SignsManager {

    private Map<String, String> signStatus = new HashMap<>();

    public void updateSignStatus(String serverName, String status) {
        signStatus.put(serverName, status);
        // Aktualisiere das Schild ingame entsprechend des Status (ONLINE, OFFLINE etc.)
        ConsoleScreenManager.logToMainScreen("Schildstatus für " + serverName + " auf " + status + " gesetzt");
    }

    public String getSignStatus(String serverName) {
        return signStatus.getOrDefault(serverName, "OFFLINE");
    }
}
