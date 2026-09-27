package de.kallifabio.cloud.commands.wrapper;

import de.kallifabio.cloud.commands.BaseCloudCommand;
import de.kallifabio.cloud.master.WrapperConnection;

public class WrapperInfoCommand extends BaseCloudCommand {
    @Override
    public boolean execute(String sender, String[] args) {
        if (!ensureMaster()) return false;
        if (master().getConnectedWrappers().isEmpty()) {
            info("Keine Wrapper verbunden.");
            return true;
        }
        for (WrapperConnection wrapper : master().getConnectedWrappers().values()) {
            info(wrapper.wrapperId + " @" + wrapper.hostname + " route=" + wrapper.routeHost + " CPU=" +
                    String.format("%.1f", wrapper.cpuUsage) + "% RAM=" +
                    wrapper.availableMemory + "/" + wrapper.maxMemory + "MB servers=" +
                    wrapper.activeServers + " healthy=" + wrapper.isHealthy() +
                    " draining=" + master().getLoadBalancerManager().isWrapperDraining(wrapper.wrapperId));
        }
        return true;
    }

    @Override
    public String getDescription() {
        return "Zeigt Wrapper-Status.";
    }

    @Override
    public String getUsage() {
        return "wrapperinfo";
    }
}
