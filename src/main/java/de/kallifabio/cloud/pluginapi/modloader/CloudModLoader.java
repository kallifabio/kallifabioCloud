package de.kallifabio.cloud.pluginapi.modloader;

/**
 * Modloader values understood by the CloudSystem server-group configuration.
 */
public enum CloudModLoader {
    FORGE("forge", "Forge"),
    NEOFORGE("neoforge", "NeoForge"),
    FABRIC("fabric", "Fabric");

    private final String softwareValue;
    private final String displayName;

    CloudModLoader(String softwareValue, String displayName) {
        this.softwareValue = softwareValue;
        this.displayName = displayName;
    }

    public String softwareValue() {
        return softwareValue;
    }

    public String displayName() {
        return displayName;
    }
}
