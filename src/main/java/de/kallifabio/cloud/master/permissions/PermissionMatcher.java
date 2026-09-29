package de.kallifabio.cloud.master.permissions;

import java.util.Collection;
import java.util.Locale;
import java.util.regex.Pattern;

/** Reine, testbare Wildcard-Logik fuer Permission-Nodes (inkl. Verboten mit "-" Praefix). */
public final class PermissionMatcher {

    private PermissionMatcher() {
    }

    /** Exakte Nodes, Wildcards ("cloud.*", "cloud.group.*.join") und Verbote ("-node"). Ein Verbot schlaegt jeden Grant. */
    public static boolean matches(Collection<String> nodes, String required) {
        if (nodes == null || required == null) {
            return false;
        }
        String req = required.toLowerCase(Locale.ROOT);
        boolean granted = false;
        for (String raw : nodes) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String node = raw.trim().toLowerCase(Locale.ROOT);
            boolean negated = node.startsWith("-");
            if (negated) {
                node = node.substring(1);
            }
            if (wildcardMatch(node, req)) {
                if (negated) {
                    return false;
                }
                granted = true;
            }
        }
        return granted;
    }

    public static boolean wildcardMatch(String pattern, String value) {
        if (pattern.equals(value) || pattern.equals("*")) {
            return true;
        }
        if (!pattern.contains("*")) {
            return false;
        }
        StringBuilder regex = new StringBuilder();
        boolean first = true;
        for (String part : pattern.split("\\*", -1)) {
            if (!first) {
                regex.append(".*");
            }
            first = false;
            regex.append(Pattern.quote(part));
        }
        return value.matches(regex.toString());
    }
}
