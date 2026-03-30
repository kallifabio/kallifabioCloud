package de.kallifabio.cloud.commands.scaling;

import de.kallifabio.cloud.commands.BaseCloudCommand;
import de.kallifabio.cloud.master.ServerInstance;

import java.util.List;

public class ScaleNowCommand extends BaseCloudCommand {
    @Override
    public boolean execute(String sender, String[] args) {
        if (!ensureMaster()) return false;
        if (args.length < 2) {
            warn("Usage: " + getUsage());
            return false;
        }
        String group = args[0];
        int target;
        try {
            target = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            error("count muss eine Zahl sein.");
            return false;
        }
        if (target < 0) {
            error("count darf nicht negativ sein.");
            return false;
        }

        List<ServerInstance> servers = master().getRunningServers().values().stream()
                .filter(s -> s.groupName.equalsIgnoreCase(group))
                .toList();
        int current = servers.size();
        if (current == target) {
            info("Group " + group + " ist bereits bei " + target + " Servern.");
            return true;
        }

        if (current < target) {
            for (int i = current + 1; i <= target; i++) {
                String name = group + "-" + i;
                if (master().getRunningServers().containsKey(name)) {
                    name = group + "-" + System.currentTimeMillis();
                }
                master().startServer(name, group);
            }
        } else {
            int toStop = current - target;
            for (ServerInstance instance : servers) {
                if (toStop <= 0) break;
                if (instance.playerCount == 0) {
                    master().stopServer(instance.serverName);
                    toStop--;
                }
            }
            if (toStop > 0) {
                warn("Nicht genug leere Server fuer komplettes Scale-Down gefunden.");
            }
        }

        info("ScaleNow fuer " + group + " ausgefuehrt (target=" + target + ").");
        return true;
    }

    @Override
    public String getDescription() {
        return "Skaliert eine Group sofort auf Anzahl Server.";
    }

    @Override
    public String getUsage() {
        return "scalenow <group> <count>";
    }
}
