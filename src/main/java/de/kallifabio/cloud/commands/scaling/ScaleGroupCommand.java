package de.kallifabio.cloud.commands.scaling;

public class ScaleGroupCommand extends ScaleNowCommand {
    @Override
    public String getDescription() {
        return "Alias für scalenow.";
    }

    @Override
    public String getUsage() {
        return "scalegroup <group> <count>";
    }
}
