/**
 * Erstellt von Gamer_Kidd_LP | kallifabio
 * am 29.12.2024 um 03:45
 * Projektname: CloudSystemTest
 * Packagename: de.kallifabio.cloudsystem.commands
 */

package de.kallifabio.cloudsystem.commands;

public interface Command {

    boolean execute(String sender, String[] args);
}
