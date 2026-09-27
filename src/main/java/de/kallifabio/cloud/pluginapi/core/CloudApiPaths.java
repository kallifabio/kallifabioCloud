package de.kallifabio.cloud.pluginapi.core;

public final class CloudApiPaths {

    private CloudApiPaths() {
    }

    public static final String HEALTH = "/api/v1/health";
    public static final String AUTH_SESSION = "/api/v1/auth/session";
    public static final String AUTH_LOGOUT = "/api/v1/auth/logout";
    public static final String AUTH_ME = "/api/v1/auth/me";
    public static final String STATUS = "/api/v1/status";
    public static final String DASHBOARD_OVERVIEW = "/api/v1/dashboard/overview";
    public static final String SYSTEM_DOCTOR = "/api/v1/system/doctor";
    public static final String SERVERS = "/api/v1/servers";
    public static final String SERVERS_START = "/api/v1/servers/start";
    public static final String SERVERS_STOP = "/api/v1/servers/stop";
    public static final String SERVERS_RESTART = "/api/v1/servers/restart";
    public static final String WRAPPERS = "/api/v1/wrappers";
    public static final String WRAPPERS_DRAIN = "/api/v1/wrappers/drain";
    public static final String QUEUE_STATUS = "/api/v1/queue/status";
    public static final String ALERTS = "/api/v1/alerts";
    public static final String ALERTS_CLEAR = "/api/v1/alerts/clear";
    public static final String METRICS = "/api/v1/metrics";
    public static final String METRICS_HISTORY = "/api/v1/metrics/history";
    public static final String CONFIG_GET = "/api/v1/config/get";
    public static final String CONFIG_SET = "/api/v1/config/set";
    public static final String MOTD = "/api/v1/motd";
    public static final String MOTD_UPDATE = "/api/v1/motd/update";
    public static final String SIGNS = "/api/v1/signs";
    public static final String SIGNS_RENDER = "/api/v1/signs/render";
    public static final String SIGNS_UPSERT = "/api/v1/signs/upsert";
    public static final String SIGNS_DELETE = "/api/v1/signs/delete";
    public static final String SIGNS_LAYOUTS = "/api/v1/signs/layouts";
    public static final String ENTITY_SELECTORS = "/api/v1/entity-selectors";
    public static final String ENTITY_SELECTORS_RENDER = "/api/v1/entity-selectors/render";
    public static final String ENTITY_SELECTORS_UPSERT = "/api/v1/entity-selectors/upsert";
    public static final String ENTITY_SELECTORS_DELETE = "/api/v1/entity-selectors/delete";
    public static final String ENTITY_SELECTORS_LAYOUTS = "/api/v1/entity-selectors/layouts";
    public static final String SELECTORS = "/api/v1/selectors";
    public static final String SELECTORS_RENDER = "/api/v1/selectors/render";
    public static final String SELECTORS_UPSERT = "/api/v1/selectors/upsert";
    public static final String SELECTORS_DELETE = "/api/v1/selectors/delete";
    public static final String SELECTORS_LAYOUTS = "/api/v1/selectors/layouts";
    public static final String SELECTORS_TEMPLATES = "/api/v1/selectors/templates";
    public static final String SELECTORS_PREVIEW = "/api/v1/selectors/preview";
    public static final String SELECTORS_BULK = "/api/v1/selectors/bulk";
    public static final String SELECTORS_CLEANUP = "/api/v1/selectors/cleanup";
    public static final String SELECTORS_VERSIONS = "/api/v1/selectors/versions";
    public static final String SELECTORS_ROLLBACK = "/api/v1/selectors/rollback";
    public static final String SELECTORS_HEARTBEAT = "/api/v1/selectors/heartbeat";
}
