package de.kallifabio.cloud.pluginapi;

import de.kallifabio.cloud.pluginapi.async.CloudPluginApiAsync;
import de.kallifabio.cloud.pluginapi.game.GameModeCloudFacade;
import de.kallifabio.cloud.pluginapi.live.CloudLiveEventListener;
import de.kallifabio.cloud.pluginapi.live.CloudLiveWebSocketClient;
import de.kallifabio.cloud.pluginapi.service.*;

import java.util.concurrent.Executor;

public final class CloudPluginApi implements AutoCloseable {

    private final CloudApiClient client;

    private final AuthService authService;
    private final StatusService statusService;
    private final ClusterService clusterService;
    private final ServerService serverService;
    private final WrapperService wrapperService;
    private final MonitoringApiService monitoringService;
    private final ScalingService scalingService;
    private final PlayerService playerService;
    private final PermissionService permissionService;
    private final GroupService groupService;
    private final TemplateService templateService;
    private final OperationsService operationsService;
    private final SocialService socialService;
    private final ServerOrchestrationService orchestrationService;
    private final QueueService queueService;
    private final AlertService alertService;
    private final ConfigService configService;
    private final MatchmakingService matchmakingService;
    private final LobbyPluginApi lobbyPluginApi;
    private final BedwarsPluginApi bedwarsPluginApi;
    private final SurvivalPluginApi survivalPluginApi;
    private final AdminService adminService;
    private final ServerLoadService serverLoadService;
    private final WrapperCapacityService wrapperCapacityService;
    private final PlayerRoutingService playerRoutingService;
    private final InsightsService insightsService;
    private final GroupCatalogService groupCatalogService;

    public CloudPluginApi(CloudApiConfig config) {
        this.client = new CloudApiClient(config);
        this.authService = new AuthService(client);
        this.statusService = new StatusService(client);
        this.clusterService = new ClusterService(client);
        this.serverService = new ServerService(client);
        this.wrapperService = new WrapperService(client);
        this.monitoringService = new MonitoringApiService(client);
        this.scalingService = new ScalingService(client);
        this.playerService = new PlayerService(client);
        this.permissionService = new PermissionService(client);
        this.groupService = new GroupService(client);
        this.templateService = new TemplateService(client);
        this.operationsService = new OperationsService(client);
        this.socialService = new SocialService(client);
        this.orchestrationService = new ServerOrchestrationService(serverService);
        this.queueService = new QueueService(playerService);
        this.alertService = new AlertService(monitoringService);
        this.configService = new ConfigService(operationsService);
        this.matchmakingService = new MatchmakingService(serverService);
        this.lobbyPluginApi = new LobbyPluginApi(matchmakingService, serverService);
        this.bedwarsPluginApi = new BedwarsPluginApi(matchmakingService, serverService);
        this.survivalPluginApi = new SurvivalPluginApi(matchmakingService, serverService);
        this.adminService = new AdminService(monitoringService, operationsService, scalingService);
        this.serverLoadService = new ServerLoadService(serverService);
        this.wrapperCapacityService = new WrapperCapacityService(wrapperService);
        this.playerRoutingService = new PlayerRoutingService(serverService);
        this.insightsService = new InsightsService(serverService, wrapperService, queueService, alertService);
        this.groupCatalogService = new GroupCatalogService(groupService);
    }

    public static CloudPluginApi create(String baseUrl, String apiKey) {
        return new CloudPluginApi(CloudApiConfig.builder(baseUrl, apiKey).build());
    }

    public CloudPluginApiAsync async(Executor executor) {
        return new CloudPluginApiAsync(this, executor);
    }

    public GameModeCloudFacade gameModes() {
        return new GameModeCloudFacade(this);
    }

    public CloudLiveWebSocketClient liveClient(String wsUrl, String wsToken, CloudLiveEventListener listener) {
        return new CloudLiveWebSocketClient(wsUrl, wsToken, listener);
    }

    public AuthService auth() {
        return authService;
    }

    public StatusService status() {
        return statusService;
    }

    public ClusterService cluster() {
        return clusterService;
    }

    public ServerService servers() {
        return serverService;
    }

    public WrapperService wrappers() {
        return wrapperService;
    }

    public MonitoringApiService monitoring() {
        return monitoringService;
    }

    public ScalingService scaling() {
        return scalingService;
    }

    public PlayerService players() {
        return playerService;
    }

    public PermissionService permissions() {
        return permissionService;
    }

    public GroupService groups() {
        return groupService;
    }

    public TemplateService templates() {
        return templateService;
    }

    public OperationsService operations() {
        return operationsService;
    }

    public SocialService social() {
        return socialService;
    }

    public ServerOrchestrationService orchestration() {
        return orchestrationService;
    }

    public QueueService queue() {
        return queueService;
    }

    public AlertService alerts() {
        return alertService;
    }

    public ConfigService config() {
        return configService;
    }

    public MatchmakingService matchmaking() {
        return matchmakingService;
    }

    public LobbyPluginApi lobby() {
        return lobbyPluginApi;
    }

    public BedwarsPluginApi bedwars() {
        return bedwarsPluginApi;
    }

    public SurvivalPluginApi survival() {
        return survivalPluginApi;
    }

    public AdminService admin() {
        return adminService;
    }

    public ServerLoadService serverLoad() {
        return serverLoadService;
    }

    public WrapperCapacityService wrapperCapacity() {
        return wrapperCapacityService;
    }

    public PlayerRoutingService routing() {
        return playerRoutingService;
    }

    public InsightsService insights() {
        return insightsService;
    }

    public GroupCatalogService groupCatalog() {
        return groupCatalogService;
    }

    @Override
    public void close() {
        client.close();
    }
}
