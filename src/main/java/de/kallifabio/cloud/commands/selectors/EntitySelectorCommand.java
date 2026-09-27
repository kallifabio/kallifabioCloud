package de.kallifabio.cloud.commands.selectors;

import java.util.Map;

public class EntitySelectorCommand extends BaseSelectorCommand {

    @Override
    public boolean execute(String sender, String[] args) {
        if (!ensureMaster()) return false;
        if (args.length == 0) {
            printHelp();
            return true;
        }

        String action = args[0].toLowerCase();
        switch (action) {
            case "list" -> {
                if (args.length >= 2) {
                    selectors(normalizeType(args[1])).forEach(this::printSelector);
                } else {
                    selectors("NPC", "MOB").forEach(this::printSelector);
                }
                return true;
            }
            case "info" -> {
                if (args.length < 2) return false;
                Map<String, Object> selector = findSelector(args[1], "NPC", "MOB");
                if (selector == null) {
                    error("Entity-Selector nicht gefunden: " + args[1]);
                    return false;
                }
                printSelector(selector);
                printRendered(selector);
                return true;
            }
            case "render" -> {
                if (args.length >= 2) {
                    String filterOrId = args[1];
                    if ("npc".equalsIgnoreCase(filterOrId) || "mob".equalsIgnoreCase(filterOrId)) {
                        selectors(normalizeType(filterOrId)).forEach(this::printRendered);
                        return true;
                    }
                    Map<String, Object> selector = findSelector(filterOrId, "NPC", "MOB");
                    if (selector == null) {
                        error("Entity-Selector nicht gefunden: " + filterOrId);
                        return false;
                    }
                    printRendered(selector);
                    return true;
                }
                selectors("NPC", "MOB").forEach(this::printRendered);
                return true;
            }
            case "create", "set" -> {
                if (args.length < 2) return false;
                String selectorType = normalizeType(args[1]);
                Map<String, Object> selector;
                if (args.length >= 5 && isTargetType(args[2])) {
                    selector = autoSelector(
                            selectorType,
                            args[2],
                            args[3],
                            args.length > 6 ? args[6] : defaultLayout(selectorType),
                            optionalInt(args, 9, 0, "priority")
                    );
                    selector.put("entityType", normalizeEntityType(args[4], selectorType));
                    selector.put("displayName", args.length > 5 ? args[5] : "");
                    selector.put("yaw", optionalDouble(args, 7, 0.0, "yaw"));
                    selector.put("pitch", optionalDouble(args, 8, 0.0, "pitch"));
                    applyOptionalMetadata(selector, args, 10, 11, 12);
                } else {
                    if (args.length < 10) return false;
                    String targetType = args[7];
                    if (!isTargetType(targetType)) {
                        error("Target-Typ muss 'server' oder 'group' sein.");
                        return false;
                    }
                    selector = baseSelector(
                            args[2],
                            selectorType,
                            args[3],
                            parseInt(args[4], "x"),
                            parseInt(args[5], "y"),
                            parseInt(args[6], "z"),
                            targetType,
                            args[8],
                            args.length > 11 ? args[11] : defaultLayout(selectorType),
                            optionalInt(args, 14, 0, "priority")
                    );
                    selector.put("entityType", normalizeEntityType(args[9], selectorType));
                    selector.put("displayName", args.length > 10 ? args[10] : "");
                    selector.put("yaw", optionalDouble(args, 12, 0.0, "yaw"));
                    selector.put("pitch", optionalDouble(args, 13, 0.0, "pitch"));
                    applyOptionalMetadata(selector, args, 15, 16, 17);
                }
                selector.put("glowing", false);
                Map<String, Object> saved = master().getConfigManager().upsertCloudSign(selector);
                info(selectorType + "-Selector gespeichert: " + saved.get("id"));
                printSelector(saved);
                return true;
            }
            case "move" -> {
                if (args.length < 6) return false;
                return moveSelector(args[1], args[2], parseInt(args[3], "x"), parseInt(args[4], "y"),
                        parseInt(args[5], "z"), "NPC", "MOB");
            }
            case "rotate" -> {
                if (args.length < 4) return false;
                return rotateSelector(args[1], parseDouble(args[2], "yaw"), parseDouble(args[3], "pitch"));
            }
            case "enable" -> {
                if (args.length < 2) return false;
                return setEnabled(args[1], true, "NPC", "MOB");
            }
            case "disable" -> {
                if (args.length < 2) return false;
                return setEnabled(args[1], false, "NPC", "MOB");
            }
            case "delete", "remove" -> {
                if (args.length < 2) return false;
                return deleteSelector(args[1], "NPC", "MOB");
            }
            case "layout" -> {
                if (args.length < 6) return false;
                String layout = normalizeType(args[1]);
                return saveLayout("NPC".equals(layout) ? "Npc" : "Mob", args, 2);
            }
            default -> {
                printHelp();
                return false;
            }
        }
    }

    private void printHelp() {
        info("NPC/Mob-Selector Commands:");
        info("  entityselector list [npc|mob]");
        info("  entityselector create <npc|mob> <server|group> <target> <entityType> [displayName] [layout] [yaw] [pitch] [priority] [category] [permission] [region]");
        info("  entityselector create <npc|mob> <id> <world> <x> <y> <z> <server|group> <target> <entityType> [displayName] [layout] [yaw] [pitch] [priority] [category] [permission] [region]");
        info("  entityselector info <id>");
        info("  entityselector render [id|npc|mob]");
        info("  entityselector move <id> <world> <x> <y> <z>");
        info("  entityselector rotate <id> <yaw> <pitch>");
        info("  entityselector enable <id> | entityselector disable <id>");
        info("  entityselector delete <id>");
        info("  entityselector layout <npc|mob> <line1> <line2> <line3> <line4>");
    }

    @Override
    public String getDescription() {
        return "Verwaltet zentrale NPC- und Mob-Server-Selectoren.";
    }

    @Override
    public String getUsage() {
        return "entityselector create <npc|mob> <server|group> <target> <entityType> [displayName] [layout]";
    }
}
