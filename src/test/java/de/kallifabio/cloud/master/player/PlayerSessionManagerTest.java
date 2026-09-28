package de.kallifabio.cloud.master.player;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class PlayerSessionManagerTest {

    @Test
    void assignAndClearServerAssignmentsAreCaseInsensitive() {
        PlayerSessionManager sessions = new PlayerSessionManager(null);

        sessions.assignServer("ABC-123", "Lobby-1");
        sessions.assignServer("def-456", "Lobby-1");
        sessions.assignServer("ghi-789", "BedWars-1");

        Assertions.assertEquals("Lobby-1", sessions.getCurrentServer("abc-123"));
        Assertions.assertEquals(3, sessions.getTrackedOnlineCount());
        Assertions.assertEquals(2, sessions.getTrackedPlayerCount("lobby-1"));
        Assertions.assertEquals(1, sessions.getTrackedPlayerCount("bedwars-1"));
        Assertions.assertEquals(2, sessions.getServerPlayerCountsSnapshot().get("Lobby-1"));
        Assertions.assertEquals(1, sessions.getServerPlayerCountsSnapshot().get("BedWars-1"));

        int removed = sessions.clearServerAssignments("lobby-1");

        Assertions.assertEquals(2, removed);
        Assertions.assertNull(sessions.getCurrentServer("ABC-123"));
        Assertions.assertNull(sessions.getCurrentServer("def-456"));
        Assertions.assertEquals("BedWars-1", sessions.getCurrentServer("GHI-789"));
    }

    @Test
    void duplicateConnectionOnlyAppliesWhilePlayerHasCurrentServer() {
        PlayerSessionManager sessions = new PlayerSessionManager(null);

        sessions.touch("player-1");
        Assertions.assertFalse(sessions.isDuplicateConnection("player-1"));

        sessions.assignServer("player-1", "Lobby-1");
        Assertions.assertTrue(sessions.isDuplicateConnection("PLAYER-1"));

        sessions.clearServer("player-1");
        Assertions.assertFalse(sessions.isDuplicateConnection("player-1"));
    }
}
