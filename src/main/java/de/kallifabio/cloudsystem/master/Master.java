/**
 * Erstellt von Gamer_Kidd_LP | kallifabio
 * am 03.10.2024 um 18:21
 * Projektname: CloudSystemTest
 * Packagename: de.kallifabio.cloudsystem
 */

package de.kallifabio.cloudsystem.master;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;
import de.kallifabio.cloudsystem.libs.ConsoleColors;
import de.kallifabio.cloudsystem.libs.Message;
import de.kallifabio.cloudsystem.managers.ConfigManager;
import de.kallifabio.cloudsystem.managers.ConsoleScreenManager;
import de.kallifabio.cloudsystem.managers.ServerGroupManager;
import de.kallifabio.cloudsystem.wrapper.Wrapper;
import org.checkerframework.checker.units.qual.C;

import java.io.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;

public class Master {

    private static Master instance;
    private Server server;
    private String masterHost = detectIp();
    private Integer masterPort = 9000;
    private ConfigManager configManager;
    private Wrapper wrapper;

    public void start() {
        instance = this;
        server = new Server();
        Kryo kryo = server.getKryo();

        // Registrierung der Nachrichten für KryoNet
        kryo.register(Message.ServerStatusMessage.class);
        kryo.register(Message.ServerCommand.class);
        kryo.register(String[].class); // Beispielsweise für das Senden von Argumenten
        this.configManager = new ConfigManager();

        // Listener, um Nachrichten vom Wrapper zu empfangen
        server.addListener(new Listener() {
            public void received(Connection connection, Object object) {
                if (object instanceof Message.ServerStatusMessage) {
                    Message.ServerStatusMessage message = (Message.ServerStatusMessage) object;
                    ConsoleScreenManager.logToMainScreen(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() + " Status vom Server: " + message.status);
                } else if (object instanceof Message.ServerCommand) {
                    Message.ServerCommand command = (Message.ServerCommand) object;
                    if ("START".equalsIgnoreCase(command.command)) {
                        ConsoleScreenManager.logToMainScreen(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() + " Starte Server: " + command.serverName);
                        startServer(command.serverName); // Implementiere die Logik
                    }
                }
            }

            public void disconnected(Connection connection) {
                ConsoleScreenManager.logToMainScreen(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() + " Ein Client hat die Verbindung getrennt: " + connection.getID());
            }

            public void idle(Connection connection) {
                // Optionale Idle-Logik
            }

            public void exceptionCaught(Connection connection, Throwable cause) {
                if (cause instanceof IOException) {
                    ConsoleScreenManager.logToMainScreen(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() + " IO-Fehler aufgetreten: " + cause.getMessage());
                } else {
                    System.err.println(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() + " Unerwartete Ausnahme: " + cause.getMessage());
                    cause.printStackTrace();
                }
            }
        });

        try {
            server.bind(54555, 54777); // Beispielhafte Ports
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Cloud-System wird heruntergefahren...");
            if (server != null) {
                server.stop();
                ConsoleScreenManager.logToMainScreen("Netty-Server erfolgreich geschlossen.");
            }
        }));

        ConsoleScreenManager.logToMainScreen(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() + " Cloud-System gestartet auf IP: " + masterHost);


    }

    public String detectIp() {
        try {
            InetAddress localHost = InetAddress.getLocalHost();
            return localHost.getHostAddress();
        } catch (UnknownHostException e) {
            e.printStackTrace();
            return "127.0.0.1"; // Fallback auf localhost
        }
    }

    public void startServer(String serverName) {
        String groupName = serverName.startsWith("Proxy") ? "Proxy" : "Lobby"; // Gruppenerkennung
        ServerGroupManager groupManager = new ServerGroupManager(groupName, true, 1024);
        try {
            groupManager.startServer(serverName);
        } catch (IOException e) {
            System.err.println("Fehler beim Starten des Servers: " + serverName);
            e.printStackTrace();
        }
    }

    public static Master getInstance() {
        return instance;
    }

    public Integer getMasterPort() {
        return masterPort;
    }

    public String getMasterHost() {
        return masterHost;
    }

    public Server getServer() {
        return server;
    }

    public Wrapper getWrapper() {
        return wrapper;
    }

    public void setWrapper(Wrapper wrapper) {
        this.wrapper = wrapper;
    }
}
