package de.kallifabio.cloud.commands.permissions;

import de.kallifabio.cloud.commands.BaseCloudCommand;

import java.util.concurrent.TimeUnit;

public class PermissionTempCommand extends BaseCloudCommand {
    @Override
    public boolean execute(String sender, String[] args) {
        if (!ensureMaster()) return false;
        if (args.length < 3) {
            warn("Usage: " + getUsage());
            return false;
        }
        String playerUuid = args[0];
        String permission = args[1];
        long minutes;
        try {
            minutes = Long.parseLong(args[2]);
        } catch (NumberFormatException e) {
            error("minutes muss eine Zahl sein.");
            return false;
        }
        long expiresAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(minutes);
        master().getDataStore().setTempPermission(playerUuid, permission, expiresAt);
        master().syncPermissionsForPlayer(playerUuid);
        info("Temporare Permission gesetzt bis +"+ minutes +"min: " + permission);
        return true;
    }

    @Override
    public String getDescription() {
        return "Setzt eine temporäre Permission.";
    }

    @Override
    public String getUsage() {
        return "permtemp <playerUuid> <permission> <minutes>";
    }
}
