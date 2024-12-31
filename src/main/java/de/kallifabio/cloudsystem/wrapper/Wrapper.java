/**
 * Erstellt von Gamer_Kidd_LP | kallifabio
 * am 03.10.2024 um 18:21
 * Projektname: CloudSystemTest
 * Packagename: de.kallifabio.cloudsystem
 */

package de.kallifabio.cloudsystem.wrapper;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryonet.Client;
import de.kallifabio.cloudsystem.commands.CommandHandler;
import de.kallifabio.cloudsystem.commands.startserverCommand;
import de.kallifabio.cloudsystem.libs.ConsoleColors;
import de.kallifabio.cloudsystem.libs.Message;
import de.kallifabio.cloudsystem.managers.ConsoleScreenManager;
import de.kallifabio.cloudsystem.master.Master;

import java.io.*;

public class Wrapper {

    private Client client;
    CommandHandler commandHandler = new CommandHandler();

    public void start() {
        client = new Client();
        Kryo kryo = client.getKryo();

        // Registrierung von Nachrichtenklassen
        kryo.register(Message.ServerStatusMessage.class);
        kryo.register(Message.ServerCommand.class);

        client.start();
        try {
            client.connect(5000, Master.getInstance().detectIp(), 54555, 54777); // Verbindet sich mit dem Master
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        commandHandler.registerCommand("startserver", new startserverCommand());

        Message.ServerCommand startProxyCommand = new Message.ServerCommand();
        startProxyCommand.command = "START";
        startProxyCommand.serverName = "Proxy-1";
        client.sendTCP(startProxyCommand);

        Message.ServerCommand startLobbyCommand = new Message.ServerCommand();
        startLobbyCommand.command = "START";
        startLobbyCommand.serverName = "Lobby-1";
        client.sendTCP(startLobbyCommand);

        ConsoleScreenManager.logToMainScreen(ConsoleColors.PREFIX + ConsoleColors.getCurrentTime() + " Wrapper verbunden");
    }

    public Client getClient() {
        return client;
    }
}
