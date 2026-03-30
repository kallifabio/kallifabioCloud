package de.kallifabio.cloud.commands.core;

import de.kallifabio.cloud.commands.BaseCloudCommand;
import de.kallifabio.cloud.commands.Command;
import de.kallifabio.cloud.commands.CommandHandler;
import de.kallifabio.cloud.libs.console.ConsoleColors;
import de.kallifabio.cloud.libs.console.ConsoleScreenManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class HelpCommand extends BaseCloudCommand {

    private final CommandHandler commandHandler;

    public HelpCommand(CommandHandler commandHandler) {
        this.commandHandler = commandHandler;
    }

    @Override
    public boolean execute(String sender, String[] args) {
        Map<String, Command> all = commandHandler.getCommands();
        Map<Class<?>, List<String>> namesByClass = new LinkedHashMap<>();
        Map<Class<?>, Command> commandByClass = new LinkedHashMap<>();

        for (Map.Entry<String, Command> entry : all.entrySet()) {
            namesByClass.computeIfAbsent(entry.getValue().getClass(), k -> new ArrayList<>()).add(entry.getKey());
            commandByClass.putIfAbsent(entry.getValue().getClass(), entry.getValue());
        }

        Map<String, List<Class<?>>> byCategory = new HashMap<>();
        for (Class<?> clazz : namesByClass.keySet()) {
            String category = detectCategory(clazz);
            byCategory.computeIfAbsent(category, k -> new ArrayList<>()).add(clazz);
        }

        List<String> categoryOrder = Arrays.asList(
                "Core", "Console", "Server", "Wrapper", "Groups", "Scaling", "Templates",
                "Queue", "Permissions", "Social", "Monitoring", "Other"
        );

        ConsoleScreenManager.printToTerminal(ConsoleColors.CYAN + "+------------------------------------------------------------+");
        ConsoleScreenManager.printToTerminal(ConsoleColors.CYAN + "|                 KalliCloud Command Help                   |");
        ConsoleScreenManager.printToTerminal(ConsoleColors.CYAN + "+------------------------------------------------------------+");

        int commandCount = namesByClass.size();
        ConsoleScreenManager.printToTerminal(ConsoleColors.WHITE +
                " Gesamt: " + commandCount + " Commands | Alias: " + all.size());

        for (String category : categoryOrder) {
            List<Class<?>> classes = byCategory.get(category);
            if (classes == null || classes.isEmpty()) {
                continue;
            }
            classes.sort(Comparator.comparing(c -> canonicalName(namesByClass.get(c))));
            ConsoleScreenManager.printToTerminal(" ");
            ConsoleScreenManager.printToTerminal(ConsoleColors.CYAN + "== " + category + " ==");

            for (Class<?> clazz : classes) {
                Command cmd = commandByClass.get(clazz);
                List<String> names = namesByClass.get(clazz).stream()
                        .map(String::toLowerCase)
                        .distinct()
                        .sorted()
                        .toList();
                String canonical = canonicalName(names);
                String aliasText = names.stream()
                        .filter(n -> !n.equals(canonical))
                        .collect(Collectors.joining(", "));

                ConsoleScreenManager.printToTerminal(ConsoleColors.YELLOW + " " + canonical +
                        ConsoleColors.WHITE + " -> " + cmd.getDescription());
                ConsoleScreenManager.printToTerminal(ConsoleColors.WHITE + "    Usage: " + cmd.getUsage());
                if (!aliasText.isBlank()) {
                    ConsoleScreenManager.printToTerminal(ConsoleColors.WHITE + "    Aliases: " + aliasText);
                }
            }
        }

        ConsoleScreenManager.printToTerminal(" ");
        ConsoleScreenManager.printToTerminal(ConsoleColors.CYAN + "Tipp: helpcommand und <name>command Aliases sind ebenfalls aktiv.");
        return true;
    }

    private String canonicalName(List<String> names) {
        return names.stream()
                .sorted(Comparator.comparingInt(String::length).thenComparing(s -> s))
                .findFirst()
                .orElse("?");
    }

    private String detectCategory(Class<?> clazz) {
        String pkg = clazz.getPackageName().toLowerCase(Locale.ROOT);
        if (pkg.contains(".core")) return "Core";
        if (pkg.contains(".console")) return "Console";
        if (pkg.contains(".server")) return "Server";
        if (pkg.contains(".wrapper")) return "Wrapper";
        if (pkg.contains(".groups")) return "Groups";
        if (pkg.contains(".scaling")) return "Scaling";
        if (pkg.contains(".templates")) return "Templates";
        if (pkg.contains(".queue")) return "Queue";
        if (pkg.contains(".permissions")) return "Permissions";
        if (pkg.contains(".social")) return "Social";
        if (pkg.contains(".monitoring")) return "Monitoring";
        return "Other";
    }

    @Override
    public String getDescription() {
        return "Zeigt alle verfügbaren Commands.";
    }

    @Override
    public String getUsage() {
        return "help";
    }
}
