package de.kallifabio.cloud.commands.wrapper;

import de.kallifabio.cloud.commands.BaseCloudCommand;
import de.kallifabio.cloud.master.ServerInstance;

import java.util.List;

public class WrapperDrainCommand extends BaseCloudCommand {
    @Override
    public boolean execute(String sender, String[] args) {
        if (!ensureMaster()) return false;
        if (args.length < 1) {
            warn("Usage: " + getUsage());
            return false;
        }

        String wrapperId = args[0];
        String action = args.length > 1 ? args[1].toLowerCase() : "on";
        if ("status".equals(action)) {
            info("Wrapper " + wrapperId + " draining=" + master().getLoadBalancerManager().isWrapperDraining(wrapperId));
            return true;
        }

        boolean enable = !"off".equals(action) && !"false".equals(action);
        master().getLoadBalancerManager().setWrapperDraining(wrapperId, enable);
        info("Wrapper " + wrapperId + " draining auf " + enable + " gesetzt.");

        if (!enable) {
            return true;
        }

        List<ServerInstance> hosted = master().getRunningServers().values().stream()
                .filter(s -> s.wrapperId.equalsIgnoreCase(wrapperId))
                .toList();

        if (hosted.isEmpty()) {
            info("Keine Server auf Wrapper " + wrapperId + " gefunden.");
            return true;
        }

        int stopped = 0;
        for (ServerInstance instance : hosted) {
            if (instance.playerCount == 0) {
                master().stopServer(instance.serverName);
                stopped++;
            }
        }

        info("Drain aktiv. Gestoppte leere Server: " + stopped + "/" + hosted.size());
        return true;
    }

    @Override
    public String getDescription() {
        return "Setzt Wrapper-Drain-Status (on/off/status) und stoppt bei drain=on leere Server.";
    }

    @Override
    public String getUsage() {
        return "wrapperdrain <wrapperId> [on|off|status]";
    }
}
