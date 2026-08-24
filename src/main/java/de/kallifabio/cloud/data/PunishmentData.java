package de.kallifabio.cloud.data;

public class PunishmentData {
    public String id = "";
    public String targetUuid = "";
    public String targetName = "";
    public String actorUuid = "";
    public String actorName = "";
    public String type = "";
    public String reason = "";
    public String proof = "";
    public String notes = "";
    public String address = "";
    public long createdAt;
    public long expiresAt;
    public boolean active = true;

    public boolean isExpired(long now) {
        return expiresAt > 0L && expiresAt <= now;
    }
}
