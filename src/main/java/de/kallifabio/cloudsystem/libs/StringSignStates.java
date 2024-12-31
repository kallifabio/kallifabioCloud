/**
 * Erstellt von Gamer_Kidd_LP | kallifabio
 * am 30.12.2024 um 19:57
 * Projektname: CloudSystemTest
 * Packagename: de.kallifabio.cloudsystem.libs
 */

package de.kallifabio.cloudsystem.libs;

public enum StringSignStates {

    STARTING_STRING(ConsoleColors.GREEN + "Startet"),
    STOPPING_STRING(ConsoleColors.BLACK + "Stoppt"),
    ONLINE_STRING(ConsoleColors.GREEN + "Online"),
    OFFLINE_STRING(ConsoleColors.BLACK + "Offline"),
    FULL_STRING(ConsoleColors.ORANGE + "Voll"),
    MAINTENANCE_STRING(ConsoleColors.RED + "Wartung");

    private final String signStates;

    StringSignStates(String signStates) {
        this.signStates = signStates;
    }

    public String getSignStates() {
        return signStates;
    }
}
