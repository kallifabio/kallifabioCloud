package de.kallifabio.cloud.master.permissions;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionMatcherTest {

    @Test
    void exactMatch() {
        assertTrue(PermissionMatcher.matches(List.of("cloud.join"), "cloud.join"));
        assertFalse(PermissionMatcher.matches(List.of("cloud.join"), "cloud.hub"));
    }

    @Test
    void globalWildcard() {
        assertTrue(PermissionMatcher.matches(List.of("*"), "cloud.anything.at.all"));
    }

    @Test
    void prefixWildcard() {
        assertTrue(PermissionMatcher.matches(List.of("cloud.*"), "cloud.join"));
        assertTrue(PermissionMatcher.matches(List.of("cloud.*"), "cloud.group.lobby.join"));
        assertFalse(PermissionMatcher.matches(List.of("cloud.*"), "other.join"));
    }

    @Test
    void middleWildcard() {
        assertTrue(PermissionMatcher.matches(List.of("cloud.group.*.join"), "cloud.group.lobby.join"));
        assertFalse(PermissionMatcher.matches(List.of("cloud.group.*.join"), "cloud.group.lobby.leave"));
    }

    @Test
    void denyBeatsGrant() {
        assertFalse(PermissionMatcher.matches(List.of("cloud.*", "-cloud.join"), "cloud.join"));
        assertFalse(PermissionMatcher.matches(List.of("-cloud.join", "cloud.*"), "cloud.join"));
        assertTrue(PermissionMatcher.matches(List.of("cloud.*", "-cloud.join"), "cloud.hub"));
    }

    @Test
    void caseInsensitiveAndBlankIgnored() {
        assertTrue(PermissionMatcher.matches(java.util.Arrays.asList(" ", null, "Cloud.JOIN"), "cloud.join"));
        assertFalse(PermissionMatcher.matches(List.of(), "cloud.join"));
    }
}
