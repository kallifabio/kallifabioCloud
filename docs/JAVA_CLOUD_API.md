# KalliCloud Java API (Plugin-SDK) Dokumentation

Diese Dokumentation beschreibt die Java-API-Klassen im Package:

- `de.kallifabio.cloud.pluginapi.*`

Ziel: Minecraft-Plugins (Lobby, Bedwars, Survival, Admin-Tools) schnell auf Basis deines Cloudsystems entwickeln.

---

## 1. Einstieg

Wichtigste Einstiegsklassen:

- `CloudApiConfig`
- `CloudPluginApi`
- `CloudApiFactory`

### Minimalbeispiel

```java
import de.kallifabio.cloud.pluginapi.CloudPluginApi;

try (CloudPluginApi api = CloudPluginApi.create("http://45.82.120.47:8081", "DEIN_API_KEY")) {
    var auth = api.auth().me();
    System.out.println("Role: " + auth.role());
}
```

---

## 2. Konfiguration

### `CloudApiConfig`

```java
import de.kallifabio.cloud.pluginapi.CloudApiConfig;
import de.kallifabio.cloud.pluginapi.CloudPluginApi;
import java.time.Duration;

CloudApiConfig config = CloudApiConfig.builder("http://45.82.120.47:8081", "DEIN_API_KEY")
    .connectTimeout(Duration.ofSeconds(5))
    .readTimeout(Duration.ofSeconds(10))
    .writeTimeout(Duration.ofSeconds(10))
    .userAgent("MyLobbyPlugin/1.0")
    .build();

CloudPluginApi api = new CloudPluginApi(config);
```

### `CloudApiFactory`

```java
import de.kallifabio.cloud.pluginapi.CloudApiFactory;

var api = CloudApiFactory.quick("http://45.82.120.47:8081", "DEIN_API_KEY");
```

---

## 3. Service-Übersicht (CloudPluginApi)

`CloudPluginApi` liefert direkte Service-Zugriffe:

- `auth()`
- `status()`
- `cluster()`
- `servers()`
- `wrappers()`
- `monitoring()`
- `scaling()`
- `players()`
- `permissions()`
- `groups()`
- `templates()`
- `operations()`
- `social()`
- `orchestration()`
- `queue()`
- `alerts()`
- `config()`
- `matchmaking()`
- `lobby()`
- `bedwars()`
- `survival()`
- `admin()`
- `serverLoad()`
- `wrapperCapacity()`
- `routing()`
- `insights()`
- `groupCatalog()`

---

## 4. Häufige Anwendungsfälle

### 4.1 Server starten/stoppen/restarten

```java
import de.kallifabio.cloud.pluginapi.request.ServerStartRequest;
import de.kallifabio.cloud.pluginapi.request.ServerActionRequest;

api.servers().start(new ServerStartRequest("Lobby-2", "Lobby"));
api.servers().restart(new ServerActionRequest("Lobby-2"));
api.servers().stop(new ServerActionRequest("Lobby-2"));
```

### 4.2 Start + warten bis ONLINE

```java
import de.kallifabio.cloud.pluginapi.request.ServerStartRequest;
import java.time.Duration;

var result = api.orchestration().startAndWaitOnline(
    new ServerStartRequest("Bedwars-123", "Bedwars"),
    Duration.ofSeconds(60),
    Duration.ofSeconds(2)
);
```

### 4.3 Player Data / Friends / Party

```java
var data = api.players().getPlayerData(playerUuid);
api.players().saveFriends(playerUuid, java.util.List.of(friendUuid));
api.players().switchParty("party-1", "Lobby-1");
```

### 4.4 Permissions

```java
api.permissions().assignGroup(playerUuid, "vip");
api.permissions().setTempPermission(playerUuid, "cloud.join.bedwars", 3600);
```

### 4.5 Matchmaking / Routing

```java
var target = api.matchmaking().bestServerForGroup("Lobby");
var route = api.routing().routeToBest("Bedwars");
```

### 4.6 Monitoring / Alerts

```java
var metrics = api.monitoring().metricsSnapshot();
var active = api.alerts().active();
boolean critical = api.alerts().hasCritical();
```

---

## 5. Request-DTOs & Builder

Vorhandene DTOs (Auszug):

- `ServerStartRequest`, `ServerActionRequest`
- `WrapperDrainRequest`
- `PlayerDataSaveRequest`, `FriendsSaveRequest`, `PartySaveRequest`, `PartySwitchRequest`
- `PermissionGroupUpsertRequest`, `PermissionAssignRequest`, `TempPermissionRequest`
- `GroupCreateRequest`, `GroupDeleteRequest`, `GroupUpdateRequest`
- `TemplateRollbackRequest`, `ConfigSetRequest`

Builder-Klassen (Auszug):

- `ServerStartRequestBuilder`
- `ServerActionRequestBuilder`
- `WrapperDrainRequestBuilder`
- `PermissionGroupUpsertRequestBuilder`
- `PermissionAssignRequestBuilder`
- `TempPermissionRequestBuilder`
- `PlayerDataSaveRequestBuilder`
- `FriendsSaveRequestBuilder`
- `PartySaveRequestBuilder`
- `PartySwitchRequestBuilder`
- `GroupCreateRequestBuilder`
- `GroupDeleteRequestBuilder`
- `GroupUpdateRequestBuilder`
- `TemplateRollbackRequestBuilder`
- `ConfigSetRequestBuilder`

---

## 6. Async API

`CloudPluginApiAsync` erlaubt Aufrufe über eigenes Executor-Threading:

```java
var async = api.async(java.util.concurrent.Executors.newFixedThreadPool(2));
async.servers().thenAccept(list -> {
    // async handling
});
```

---

## 7. Live WebSocket API

Klassen:

- `CloudLiveWebSocketClient`
- `CloudLiveEventListener`
- `LiveEventEnvelope`
- `LiveEventType`

Beispiel:

```java
var auth = api.auth().me();
var ws = api.liveClient(auth.wsUrl(), auth.wsTicket(), new de.kallifabio.cloud.pluginapi.live.CloudLiveEventListener() {
    @Override
    public void onEvent(com.google.gson.JsonObject event) {
        System.out.println(event);
    }
});
ws.connect();
```

---

## 8. Fehlerbehandlung

Alle HTTP-/API-Fehler werfen `CloudApiException`.

Wichtige Felder:

- `getStatusCode()`
- `getEndpoint()`
- `getResponseBody()`

Beispiel:

```java
try {
    api.servers().restart("Lobby-1");
} catch (de.kallifabio.cloud.pluginapi.CloudApiException ex) {
    getLogger().warning("API Error " + ex.getStatusCode() + " @ " + ex.getEndpoint());
}
```

---

## 9. Best Practices für Bukkit/Spigot/Paper

- API-Calls nicht im Main Thread ausführen (`BukkitScheduler` async nutzen).
- `CloudPluginApi` beim Plugin-Disable sauber schließen (`close()`).
- Bei kritischen Aktionen immer Rolle prüfen (`auth().me().role()`).
- Für wiederholte Pull-Tasks `CloudPollingTask` nutzen.
- Für Lastspitzen Routing/Matchmaking statt fixer Servernamen verwenden.

---

## 10. Relevante Quellpfade

- `src/main/java/de/kallifabio/cloud/pluginapi/`
- `src/main/java/de/kallifabio/cloud/pluginapi/service/`
- `src/main/java/de/kallifabio/cloud/pluginapi/model/`
- `src/main/java/de/kallifabio/cloud/pluginapi/request/`

## Capacity Planner API

```java
var plan = api.operations().capacityPlannerModel();

for (var group : plan.groupPlans()) {
    if (group.canStartNow() && group.recommendedServers() > group.runningServers()) {
        getLogger().info(group.groupName() + " kann jetzt starten. Bester Wrapper: " + group.bestWrapperId());
    }
}
```

Der Planner eignet sich fuer Lobby-, BedWars- oder Minigame-Plugins, die vor einem Serverstart pruefen wollen, ob genug Wrapper-RAM frei ist und ob Maintenance/MaxServers den Start blockieren.

## Readiness API

```java
var readiness = api.status().readiness();

if (!readiness.ready()) {
    getLogger().warning("Cloud ist DEGRADED: " + readiness.diagnosticsState());
}
```

Readiness prueft Master, REST API, WebSocket und mindestens einen gesunden Wrapper. Fuer Monitoring und Plugin-Startlogik ist das aussagekraeftiger als ein reiner Health-Ping.

## Operations Report, Log Search und Audit

```java
var report = api.operations().systemReport();
var errors = api.operations().searchLogsModel("Lobby-1", "ERROR", 100);
var audit = api.operations().recentAudit(50);

getLogger().info("Log Treffer: " + errors.count());
for (String line : audit.lines()) {
    getLogger().fine(line);
}
```

`systemReport()` ist fuer Support-Dumps, externe Monitoring-Integrationen und Dashboard-Exports gedacht. `searchLogsModel(...)` und `recentAudit(...)` ersparen Plugins eigene REST-Query-Logik.

## Platform Operations API

```java
api.operations().recentEvents(100);
api.operations().lifecycle("Lobby-1", 50);
api.operations().incidents(25);
api.operations().backups(25);
api.operations().createBackup("before-update", false);
api.operations().rollingRestart("Lobby", 15);
api.operations().firewallCheck();
```

Diese Methoden bilden die neuen Plattform-Funktionen ab: Event Timeline, Lifecycle-State-Machine, Incident Reports, Backup-Snapshots, Rolling Restarts und Firewall-/Route-Checks. Fuer Restore wird bewusst `restoreBackupToStaging(...)` angeboten, damit Plugins keine Live-Dateien ungeprueft ueberschreiben.
