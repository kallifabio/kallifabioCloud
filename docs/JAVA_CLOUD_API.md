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

## 3. Service-Uebersicht (CloudPluginApi)

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
- `signs()`
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
- `fabric()`
- `forge()`
- `neoForge()` / `neoforge()`
- `modLoader(CloudModLoader loader)`

---

## 4. Haeufige Anwendungsfaelle

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

### 4.7 Forge / NeoForge / Fabric Mods

Die API enthaelt dependency-freie Modloader-Fassaden. Sie importieren keine Forge-, NeoForge- oder Fabric-Klassen und koennen dadurch in Mods geshadet oder aus einem kleinen Loader-Adapter heraus verwendet werden.

```java
import de.kallifabio.cloud.pluginapi.CloudPluginApi;
import de.kallifabio.cloud.pluginapi.modloader.CloudModLoader;

try (CloudPluginApi api = CloudPluginApi.create("http://45.82.120.47:8081", "DEIN_PLUGIN_API_KEY")) {
    api.fabric().configureGroup("Survival", 4096, 80);
    api.forge().configureGroup("ModdedForge", 6144, 60);
    api.neoForge().configureGroup("ModdedNeoForge", 6144, 60);

    var bestFabricServer = api.fabric().bestServer("Survival");
    var route = api.modLoader(CloudModLoader.NEOFORGE).routeToBest("ModdedNeoForge");
}
```

Wichtige Klassen:

- `CloudModLoader`
- `ModLoaderCloudFacade`
- `FabricCloudApi`
- `ForgeCloudApi`
- `NeoForgeCloudApi`

Wichtige Methoden:

- `configureGroup(groupName, ramMb, maxPlayers)`
- `setGroupSoftware(groupName)`
- `setJavaArgs(groupName, args)`
- `setStartArgs(groupName, args)`
- `startServer(serverName, groupName)`
- `startGeneratedServer(groupName)`
- `stopServer(serverName)`
- `restartServer(serverName)`
- `onlineServers(groupName)`
- `bestServer(groupName)`
- `routeToBest(groupName)`
- `queueSize(groupName)`
- `permissionProfile(playerUuid)`
- `hasPermission(playerUuid, permission)`

Best Practice fuer Mods:

- REST-Aufrufe niemals im Tick/Main-Thread ausfuehren.
- API-Key als rollenbeschraenkten Plugin-/Mod-Key in der Server-Config halten.
- Bei wiederholten Reads Caches mit TTL nutzen.
- Fuer Join-/Teleport-Entscheidungen `bestServer(...)` oder `routeToBest(...)` statt fixer Servernamen nutzen.

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

`CloudPluginApiAsync` erlaubt Aufrufe ueber eigenes Executor-Threading:

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

Fuer Dashboard-/Tool-Clients, die den echten API-Key nicht dauerhaft im Browser halten sollen, kann eine kurzlebige Session erzeugt werden:

```java
var session = api.auth().createDashboardSession("DEIN_API_KEY");
String sessionToken = session.get("sessionToken").getAsString();
```

Der Session-Token ist fuer Dashboard-/Web-Clients gedacht. Server-Plugins sollten in der Regel weiterhin einen rollenbeschraenkten Plugin-/Proxy-Key aus ihrer sicheren Config verwenden.

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

## 9. Best Practices fuer Bukkit/Spigot/Paper

- API-Calls nicht im Main Thread ausfuehren (`BukkitScheduler` async nutzen).
- `CloudPluginApi` beim Plugin-Disable sauber schliessen (`close()`).
- Bei kritischen Aktionen immer Rolle pruefen (`auth().me().role()`).
- Fuer wiederholte Pull-Tasks `CloudPollingTask` nutzen.
- Fuer Lastspitzen Routing/Matchmaking statt fixer Servernamen verwenden.

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
var doctor = api.operations().systemDoctor();
var doctorModel = api.operations().systemDoctorModel();
if (doctorModel.hasCriticalFindings()) {
    getLogger().warning("Cloud Doctor state: " + doctorModel.state());
}
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
api.operations().recoveryState();
api.operations().clearServerQuarantine("Lobby-1");
api.operations().incidents(25);
api.operations().backups(25);
api.operations().createBackup("before-update", false);
api.operations().rollingRestart("Lobby", 15);
api.operations().firewallCheck();
api.operations().motd();
api.operations().updateMotd(java.util.Map.of(
    "enabled", true,
    "line1", "&bKalliCloud Network",
    "line2", "&7Powered by KalliCloud",
    "fakeSlotsEnabled", false
));
```

Diese Methoden bilden die neuen Plattform-Funktionen ab: Event Timeline, Lifecycle-State-Machine, Recovery-/Quarantine-State, Incident Reports, Backup-Snapshots, Rolling Restarts, Firewall-/Route-Checks und zentrale MOTD-/Slot-Konfiguration. Fuer Restore wird bewusst `restoreBackupToStaging(...)` angeboten, damit Plugins keine Live-Dateien ungeprueft ueberschreiben.

## Cloud Sign / Entity Selector API

Das SignSystem und das NPC/Mob-Selector-System liegen zentral im CloudSystem. Lobby-/Spigot-Plugins sollen Signs, NPCs und Mob-Server-Selectoren nicht mehr lokal verwalten, sondern die Cloud-Registry abrufen und nur noch die Ingame-Darstellung aktualisieren.

```java
// Nur echte Schild-Selectoren
var snapshot = api.signs().snapshot();
var rendered = api.signs().render();
api.signs().createSign("", "Lobby", "Default");

// Nur NPC-/Mob-Selectoren
var entities = api.signs().entitySelectorSnapshot();
var renderedEntities = api.signs().renderEntitySelectors();
api.signs().createNpc("VILLAGER", "&aLobby", "", "Lobby", "Npc");
api.signs().createMob("ZOMBIE", "&cGunGame", "", "GunGame", "Mob");

// Gesamtansicht fuer Clients, die beides rendern
var selectors = api.signs().renderSelectors();
var templates = api.signs().selectorTemplates();

// Preview fuer Dashboard/Setup-Wizards
var preview = api.signs().preview("lobby-spawn-1");

api.signs().upsert(
    "lobby-spawn-1",
    "world",
    10,
    65,
    -4,
    "",
    "Lobby",
    "Default",
    true
);

api.signs().upsertNpc(
    "bedwars-npc",
    "world",
    12,
    65,
    -8,
    180.0,
    0.0,
    "&aBedWars",
    "",
    "BedWars",
    "Default",
    true
);

api.signs().upsertMob(
    "survival-villager",
    "VILLAGER",
    "world",
    15,
    65,
    -8,
    90.0,
    0.0,
    "&aSurvival",
    "",
    "Survival",
    "Default",
    true
);

api.signs().upsertLayout("Default", java.util.List.of(
    "&7&m--- &e{server_name} &7&m---",
    "{status} &8{animation}",
    "&2{players_online} &8/ &4{max_players}",
    "&7&m--- &e{group} &7&m---"
));

// Health/Lifecycle Sync vom LobbySystem zur Cloud
api.signs().heartbeat(
    "lobby-npc-1",
    true,
    "world",
    12,
    65,
    -4,
    180.0,
    0.0
);

// Bulk/Recovery
api.signs().bulk(java.util.Map.of(
    "action", "disable",
    "groupName", "BedWars",
    "selectorType", "NPC"
));
api.signs().cleanup(true);
api.signs().versions();
api.signs().rollback("20260826-184000-upsert_lobby-npc-1.yml");
```

Console-Commands im CloudSystem:

```text
sign create <server|group> <target> [layout] [priority] [category] [permission] [region]
entityselector create <npc|mob> <server|group> <target> <entityType> [displayName] [layout] [yaw] [pitch] [priority] [category] [permission] [region]
selector preview <id>
selector templates
selector cleanup [disable|delete]
selector bulk <enable|disable|layout|permission> groupName=<group> selectorType=<SIGN|NPC|MOB> category=<name>
selector versions
selector rollback <versionFile>
```

IDs werden automatisch vergeben, wenn keine ID gesetzt ist. Ohne explizite `world/x/y/z` wird `locationMode=AUTO` gespeichert; ein Spigot-/LobbySystem-Setup-Command kann daraus die aktuelle Spielerposition automatisch setzen.

Wichtige Platzhalter fuer Layouts: `{id}`, `{world}`, `{selector_type}`, `{entity_type}`, `{display_name}`, `{server_name}`, `{server}`, `{group}`, `{status}`, `{players_online}`, `{max_players}`, `{players}`, `{tps}`, `{port}`, `{wrapper}`, `{animation}`, `{category}`, `{permission}`, `{region}`, `{health}`, `{action}`.

Wichtige Selector-Felder fuer Plugins:
- `category` gruppiert Selector fuer Navigator/NPC-UIs.
- `permission` kann im LobbySystem vor dem Join geprueft werden.
- `queueOnFull` und `partyAware` sagen dem Plugin, ob Queue und Party-Warp statt Einzeljoin genutzt werden sollen.
- `fallbackGroup` erlaubt automatische Alternative, wenn das Ziel offline/voll ist.
- `hologramLines` sind fuer NPC/Mob-Layouts gedacht und duerfen mehr als 4 Zeilen enthalten.
- `skinName`, `skinUrl`, `variant`, `baby` und `glowing` steuern NPC-/Mob-Darstellung.
- `health` aus `/render` zeigt dem Plugin, ob der Selector gespawnt, stale, offline, voll oder wartend ist.

