package de.kallifabio.cloud.software;

import java.util.List;
import java.util.Locale;

public enum ServerSoftware {
    PROXY("bungeecord.jar", List.of("bungeecord.jar", "waterfall.jar", "velocity.jar", "proxy.jar")),
    SPIGOT("spigot.jar", List.of("spigot.jar", "paper.jar", "purpur.jar", "spigot-api.jar", "server.jar")),
    FABRIC("fabric-server-launch.jar", List.of("fabric-server-launch.jar", "fabric-server.jar", "fabric.jar", "server.jar")),
    FORGE("forge.jar", List.of("forge.jar", "forge-server.jar", "minecraftforge.jar", "server.jar")),
    NEOFORGE("neoforge.jar", List.of("neoforge.jar", "neoforge-server.jar", "forge.jar", "server.jar")),
    VANILLA("server.jar", List.of("server.jar", "minecraft-server.jar", "vanilla.jar"));

    private final String primaryJarName;
    private final List<String> jarAliases;

    ServerSoftware(String primaryJarName, List<String> jarAliases) {
        this.primaryJarName = primaryJarName;
        this.jarAliases = jarAliases;
    }

    public String primaryJarName() {
        return primaryJarName;
    }

    public List<String> jarAliases() {
        return jarAliases;
    }

    public boolean isProxy() {
        return this == PROXY;
    }

    public boolean isSpigotLike() {
        return this == SPIGOT;
    }

    public boolean supportsModernArgFile() {
        return this == FORGE || this == NEOFORGE;
    }

    public static ServerSoftware resolve(String configured, String groupName) {
        String value = configured == null || configured.isBlank() ? groupName : configured;
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
        if (normalized.contains("proxy") || normalized.contains("bungee")
                || normalized.contains("waterfall") || normalized.contains("velocity")) {
            return PROXY;
        }
        if (normalized.contains("neoforge") || normalized.contains("neo-forge")) {
            return NEOFORGE;
        }
        if (normalized.contains("forge")) {
            return FORGE;
        }
        if (normalized.contains("fabric")) {
            return FABRIC;
        }
        if (normalized.contains("vanilla")) {
            return VANILLA;
        }
        return SPIGOT;
    }
}
