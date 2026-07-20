package de.kallifabio.cloud.commands.scaling;

import de.kallifabio.cloud.commands.BaseCloudCommand;
import de.kallifabio.cloud.master.capacity.CapacityPlannerService;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class CapacityCommand extends BaseCloudCommand {

    @Override
    public boolean execute(String sender, String[] args) {
        if (!ensureMaster()) return false;

        Map<String, Object> plan = CapacityPlannerService.build(master());
        Map<String, Object> summary = map(plan.get("summary"));
        List<Map<String, Object>> groups = listOfMaps(plan.get("groups"));

        if (args.length > 0) {
            String groupName = args[0];
            Map<String, Object> selected = groups.stream()
                    .filter(group -> groupName.equalsIgnoreCase(str(group.get("groupName"))))
                    .findFirst()
                    .orElse(null);
            if (selected == null) {
                error("Group nicht gefunden: " + groupName);
                return false;
            }
            printGroupDetails(selected);
            return true;
        }

        info("Capacity Planner: startable=" + number(summary.get("startableGroups")) + "/" + number(summary.get("groups"))
                + " | Wrapper healthy=" + number(summary.get("healthyWrappers"))
                + " | RAM frei=" + number(summary.get("availableWrapperMemoryMb")) + "MB/"
                + number(summary.get("totalWrapperMemoryMb")) + "MB"
                + " | Queue=" + number(summary.get("queueTotal")));

        groups.stream()
                .sorted(Comparator
                        .comparingInt((Map<String, Object> group) -> bool(group.get("canStartNow")) ? 1 : 0)
                        .thenComparing((Map<String, Object> group) -> number(group.get("queuedPlayers")), Comparator.reverseOrder())
                        .thenComparing(group -> str(group.get("groupName"))))
                .limit(12)
                .forEach(this::printGroupLine);

        List<String> recommendations = listOfStrings(plan.get("recommendations"));
        if (!recommendations.isEmpty()) {
            info("Empfehlungen:");
            recommendations.stream().limit(5).forEach(item -> info(" - " + item));
        }
        return true;
    }

    private void printGroupLine(Map<String, Object> group) {
        String state = bool(group.get("canStartNow")) ? "READY" : "BLOCKED";
        info("[" + state + "] " + str(group.get("groupName"))
                + " | RAM=" + number(group.get("ramMb")) + "MB"
                + " | Server=" + number(group.get("runningServers")) + "/" + number(group.get("maxServers"))
                + " | empfohlen=" + number(group.get("recommendedServers"))
                + " | Queue=" + number(group.get("queuedPlayers"))
                + " | Wrapper=" + fallback(str(group.get("bestWrapperId")), "none")
                + " | Shortfall=" + number(group.get("capacityShortfallMb")) + "MB");
    }

    private void printGroupDetails(Map<String, Object> group) {
        printGroupLine(group);
        info("Maintenance=" + bool(group.get("maintenance"))
                + " | Dynamic=" + bool(group.get("dynamic"))
                + " | Online=" + number(group.get("onlineServers"))
                + " | Starting=" + number(group.get("startingServers"))
                + " | Players=" + number(group.get("players")) + "/" + number(group.get("maxPlayers")));
        info("Startbare Wrapper: " + String.join(", ", listOfStrings(group.get("startableWrappers"))));
        info("Empfehlung: " + str(group.get("recommendation")));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : Map.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listOfMaps(Object value) {
        return value instanceof List ? (List<Map<String, Object>>) value : List.of();
    }

    @SuppressWarnings("unchecked")
    private List<String> listOfStrings(Object value) {
        if (!(value instanceof List)) {
            return List.of();
        }
        return ((List<Object>) value).stream().map(String::valueOf).toList();
    }

    private int number(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private boolean bool(Object value) {
        return value instanceof Boolean && (Boolean) value;
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    @Override
    public String getDescription() {
        return "Zeigt Capacity-Planung, RAM-Headroom und Startbarkeit pro Group.";
    }

    @Override
    public String getUsage() {
        return "capacity [group]";
    }
}
