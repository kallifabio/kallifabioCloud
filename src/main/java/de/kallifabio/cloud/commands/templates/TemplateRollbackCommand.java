package de.kallifabio.cloud.commands.templates;

import de.kallifabio.cloud.commands.BaseCloudCommand;

public class TemplateRollbackCommand extends BaseCloudCommand {
    @Override
    public boolean execute(String sender, String[] args) {
        if (!ensureMaster()) return false;
        if (args.length < 2) {
            warn("Usage: " + getUsage());
            return false;
        }
        boolean ok = master().getTemplateManager().rollback(args[0], args[1]);
        if (!ok) {
            error("Template-Rollback fehlgeschlagen.");
            return false;
        }
        info("Template-Rollback erfolgreich: " + args[0] + " -> " + args[1]);
        return true;
    }

    @Override
    public String getDescription() {
        return "Rollt Template auf Version zurueck.";
    }

    @Override
    public String getUsage() {
        return "templaterollback <group> <version>";
    }
}
