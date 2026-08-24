package de.kallifabio.cloud.data;

import java.util.ArrayList;
import java.util.List;

public class ClanData {
    public String name = "";
    public String tag = "";
    public String ownerUuid = "";
    public String homeServer = "";
    public boolean friendlyFire = false;
    public long createdAt;
    public int wins;
    public int kills;
    public int points;
    public List<String> admins = new ArrayList<>();
    public List<String> moderators = new ArrayList<>();
    public List<String> members = new ArrayList<>();
}
