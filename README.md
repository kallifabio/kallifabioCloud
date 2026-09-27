# KalliCloud

Ein modulares Minecraft-Cloudsystem mit:
- Master + Wrapper Architektur
- Auto-Scaling, Queueing, Load-Balancing
- REST API + Live WebSocket
- Web Dashboard
- Persistenz (SQLite / MySQL / MongoDB)
- Monitoring + Alerts + zentrales Logging

## Features

### Core
- Server starten/stoppen/restarten (graceful + force)
- Crash-Detection (Server + Wrapper)
- Auto-Recovery und Player-Transfer bei Ausfall
- Restart-Guards mit Port-Check, Retry/Backoff und Locking gegen Doppel-Neustarts
- Config-Reload mit Backup
- Template-Diff, Snapshot, Rollback

### Scaling / Queue / Routing
- Auto-Scaling Policies pro Group
- Manual Scaling Commands
- Queue mit Priorität, Timeout und AFK-Handling
- Load Balancing (Least Loaded, Round Robin, ...)
- Wrapper Draining (kein neues Routing auf draining Wrapper)

### Permissions / Social
- Cross-Server Permission-Sync
- Runtime Permission-Enforcement im Master Join-/Switch-Pfad
- Cloud-Permissions als Standard-System ohne LuckPerms-Pflicht
- Separates Proxy-/Spigot-Permission-Enforcer-Plugin im externen Plugin-Workspace `D:\Programmieren\IntelliJ\kallifabioCloud-Plugins\CloudPermissionEnforcer`
- REST Permission-Profile und Permission-Check Endpoints für eigene Minecraft-Plugins
- Friend-/Party-Daten persistent im DataStore
- Pending Friend/Party Requests persistent inkl. TTL + Cleanup Job

### API / Dashboard / Security
- REST API (`/api/v1/*`)
- Vollständige OpenAPI-Spezifikation unter `/openapi.yml`
- Live WebSocket Stream
- Dashboard mit TailwindCSS
- Dashboard Unterseiten (`/dashboard/overview`, `/dashboard/servers`, `/dashboard/monitoring`, `/dashboard/operations`, `/dashboard/setup`, `/dashboard/advanced`, `/dashboard/console`)
- Server-Detailseiten (`/dashboard/server/<name>`) mit Live-Metriken, Logs, Quick-Actions und File-Browser
- Wrapper-Detailseiten (`/dashboard/wrapper/<id>`) mit Kapazität, Serverliste und Wrapper-Operations
- Sicherer File-Browser für verwaltete Server-/Template-Dateien
- Live Console im Dashboard (Screen-Auswahl, Tail, Command-Send)
- API Key Auth mit Rollen (`VIEWER`, `OPERATOR`, `ADMIN`, `OWNER`)
- Kurzlebige Dashboard-Sessions statt dauerhaftem API-Key im Browser
- Key Rotation Endpoint
- Optional TLS für REST + WSS
- Rate Limiting
- Zentrale MOTD-/Slots-Konfiguration fuer Proxy, Dashboard und Plugins

---

## Architektur

- `Master`
  - Orchestrierung, Routing, Scaling, Monitoring, API
- `Wrapper`
  - Hostet und überwacht `Serverprocess` Instanzen
- `CloudDataStore`
  - Persistenz-Layer für Spieler, Queue, Party, Permissions, Pending Requests
- `CloudHttpServer` + `LiveWebSocketServer`
  - API, Dashboard, Live-Updates

---

## Voraussetzungen

- Java 20+ (empfohlen 21)
- Optional: MySQL oder MongoDB

> Hinweis: `mvnw`/`mvnw.cmd` laden Maven automatisch aus `distributionUrl` in `.mvn/wrapper/maven-wrapper.properties`.

---

## Build & Start

```bash
./mvnw -DskipTests compile
./mvnw test
./mvnw package
```

Start:

```bash
java -jar target/*.jar --combined
```

Modi:
- `--master`
- `--wrapper`
- `--combined`

---

## Quick Start Single-Host

Ziel: Master, Wrapper, Proxy und Lobby laufen auf **einem** Server.

1. Build + Start:
```bash
./mvnw -DskipTests package
java -jar target/*.jar --combined
```
2. In `config/CloudSystem_Config.yml`:
```yml
CloudMaster:
  Network:
    GameHost: auto
    EnforceBackendBind: true
    BackendBindAddress: auto
CloudWrapper:
  RouteHost: auto
```
3. In der Cloud-Konsole:
```text
reloadconfig
networkdoctor --fix
restartserver Proxy-1
restartserver Lobby-1
```
4. Minecraft-Join:
```text
<deine-server-ip>:25577
```

---

## Quick Start Multi-Host

Ziel: Server 1 = Master/Proxy, Server 2 = zusätzliche Game-Backends.

1. Server 1 starten:
```bash
java -jar target/*.jar --master
java -jar target/*.jar --wrapper
```
2. Server 2 starten:
```bash
java -jar target/*.jar --wrapper
```
3. `CloudMaster.Network.ConnectHost` auf beiden Wrappern auf die Master-IP setzen.
4. Pro Wrapper den erreichbaren Route-Host automatisch erkennen lassen oder fest setzen:
```yml
CloudWrapper:
  RouteHost: auto
```
Optional fest pro Root-Server:
- Server 1: `CloudWrapper.RouteHost: <private-ip-server1>`
- Server 2: `CloudWrapper.RouteHost: <private-ip-server2>`
- Alternativ als Umgebungsvariable: `KALLICLOUD_ROUTE_HOST=<private-ip-des-root-servers>`
5. Backend-Bind für Single-Host und Multi-Host automatisch:
```yml
CloudMaster:
  Network:
    EnforceBackendBind: true
    BackendBindAddress: auto
```
Bei `auto` gilt: Single-Host bindet auf `127.0.0.1`, Multi-Host-Backends binden auf `0.0.0.0`, damit der Proxy sie ueber die Wrapper-Route erreichen kann.
6. Firewall:
- Proxy-Port (z. B. `25577`) öffentlich
- Backend-Ports nur intern/zwischen den Host-IP-Adressen erlauben
7. Danach:
```text
reloadconfig
networkdoctor --fix
```

---

## Konfiguration

Wichtige Dateien:
- `config/CloudSystem_Config.yml`
- `config/ServerGroups.yml`
- `config/Cluster.yml`

### API Keys
- `CloudMaster.API.AdminKey`
- `CloudMaster.API.DashboardKey`

### TLS
- `CloudMaster.API.TLS.Enabled`
- `CloudMaster.API.TLS.KeystorePath`
- `CloudMaster.API.TLS.KeystorePassword`
- `CloudMaster.API.TLS.KeystoreType`
- `CloudMaster.Network.ProxyOnlineMode` (Default: `true`)
- `CloudMaster.Network.TcpPort` (Master TCP, Default: `54555`)
- `CloudMaster.Network.UdpPort` (Master UDP, Default: `54777`)
- `CloudMaster.Network.ConnectHost` (Wrapper Zielhost)
- `CloudMaster.Network.GameHost` (`auto` empfohlen; globaler Fallback für Proxy->Backend-Routen)
- `CloudWrapper.RouteHost` (`auto` empfohlen; pro Root-Server erreichbare Backend-Adresse)
- `CloudMaster.Network.ProxyBindHost` (Proxy listener bind, Default: `0.0.0.0`)
- `CloudMaster.Network.EnforceBackendBind` (erzwingt Backend `server-ip`)
- `CloudMaster.Network.BackendBindAddress` (`auto` empfohlen; Single-Host `127.0.0.1`, Multi-Host `0.0.0.0`)
- `CloudMaster.Network.ForwardingSecret` (Proxy forwarding secret sync)

### Runtime Permission Enforcer
- `CloudMaster.Permissions.Runtime.Enabled`
- `CloudMaster.Permissions.Runtime.Provider` (`cloud` Standard)
- `CloudMaster.Permissions.Runtime.EnforcePrefixSuffix`
- `CloudMaster.Permissions.Runtime.PermissionPrefixFilter` (Default: `cloud.`)
- `CloudMaster.Permissions.Runtime.CloudSyncCommand` (optional, Platzhalter: `{uuid}`, `{server}`)

Separates Plugin:

- Spigot/Paper: `D:\Programmieren\IntelliJ\kallifabioCloud-Plugins\CloudPermissionEnforcer\spigot`
- Bungee/Waterfall: `D:\Programmieren\IntelliJ\kallifabioCloud-Plugins\CloudPermissionEnforcer\proxy-bungee`
- Build-Hinweise: `D:\Programmieren\IntelliJ\kallifabioCloud-Plugins\CloudPermissionEnforcer\README.md`

### Monitoring Thresholds (konfigurierbar)
- `CloudMaster.Monitoring.CPU.Warning`
- `CloudMaster.Monitoring.CPU.Critical`
- `CloudMaster.Monitoring.Memory.Warning`
- `CloudMaster.Monitoring.Memory.Critical`
- `CloudMaster.Monitoring.TPS.Warning`
- `CloudMaster.Monitoring.TPS.Critical`
- `CloudMaster.Monitoring.RequiredConsecutiveBreaches`
- `CloudMaster.Monitoring.Cooldown.WarningMs`
- `CloudMaster.Monitoring.Cooldown.CriticalMs`
- `CloudMaster.Monitoring.Cooldown.InfoMs`

### API Port
- `CloudMaster.API.Port` (Default: `8081`)
- Bei Port-Konflikt wird automatisch auf freie Fallback-Ports ausgewichen (Retry aktiv).

Wenn TLS aktiv ist:
- REST läuft über HTTPS
- WebSocket läuft über WSS

---

## Wichtige Commands

### Core / Server
- `help`, `list`, `status`, `reloadconfig`
- `setup [--fix]`
- `systemdoctor [--verbose]`
- `startserver <name> <group>`
- `stopserver <name>`
- `restartserver <name>`
- `forcestopserver <name>`

### Diagnose
- `setup [--fix]`: prüft Templates, JARs und Modloader-Startdateien pro ServerGroup
- `networkdoctor [--fix] [--prune]`: prüft Proxy-/Backend-Routing, Forwarding und stale Routen
- `systemdoctor [--verbose]`: prüft Config-Dateien, Ports, Netzwerk-Bindings, Monitoring-/Recovery-Schwellen, AutoStart und ServerGroups; schreibt `logs/systemdoctor-report-*.json`

### Monitoring / Wrapper
- `alerts`, `clearalerts`, `webhooktest`
- `wrapperinfo`
- `wrapperdrain <wrapperId> [on|off|status]`
- `restartstatus` / `rstatus` (zeigt aktive Restart-Locks + Retry-Zähler)

### Groups / Templates / Scaling
- `groups`, `creategroup`, `deletegroup`
- `groupmaintenance <group> <on|off>`
- `groupwhitelist <group> <uuid1,uuid2,...>`
- `templatediff <group>`
- `templaterollback <group> <version>`
- `templatepull <serverName>`
- `templatepush <serverName> [--clear] [--restart]`
- `capacity [group]` / `cap` (RAM-Headroom, Startbarkeit, Empfehlung pro Group)
- `scalenow <group> <count>`

### Selector-System
- `sign list`
- `sign create <server|group> <target> [layout] [priority] [category] [permission] [region]` (Auto-ID, Auto-Location)
- `sign create <id> <world> <x> <y> <z> <server|group> <target> [layout] [priority]` (explizite Position)
- `sign render [id]`, `sign move <id> <world> <x> <y> <z>`, `sign delete <id>`
- `entityselector list [npc|mob]`
- `entityselector create <npc|mob> <server|group> <target> <entityType> [displayName] [layout] [yaw] [pitch] [priority] [category] [permission] [region]` (Auto-ID, Auto-Location)
- `entityselector create <npc|mob> <id> <world> <x> <y> <z> <server|group> <target> <entityType> [displayName] [layout] [yaw] [pitch] [priority]` (explizite Position)
- `entityselector render [id|npc|mob]`, `entityselector rotate <id> <yaw> <pitch>`, `entityselector delete <id>`
- `selector preview <id>` zeigt Sign-Zeilen, NPC/Mob-Name, Hologramm, Target und Health live an
- `selector templates` listet Presets wie `LobbyNPC`, `GameMob`, `MaintenanceSign`, `QueueSign`
- `selector cleanup [disable|delete]` markiert oder entfernt stale Selector-Ziele
- `selector bulk <enable|disable|layout|permission> groupName=<group> ...` fuer Gruppen-Aktionen
- `selector versions` und `selector rollback <versionFile>` fuer Rollback/Versionierung
- Aliases: `signs`, `cloudsign`, `selectorcenter`, `npcselector`, `mobselector`, `npc`, `mob`

Das CloudSystem ist die zentrale Quelle fuer Signs, NPCs und Mob-Selectoren. Lobby-/Spigot-Plugins sollen die Registry per REST/Java-API abrufen, lokal spawnen/rendern und per Heartbeat melden, ob ein Selector wirklich gespawnt ist.

Wichtige Selector-Felder:
- `selectorType`: `SIGN`, `NPC` oder `MOB`
- `targetType`: ueber `serverName` oder `groupName`
- `category`: z. B. `Lobby`, `BedWars`, `Survival`, `Event`
- `permission`: z. B. `cloud.selector.vip`
- `region`: z. B. `GLOBAL`, `EU`, `US`
- `queueOnFull`, `partyAware`, `fallbackGroup`
- `hologramLines` fuer NPC/Mob-Hologramme mit mehr als 4 Zeilen
- `skinName`, `skinUrl`, `variant`, `baby`, `glowing`

### Server Software Support

KalliCloud kann pro ServerGroup unterschiedliche Minecraft-Server-Software starten:

- `proxy`: BungeeCord, Waterfall, Velocity-kompatible Proxy-JARs
- `paper`: Spigot, Paper, Purpur
- `fabric`: Fabric Server Launcher
- `forge`: Forge Server, inkl. moderner `libraries/.../unix_args.txt` Starts
- `neoforge`: NeoForge Server, inkl. moderner `libraries/.../unix_args.txt` Starts
- `vanilla`: Vanilla Minecraft Server

Beispiel `config/ServerGroups.yml`:

```yml
ServerGroup:
  Survival:
    Software: fabric
    Ram: 4096
    MaxPlayers: 100
    Dynamic: false
    JavaArgs:
      - -Dfile.encoding=UTF-8
    StartArgs:
      - nogui

  Modded:
    Software: neoforge
    Ram: 6144
    MaxPlayers: 80
    Dynamic: false
    StartArgs:
      - nogui
```

JAR-/Template-Erkennung:

- Proxy: `bungeecord.jar`, `waterfall.jar`, `velocity.jar`, `proxy.jar`
- Paper/Spigot/Purpur: `spigot.jar`, `paper.jar`, `purpur.jar`, `server.jar`
- Fabric: `fabric-server-launch.jar`, `fabric-server.jar`, `fabric.jar`, `server.jar`
- Forge: `forge.jar`, `forge-server.jar`, `minecraftforge.jar`, `server.jar`
- NeoForge: `neoforge.jar`, `neoforge-server.jar`, `forge.jar`, `server.jar`
- Vanilla: `server.jar`, `minecraft-server.jar`, `vanilla.jar`

Die Suche ist case-insensitive und akzeptiert auch Versionsnamen wie `fabric-server-launch-1.21.1.jar`, `forge-1.20.1.jar` oder `neoforge-21.1.0.jar`. Moderne Forge/NeoForge-Installationen mit `libraries/.../unix_args.txt` werden ohne Umbenennen gestartet.

### Forge / NeoForge / Fabric Java API

Die Java Cloud API enthaelt zusaetzlich modloader-freundliche Fassaden. Diese Klassen importieren keine Forge-, NeoForge- oder Fabric-Klassen und koennen deshalb in Mods geshadet oder ueber einen kleinen Loader-Adapter genutzt werden.

```java
try (var api = de.kallifabio.cloud.pluginapi.CloudPluginApi.create(
        "http://45.82.120.47:8081",
        "DEIN_PLUGIN_API_KEY"
)) {
    api.fabric().configureGroup("Survival", 4096, 80);
    api.forge().configureGroup("ModdedForge", 6144, 60);
    api.neoForge().configureGroup("ModdedNeoForge", 6144, 60);

    api.fabric().bestServer("Survival").ifPresent(server -> {
        System.out.println("Best Survival server: " + server.serverName());
    });
}
```

Wichtige Einstiege:

- `api.fabric()`
- `api.forge()`
- `api.neoForge()` / `api.neoforge()`
- `api.modLoader(CloudModLoader.FABRIC|FORGE|NEOFORGE)`

Funktionen der Modloader-Fassaden:

- Gruppe auf passende `Software` setzen
- RAM/MaxPlayers/StartArgs konfigurieren
- Server starten, stoppen und restarten
- beste Online-Instanz einer Gruppe finden
- Routing-Entscheidung abfragen
- Queue-Groesse lesen
- Cloud-Permissions eines Spielers pruefen

### Permissions
- `permgroupcreate`, `permgroupgrant`
- `permassign <playerUuid> <group>`
- `permtemp <playerUuid> <permission> <minutes>`
- `permsync <playerUuid>`
- `permprofile <playerUuid>`

### Social
- `friendrequest <fromUuid> <toUuid>`
- `friendaccept <playerUuid>`
- `friendadd`, `friendremove`, `friendlist`, `friendonline`
- `partyinvite <partyId> <playerUuid>`
- `partyaccept <playerUuid>`
- `partycreate`, `partyadd`, `partykick`, `partyswitch`, `partylist`, `partyleader`, `partyremove`

---

## Console Bedienung

- ` /switch <screen>`: wechselt auf einen Screen (z. B. `Proxy-1`)
- ` /screens`: listet verfügbare Screens
- ` /exit`: zurück zum Main-Screen
- ` exit` / `stop`: beendet den Prozess
- `screen <list|main|server>`: Screen-Navigation
- `screentail <server> [lines]`: letzte Zeilen eines Server-Screens
- `screencmd <server> <command...>`: Befehl an laufenden Server senden

Aktueller Status:
- Screen-Input wird im Server-Screen an den lokalen `Serverprocess` gesendet.
- Bei nicht lokal verfügbarem Server erfolgt Warnung + Log-Echo.

---

## REST API (Auszug)

### Auth / Dashboard
- `POST /api/v1/auth/session`
- `POST /api/v1/auth/logout`
- `GET /api/v1/auth/me`
- `POST /api/v1/auth/rotate` (ADMIN)
- `GET /api/v1/dashboard/overview`
- `GET /dashboard`

### Infrastruktur
- `GET /api/v1/health`
- `GET /api/v1/readiness`
- `GET /api/v1/status`
- `GET /api/v1/servers`
- `POST /api/v1/servers/start`
- `POST /api/v1/servers/stop`
- `POST /api/v1/servers/restart`
- `GET /api/v1/wrappers`

### Monitoring
- `GET /api/v1/metrics`
- `GET /api/v1/metrics/history`
- `GET /api/v1/metrics/prometheus`
- `GET /api/v1/alerts`
- `POST /api/v1/alerts/clear`
- `GET /api/v1/system/diagnostics`
- `GET /api/v1/system/doctor`
- `GET /api/v1/system/capacity`
- `GET /api/v1/system/report`
- `GET /api/v1/events/recent`
- `GET /api/v1/signs`
- `GET /api/v1/signs/render`
- `POST /api/v1/signs/upsert`
- `POST /api/v1/signs/delete`
- `GET/POST /api/v1/signs/layouts`
- `GET /api/v1/entity-selectors`
- `GET /api/v1/entity-selectors/render`
- `POST /api/v1/entity-selectors/upsert`
- `POST /api/v1/entity-selectors/delete`
- `GET/POST /api/v1/entity-selectors/layouts`
- `GET /api/v1/selectors`
- `GET /api/v1/selectors/render`
- `POST /api/v1/selectors/upsert`
- `POST /api/v1/selectors/delete`
- `GET/POST /api/v1/selectors/layouts`
- `GET /api/v1/selectors/templates`
- `GET|POST /api/v1/selectors/preview`
- `POST /api/v1/selectors/bulk`
- `POST /api/v1/selectors/cleanup`
- `GET /api/v1/selectors/versions`
- `POST /api/v1/selectors/rollback`
- `POST /api/v1/selectors/heartbeat`
- `GET /api/v1/lifecycle`
- `GET /api/v1/recovery/state`
- `POST /api/v1/recovery/unquarantine`
- `GET /api/v1/incidents`
- `GET /api/v1/backups`
- `POST /api/v1/backups/create`
- `POST /api/v1/backups/restore-staging`
- `POST /api/v1/rolling/restart`
- `GET /api/v1/firewall/check`
- `GET /api/v1/motd`
- `POST /api/v1/motd/update`
- `GET /openapi.yml`

---

### Permissions / Files
- `GET /api/v1/permissions/profile?playerUuid=<uuid>`
- `GET /api/v1/permissions/check?playerUuid=<uuid>&permission=<node>`
- `POST /api/v1/permissions/group`
- `POST /api/v1/permissions/assign`
- `POST /api/v1/permissions/temp`
- `GET /api/v1/files/list?scope=server|template|wrapper&...`
- `GET /api/v1/files/read?scope=server|template|wrapper&...`
- `POST /api/v1/files/write`
- `POST /api/v1/files/mkdir`
- `POST /api/v1/files/delete`

Der File-Browser ist auf verwaltete Cloud-Pfade begrenzt und blockiert Path-Traversal.

---

### Operations / Setup
- `GET /api/v1/setup/report`
- `POST /api/v1/wrappers/drain`
- `GET /api/v1/logs/recent`
- `GET /api/v1/logs/search?query=<text>&level=<level>&limit=<n>`
- `GET /api/v1/audit/recent?limit=<n>`
- `GET /api/v1/console/screens`
- `GET /api/v1/console/tail?serverName=<name>&limit=<n>`
- `POST /api/v1/console/send`

---

## API Dokumentation

- Vollständige REST-API-Doku für GitHub:
  - [docs/CLOUD_API.md](docs/CLOUD_API.md)
- Java Plugin-SDK Doku für GitHub:
  - [docs/JAVA_CLOUD_API.md](docs/JAVA_CLOUD_API.md)

---

## Security Modell

- Rollenbasiert:
  - `VIEWER`: read-only Endpoints
  - `ADMIN`: mutierende Endpoints
- WebSocket Auth:
  - `X-API-Key` Header oder `?token=...`
- Rate Limit aktiv
- Key Rotation über API verfügbar

---

## CI

GitHub Actions Workflow:
- Smoke Compile
- Test Run

Datei:
- `.github/workflows/ci.yml`

---

## Troubleshooting

- **Smoke-Compile Fehler `illegal character: '\ufeff'`**
  - Ursache: UTF-8 BOM am Dateianfang (z. B. Java/YAML).
  - Fix: Datei ohne BOM speichern (UTF-8 no BOM).

- **`mvn` nicht gefunden**
  - Maven installieren und im PATH verfügbar machen.

- **API 401**
  - Prüfen, ob `X-API-Key` gesetzt ist und gültiger Key genutzt wird.

- **TLS aktiviert, Server startet nicht**
  - Keystore Pfad/Passwort/Typ prüfen.

- **`Address already in use` beim API-Start**
  - API nutzt Port-Fallback + Retry im Bereich ab konfiguriertem Port.
  - Im Startup-Log den effektiv gebundenen API-Port prüfen.

- **Proxy-Startfehler `Could not bind to host /0.0.0.0:25577`**
  - Ursache ist meist ein hängender Altprozess oder ein paralleler Neustart.
  - Nutze `restartstatus` zur Live-Diagnose von Restart-Locks/Retry-Zählern.
  - Das System nutzt jetzt Restart-Guards (Port-Check + Backoff + Locking), um Bind-Races zu minimieren.

- **Player kann Group nicht joinen**
  - Runtime-Enforcer prüft `cloud.join` und Group-Join Permissions.

- **`bungeecord.jar` / `spigot.jar` oder Templates fehlen**
  - Beim Serverstart läuft ein Setup-Preflight:
  - fehlende Template-Ordner werden angelegt
  - fehlende JARs werden aus `templates*`, `templates_backup`, `jars` oder Projektroot nachgezogen

---

## Lizenz

Interne Entwicklung / projektspezifisch.



