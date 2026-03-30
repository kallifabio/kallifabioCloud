# KalliCloud / CloudSystemTest

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
- Runtime Permission-Enforcer im Wrapper/Serverprocess-Pfad (Cloud-Provider Standard, LuckPerms optional)
- Friend-/Party-Daten persistent im DataStore
- Pending Friend/Party Requests persistent inkl. TTL + Cleanup Job

### API / Dashboard / Security
- REST API (`/api/v1/*`)
- Live WebSocket Stream
- Dashboard mit TailwindCSS
- API Key Auth mit Rollen (`ADMIN`, `VIEWER`)
- Key Rotation Endpoint
- Optional TLS für REST + WSS
- Rate Limiting

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
java -jar target/CloudSystemTest.jar --combined
```

Modi:
- `--master`
- `--wrapper`
- `--combined`

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
- `CloudMaster.Network.GameHost` (Route-Host für Proxy->Backend, pro Wrapper-Host setzen)
- `CloudMaster.Network.ProxyBindHost` (Proxy listener bind, Default: `0.0.0.0`)
- `CloudMaster.Network.EnforceBackendBind` (erzwingt Backend `server-ip`)
- `CloudMaster.Network.BackendBindAddress` (Default: `127.0.0.1`)
- `CloudMaster.Network.ForwardingSecret` (Proxy forwarding secret sync)

### Runtime Permission Enforcer
- `CloudMaster.Permissions.Runtime.Enabled`
- `CloudMaster.Permissions.Runtime.Provider` (`cloud` Standard, optional `luckperms`)
- `CloudMaster.Permissions.Runtime.EnforcePrefixSuffix`
- `CloudMaster.Permissions.Runtime.PermissionPrefixFilter` (Default: `cloud.`)
- `CloudMaster.Permissions.Runtime.CloudSyncCommand` (optional, Platzhalter: `{uuid}`, `{server}`)

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
- `startserver <name> <group>`
- `stopserver <name>`
- `restartserver <name>`
- `forcestopserver <name>`

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
- `scalenow <group> <count>`

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
- `GET /api/v1/auth/me`
- `POST /api/v1/auth/rotate` (ADMIN)
- `GET /api/v1/dashboard/overview`
- `GET /dashboard`

### Infrastruktur
- `GET /api/v1/health`
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

---

### Operations / Setup
- `GET /api/v1/setup/report`
- `POST /api/v1/wrappers/drain`
- `GET /api/v1/logs/recent`

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
