package de.kallifabio.cloud.setup;

import de.kallifabio.cloud.config.ConfigManager;
import de.kallifabio.cloud.software.ServerSoftware;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Broad, read-only health inspection for common CloudSystem misconfiguration.
 */
public final class SystemDoctor {

    private SystemDoctor() {
    }

    public static Map<String, Object> buildReport(ConfigManager configManager) {
        List<Map<String, Object>> findings = new ArrayList<>();
        Map<String, Object> report = new LinkedHashMap<>();

        inspectFiles(configManager, findings);
        inspectPorts(configManager, findings);
        inspectNetwork(configManager, findings);
        inspectMonitoringAndRecovery(configManager, findings);
        inspectServerGroups(configManager, findings);
        inspectAutoStart(configManager, findings);
        inspectSetupValidator(configManager, findings);

        long critical = findings.stream().filter(f -> "CRITICAL".equals(f.get("severity"))).count();
        long warnings = findings.stream().filter(f -> "WARNING".equals(f.get("severity"))).count();
        long info = findings.stream().filter(f -> "INFO".equals(f.get("severity"))).count();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("state", critical > 0 ? "CRITICAL" : warnings > 0 ? "WARNING" : "OK");
        summary.put("critical", critical);
        summary.put("warnings", warnings);
        summary.put("info", info);
        summary.put("findings", findings.size());
        summary.put("groups", configManager.getAllServerGroups().size());

        report.put("timestamp", System.currentTimeMillis());
        report.put("summary", summary);
        report.put("findings", findings);
        return report;
    }

    private static void inspectFiles(ConfigManager config, List<Map<String, Object>> findings) {
        checkReadableFile(findings, "config:master", config.getMasterConfigFile(), "Master config file");
        checkReadableFile(findings, "config:servergroups", config.getServerGroupsFile(), "ServerGroups config file");
        checkReadableFile(findings, "config:cluster", config.getClusterConfigFile(), "Cluster config file");
        checkWritableDirectory(findings, "dir:logs", Path.of("logs"), "Log/report directory");
        checkDirectory(findings, "dir:templates", Path.of("templates"), "Template directory");
        checkDirectory(findings, "dir:data", Path.of("data"), "Data directory");
    }

    private static void inspectPorts(ConfigManager config, List<Map<String, Object>> findings) {
        Map<String, Integer> ports = new LinkedHashMap<>();
        ports.put("CloudMaster.Network.TcpPort", config.getMasterTcpPort());
        ports.put("CloudMaster.Network.UdpPort", config.getMasterUdpPort());
        ports.put("Ports.FirstProxy", config.getFirstProxyPort());
        ports.put("Ports.FirstLobby", config.getFirstLobbyPort());
        ports.put("Ports.DynamicStart", config.getDynamicPortStart());

        Map<Integer, List<String>> byPort = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : ports.entrySet()) {
            int port = entry.getValue();
            if (port < 1 || port > 65535) {
                finding(findings, "CRITICAL", "port:" + entry.getKey(),
                        entry.getKey() + " is outside valid port range: " + port,
                        "Set it to a value between 1 and 65535.");
            }
            byPort.computeIfAbsent(port, ignored -> new ArrayList<>()).add(entry.getKey());
        }

        for (Map.Entry<Integer, List<String>> entry : byPort.entrySet()) {
            if (entry.getValue().size() > 1) {
                finding(findings, "CRITICAL", "port:collision:" + entry.getKey(),
                        "Port " + entry.getKey() + " is used by multiple settings: " + String.join(", ", entry.getValue()),
                        "Use unique ports for master, proxy, lobby and dynamic ranges.");
            }
        }

        if (config.getDynamicPortStart() <= config.getFirstLobbyPort()) {
            finding(findings, "WARNING", "port:dynamic",
                    "Ports.DynamicStart is not above Ports.FirstLobby.",
                    "Set DynamicStart above your static proxy/lobby ports to avoid accidental overlap.");
        }
    }

    private static void inspectNetwork(ConfigManager config, List<Map<String, Object>> findings) {
        String connectHost = config.getMasterConnectHost();
        String proxyBind = config.getMasterConfigData().getString("CloudMaster.Network.ProxyBindHost", "0.0.0.0");
        String gameHost = config.getMasterConfigData().getString("CloudMaster.Network.GameHost", "127.0.0.1");
        boolean enforceBackendBind = config.getMasterConfigData().getBoolean("CloudMaster.Network.EnforceBackendBind", true);
        String backendBind = config.getMasterConfigData().getString("CloudMaster.Network.BackendBindAddress", "127.0.0.1");

        if (connectHost == null || connectHost.isBlank()) {
            finding(findings, "CRITICAL", "network:connectHost",
                    "CloudMaster.Network.ConnectHost is empty.",
                    "Set it to 127.0.0.1 for single-host or to a reachable private/public master address for multi-host.");
        } else if ("0.0.0.0".equals(connectHost)) {
            finding(findings, "CRITICAL", "network:connectHost",
                    "ConnectHost is 0.0.0.0, which clients cannot connect to.",
                    "Use 127.0.0.1 for local combined mode or the real master IP for remote wrappers.");
        }

        if (proxyBind == null || proxyBind.isBlank()) {
            finding(findings, "WARNING", "network:proxyBind",
                    "ProxyBindHost is empty.",
                    "Use 0.0.0.0 for public proxy listeners or a specific interface address.");
        }

        if (!enforceBackendBind) {
            finding(findings, "WARNING", "network:backendBind",
                    "Backend bind enforcement is disabled.",
                    "Enable EnforceBackendBind so backend servers are not accidentally public.");
        }

        boolean singleHost = "127.0.0.1".equals(gameHost) || "localhost".equalsIgnoreCase(gameHost);
        if (singleHost && !"127.0.0.1".equals(backendBind) && !"localhost".equalsIgnoreCase(backendBind)) {
            finding(findings, "WARNING", "network:singleHostBackend",
                    "GameHost is local, but BackendBindAddress is not local.",
                    "For single-host testing, bind backends to 127.0.0.1 so players must join through the proxy.");
        }
    }

    private static void inspectMonitoringAndRecovery(ConfigManager config, List<Map<String, Object>> findings) {
        double cpuWarn = config.getMonitoringCpuWarning();
        double cpuCritical = config.getMonitoringCpuCritical();
        double memWarn = config.getMonitoringMemoryWarning();
        double memCritical = config.getMonitoringMemoryCritical();
        double tpsWarn = config.getMonitoringTpsWarning();
        double tpsCritical = config.getMonitoringTpsCritical();

        if (cpuWarn >= cpuCritical) {
            finding(findings, "WARNING", "monitoring:cpu",
                    "CPU warning threshold is greater than or equal to critical threshold.",
                    "Keep CPU.Warning below CPU.Critical, for example 90 and 97.");
        }
        if (memWarn >= memCritical) {
            finding(findings, "WARNING", "monitoring:memory",
                    "Memory warning threshold is greater than or equal to critical threshold.",
                    "Keep Memory.Warning below Memory.Critical, for example 90 and 96.");
        }
        if (tpsWarn <= tpsCritical) {
            finding(findings, "WARNING", "monitoring:tps",
                    "TPS warning threshold is lower than or equal to critical threshold.",
                    "Because lower TPS is worse, use for example TPS.Warning=18 and TPS.Critical=15.");
        }

        if (config.getRecoveryServerStartingTimeoutMs() < 30_000L) {
            finding(findings, "WARNING", "recovery:startingTimeout",
                    "ServerStartingTimeoutMs is very low.",
                    "Use at least 60-120 seconds for modern Paper/Forge/Fabric starts.");
        }
        if (config.getRecoveryWrapperPongTimeoutMs() >= config.getRecoveryServerHeartbeatTimeoutMs()) {
            finding(findings, "INFO", "recovery:timeouts",
                    "Wrapper pong timeout is greater than or equal to server heartbeat timeout.",
                    "Usually wrapper pong should be shorter so wrapper health is detected before server recovery gets noisy.");
        }
    }

    private static void inspectServerGroups(ConfigManager config, List<Map<String, Object>> findings) {
        List<String> groups = new ArrayList<>(config.getAllServerGroups());
        if (groups.isEmpty()) {
            finding(findings, "CRITICAL", "groups:none",
                    "No ServerGroup entries found.",
                    "Create at least Proxy and Lobby groups.");
            return;
        }

        boolean hasProxy = false;
        for (String group : groups) {
            ServerSoftware software = ServerSoftware.resolve(config.getSoftwareForGroup(group), group);
            if (software.isProxy()) {
                hasProxy = true;
            }
            inspectGroup(config, group, software, findings);
        }

        if (!hasProxy) {
            finding(findings, "CRITICAL", "groups:proxy",
                    "No proxy ServerGroup was detected.",
                    "Create a Proxy group with Software=proxy so players can join through the network entrypoint.");
        }
    }

    private static void inspectGroup(ConfigManager config, String group, ServerSoftware software, List<Map<String, Object>> findings) {
        int ram = config.getRamForGroup(group);
        int maxPlayers = config.getMaxPlayersForGroup(group);
        int minServers = config.getMinServersForGroup(group);
        int maxServers = config.getMaxServersForGroup(group);
        double scaleUp = config.getScaleUpThreshold(group);
        double scaleDown = config.getScaleDownThreshold(group);

        if (ram < 256) {
            finding(findings, "CRITICAL", "group:" + group + ":ram",
                    group + " has less than 256 MB RAM configured.",
                    "Increase ServerGroup." + group + ".Ram.");
        }
        if (software.isProxy() && config.isDynamicGroup(group)) {
            finding(findings, "WARNING", "group:" + group + ":proxyDynamic",
                    group + " resolves to proxy software but Dynamic=true.",
                    "Static proxy groups are safer. Set Dynamic=false unless you intentionally run multiple proxies.");
        }
        if (software.isProxy() && maxPlayers < 1) {
            finding(findings, "WARNING", "group:" + group + ":slots",
                    group + " has no player slots configured.",
                    "Set MaxPlayers to the advertised proxy capacity.");
        }
        if (!software.isProxy() && maxPlayers > 500) {
            finding(findings, "INFO", "group:" + group + ":slots",
                    group + " has a very high backend MaxPlayers value: " + maxPlayers,
                    "For most game/lobby servers, smaller backend slots improve routing and autoscaling quality.");
        }
        if (maxServers < minServers) {
            finding(findings, "CRITICAL", "group:" + group + ":minmax",
                    group + " has MaxServers lower than MinServers.",
                    "Set MaxServers >= MinServers.");
        }
        if (scaleDown >= scaleUp) {
            finding(findings, "WARNING", "group:" + group + ":scaling",
                    group + " has ScaleDownThreshold >= ScaleUpThreshold.",
                    "Keep scale-down below scale-up to avoid scaling flaps.");
        }
        if (software.supportsModernArgFile() && config.getStartArgsForGroup(group).isEmpty()) {
            finding(findings, "INFO", "group:" + group + ":startArgs",
                    group + " is " + software.name().toLowerCase(Locale.ROOT) + " without StartArgs.",
                    "Use StartArgs: [nogui] unless your modloader installer generated a custom argument file.");
        }
    }

    private static void inspectAutoStart(ConfigManager config, List<Map<String, Object>> findings) {
        if (!config.isAutoStartEnabled()) {
            finding(findings, "INFO", "autostart:disabled",
                    "AutoStart is disabled.",
                    "Enable AutoStart if Proxy/Lobby should boot automatically.");
            return;
        }

        Set<String> knownGroups = new LinkedHashSet<>(config.getAllServerGroups());
        for (String entry : config.getAutoStartGroups()) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            String[] parts = entry.split(":");
            String groupName = parts[0].trim();
            if (!knownGroups.contains(groupName)) {
                finding(findings, "CRITICAL", "autostart:" + groupName,
                        "AutoStart references unknown group: " + groupName,
                        "Fix AutoStart.Groups or create the missing ServerGroup.");
            }
            if (parts.length > 1) {
                try {
                    int count = Integer.parseInt(parts[1].trim());
                    if (count < 0) {
                        finding(findings, "WARNING", "autostart:" + groupName + ":count",
                                "AutoStart count is negative for " + groupName + ".",
                                "Use 0 or a positive server count.");
                    }
                } catch (NumberFormatException ex) {
                    finding(findings, "WARNING", "autostart:" + groupName + ":count",
                            "AutoStart count is not numeric for " + groupName + ": " + parts[1],
                            "Use format Group:Count, for example Proxy:1,Lobby:1.");
                }
            }
        }
    }

    private static void inspectSetupValidator(ConfigManager config, List<Map<String, Object>> findings) {
        Map<String, Object> setup = SetupValidator.buildReport(config, false);
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) setup.get("summary");
        int issues = summary.get("issuesTotal") instanceof Number number ? number.intValue() : 0;
        if (issues > 0) {
            finding(findings, "WARNING", "setup:templates",
                    "Setup validator found " + issues + " template/JAR issue(s).",
                    "Run setup or setup --fix for detailed template and JAR diagnostics.");
        }
    }

    private static void checkReadableFile(List<Map<String, Object>> findings, String id, File file, String label) {
        if (file == null || !file.exists()) {
            finding(findings, "CRITICAL", id, label + " does not exist.",
                    "Regenerate default configs or restore the missing file.");
        } else if (!file.isFile() || !file.canRead()) {
            finding(findings, "CRITICAL", id, label + " is not readable.",
                    "Fix file permissions and verify the path.");
        }
    }

    private static void checkDirectory(List<Map<String, Object>> findings, String id, Path directory, String label) {
        if (!Files.exists(directory)) {
            finding(findings, "WARNING", id, label + " does not exist.",
                    "Create the directory or run the matching setup command if this is expected.");
        } else if (!Files.isDirectory(directory)) {
            finding(findings, "CRITICAL", id, label + " exists but is not a directory.",
                    "Replace it with a directory.");
        }
    }

    private static void checkWritableDirectory(List<Map<String, Object>> findings, String id, Path directory, String label) {
        checkDirectory(findings, id, directory, label);
        if (Files.exists(directory) && Files.isDirectory(directory) && !Files.isWritable(directory)) {
            finding(findings, "WARNING", id, label + " is not writable.",
                    "Fix permissions so reports and logs can be written.");
        }
    }

    private static void finding(List<Map<String, Object>> findings, String severity, String id, String message, String recommendation) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("severity", severity);
        item.put("id", id);
        item.put("message", message);
        item.put("recommendation", recommendation);
        findings.add(item);
    }
}
