package de.kallifabio.cloud.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlayerData {
    public final String playerUuid;
    public int coins;
    public Map<String, Integer> stats = new HashMap<>();
    public List<String> permissions = new ArrayList<>();
    public String lastServer = "";
    public long lastSeen = System.currentTimeMillis();

    public PlayerData(String playerUuid) {
        this.playerUuid = playerUuid;
    }
}
