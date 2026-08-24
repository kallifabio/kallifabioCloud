package de.kallifabio.cloud.pluginapi.enforcer;

import de.kallifabio.cloud.pluginapi.CloudPluginApi;
import de.kallifabio.cloud.pluginapi.model.CloudPermissionProfileInfo;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CloudPermissionEnforcerEngine {

    private final CloudPluginApi api;
    private final Map<UUID, CachedProfile> cache = new ConcurrentHashMap<>();
    private final long cacheTtlMs;
    private final boolean failOpen;

    public CloudPermissionEnforcerEngine(CloudPluginApi api) {
        this(api, Duration.ofSeconds(30), false);
    }

    public CloudPermissionEnforcerEngine(CloudPluginApi api, Duration cacheTtl, boolean failOpen) {
        this.api = api;
        this.cacheTtlMs = Math.max(1_000L, cacheTtl == null ? 30_000L : cacheTtl.toMillis());
        this.failOpen = failOpen;
    }

    public CloudPermissionProfileInfo loadProfile(UUID uniqueId) {
        if (uniqueId == null) {
            throw new IllegalArgumentException("uniqueId required");
        }
        CloudPermissionProfileInfo profile = api.permissions().profile(uniqueId.toString());
        cache.put(uniqueId, new CachedProfile(profile, System.currentTimeMillis() + cacheTtlMs));
        return profile;
    }

    public CloudPermissionProfileInfo cachedOrLoad(UUID uniqueId) {
        CachedProfile cached = cache.get(uniqueId);
        long now = System.currentTimeMillis();
        if (cached != null && cached.expiresAt() > now) {
            return cached.profile();
        }
        return loadProfile(uniqueId);
    }

    public CloudPermissionDecision check(CloudPermissionSubject subject, String permission) {
        if (subject == null || subject.uniqueId() == null) {
            return CloudPermissionDecision.deny(permission, "missing subject", null);
        }
        if (permission == null || permission.isBlank()) {
            return CloudPermissionDecision.deny(permission, "missing permission", cachedProfile(subject.uniqueId()));
        }

        try {
            CloudPermissionProfileInfo profile = cachedOrLoad(subject.uniqueId());
            boolean allowed = matches(profile.permissions(), permission);
            return allowed
                    ? CloudPermissionDecision.allow(permission, "cloud permission matched", profile)
                    : CloudPermissionDecision.deny(permission, "permission not granted by cloud profile", profile);
        } catch (RuntimeException ex) {
            CloudPermissionProfileInfo cached = cachedProfile(subject.uniqueId());
            if (cached != null && matches(cached.permissions(), permission)) {
                return CloudPermissionDecision.allow(permission, "cached profile matched after cloud error", cached);
            }
            return failOpen
                    ? CloudPermissionDecision.allow(permission, "cloud unavailable and failOpen=true", cached)
                    : CloudPermissionDecision.deny(permission, "cloud unavailable: " + ex.getMessage(), cached);
        }
    }

    public void invalidate(UUID uniqueId) {
        if (uniqueId != null) {
            cache.remove(uniqueId);
        }
    }

    public void clear() {
        cache.clear();
    }

    private CloudPermissionProfileInfo cachedProfile(UUID uniqueId) {
        CachedProfile cached = cache.get(uniqueId);
        return cached == null ? null : cached.profile();
    }

    private boolean matches(List<String> grantedPermissions, String requestedPermission) {
        if (grantedPermissions == null || requestedPermission == null) {
            return false;
        }
        String requested = requestedPermission.trim().toLowerCase(Locale.ROOT);
        boolean allowed = false;
        for (String grantedRaw : grantedPermissions) {
            if (grantedRaw == null || grantedRaw.isBlank()) {
                continue;
            }
            String granted = grantedRaw.trim().toLowerCase(Locale.ROOT);
            boolean negative = granted.startsWith("-");
            if (negative) {
                granted = granted.substring(1);
            }
            boolean matched = "*".equals(granted) || granted.equals(requested);
            if (!matched && granted.endsWith(".*")) {
                String prefix = granted.substring(0, granted.length() - 1);
                matched = requested.startsWith(prefix);
            }
            if (!matched) {
                continue;
            }
            if (negative) {
                return false;
            }
            allowed = true;
        }
        return allowed;
    }

    private record CachedProfile(CloudPermissionProfileInfo profile, long expiresAt) {
    }
}
