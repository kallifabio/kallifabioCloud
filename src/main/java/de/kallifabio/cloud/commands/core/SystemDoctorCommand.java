package de.kallifabio.cloud.commands.core;

import com.google.gson.GsonBuilder;
import de.kallifabio.cloud.commands.BaseCloudCommand;
import de.kallifabio.cloud.setup.SystemDoctor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class SystemDoctorCommand extends BaseCloudCommand {

    @Override
    public boolean execute(String sender, String[] args) {
        if (!ensureMaster()) return false;

        boolean verbose = contains(args, "--verbose") || contains(args, "-v");
        info("SystemDoctor gestartet" + (verbose ? " (verbose)" : ""));

        Map<String, Object> report = SystemDoctor.buildReport(master().getConfigManager());
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) report.get("summary");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> findings = (List<Map<String, Object>>) report.get("findings");

        info("SystemDoctor: state=" + summary.get("state")
                + ", critical=" + summary.get("critical")
                + ", warnings=" + summary.get("warnings")
                + ", info=" + summary.get("info")
                + ", findings=" + summary.get("findings"));

        for (Map<String, Object> finding : findings) {
            String severity = String.valueOf(finding.get("severity"));
            if (!verbose && "INFO".equalsIgnoreCase(severity)) {
                continue;
            }
            String line = "[" + severity + "] " + finding.get("id") + " - " + finding.get("message");
            if ("CRITICAL".equalsIgnoreCase(severity)) {
                error(line);
            } else if ("WARNING".equalsIgnoreCase(severity)) {
                warn(line);
            } else {
                info(line);
            }
            if (verbose) {
                info("  Empfehlung: " + finding.get("recommendation"));
            }
        }

        saveReport(report);
        return true;
    }

    private boolean contains(String[] args, String value) {
        for (String arg : args) {
            if (value.equalsIgnoreCase(arg)) {
                return true;
            }
        }
        return false;
    }

    private void saveReport(Map<String, Object> report) {
        try {
            Files.createDirectories(Path.of("logs"));
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Path out = Path.of("logs", "systemdoctor-report-" + ts + ".json");
            String json = new GsonBuilder().setPrettyPrinting().create().toJson(report);
            Files.writeString(out, json, StandardCharsets.UTF_8);
            info("SystemDoctor-Report gespeichert: " + out.toAbsolutePath());
        } catch (Exception e) {
            error("Konnte SystemDoctor-Report nicht speichern: " + e.getMessage());
        }
    }

    @Override
    public String getDescription() {
        return "Prüft Config, Ports, Netzwerk, Monitoring, Recovery und ServerGroups.";
    }

    @Override
    public String getUsage() {
        return "systemdoctor [--verbose]";
    }
}
