/**
 * Erstellt von Gamer_Kidd_LP | kallifabio
 * am 09.01.2026 um 21:35
 * Projektname: CloudSystemTest
 * Packagename: de.kallifabio.cloud.master.scaling
 */

package de.kallifabio.cloud.master.scaling;

// Supporting Classes
public class ScalingPolicy {

    private final String groupName;
    private int minServers;
    private int maxServers;
    private int scaleUpThreshold;
    private int scaleDownThreshold;
    private boolean predictiveScaling;
    private boolean enabled;

    public ScalingPolicy(String groupName, int minServers, int maxServers,
                         int scaleUpThreshold, int scaleDownThreshold, boolean predictiveScaling) {
        this.groupName = groupName;
        this.minServers = Math.max(0, minServers);
        this.maxServers = Math.max(this.minServers, maxServers);
        this.scaleUpThreshold = clampThreshold(scaleUpThreshold);
        this.scaleDownThreshold = clampThreshold(scaleDownThreshold);
        this.predictiveScaling = predictiveScaling;
        this.enabled = true;
    }

    // Getters for HTTP API
    public String getGroupName() { return groupName; }
    public int getMinServers() { return minServers; }
    public int getMaxServers() { return maxServers; }
    public int getScaleUpThreshold() { return scaleUpThreshold; }
    public int getScaleDownThreshold() { return scaleDownThreshold; }
    public boolean isPredictiveScaling() { return predictiveScaling; }
    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    private int clampThreshold(int threshold) {
        return Math.max(0, Math.min(100, threshold));
    }
}
