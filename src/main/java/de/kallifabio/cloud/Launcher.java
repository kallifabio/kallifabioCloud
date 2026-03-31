/**
 * Erstellt von Gamer_Kidd_LP | kallifabio
 * am 03.10.2024 um 18:42
 * Projektname: CloudSystemTest
 * Packagename: de.kallifabio.cloudsystem
 */

package de.kallifabio.cloud;

import de.kallifabio.cloud.api.CloudHttpServer;
import de.kallifabio.cloud.commands.CommandHandler;
import de.kallifabio.cloud.libs.console.ConsoleColors;
import de.kallifabio.cloud.libs.console.ConsoleScreenManager;
import de.kallifabio.cloud.libs.logging.CentralLogger;
import de.kallifabio.cloud.master.Master;
import de.kallifabio.cloud.wrapper.Wrapper;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class Launcher {

    private static Master master;
    private static Wrapper wrapper;
    private static CloudHttpServer apiServer;
    private static CommandHandler commandHandler;
    private static boolean shutdownRequested = false;

    // Launch modes
    private static LaunchMode launchMode = LaunchMode.COMBINED;

    public static void main(String[] args) {
        CentralLogger.init();

        // Parse launch arguments
        parseLaunchArguments(args);

        // Initialize console
        ConsoleScreenManager.startConsole();

        // Initialize CommandHandler
        commandHandler = new CommandHandler();
        ConsoleScreenManager.setCommandHandler(commandHandler);

        printBanner();

        // Register shutdown hook
        registerShutdownHook();

        try {
            // Start components based on launch mode
            switch (launchMode) {
                case MASTER_ONLY -> startMasterOnly();
                case WRAPPER_ONLY -> startWrapperOnly();
                case COMBINED -> startCombined();
            }

            // Keep application running
            keepAlive();

        } catch (Exception e) {
            ConsoleScreenManager.printToTerminal(ConsoleColors.RED + ConsoleColors.PREFIX +
                    ConsoleColors.getCurrentTime() + " KRITISCHER FEHLER beim Start: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void parseLaunchArguments(String[] args) {
        if (args.length > 0) {
            String mode = args[0].toLowerCase();
            switch (mode) {
                case "--master" -> {
                    launchMode = LaunchMode.MASTER_ONLY;
                    ConsoleScreenManager.printToTerminal(ConsoleColors.BLUE +
                            "Launch-Modus: MASTER_ONLY");
                }
                case "--wrapper" -> {
                    launchMode = LaunchMode.WRAPPER_ONLY;
                    ConsoleScreenManager.printToTerminal(ConsoleColors.BLUE +
                            "Launch-Modus: WRAPPER_ONLY");
                }
                case "--combined" -> {
                    launchMode = LaunchMode.COMBINED;
                    ConsoleScreenManager.printToTerminal(ConsoleColors.BLUE +
                            "Launch-Modus: COMBINED");
                }
                default -> ConsoleScreenManager.printToTerminal(ConsoleColors.YELLOW +
                        "Unbekannter Modus '" + mode + "', verwende COMBINED");
            }
        }
    }

    private static void printBanner() {
        ConsoleScreenManager.printToTerminal(" ");
        ConsoleScreenManager.printToTerminal(ConsoleColors.YELLOW +
                " __  __     ______     __         __         __     ______     __         ______     __  __     _____   \n" +
                "/\\ \\/ /    /\\  __ \\   /\\ \\       /\\ \\       /\\ \\   /\\  ___\\   /\\ \\       /\\  __ \\   /\\ \\/\\ \\   /\\  __-. \n" +
                "\\ \\  _\"-.  \\ \\  __ \\  \\ \\ \\____  \\ \\ \\____  \\ \\ \\  \\ \\ \\____  \\ \\ \\____  \\ \\ \\/\\ \\  \\ \\ \\_\\ \\  \\ \\ \\/\\ \\\n" +
                " \\ \\_\\ \\_\\  \\ \\_\\ \\_\\  \\ \\_____\\  \\ \\_____\\  \\ \\_\\  \\ \\_____\\  \\ \\_____\\  \\ \\_____\\  \\ \\_____\\  \\ \\____-\n" +
                "  \\/_/\\/_/   \\/_/\\/_/   \\/_____/   \\/_____/   \\/_/   \\/_____/   \\/_____/   \\/_____/   \\/_____/   \\/____/\n");
        ConsoleScreenManager.printToTerminal(ConsoleColors.RESET + " ");
        ConsoleScreenManager.printToTerminal(ConsoleColors.CYAN + "+----------------------------------------------------------------+");
        ConsoleScreenManager.printToTerminal(ConsoleColors.CYAN + "|" + ConsoleColors.YELLOW +
                "          KalliCloud Enterprise - Version 1.0.2                " + ConsoleColors.CYAN + "|");
        ConsoleScreenManager.printToTerminal(ConsoleColors.CYAN + "|" + ConsoleColors.WHITE +
                "     Multi-Master Cluster | Smart Auto-Scaling | REST API      " + ConsoleColors.CYAN + "|");
        ConsoleScreenManager.printToTerminal(ConsoleColors.CYAN + "+----------------------------------------------------------------+");
        ConsoleScreenManager.printToTerminal(ConsoleColors.RESET + " ");
    }

    private static void startMasterOnly() {
        ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Starte im MASTER-ONLY Modus...");

        // Start Master
        ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " [1/2] Initialisiere Master...");
        master = new Master();
        master.start();

        // Start API Server
        ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " [2/2] Initialisiere REST API...");
        apiServer = new CloudHttpServer();

        ConsoleScreenManager.printToTerminal(ConsoleColors.GREEN + ConsoleColors.PREFIX +
                ConsoleColors.getCurrentTime() + " [OK] Master erfolgreich gestartet!");
        printStartupInfo();
    }

    private static void startWrapperOnly() {
        ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Starte im WRAPPER-ONLY Modus...");

        // Start Wrapper
        ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " [1/1] Initialisiere Wrapper...");
        wrapper = new Wrapper();
        wrapper.start();

        ConsoleScreenManager.printToTerminal(ConsoleColors.GREEN + ConsoleColors.PREFIX +
                ConsoleColors.getCurrentTime() + " [OK] Wrapper erfolgreich gestartet!");
    }

    private static void startCombined() {
        ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " Starte im COMBINED Modus...");

        CountDownLatch masterLatch = new CountDownLatch(1);

        // Start Master asynchronously
        new Thread(() -> {
            ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                    " [1/3] Initialisiere Master...");
            master = new Master();
            master.start();

            // Wait a bit for master to fully initialize
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            masterLatch.countDown();
        }, "Master-Startup").start();

        // Wait for master to start
        try {
            if (!masterLatch.await(30, TimeUnit.SECONDS)) {
                throw new RuntimeException("Master-Start Timeout");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Master-Start unterbrochen");
        }

        // Start Wrapper
        ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " [2/3] Initialisiere Wrapper...");
        wrapper = new Wrapper();
        wrapper.start();

        // Start API Server
        ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                " [3/3] Initialisiere REST API...");
        apiServer = new CloudHttpServer();

        ConsoleScreenManager.printToTerminal(ConsoleColors.GREEN + ConsoleColors.PREFIX +
                ConsoleColors.getCurrentTime() + " [OK] Alle Komponenten erfolgreich gestartet!");
        printStartupInfo();
    }

    private static void printStartupInfo() {
        ConsoleScreenManager.printToTerminal(" ");
        ConsoleScreenManager.printToTerminal(ConsoleColors.CYAN + "+----------------------------------------------------------------+");
        ConsoleScreenManager.printToTerminal(ConsoleColors.CYAN + "|" + ConsoleColors.WHITE +
                "                    System Information                         " + ConsoleColors.CYAN + "|");
        ConsoleScreenManager.printToTerminal(ConsoleColors.CYAN + "+----------------------------------------------------------------+");

        if (master != null) {
            ConsoleScreenManager.printToTerminal(ConsoleColors.CYAN + "|" + ConsoleColors.YELLOW +
                    " Master ID:      " + ConsoleColors.WHITE + master.getMasterId() +
                    ConsoleScreenManager.padRight("", 64 - 16 - master.getMasterId().length()) + ConsoleColors.CYAN + "|");
            ConsoleScreenManager.printToTerminal(ConsoleColors.CYAN + "|" + ConsoleColors.YELLOW +
                    " Master IP:      " + ConsoleColors.WHITE + master.getMasterHost() + ":" + master.getMasterPort() +
                    ConsoleScreenManager.padRight("", 64 - 16 - (master.getMasterHost() + ":" + master.getMasterPort()).length()) +
                    ConsoleColors.CYAN + "|");
            ConsoleScreenManager.printToTerminal(ConsoleColors.CYAN + "|" + ConsoleColors.YELLOW +
                    " Primary:        " + ConsoleColors.WHITE + (master.isPrimaryMaster() ? "Yes" : "No") +
                    ConsoleScreenManager.padRight("", 64 - 16 - 3) + ConsoleColors.CYAN + "|");
        }

        if (apiServer != null) {
            String apiPort = String.valueOf(apiServer.getPort());
            ConsoleScreenManager.printToTerminal(ConsoleColors.CYAN + "|" + ConsoleColors.YELLOW +
                    " API Endpoint:   " + ConsoleColors.WHITE + "http://" + (master != null ? master.getMasterHost() : "localhost") + ":" + apiPort +
                    ConsoleScreenManager.padRight("", 64 - 16 - ("http://" + (master != null ? master.getMasterHost() : "localhost") + ":" + apiPort).length()) +
                    ConsoleColors.CYAN + "|");
            ConsoleScreenManager.printToTerminal(ConsoleColors.CYAN + "|" + ConsoleColors.YELLOW +
                    " Dashboard:      " + ConsoleColors.WHITE + "http://" + (master != null ? master.getMasterHost() : "localhost") + ":" + apiPort + "/dashboard" +
                    ConsoleScreenManager.padRight("", 64 - 16 - ("http://" + (master != null ? master.getMasterHost() : "localhost") + ":" + apiPort + "/dashboard").length()) +
                    ConsoleColors.CYAN + "|");
        }

        ConsoleScreenManager.printToTerminal(ConsoleColors.CYAN + "+----------------------------------------------------------------+");
        ConsoleScreenManager.printToTerminal(ConsoleColors.CYAN + "|" + ConsoleColors.GREEN +
                "                 System Ready - Type 'help'                    " + ConsoleColors.CYAN + "|");
        ConsoleScreenManager.printToTerminal(ConsoleColors.CYAN + "+----------------------------------------------------------------+");
        ConsoleScreenManager.printToTerminal(ConsoleColors.RESET + " ");
    }

    private static void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (!shutdownRequested) {
                performShutdown();
            }
        }, "Shutdown-Hook"));
    }

    private static void performShutdown() {
        if (shutdownRequested) {
            return;
        }
        shutdownRequested = true;

        ConsoleScreenManager.printToTerminal(" ");
        ConsoleScreenManager.printToTerminal(ConsoleColors.YELLOW + ConsoleColors.PREFIX +
                ConsoleColors.getCurrentTime() + " Cloud-System wird heruntergefahren...");

        if (master != null) {
            master.initiateShutdownMode();
        }

        // Stop Wrapper ZUERST (damit Reconnect gestoppt wird)
        if (wrapper != null) {
            ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                    " Stoppe Wrapper...");
            wrapper.shutdown();
        }

        // Stop API Server
        if (apiServer != null) {
            ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                    " Stoppe REST API...");
            apiServer.stop();
        }

        // Stop Master (should be last)
        if (master != null) {
            ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() +
                    " Stoppe Master...");
            master.shutdown();
        }

        ConsoleScreenManager.printToTerminal(ConsoleColors.GREEN + ConsoleColors.PREFIX +
                ConsoleColors.getCurrentTime() + " [OK] Cloud-System erfolgreich heruntergefahren");
        ConsoleScreenManager.printToTerminal(" ");

        // NEU: Console beenden
        ConsoleScreenManager.stopConsole();

        // Kurze Pause vor finalem Exit
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void requestShutdown() {
        performShutdown();
    }

    private static void keepAlive() {
        // Main thread stays alive to handle console input
        // The ConsoleScreenManager already has an input thread running
        try {
            while (!shutdownRequested) {
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // Getters for components (for testing/debugging)
    public static Master getMaster() {
        return master;
    }

    public static Wrapper getWrapper() {
        return wrapper;
    }

    public static CloudHttpServer getApiServer() {
        return apiServer;
    }

    enum LaunchMode {
        MASTER_ONLY,
        WRAPPER_ONLY,
        COMBINED
    }
}

// Helper method for ConsoleScreenManager
// Add this to ConsoleScreenManager class:
class ConsoleScreenManagerHelper {
    public static String padRight(String s, int n) {
        return String.format("%-" + n + "s", s);
    }
}


