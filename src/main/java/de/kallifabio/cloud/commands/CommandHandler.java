package de.kallifabio.cloud.commands;

import de.kallifabio.cloud.commands.core.HelpCommand;
import de.kallifabio.cloud.commands.core.ListCommand;
import de.kallifabio.cloud.commands.core.NetworkDoctorCommand;
import de.kallifabio.cloud.commands.core.ReloadConfigCommand;
import de.kallifabio.cloud.commands.core.RestartStatusCommand;
import de.kallifabio.cloud.commands.core.SetupCommand;
import de.kallifabio.cloud.commands.core.StatusCommand;
import de.kallifabio.cloud.commands.core.SystemDoctorCommand;
import de.kallifabio.cloud.commands.console.ScreenCommand;
import de.kallifabio.cloud.commands.console.ScreenSendCommand;
import de.kallifabio.cloud.commands.console.ScreenTailCommand;
import de.kallifabio.cloud.commands.groups.AutoStartCommand;
import de.kallifabio.cloud.commands.groups.CreateGroupCommand;
import de.kallifabio.cloud.commands.groups.DeleteGroupCommand;
import de.kallifabio.cloud.commands.groups.GroupMaintenanceCommand;
import de.kallifabio.cloud.commands.groups.GroupWhitelistCommand;
import de.kallifabio.cloud.commands.groups.GroupsCommand;
import de.kallifabio.cloud.commands.monitoring.AlertsCommand;
import de.kallifabio.cloud.commands.monitoring.ClearAlertsCommand;
import de.kallifabio.cloud.commands.monitoring.WebhookTestCommand;
import de.kallifabio.cloud.commands.permissions.PermissionAssignCommand;
import de.kallifabio.cloud.commands.permissions.PermissionGroupCreateCommand;
import de.kallifabio.cloud.commands.permissions.PermissionGroupGrantCommand;
import de.kallifabio.cloud.commands.permissions.PermissionProfileCommand;
import de.kallifabio.cloud.commands.permissions.PermissionSyncCommand;
import de.kallifabio.cloud.commands.permissions.PermissionTempCommand;
import de.kallifabio.cloud.commands.queue.QueueCommand;
import de.kallifabio.cloud.commands.scaling.CapacityCommand;
import de.kallifabio.cloud.commands.scaling.ScaleGroupCommand;
import de.kallifabio.cloud.commands.scaling.ScaleNowCommand;
import de.kallifabio.cloud.commands.server.ForceStopServerCommand;
import de.kallifabio.cloud.commands.server.RestartServerCommand;
import de.kallifabio.cloud.commands.server.ServerInfoCommand;
import de.kallifabio.cloud.commands.server.ServerPortCommand;
import de.kallifabio.cloud.commands.server.StartServerCommand;
import de.kallifabio.cloud.commands.server.StopServerCommand;
import de.kallifabio.cloud.commands.selectors.EntitySelectorCommand;
import de.kallifabio.cloud.commands.selectors.SelectorCommand;
import de.kallifabio.cloud.commands.selectors.SignCommand;
import de.kallifabio.cloud.commands.social.FriendAcceptCommand;
import de.kallifabio.cloud.commands.social.FriendAddCommand;
import de.kallifabio.cloud.commands.social.FriendListCommand;
import de.kallifabio.cloud.commands.social.FriendOnlineCommand;
import de.kallifabio.cloud.commands.social.FriendRemoveCommand;
import de.kallifabio.cloud.commands.social.FriendRequestCommand;
import de.kallifabio.cloud.commands.social.PartyAcceptCommand;
import de.kallifabio.cloud.commands.social.PartyAddCommand;
import de.kallifabio.cloud.commands.social.PartyCreateCommand;
import de.kallifabio.cloud.commands.social.PartyInviteCommand;
import de.kallifabio.cloud.commands.social.PartyKickCommand;
import de.kallifabio.cloud.commands.social.PartyLeaderCommand;
import de.kallifabio.cloud.commands.social.PartyListCommand;
import de.kallifabio.cloud.commands.social.PartyRemoveCommand;
import de.kallifabio.cloud.commands.social.PartySwitchCommand;
import de.kallifabio.cloud.commands.templates.TemplateDiffCommand;
import de.kallifabio.cloud.commands.templates.TemplatePullCommand;
import de.kallifabio.cloud.commands.templates.TemplatePushCommand;
import de.kallifabio.cloud.commands.templates.TemplateRollbackCommand;
import de.kallifabio.cloud.commands.wrapper.WrapperDrainCommand;
import de.kallifabio.cloud.commands.wrapper.WrapperInfoCommand;
import de.kallifabio.cloud.libs.console.ConsoleColors;
import de.kallifabio.cloud.libs.console.ConsoleScreenManager;
import de.kallifabio.cloud.libs.logging.CentralLogger;

import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

public class CommandHandler {

    private final Map<String, Command> commands = new HashMap<>();

    public CommandHandler() {
        registerDefaultCommands();
    }

    private void registerDefaultCommands() {
        StartServerCommand start = new StartServerCommand();
        StopServerCommand stop = new StopServerCommand();
        RestartServerCommand restart = new RestartServerCommand();
        ForceStopServerCommand forceStop = new ForceStopServerCommand();
        ListCommand list = new ListCommand();
        StatusCommand status = new StatusCommand();
        ReloadConfigCommand reload = new ReloadConfigCommand();
        RestartStatusCommand restartStatus = new RestartStatusCommand();
        SetupCommand setup = new SetupCommand();
        SystemDoctorCommand systemDoctor = new SystemDoctorCommand();
        NetworkDoctorCommand networkDoctor = new NetworkDoctorCommand();
        HelpCommand help = new HelpCommand(this);
        ScreenCommand screen = new ScreenCommand();
        ScreenTailCommand screenTail = new ScreenTailCommand();
        ScreenSendCommand screenSend = new ScreenSendCommand();
        ServerInfoCommand serverInfo = new ServerInfoCommand();
        ServerPortCommand serverPort = new ServerPortCommand();
        WrapperInfoCommand wrapperInfo = new WrapperInfoCommand();
        WrapperDrainCommand wrapperDrain = new WrapperDrainCommand();
        QueueCommand queue = new QueueCommand();
        AlertsCommand alerts = new AlertsCommand();
        ClearAlertsCommand clearAlerts = new ClearAlertsCommand();
        WebhookTestCommand webhookTest = new WebhookTestCommand();
        GroupsCommand groups = new GroupsCommand();
        CreateGroupCommand createGroup = new CreateGroupCommand();
        DeleteGroupCommand deleteGroup = new DeleteGroupCommand();
        GroupMaintenanceCommand maintenance = new GroupMaintenanceCommand();
        GroupWhitelistCommand whitelist = new GroupWhitelistCommand();
        AutoStartCommand autoStart = new AutoStartCommand();
        TemplateDiffCommand templateDiff = new TemplateDiffCommand();
        TemplatePullCommand templatePull = new TemplatePullCommand();
        TemplatePushCommand templatePush = new TemplatePushCommand();
        TemplateRollbackCommand templateRollback = new TemplateRollbackCommand();
        CapacityCommand capacity = new CapacityCommand();
        ScaleNowCommand scaleNow = new ScaleNowCommand();
        ScaleGroupCommand scaleGroup = new ScaleGroupCommand();
        PermissionGroupCreateCommand permGroupCreate = new PermissionGroupCreateCommand();
        PermissionGroupGrantCommand permGroupGrant = new PermissionGroupGrantCommand();
        PermissionAssignCommand permAssign = new PermissionAssignCommand();
        PermissionTempCommand permTemp = new PermissionTempCommand();
        PermissionSyncCommand permSync = new PermissionSyncCommand();
        PermissionProfileCommand permProfile = new PermissionProfileCommand();
        FriendAddCommand friendAdd = new FriendAddCommand();
        FriendRemoveCommand friendRemove = new FriendRemoveCommand();
        FriendListCommand friendList = new FriendListCommand();
        FriendOnlineCommand friendOnline = new FriendOnlineCommand();
        FriendRequestCommand friendRequest = new FriendRequestCommand();
        FriendAcceptCommand friendAccept = new FriendAcceptCommand();
        PartyCreateCommand partyCreate = new PartyCreateCommand();
        PartyAddCommand partyAdd = new PartyAddCommand();
        PartyKickCommand partyKick = new PartyKickCommand();
        PartySwitchCommand partySwitch = new PartySwitchCommand();
        PartyListCommand partyList = new PartyListCommand();
        PartyLeaderCommand partyLeader = new PartyLeaderCommand();
        PartyRemoveCommand partyRemove = new PartyRemoveCommand();
        PartyInviteCommand partyInvite = new PartyInviteCommand();
        PartyAcceptCommand partyAccept = new PartyAcceptCommand();
        SelectorCommand selector = new SelectorCommand();
        SignCommand sign = new SignCommand();
        EntitySelectorCommand entitySelector = new EntitySelectorCommand();

        registerWithAlias("startserver", start, "start");
        registerWithAlias("stopserver", stop, "stop");
        registerWithAlias("restartserver", restart, "restart");
        registerWithAlias("forcestopserver", forceStop, "fstop");
        registerWithAlias("list", list, "ls");
        registerWithAlias("status", status);
        registerWithAlias("reloadconfig", reload, "reload");
        registerWithAlias("restartstatus", restartStatus, "rstatus");
        registerWithAlias("setup", setup, "setupcheck");
        registerWithAlias("systemdoctor", systemDoctor, "doctor", "sysdoc");
        registerWithAlias("networkdoctor", networkDoctor, "netdoc");
        registerWithAlias("help", help);
        registerWithAlias("screen", screen, "screenopen");
        registerWithAlias("screentail", screenTail, "tail");
        registerWithAlias("screencmd", screenSend, "screencommand", "scmd");
        registerWithAlias("serverinfo", serverInfo);
        registerWithAlias("serverport", serverPort, "aport");
        registerWithAlias("wrapperinfo", wrapperInfo);
        registerWithAlias("wrapperdrain", wrapperDrain);
        registerWithAlias("queue", queue);
        registerWithAlias("alerts", alerts);
        registerWithAlias("clearalerts", clearAlerts);
        registerWithAlias("webhooktest", webhookTest);
        registerWithAlias("groups", groups, "gp");
        registerWithAlias("creategroup", createGroup);
        registerWithAlias("deletegroup", deleteGroup);
        registerWithAlias("groupmaintenance", maintenance);
        registerWithAlias("groupwhitelist", whitelist);
        registerWithAlias("autostart", autoStart);
        registerWithAlias("templatediff", templateDiff);
        registerWithAlias("templatepull", templatePull);
        registerWithAlias("templatepush", templatePush, "templateapply");
        registerWithAlias("templaterollback", templateRollback);
        registerWithAlias("capacity", capacity, "cap", "capacityplanner");
        registerWithAlias("scalenow", scaleNow, "scale");
        registerWithAlias("scalegroup", scaleGroup);
        registerWithAlias("permgroupcreate", permGroupCreate);
        registerWithAlias("permgroupgrant", permGroupGrant);
        registerWithAlias("permassign", permAssign, "passign");
        registerWithAlias("permtemp", permTemp);
        registerWithAlias("permsync", permSync, "psync");
        registerWithAlias("permprofile", permProfile);
        registerWithAlias("friendadd", friendAdd, "fadd");
        registerWithAlias("friendremove", friendRemove, "frem");
        registerWithAlias("friendlist", friendList, "flist");
        registerWithAlias("friendonline", friendOnline);
        registerWithAlias("friendrequest", friendRequest);
        registerWithAlias("friendaccept", friendAccept);
        registerWithAlias("partycreate", partyCreate);
        registerWithAlias("partyadd", partyAdd);
        registerWithAlias("partykick", partyKick);
        registerWithAlias("partyswitch", partySwitch);
        registerWithAlias("partylist", partyList);
        registerWithAlias("partyleader", partyLeader);
        registerWithAlias("partyremove", partyRemove);
        registerWithAlias("partyinvite", partyInvite);
        registerWithAlias("partyaccept", partyAccept);
        registerWithAlias("selector", selector, "selectors", "selectorcenter");
        registerWithAlias("sign", sign, "signs", "cloudsign");
        registerWithAlias("entityselector", entitySelector, "entityselectors", "npcselector", "mobselector", "npc", "mob");

        ConsoleScreenManager.printToTerminal(ConsoleColors.PREFIX +
                ConsoleColors.getCurrentTime() + " Commands registriert: " + commands.size());
    }

    private void registerWithAlias(String commandName, Command command, String... aliases) {
        registerCommand(commandName, command);
        registerCommand(commandName + "command", command);
        for (String alias : aliases) {
            registerCommand(alias, command);
        }
    }

    public void registerCommand(String name, Command command) {
        commands.put(name.toLowerCase(), command);
    }

    public boolean executeCommand(String input, String sender) {
        if (input == null || input.trim().isEmpty()) {
            return false;
        }

        if (input.startsWith("/")) {
            return false;
        }

        String[] args = parseCommandLine(input.trim());
        if (args.length == 0) {
            return false;
        }
        String commandName = args[0].toLowerCase();
        Command command = commands.get(commandName);

        if (command == null) {
            ConsoleScreenManager.printToTerminal(ConsoleColors.RED + "Unbekannter Befehl: " + commandName);
            ConsoleScreenManager.printToTerminal(ConsoleColors.YELLOW + "Tippe 'help' für eine Liste aller Commands");
            return false;
        }

        String[] commandArgs = new String[args.length - 1];
        System.arraycopy(args, 1, commandArgs, 0, commandArgs.length);

        try {
            boolean success = command.execute(sender, commandArgs);
            CentralLogger.audit(sender, commandName + (success ? " OK" : " FAILED"), String.join(" ", commandArgs));
            if (!success && command.getUsage() != null && !command.getUsage().isBlank()) {
                ConsoleScreenManager.printToTerminal(ConsoleColors.YELLOW + "Usage: " + command.getUsage());
            }
            return success;
        } catch (Exception e) {
            ConsoleScreenManager.printToTerminal(ConsoleColors.RED +
                    "Fehler beim Ausfuehren von '" + commandName + "': " + e.getMessage());
            if (command.getUsage() != null && !command.getUsage().isBlank()) {
                ConsoleScreenManager.printToTerminal(ConsoleColors.YELLOW + "Usage: " + command.getUsage());
            }
            CentralLogger.error("Command", "Fehler bei Command '" + commandName + "'", e);
            return false;
        }
    }

    private String[] parseCommandLine(String input) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        char quoteChar = 0;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if ((c == '"' || c == '\'') && (quoteChar == 0 || quoteChar == c)) {
                quoted = !quoted;
                quoteChar = quoted ? c : 0;
                continue;
            }
            if (Character.isWhitespace(c) && !quoted) {
                if (current.length() > 0) {
                    parts.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            current.append(c);
        }
        if (current.length() > 0) {
            parts.add(current.toString());
        }
        return parts.toArray(new String[0]);
    }

    public Map<String, Command> getCommands() {
        return new HashMap<>(commands);
    }

    public boolean hasCommand(String name) {
        return commands.containsKey(name.toLowerCase());
    }

    public void unregisterCommand(String name) {
        commands.remove(name.toLowerCase());
    }
}
