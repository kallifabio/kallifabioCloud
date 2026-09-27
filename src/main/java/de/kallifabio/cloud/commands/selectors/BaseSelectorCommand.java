package de.kallifabio.cloud.commands.selectors;

import de.kallifabio.cloud.commands.BaseCloudCommand;
import de.kallifabio.cloud.master.Master;
import de.kallifabio.cloud.master.ServerInstance;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

abstract class BaseSelectorCommand extends BaseCloudCommand {

    protected List<Map<String, Object>> selectors(String... types) {
        return master().getConfigManager().getCloudSelectors(types);
    }

    protected Map<String, Object> findSelector(String id, String... types) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return selectors(types).stream()
                .filter(selector -> id.equalsIgnoreCase(String.valueOf(selector.get("id"))))
                .findFirst()
                .orElse(null);
    }

    protected Map<String, Object> baseSelector(String id, String selectorType, String world, int x, int y, int z,
                                               String targetType, String target, String layout, int priority) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id);
        body.put("selectorType", selectorType);
        body.put("world", world);
        body.put("x", x);
        body.put("y", y);
        body.put("z", z);
        body.put("locationMode", "FIXED");
        body.put("autoLocation", false);
        body.put("layout", layout == null || layout.isBlank() ? defaultLayout(selectorType) : layout);
        body.put("priority", priority);
        body.put("enabled", true);
        if ("group".equalsIgnoreCase(targetType)) {
            body.put("groupName", target);
        } else {
            body.put("serverName", target);
        }
        return body;
    }

    protected Map<String, Object> autoSelector(String selectorType, String targetType, String target,
                                               String layout, int priority) {
        Map<String, Object> body = baseSelector("", selectorType, "AUTO", 0, 0, 0,
                targetType, target, layout, priority);
        body.put("locationMode", "AUTO");
        body.put("autoLocation", true);
        return body;
    }

    protected void printSelector(Map<String, Object> selector) {
        if (selector == null) {
            return;
        }
        String id = text(selector.get("id"));
        String type = text(selector.get("selectorType"));
        String entity = text(selector.get("entityType"));
        String target = !text(selector.get("serverName")).isBlank()
                ? "server=" + text(selector.get("serverName"))
                : "group=" + text(selector.get("groupName"));
        String locationMode = text(selector.get("locationMode"));
        String location = "AUTO".equalsIgnoreCase(locationMode)
                ? "AUTO"
                : text(selector.get("world")) + " " + selector.get("x") + " " + selector.get("y") + " " + selector.get("z");
        info(id + " | type=" + type + ("SIGN".equalsIgnoreCase(type) ? "" : " entity=" + entity)
                + " | " + target + " | loc=" + location + " | layout=" + text(selector.get("layout"))
                + " | enabled=" + selector.get("enabled"));
    }

    protected void printRendered(Map<String, Object> selector) {
        ServerInstance target = resolveTarget(selector);
        String selectorType = text(selector.get("selectorType"));
        String animation = master().getConfigManager().getSignAnimationFrames().stream().findFirst().orElse("");
        String layout = text(selector.get("layout")).isBlank() ? defaultLayout(selectorType) : text(selector.get("layout"));
        List<String> lines = renderLines(master().getConfigManager().getSignLayout(layout), selector, target, animation);
        info("Render fuer " + selector.get("id") + " (" + selectorType + "):");
        for (int i = 0; i < lines.size(); i++) {
            info("  " + (i + 1) + ": " + lines.get(i));
        }
    }

    protected boolean deleteSelector(String id, String... allowedTypes) {
        Map<String, Object> existing = findSelector(id, allowedTypes);
        if (existing == null) {
            error("Selector nicht gefunden oder falscher Typ: " + id);
            return false;
        }
        boolean deleted = master().getConfigManager().deleteCloudSign(id);
        if (deleted) {
            info("Selector geloescht: " + id);
        }
        return deleted;
    }

    protected boolean setEnabled(String id, boolean enabled, String... allowedTypes) {
        Map<String, Object> existing = findSelector(id, allowedTypes);
        if (existing == null) {
            error("Selector nicht gefunden oder falscher Typ: " + id);
            return false;
        }
        existing.put("enabled", enabled);
        master().getConfigManager().upsertCloudSign(existing);
        info("Selector " + id + " ist jetzt " + (enabled ? "aktiv" : "deaktiviert") + ".");
        return true;
    }

    protected boolean moveSelector(String id, String world, int x, int y, int z, String... allowedTypes) {
        Map<String, Object> existing = findSelector(id, allowedTypes);
        if (existing == null) {
            error("Selector nicht gefunden oder falscher Typ: " + id);
            return false;
        }
        existing.put("world", world);
        existing.put("x", x);
        existing.put("y", y);
        existing.put("z", z);
        existing.put("locationMode", "FIXED");
        existing.put("autoLocation", false);
        master().getConfigManager().upsertCloudSign(existing);
        info("Selector " + id + " verschoben nach " + world + " " + x + " " + y + " " + z + ".");
        return true;
    }

    protected boolean isTargetType(String value) {
        return "server".equalsIgnoreCase(value) || "group".equalsIgnoreCase(value);
    }

    protected boolean rotateSelector(String id, double yaw, double pitch) {
        Map<String, Object> existing = findSelector(id, "NPC", "MOB");
        if (existing == null) {
            error("Entity-Selector nicht gefunden: " + id);
            return false;
        }
        existing.put("yaw", yaw);
        existing.put("pitch", pitch);
        master().getConfigManager().upsertCloudSign(existing);
        info("Entity-Selector " + id + " rotiert: yaw=" + yaw + " pitch=" + pitch);
        return true;
    }

    protected void applyOptionalMetadata(Map<String, Object> selector, String[] args, int categoryIndex,
                                         int permissionIndex, int regionIndex) {
        if (args.length > categoryIndex && !args[categoryIndex].isBlank()) {
            selector.put("category", args[categoryIndex]);
        }
        if (args.length > permissionIndex && !args[permissionIndex].isBlank()) {
            selector.put("permission", args[permissionIndex]);
        }
        if (args.length > regionIndex && !args[regionIndex].isBlank()) {
            selector.put("region", args[regionIndex]);
        }
    }

    protected boolean saveLayout(String layoutName, String[] args, int offset) {
        if (args.length < offset + 4) {
            return false;
        }
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            lines.add(args[offset + i]);
        }
        boolean saved = master().getConfigManager().upsertSignLayout(layoutName, lines);
        if (saved) {
            info("Layout gespeichert: " + layoutName);
        }
        return saved;
    }

    protected int parseInt(String value, String name) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " muss eine Zahl sein: " + value);
        }
    }

    protected double parseDouble(String value, String name) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " muss eine Zahl sein: " + value);
        }
    }

    protected int optionalInt(String[] args, int index, int fallback, String name) {
        return args.length > index ? parseInt(args[index], name) : fallback;
    }

    protected double optionalDouble(String[] args, int index, double fallback, String name) {
        return args.length > index ? parseDouble(args[index], name) : fallback;
    }

    protected String normalizeType(String value) {
        String type = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!"NPC".equals(type) && !"MOB".equals(type)) {
            throw new IllegalArgumentException("Typ muss NPC oder MOB sein.");
        }
        return type;
    }

    protected String normalizeEntityType(String value, String selectorType) {
        String fallback = "MOB".equalsIgnoreCase(selectorType) ? "ZOMBIE" : "VILLAGER";
        String entityType = value == null || value.isBlank() ? fallback : value.trim().toUpperCase(Locale.ROOT);
        if (!entityType.matches("[A-Z0-9_]{2,64}")) {
            throw new IllegalArgumentException("EntityType ist ungueltig: " + entityType);
        }
        return entityType;
    }

    protected String defaultLayout(String selectorType) {
        if ("NPC".equalsIgnoreCase(selectorType)) {
            return "Npc";
        }
        if ("MOB".equalsIgnoreCase(selectorType)) {
            return "Mob";
        }
        return "Default";
    }

    private ServerInstance resolveTarget(Map<String, Object> selector) {
        Master currentMaster = master();
        String serverName = text(selector.get("serverName"));
        if (!serverName.isBlank()) {
            return currentMaster.getRunningServers().get(serverName);
        }
        String groupName = text(selector.get("groupName"));
        if (groupName.isBlank()) {
            return null;
        }
        return currentMaster.getRunningServers().values().stream()
                .filter(server -> groupName.equalsIgnoreCase(server.groupName))
                .sorted(java.util.Comparator
                        .comparing((ServerInstance server) -> !"ONLINE".equalsIgnoreCase(server.status))
                        .thenComparingInt(server -> server.playerCount)
                        .thenComparing(server -> server.serverName))
                .findFirst()
                .orElse(null);
    }

    private List<String> renderLines(List<String> templateLines, Map<String, Object> selector, ServerInstance target, String animation) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("{id}", text(selector.get("id")));
        placeholders.put("{world}", text(selector.get("world")));
        placeholders.put("{selector_type}", text(selector.get("selectorType")));
        placeholders.put("{entity_type}", text(selector.get("entityType")));
        placeholders.put("{display_name}", text(selector.get("displayName")));
        placeholders.put("{server_name}", target == null ? text(selector.get("serverName")) : target.serverName);
        placeholders.put("{server}", placeholders.get("{server_name}"));
        placeholders.put("{group}", target == null ? text(selector.get("groupName")) : target.groupName);
        placeholders.put("{status}", target == null ? "OFFLINE" : target.status);
        placeholders.put("{players_online}", String.valueOf(target == null ? 0 : target.playerCount));
        placeholders.put("{max_players}", String.valueOf(target == null ? 0 : target.maxPlayers));
        placeholders.put("{players}", (target == null ? 0 : target.playerCount) + "/" + (target == null ? 0 : target.maxPlayers));
        placeholders.put("{tps}", String.format(Locale.ROOT, "%.1f", target == null ? 0.0 : target.tps));
        placeholders.put("{port}", String.valueOf(target == null ? -1 : target.port));
        placeholders.put("{wrapper}", target == null ? "" : target.wrapperId);
        placeholders.put("{animation}", animation == null ? "" : animation);

        List<String> lines = new ArrayList<>();
        for (String line : templateLines) {
            String rendered = line == null ? "" : line;
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                rendered = rendered.replace(entry.getKey(), entry.getValue());
            }
            lines.add(rendered);
        }
        while (lines.size() < 4) {
            lines.add("");
        }
        return lines.subList(0, 4);
    }

    protected String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
