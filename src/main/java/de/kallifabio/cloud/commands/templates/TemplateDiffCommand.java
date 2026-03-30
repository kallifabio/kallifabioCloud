package de.kallifabio.cloud.commands.templates;

import de.kallifabio.cloud.commands.BaseCloudCommand;

import java.util.List;

public class TemplateDiffCommand extends BaseCloudCommand {
    @Override
    public boolean execute(String sender, String[] args) {
        if (!ensureMaster()) return false;
        if (args.length < 1) {
            warn("Usage: " + getUsage());
            return false;
        }
        List<String> diff = master().getTemplateManager().calculateDiff(args[0]);
        if (diff.isEmpty()) {
            info("Keine Template-Aenderungen erkannt.");
            return true;
        }
        info("Template-Diff fuer " + args[0] + ":");
        diff.forEach(d -> info(" - " + d));
        return true;
    }

    @Override
    public String getDescription() {
        return "Zeigt Template-Diff gegen Backup.";
    }

    @Override
    public String getUsage() {
        return "templatediff <group>";
    }
}
