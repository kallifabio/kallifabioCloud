/**
 * Erstellt von Gamer_Kidd_LP | kallifabio
 * am 03.10.2024 um 23:01
 * Projektname: CloudSystemTest
 * Packagename: de.kallifabio.cloudsystem.libs
 */

package de.kallifabio.cloud.libs.console;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ConsoleColors {

    // Reset
    public static final String RESET = "\033[0m"; // Text Reset

    // Regular Colors
    public static final String BLACK = "\033[0;30m"; // BLACK
    public static final String RED = "\033[0;31m"; // RED
    public static final String GREEN = "\033[0;32m"; // GREEN
    public static final String YELLOW = "\033[0;33m"; // YELLOW
    public static final String BLUE = "\033[0;34m"; // BLUE
    public static final String PURPLE = "\033[0;35m"; // PURPLE
    public static final String CYAN = "\033[0;36m"; // CYAN
    public static final String WHITE = "\033[0;37m"; // WHITE
    public static final String ORANGE = "\033[38;2;255;165;"; // ORANGE

    // Bold
    public static final String BLACK_BOLD = "\033[1;30m"; // BLACK
    public static final String RED_BOLD = "\033[1;31m"; // RED
    public static final String GREEN_BOLD = "\033[1;32m"; // GREEN
    public static final String YELLOW_BOLD = "\033[1;33m"; // YELLOW
    public static final String BLUE_BOLD = "\033[1;34m"; // BLUE
    public static final String PURPLE_BOLD = "\033[1;35m"; // PURPLE
    public static final String CYAN_BOLD = "\033[1;36m"; // CYAN
    public static final String WHITE_BOLD = "\033[1;37m"; // WHITE

    // Background
    public static final String BLACK_BACKGROUND = "\033[40m"; // BLACK
    public static final String RED_BACKGROUND = "\033[41m"; // RED
    public static final String GREEN_BACKGROUND = "\033[42m"; // GREEN
    public static final String YELLOW_BACKGROUND = "\033[43m"; // YELLOW
    public static final String BLUE_BACKGROUND = "\033[44m"; // BLUE
    public static final String PURPLE_BACKGROUND = "\033[45m"; // PURPLE
    public static final String CYAN_BACKGROUND = "\033[46m"; // CYAN
    public static final String WHITE_BACKGROUND = "\033[47m"; // WHITE

    public static final String PREFIX = BLUE + "kallifabio" + CYAN + "Cloud" + YELLOW + " : " + RESET;

    public static String getCurrentTime() {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
        return "[" + LocalDateTime.now().format(dtf) + "]";
    }
}
