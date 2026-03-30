package de.kallifabio.cloud.commands.core;

import com.google.gson.GsonBuilder;
import de.kallifabio.cloud.commands.BaseCloudCommand;
import de.kallifabio.cloud.setup.SetupValidator;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class SetupCommand extends BaseCloudCommand {

    @Override
    public boolean execute(String sender, String[] args) {
        if (!ensureMaster()) return false;

        boolean fix = args.length > 0 && ("--fix".equalsIgnoreCase(args[0]) || "fix".equalsIgnoreCase(args[0]));
        info("Setup-Check gestartet" + (fix ? " (mit Auto-Fix fuer fehlende Template-Ordner)" : ""));

        Map<String, Object> report = SetupValidator.buildReport(master().getConfigManager(), fix);
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) report.get("summary");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> groups = (List<Map<String, Object>>) report.get("groups");

        info("Setup-Report: Groups=" + summary.get("groupsTotal") +
                ", OK=" + summary.get("groupsOk") +
                ", Mit Issues=" + summary.get("groupsWithIssues") +
                ", Issues=" + summary.get("issuesTotal"));

        for (Map<String, Object> group : groups) {
            String groupName = String.valueOf(group.get("groupName"));
            @SuppressWarnings("unchecked")
            List<String> issues = (List<String>) group.get("issues");
            if (issues.isEmpty()) {
                info("[OK] " + groupName + " | jar=" + group.get("jarFound") +
                        " | template=" + group.get("templateExists") +
                        " | backup=" + group.get("backupTemplateExists"));
            } else {
                warn("[WARN] " + groupName + " hat " + issues.size() + " Problem(e)");
                for (String issue : issues) {
                    warn("  - " + issue);
                }
            }
        }

        saveReport(report);
        return true;
    }

    private void saveReport(Map<String, Object> report) {
        try {
            Files.createDirectories(Path.of("logs"));
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Path out = Path.of("logs", "setup-report-" + ts + ".json");
            String json = new GsonBuilder().setPrettyPrinting().create().toJson(report);
            Files.writeString(out, json, StandardCharsets.UTF_8);
            info("Setup-Report gespeichert: " + out.toAbsolutePath());
        } catch (Exception e) {
            error("Konnte Setup-Report nicht speichern: " + e.getMessage());
        }
    }

    @Override
    public String getDescription() {
        return "Prueft Template/JAR-Setup aller ServerGroups und erstellt einen Report.";
    }

    @Override
    public String getUsage() {
        return "setup [--fix]";
    }
}
