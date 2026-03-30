package de.kallifabio.cloud.commands.monitoring;

import de.kallifabio.cloud.commands.BaseCloudCommand;

import java.util.LinkedHashMap;
import java.util.Map;

public class WebhookTestCommand extends BaseCloudCommand {
    @Override
    public boolean execute(String sender, String[] args) {
        if (!ensureMaster()) return false;
        String message = args.length == 0 ? "manual-test" : String.join(" ", args);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("message", message);
        details.put("timestamp", System.currentTimeMillis());
        details.put("sender", sender);
        master().getMonitoringService().publishEvent("WEBHOOK_TEST", details);
        info("Webhook-Test versendet.");
        return true;
    }

    @Override
    public String getDescription() {
        return "Sendet einen Test-Event an den Alert-Webhook.";
    }

    @Override
    public String getUsage() {
        return "webhooktest [message]";
    }
}
