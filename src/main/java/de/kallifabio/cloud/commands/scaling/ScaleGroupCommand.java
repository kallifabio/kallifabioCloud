package de.kallifabio.cloud.commands.scaling;

public class ScaleGroupCommand extends ScaleNowCommand {
    @Override
    public String getDescription() {
        return "Alias fuer scalenow.";
    }

    @Override
    public String getUsage() {
        return "scalegroup <group> <count>";
    }
}
