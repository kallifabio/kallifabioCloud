package de.kallifabio.cloud.commands.selectors;

import java.util.Map;

public class SelectorCommand extends BaseSelectorCommand {

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
                selectors().forEach(this::printSelector);
                return true;
            }
            case "preview", "render" -> {
                if (args.length < 2) return false;
                Map<String, Object> selector = findSelector(args[1]);
                if (selector == null) {
                    error("Selector nicht gefunden: " + args[1]);
                    return false;
                }
                printSelector(selector);
                printRendered(selector);
                return true;
            }
            case "templates", "presets" -> {
                master().getConfigManager().getSelectorTemplates().forEach((name, template) ->
                        info(name + " -> type=" + template.get("selectorType")
                                + " entity=" + template.get("entityType")
                                + " layout=" + template.get("layout")
                                + " category=" + template.get("category")));
                return true;
            }
            case "cleanup" -> {
                boolean disableOnly = args.length < 2 || !"delete".equalsIgnoreCase(args[1]);
                info("Selector-Cleanup: " + master().getConfigManager().cleanupStaleSelectors(disableOnly));
                return true;
            }
            case "bulk" -> {
                if (args.length < 3) return false;
                Map<String, Object> request = new java.util.LinkedHashMap<>();
                request.put("action", args[1]);
                for (int i = 2; i < args.length; i++) {
                    String[] pair = args[i].split("=", 2);
                    if (pair.length == 2) {
                        request.put(pair[0], pair[1]);
                    }
                }
                info("Selector-Bulk: " + master().getConfigManager().bulkUpdateSelectors(request));
                return true;
            }
            case "versions" -> {
                var versions = master().getConfigManager().listSelectorVersions();
                if (versions.isEmpty()) {
                    info("Keine Selector-Versionen gefunden.");
                    return true;
                }
                versions.stream().limit(20).forEach(version -> info("  " + version));
                return true;
            }
            case "rollback" -> {
                if (args.length < 2) return false;
                boolean rolledBack = master().getConfigManager().rollbackSelectorVersion(args[1]);
                if (rolledBack) {
                    info("Selector-Rollback abgeschlossen: " + args[1]);
                } else {
                    error("Selector-Rollback fehlgeschlagen: " + args[1]);
                }
                return rolledBack;
            }
            default -> {
                printHelp();
                return false;
            }
        }
    }

    private void printHelp() {
        info("Selector-Zentrale:");
        info("  selector list");
        info("  selector preview <id>");
        info("  selector templates");
        info("  selector cleanup [disable|delete]");
        info("  selector bulk <enable|disable|layout|permission> groupName=<group> selectorType=<SIGN|NPC|MOB> category=<name> layout=<layout> permission=<node>");
        info("  selector versions");
        info("  selector rollback <versionFile>");
    }

    @Override
    public String getDescription() {
        return "Verwaltet zentrale Selector-Tools wie Preview, Bulk, Cleanup und Rollback.";
    }

    @Override
    public String getUsage() {
        return "selector <list|preview|templates|cleanup|bulk|versions|rollback>";
    }
}
