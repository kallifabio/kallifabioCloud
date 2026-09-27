package de.kallifabio.cloud.commands.selectors;

import java.util.Map;

public class SignCommand extends BaseSelectorCommand {

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
                selectors("SIGN").forEach(this::printSelector);
                return true;
            }
            case "info" -> {
                if (args.length < 2) return false;
                Map<String, Object> sign = findSelector(args[1], "SIGN");
                if (sign == null) {
                    error("Sign nicht gefunden: " + args[1]);
                    return false;
                }
                printSelector(sign);
                printRendered(sign);
                return true;
            }
            case "render" -> {
                if (args.length >= 2) {
                    Map<String, Object> sign = findSelector(args[1], "SIGN");
                    if (sign == null) {
                        error("Sign nicht gefunden: " + args[1]);
                        return false;
                    }
                    printRendered(sign);
                    return true;
                }
                selectors("SIGN").forEach(this::printRendered);
                return true;
            }
            case "create", "set" -> {
                Map<String, Object> sign;
                if (args.length >= 3 && isTargetType(args[1])) {
                    sign = autoSelector(
                            "SIGN",
                            args[1],
                            args[2],
                            args.length > 3 ? args[3] : "Default",
                            optionalInt(args, 4, 0, "priority")
                    );
                    applyOptionalMetadata(sign, args, 5, 6, 7);
                } else {
                    if (args.length < 8) return false;
                    String targetType = args[6];
                    if (!isTargetType(targetType)) {
                        error("Target-Typ muss 'server' oder 'group' sein.");
                        return false;
                    }
                    sign = baseSelector(
                            args[1],
                            "SIGN",
                            args[2],
                            parseInt(args[3], "x"),
                            parseInt(args[4], "y"),
                            parseInt(args[5], "z"),
                            targetType,
                            args[7],
                            args.length > 8 ? args[8] : "Default",
                            optionalInt(args, 9, 0, "priority")
                    );
                    applyOptionalMetadata(sign, args, 10, 11, 12);
                }
                Map<String, Object> saved = master().getConfigManager().upsertCloudSign(sign);
                info("Sign gespeichert: " + saved.get("id"));
                printSelector(saved);
                return true;
            }
            case "move" -> {
                if (args.length < 6) return false;
                return moveSelector(args[1], args[2], parseInt(args[3], "x"), parseInt(args[4], "y"),
                        parseInt(args[5], "z"), "SIGN");
            }
            case "enable" -> {
                if (args.length < 2) return false;
                return setEnabled(args[1], true, "SIGN");
            }
            case "disable" -> {
                if (args.length < 2) return false;
                return setEnabled(args[1], false, "SIGN");
            }
            case "delete", "remove" -> {
                if (args.length < 2) return false;
                return deleteSelector(args[1], "SIGN");
            }
            case "layout" -> {
                if (args.length < 6) return false;
                return saveLayout(args[1], args, 2);
            }
            default -> {
                printHelp();
                return false;
            }
        }
    }

    private void printHelp() {
        info("Sign-System Commands:");
        info("  sign list");
        info("  sign create <server|group> <target> [layout] [priority] [category] [permission] [region]");
        info("  sign create <id> <world> <x> <y> <z> <server|group> <target> [layout] [priority] [category] [permission] [region]");
        info("  sign info <id>");
        info("  sign render [id]");
        info("  sign move <id> <world> <x> <y> <z>");
        info("  sign enable <id> | sign disable <id>");
        info("  sign delete <id>");
        info("  sign layout <name> <line1> <line2> <line3> <line4>");
    }

    @Override
    public String getDescription() {
        return "Verwaltet zentrale Cloud-Schilder fuer Server-Selectoren.";
    }

    @Override
    public String getUsage() {
        return "sign create <server|group> <target> [layout] [priority] [category] [permission] [region]";
    }
}
