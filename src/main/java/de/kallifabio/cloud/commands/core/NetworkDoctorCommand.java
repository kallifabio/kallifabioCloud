package de.kallifabio.cloud.commands.core;

import de.kallifabio.cloud.commands.BaseCloudCommand;
import de.kallifabio.cloud.master.ServerInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
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
        boolean expectedProxyBindLocalAddress = getExpectedProxyBindLocalAddress();
        boolean expectedProxyForceDefaultServer = getExpectedProxyForceDefaultServer();
        int expectedServerConnectTimeoutMs = getExpectedProxyServerConnectTimeoutMs();
        int expectedProxyTimeoutMs = getExpectedProxyTimeoutMs();
        int expectedRemotePingTimeoutMs = getExpectedProxyRemotePingTimeoutMs();
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

            boolean bindLocalAddress = cfg.getBoolean("listeners.0.bind_local_address", true);
            if (bindLocalAddress != expectedProxyBindLocalAddress) {
                warn("[WARN] Proxy " + proxy.serverName + " listeners.0.bind_local_address=" + bindLocalAddress +
                        " (erwartet " + expectedProxyBindLocalAddress + ")");
                ok = false;
                if (fix) {
                    cfg.set("listeners.0.bind_local_address", expectedProxyBindLocalAddress);
                    changed = true;
                    info("[FIX] Proxy " + proxy.serverName + " bind_local_address=" + expectedProxyBindLocalAddress + " gesetzt.");
                }
            } else {
                info("[OK] Proxy " + proxy.serverName + " bind_local_address=" + bindLocalAddress);
            }

            boolean forceDefaultServer = cfg.getBoolean("listeners.0.force_default_server", false);
            if (forceDefaultServer != expectedProxyForceDefaultServer) {
                warn("[WARN] Proxy " + proxy.serverName + " listeners.0.force_default_server=" + forceDefaultServer +
                        " (erwartet " + expectedProxyForceDefaultServer + ")");
                ok = false;
                if (fix) {
                    cfg.set("listeners.0.force_default_server", expectedProxyForceDefaultServer);
                    changed = true;
                    info("[FIX] Proxy " + proxy.serverName + " force_default_server=" + expectedProxyForceDefaultServer + " gesetzt.");
                }
            } else {
                info("[OK] Proxy " + proxy.serverName + " force_default_server=" + forceDefaultServer);
            }

            int serverConnectTimeout = cfg.getInt("server_connect_timeout", 5000);
            if (serverConnectTimeout != expectedServerConnectTimeoutMs) {
                warn("[WARN] Proxy " + proxy.serverName + " server_connect_timeout=" + serverConnectTimeout +
                        " (erwartet " + expectedServerConnectTimeoutMs + ")");
                ok = false;
                if (fix) {
                    cfg.set("server_connect_timeout", expectedServerConnectTimeoutMs);
                    changed = true;
                    info("[FIX] Proxy " + proxy.serverName + " server_connect_timeout=" + expectedServerConnectTimeoutMs + " gesetzt.");
                }
            } else {
                info("[OK] Proxy " + proxy.serverName + " server_connect_timeout=" + serverConnectTimeout);
            }

            int timeout = cfg.getInt("timeout", 30000);
            if (timeout != expectedProxyTimeoutMs) {
                warn("[WARN] Proxy " + proxy.serverName + " timeout=" + timeout +
                        " (erwartet " + expectedProxyTimeoutMs + ")");
                ok = false;
                if (fix) {
                    cfg.set("timeout", expectedProxyTimeoutMs);
                    changed = true;
                    info("[FIX] Proxy " + proxy.serverName + " timeout=" + expectedProxyTimeoutMs + " gesetzt.");
                }
            } else {
                info("[OK] Proxy " + proxy.serverName + " timeout=" + timeout);
            }

            int remotePingTimeout = cfg.getInt("remote_ping_timeout", 5000);
            if (remotePingTimeout != expectedRemotePingTimeoutMs) {
                warn("[WARN] Proxy " + proxy.serverName + " remote_ping_timeout=" + remotePingTimeout +
                        " (erwartet " + expectedRemotePingTimeoutMs + ")");
                ok = false;
                if (fix) {
                    cfg.set("remote_ping_timeout", expectedRemotePingTimeoutMs);
                    changed = true;
                    info("[FIX] Proxy " + proxy.serverName + " remote_ping_timeout=" + expectedRemotePingTimeoutMs + " gesetzt.");
                }
            } else {
                info("[OK] Proxy " + proxy.serverName + " remote_ping_timeout=" + remotePingTimeout);
            }

            ConfigurationSection serversSection = cfg.getConfigurationSection("servers");
            if (serversSection == null) {
                serversSection = cfg.createSection("servers");
                changed = true;
            }
            Set<String> routes = new HashSet<>(serversSection.getKeys(false));
            List<String> crossHostRouteMismatches = new ArrayList<>();

            List<String> missingRoutes = new ArrayList<>();
            for (String backend : runningBackendNames) {
                if (!routes.contains(backend)) {
                    missingRoutes.add(backend);
                }
            }
            if (missingRoutes.isEmpty()) {
                info("[OK] Proxy " + proxy.serverName + " hat Routen für alle laufenden Backends.");
            } else {
                warn("[WARN] Proxy " + proxy.serverName + " fehlende Routen: " + String.join(", ", missingRoutes));
                ok = false;
                if (fix) {
                    for (ServerInstance backend : backends) {
                        if (routes.contains(backend.serverName)) continue;
                        String targetHost = resolveBackendRouteHost(backend);
                        serversSection.set(backend.serverName + ".motd", "&a" + backend.serverName);
                        serversSection.set(backend.serverName + ".address", targetHost + ":" + backend.port);
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
                String expectedRouteHost = resolveBackendRouteHost(backend);
                expectedAddress = expectedRouteHost + ":" + backend.port;
                if (!expectedAddress.equalsIgnoreCase(address)) {
                    String actualHost = extractHost(address);
                    if (!actualHost.equalsIgnoreCase(expectedRouteHost)) {
                        crossHostRouteMismatches.add(backend.serverName + " (" + actualHost + " != " + expectedRouteHost + ")");
                    }
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

                String effectiveAddress = fix ? expectedAddress : address;
                if (isTcpReachable(effectiveAddress, 1500)) {
                    info("[OK] Route " + backend.serverName + " erreichbar: " + effectiveAddress);
                } else {
                    warn("[WARN] Route " + backend.serverName + " nicht erreichbar: " + effectiveAddress +
                            " (Backend noch nicht ONLINE, falscher Host/Port oder Firewall/Bind).");
                    ok = false;
                }
            }

            if (staleRoutes.isEmpty() && crossHostRouteMismatches.isEmpty()) {
                info("[REPORT] Proxy " + proxy.serverName + ": stale=0, cross-host=0");
            } else {
                warn("[REPORT] Proxy " + proxy.serverName + ": stale=" + staleRoutes.size() +
                        ", cross-host=" + crossHostRouteMismatches.size());
                if (!staleRoutes.isEmpty()) {
                    warn("[REPORT]   stale routes: " + String.join(", ", staleRoutes));
                }
                if (!crossHostRouteMismatches.isEmpty()) {
                    warn("[REPORT]   cross-host mismatches: " + String.join(", ", crossHostRouteMismatches));
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
                        info("[FIX] Backend " + backend.serverName + ": server.properties gehärtet.");
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

    private boolean getExpectedProxyBindLocalAddress() {
        String configured = master().getConfigManager().getMaster("CloudMaster.Network.ProxyBindLocalAddress");
        if (configured == null || configured.isBlank()) {
            return false;
        }
        return Boolean.parseBoolean(configured);
    }

    private boolean getExpectedProxyForceDefaultServer() {
        String configured = master().getConfigManager().getMaster("CloudMaster.Network.ProxyForceDefaultServer");
        if (configured == null || configured.isBlank()) {
            return true;
        }
        return Boolean.parseBoolean(configured);
    }

    private int getExpectedProxyServerConnectTimeoutMs() {
        return getConfiguredInt("CloudMaster.Network.ProxyServerConnectTimeoutMs", 15000, 1000, 120000);
    }

    private int getExpectedProxyTimeoutMs() {
        return getConfiguredInt("CloudMaster.Network.ProxyTimeoutMs", 60000, 10000, 300000);
    }

    private int getExpectedProxyRemotePingTimeoutMs() {
        return getConfiguredInt("CloudMaster.Network.ProxyRemotePingTimeoutMs", 5000, 1000, 60000);
    }

    private int getConfiguredInt(String key, int fallback, int min, int max) {
        String configured = master().getConfigManager().getMaster(key);
        if (configured == null || configured.isBlank()) {
            return fallback;
        }
        try {
            int value = Integer.parseInt(configured.trim());
            return Math.max(min, Math.min(max, value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
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

    private String resolveBackendRouteHost(ServerInstance backend) {
        if (backend == null) {
            return "127.0.0.1";
        }
        for (de.kallifabio.cloud.master.WrapperConnection wrapper : master().getConnectedWrappers().values()) {
            if (wrapper == null || wrapper.wrapperId == null || !wrapper.wrapperId.equalsIgnoreCase(backend.wrapperId)) {
                continue;
            }
            if (wrapper.routeHost != null && !wrapper.routeHost.isBlank()) {
                return wrapper.routeHost.trim();
            }
            if (wrapper.hostname != null && !wrapper.hostname.isBlank()) {
                return wrapper.hostname.trim();
            }
        }
        String configured = master().getConfigManager().getMaster("CloudMaster.Network.GameHost");
        if (configured == null || configured.isBlank()) {
            return "127.0.0.1";
        }
        return configured.trim();
    }

    private String extractHost(String address) {
        if (address == null || address.isBlank()) {
            return "";
        }
        int idx = address.lastIndexOf(':');
        if (idx <= 0) {
            return address.trim();
        }
        return address.substring(0, idx).trim();
    }

    private boolean isTcpReachable(String address, int timeoutMs) {
        if (address == null || address.isBlank()) {
            return false;
        }
        int idx = address.lastIndexOf(':');
        if (idx <= 0 || idx >= address.length() - 1) {
            return false;
        }
        String host = address.substring(0, idx).trim();
        int port;
        try {
            port = Integer.parseInt(address.substring(idx + 1).trim());
        } catch (NumberFormatException ignored) {
            return false;
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    @Override
    public String getDescription() {
        return "Prüft Proxy/Backend-Netzwerk-Konfiguration live (online_mode, ip_forward, routes, forwarding.secret, backend-bind).";
    }

    @Override
    public String getUsage() {
        return "networkdoctor [--fix] [--prune]";
    }
}
