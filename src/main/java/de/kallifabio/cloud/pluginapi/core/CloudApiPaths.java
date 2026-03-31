package de.kallifabio.cloud.pluginapi.core;

public final class CloudApiPaths {

    private CloudApiPaths() {
    }

    public static final String HEALTH = "/api/v1/health";
    public static final String AUTH_ME = "/api/v1/auth/me";
    public static final String STATUS = "/api/v1/status";
    public static final String DASHBOARD_OVERVIEW = "/api/v1/dashboard/overview";
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
}
