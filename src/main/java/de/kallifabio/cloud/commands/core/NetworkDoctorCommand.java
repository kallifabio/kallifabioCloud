package de.kallifabio.cloud.commands.core;

import de.kallifabio.cloud.commands.BaseCloudCommand;
import de.kallifabio.cloud.master.ServerInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class NetworkDoctorCommand extends BaseCloudCommand {

    @Override
    public boolean execute(String sender, String[] args) {
        if (!ensureMaster()) return false;
        boolean fix = false;
        boolean prune = false;
        for (String arg : args) {
            if ("--fix".equalsIgnoreCase(arg) || "fix".equalsIgnoreCase(arg)) {
                fix = true;
            }
            if ("--prune".equalsIgnoreCase(arg) || "prune".equalsIgnoreCase(arg)) {
                prune = true;
            }
        }

        boolean ok = true;
        String mode = "live checks";
        if (fix && prune) {
            mode += " + auto-fix + prune";
        } else if (fix) {
            mode += " + auto-fix";
        } else if (prune) {
            mode += " + prune(requested, requires --fix)";
        }
        info("NetworkDoctor gestartet (" + mode + ")...");

        boolean expectedProxyOnline = getExpectedProxyOnlineMode();
        boolean enforceBackendBind = getEnforceBackendBind();
        String expectedBackendBindAddress = getExpectedBackendBindAddress();
        String forwardingSecret = getForwardingSecret();
        Map<String, ServerInstance> running = master().getRunningServers();
        List<ServerInstance> proxies = running.values().stream().filter(s -> isProxyGroup(s.groupName)).toList();
        List<ServerInstance> backends = running.values().stream().filter(s -> !isProxyGroup(s.groupName)).toList();

        if (proxies.isEmpty()) {
            warn("[WARN] Kein laufender Proxy im Running-Set gefunden.");
            ok = false;
        }

        Set<String> runningBackendNames = new HashSet<>();
        for (ServerInstance backend : backends) {
            runningBackendNames.add(backend.serverName);
        }

        for (ServerInstance proxy : proxies) {
            File configFile = new File("./servers/" + proxy.groupName + "/" + proxy.serverName + "/config.yml");
            if (!configFile.exists()) {
                error("[FAIL] Proxy config.yml fehlt: " + configFile.getAbsolutePath());
                ok = false;
                continue;
            }

            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(configFile);
            boolean changed = false;
            boolean onlineMode = cfg.getBoolean("online_mode", false);
            boolean ipForward = cfg.getBoolean("ip_forward", false);

            if (onlineMode != expectedProxyOnline) {
                warn("[WARN] Proxy " + proxy.serverName + " online_mode=" + onlineMode +
                        " (erwartet " + expectedProxyOnline + ")");
                ok = false;
                if (fix) {
                    cfg.set("online_mode", expectedProxyOnline);
                    onlineMode = expectedProxyOnline;
                    changed = true;
                    info("[FIX] Proxy " + proxy.serverName + " online_mode auf " + expectedProxyOnline + " gesetzt.");
                }
            } else {
                info("[OK] Proxy " + proxy.serverName + " online_mode=" + onlineMode);
            }

            if (!ipForward) {
                warn("[WARN] Proxy " + proxy.serverName + " ip_forward=false (sollte true sein)");
                ok = false;
                if (fix) {
                    cfg.set("ip_forward", true);
                    ipForward = true;
                    changed = true;
                    info("[FIX] Proxy " + proxy.serverName + " ip_forward=true gesetzt.");
                }
            } else {
                info("[OK] Proxy " + proxy.serverName + " ip_forward=true");
            }

            ConfigurationSection serversSection = cfg.getConfigurationSection("servers");
            if (serversSection == null) {
                serversSection = cfg.createSection("servers");
                changed = true;
            }
            Set<String> routes = new HashSet<>(serversSection.getKeys(false));

            List<String> missingRoutes = new ArrayList<>();
            for (String backend : runningBackendNames) {
                if (!routes.contains(backend)) {
                    missingRoutes.add(backend);
                }
            }
            if (missingRoutes.isEmpty()) {
                info("[OK] Proxy " + proxy.serverName + " hat Routen fuer alle laufenden Backends.");
            } else {
                warn("[WARN] Proxy " + proxy.serverName + " fehlende Routen: " + String.join(", ", missingRoutes));
                ok = false;
                if (fix) {
                    for (ServerInstance backend : backends) {
                        if (routes.contains(backend.serverName)) continue;
                        serversSection.set(backend.serverName + ".motd", "&a" + backend.serverName);
                        serversSection.set(backend.serverName + ".address", "127.0.0.1:" + backend.port);
                        serversSection.set(backend.serverName + ".restricted", false);
                        routes.add(backend.serverName);
                    }
                    changed = true;
                    info("[FIX] Fehlende Routen auf Proxy " + proxy.serverName + " angelegt.");
                }
            }

            List<String> staleRoutes = routes.stream().filter(route -> !runningBackendNames.contains(route)).toList();
            if (!staleRoutes.isEmpty()) {
                warn("[WARN] Proxy " + proxy.serverName + " hat stale Routen (Server nicht laufend): " +
                        String.join(", ", staleRoutes));
                if (fix && prune) {
                    for (String staleRoute : staleRoutes) {
                        serversSection.set(staleRoute, null);
                        routes.remove(staleRoute);
                    }
                    changed = true;
                    info("[FIX] Proxy " + proxy.serverName + " stale Routen entfernt: " + String.join(", ", staleRoutes));
                }
            }

            for (ServerInstance backend : backends) {
                if (!routes.contains(backend.serverName)) {
                    continue;
                }
                String address = cfg.getString("servers." + backend.serverName + ".address", "");
                String expectedAddress = "127.0.0.1:" + backend.port;
                if (!expectedAddress.equalsIgnoreCase(address)) {
                    warn("[WARN] Route " + backend.serverName + " zeigt auf " + address +
                            " (erwartet " + expectedAddress + ")");
                    ok = false;
                    if (fix) {
                        serversSection.set(backend.serverName + ".address", expectedAddress);
                        serversSection.set(backend.serverName + ".restricted", false);
                        if (cfg.getString("servers." + backend.serverName + ".motd", "").isBlank()) {
                            serversSection.set(backend.serverName + ".motd", "&a" + backend.serverName);
                        }
                        changed = true;
                        info("[FIX] Route " + backend.serverName + " auf " + expectedAddress + " gesetzt.");
                    }
                }
            }

            if (fix) {
                List<String> priorities = cfg.getStringList("listeners.0.priorities");
                if (priorities == null || priorities.isEmpty()) {
                    String fallbackLobby = runningBackendNames.contains("Lobby-1")
                            ? "Lobby-1"
                            : backends.stream().map(s -> s.serverName).findFirst().orElse("Lobby-1");
                    cfg.set("listeners.0.priorities", List.of(fallbackLobby));
                    changed = true;
                    info("[FIX] listeners.0.priorities gesetzt: " + fallbackLobby);
                }
                Object globalForcedHosts = cfg.get("forced_hosts");
                if (!(globalForcedHosts instanceof Map) || !((Map<?, ?>) globalForcedHosts).isEmpty()) {
                    cfg.set("forced_hosts", new LinkedHashMap<String, Object>());
                    changed = true;
                }
                Object listenerForcedHosts = cfg.get("listeners.0.forced_hosts");
                if (!(listenerForcedHosts instanceof Map) || !((Map<?, ?>) listenerForcedHosts).isEmpty()) {
                    cfg.set("listeners.0.forced_hosts", new LinkedHashMap<String, Object>());
                    changed = true;
                }
            }

            if (changed) {
                try {
                    cfg.save(configFile);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            if (forwardingSecret != null && !forwardingSecret.isBlank()) {
                File secretFile = new File("./servers/" + proxy.groupName + "/" + proxy.serverName + "/forwarding.secret");
                String existing = "";
                if (secretFile.exists()) {
                    try {
                        existing = Files.readString(secretFile.toPath()).trim();
                    } catch (IOException ignored) {
                    }
                }
                if (!forwardingSecret.equals(existing)) {
                    warn("[WARN] Proxy " + proxy.serverName + " forwarding.secret fehlt/abweichend.");
                    ok = false;
                    if (fix) {
                        try {
                            Files.writeString(secretFile.toPath(), forwardingSecret);
                            info("[FIX] Proxy " + proxy.serverName + " forwarding.secret synchronisiert.");
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                } else {
                    info("[OK] Proxy " + proxy.serverName + ": forwarding.secret vorhanden.");
                }
            }
        }

        for (ServerInstance backend : backends) {
            File propertiesFile = new File("./servers/" + backend.groupName + "/" + backend.serverName + "/server.properties");
            if (!propertiesFile.exists()) {
                warn("[WARN] Backend " + backend.serverName + ": server.properties fehlt.");
                ok = false;
            } else {
                List<String> lines;
                try {
                    lines = Files.readAllLines(propertiesFile.toPath());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                boolean hasOnlineMode = false;
                boolean hasServerIp = false;
                boolean onlineModeFalse = false;
                boolean serverIpExpected = false;
                List<String> changed = new ArrayList<>();
                for (String line : lines) {
                    if (line.startsWith("online-mode=")) {
                        hasOnlineMode = true;
                        if ("online-mode=false".equalsIgnoreCase(line.trim())) {
                            onlineModeFalse = true;
                            changed.add(line);
                        } else {
                            changed.add("online-mode=false");
                        }
                    } else if (line.startsWith("server-ip=")) {
                        hasServerIp = true;
                        if (!enforceBackendBind) {
                            serverIpExpected = true;
                            changed.add(line);
                        } else if (("server-ip=" + expectedBackendBindAddress).equalsIgnoreCase(line.trim())) {
                            serverIpExpected = true;
                            changed.add(line);
                        } else {
                            changed.add("server-ip=" + expectedBackendBindAddress);
                        }
                    } else {
                        changed.add(line);
                    }
                }
                if (!hasOnlineMode) {
                    changed.add("online-mode=false");
                }
                if (enforceBackendBind && !hasServerIp) {
                    changed.add("server-ip=" + expectedBackendBindAddress);
                }
                if (!onlineModeFalse || !hasOnlineMode) {
                    warn("[WARN] Backend " + backend.serverName + ": online-mode nicht korrekt (erwartet false).");
                    ok = false;
                } else {
                    info("[OK] Backend " + backend.serverName + ": online-mode=false");
                }
                if (enforceBackendBind) {
                    if (!serverIpExpected || !hasServerIp) {
                        warn("[WARN] Backend " + backend.serverName + ": server-ip nicht korrekt (erwartet " +
                                expectedBackendBindAddress + ").");
                        ok = false;
                    } else {
                        info("[OK] Backend " + backend.serverName + ": server-ip=" + expectedBackendBindAddress);
                    }
                }
                if (fix && (!onlineModeFalse || !hasOnlineMode || (enforceBackendBind && (!serverIpExpected || !hasServerIp)))) {
                    try {
                        Files.write(propertiesFile.toPath(), changed);
                        info("[FIX] Backend " + backend.serverName + ": server.properties gehaertet.");
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }

            File spigotYml = new File("./servers/" + backend.groupName + "/" + backend.serverName + "/spigot.yml");
            if (!spigotYml.exists()) {
                warn("[WARN] Backend " + backend.serverName + ": spigot.yml fehlt.");
                ok = false;
                continue;
            }
            YamlConfiguration spigot = YamlConfiguration.loadConfiguration(spigotYml);
            boolean bungeeMode = spigot.getBoolean("settings.bungeecord", false);
            if (!bungeeMode) {
                warn("[WARN] Backend " + backend.serverName + ": settings.bungeecord=false");
                ok = false;
                if (fix) {
                    spigot.set("settings.bungeecord", true);
                    try {
                        spigot.save(spigotYml);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    info("[FIX] Backend " + backend.serverName + ": settings.bungeecord=true gesetzt.");
                }
            } else {
                info("[OK] Backend " + backend.serverName + ": settings.bungeecord=true");
            }
        }

        if (ok) {
            info("NetworkDoctor abgeschlossen: [OK] keine kritischen Abweichungen gefunden.");
        } else {
            warn("NetworkDoctor abgeschlossen: [WARN] bitte obige Abweichungen korrigieren.");
        }
        if (prune && !fix) {
            warn("Hinweis: --prune wurde ignoriert, weil --fix nicht aktiv war.");
        }
        return ok;
    }

    private boolean isProxyGroup(String groupName) {
        String g = groupName == null ? "" : groupName.toLowerCase(Locale.ROOT);
        return g.contains("proxy") || g.contains("bungee") || g.contains("waterfall") || g.contains("velocity");
    }

    private boolean getExpectedProxyOnlineMode() {
        String configured = master().getConfigManager().getMaster("CloudMaster.Network.ProxyOnlineMode");
        if (configured == null || configured.isBlank()) {
            return true;
        }
        return Boolean.parseBoolean(configured);
    }

    private boolean getEnforceBackendBind() {
        String configured = master().getConfigManager().getMaster("CloudMaster.Network.EnforceBackendBind");
        if (configured == null || configured.isBlank()) {
            return true;
        }
        return Boolean.parseBoolean(configured);
    }

    private String getExpectedBackendBindAddress() {
        String configured = master().getConfigManager().getMaster("CloudMaster.Network.BackendBindAddress");
        if (configured == null || configured.isBlank()) {
            return "127.0.0.1";
        }
        return configured.trim();
    }

    private String getForwardingSecret() {
        String configured = master().getConfigManager().getMaster("CloudMaster.Network.ForwardingSecret");
        if (configured == null || configured.isBlank()) {
            return "";
        }
        return configured.trim();
    }

    @Override
    public String getDescription() {
        return "Prueft Proxy/Backend-Netzwerk-Konfiguration live (online_mode, ip_forward, routes, forwarding.secret, backend-bind).";
    }

    @Override
    public String getUsage() {
        return "networkdoctor [--fix] [--prune]";
    }
}
