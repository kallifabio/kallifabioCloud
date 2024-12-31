/**
 * Erstellt von Gamer_Kidd_LP | kallifabio
 * am 03.10.2024 um 18:42
 * Projektname: CloudSystemTest
 * Packagename: de.kallifabio.cloudsystem
 */

package de.kallifabio.cloudsystem;

import de.kallifabio.cloudsystem.libs.ConsoleColors;
import de.kallifabio.cloudsystem.managers.ConsoleScreenManager;
import de.kallifabio.cloudsystem.master.Master;
import de.kallifabio.cloudsystem.wrapper.Wrapper;

public class Launcher {

    public static void main(String[] args) {
        ConsoleScreenManager.startConsole();
        ConsoleScreenManager.logToMainScreen(" ");
        ConsoleScreenManager.logToMainScreen(ConsoleColors.YELLOW + " __  __     ______     __         __         __     ______     __         ______     __  __     _____   \n" +
                "/\\ \\/ /    /\\  __ \\   /\\ \\       /\\ \\       /\\ \\   /\\  ___\\   /\\ \\       /\\  __ \\   /\\ \\/\\ \\   /\\  __-. \n" +
                "\\ \\  _\"-.  \\ \\  __ \\  \\ \\ \\____  \\ \\ \\____  \\ \\ \\  \\ \\ \\____  \\ \\ \\____  \\ \\ \\/\\ \\  \\ \\ \\_\\ \\  \\ \\ \\/\\ \\\n" +
                " \\ \\_\\ \\_\\  \\ \\_\\ \\_\\  \\ \\_____\\  \\ \\_____\\  \\ \\_\\  \\ \\_____\\  \\ \\_____\\  \\ \\_____\\  \\ \\_____\\  \\ \\____-\n" +
                "  \\/_/\\/_/   \\/_/\\/_/   \\/_____/   \\/_____/   \\/_/   \\/_____/   \\/_____/   \\/_____/   \\/_____/   \\/____/\n" +
                "                                                                                                        ");
        ConsoleScreenManager.logToMainScreen(" ");
        ConsoleScreenManager.logToMainScreen(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() + " Initialisiert Master...");
        new Master().start();
        ConsoleScreenManager.logToMainScreen(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() + " Initialisiert Wrapper...");
        new Wrapper().start();
        new CloudHttpServer();
    }
}
