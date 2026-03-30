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
- Config-Reload mit Backup
- Template-Diff, Snapshot, Rollback

### Scaling / Queue / Routing
- Auto-Scaling Policies pro Group
- Manual Scaling Commands
- Queue mit Prioritaet, Timeout und AFK-Handling
- Load Balancing (Least Loaded, Round Robin, ...)
- Wrapper Draining (kein neues Routing auf draining Wrapper)

### Permissions / Social
- Cross-Server Permission-Sync
- Runtime Permission-Enforcement im Master Join-/Switch-Pfad
- Friend-/Party-Daten persistent im DataStore
- Pending Friend/Party Requests persistent inkl. TTL + Cleanup Job

### API / Dashboard / Security
- REST API (`/api/v1/*`)
- Live WebSocket Stream
- Dashboard mit TailwindCSS
- API Key Auth mit Rollen (`ADMIN`, `VIEWER`)
- Key Rotation Endpoint
- Optional TLS fuer REST + WSS
- Rate Limiting

---

## Architektur

- `Master`
  - Orchestrierung, Routing, Scaling, Monitoring, API
- `Wrapper`
  - Hostet und ueberwacht `Serverprocess` Instanzen
- `CloudDataStore`
  - Persistenz-Layer fuer Spieler, Queue, Party, Permissions, Pending Requests
- `CloudHttpServer` + `LiveWebSocketServer`
  - API, Dashboard, Live-Updates

---

## Voraussetzungen

- Java 20+ (empfohlen 21)
- Maven installiert (fuer lokale Builds)
- Optional: MySQL oder MongoDB

> Hinweis: `mvnw`/`mvnw.cmd` sind vorhanden, nutzen aktuell lokal installiertes Maven.

---

## Build & Start

```bash
mvn -DskipTests compile
mvn test
mvn package
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

### API Port
- `CloudMaster.API.Port` (Default: `8081`)
- Bei Port-Konflikt wird automatisch auf freie Fallback-Ports ausgewichen (Retry aktiv).

Wenn TLS aktiv ist:
- REST laeuft ueber HTTPS
- WebSocket laeuft ueber WSS

---

## Wichtige Commands

### Core / Server
- `help`, `list`, `status`, `reloadconfig`
- `startserver <name> <group>`
- `stopserver <name>`
- `restartserver <name>`
- `forcestopserver <name>`

### Monitoring / Wrapper
- `alerts`, `clearalerts`, `webhooktest`
- `wrapperinfo`
- `wrapperdrain <wrapperId> [on|off|status]`

### Groups / Templates / Scaling
- `groups`, `creategroup`, `deletegroup`
- `groupmaintenance <group> <on|off>`
- `groupwhitelist <group> <uuid1,uuid2,...>`
- `templatediff <group>`
- `templaterollback <group> <version>`
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
- ` /screens`: listet verfuegbare Screens
- ` /exit`: zurueck zum Main-Screen
- ` exit` / `stop`: beendet den Prozess

Aktueller Status:
- Screen-Input wird im Server-Screen an den lokalen `Serverprocess` gesendet.
- Bei nicht lokal verfuegbarem Server erfolgt Warnung + Log-Echo.

Geplante Console-Ops (optional, noch nicht implementiert):
- `/tail <server>`
- `/clear`
- `/screen close <server>`

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

---

## Security Modell

- Rollenbasiert:
  - `VIEWER`: read-only Endpoints
  - `ADMIN`: mutierende Endpoints
- WebSocket Auth:
  - `X-API-Key` Header oder `?token=...`
- Rate Limit aktiv
- Key Rotation ueber API verfuegbar

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
  - Maven installieren und im PATH verfuegbar machen.

- **API 401**
  - Pruefen, ob `X-API-Key` gesetzt ist und gueltiger Key genutzt wird.

- **TLS aktiviert, Server startet nicht**
  - Keystore Pfad/Passwort/Typ pruefen.

- **`Address already in use` beim API-Start**
  - API nutzt Port-Fallback + Retry im Bereich ab konfiguriertem Port.
  - Im Startup-Log den effektiv gebundenen API-Port pruefen.

- **Player kann Group nicht joinen**
  - Runtime-Enforcer prueft `cloud.join` und Group-Join Permissions.

- **`bungeecord.jar` / `spigot.jar` oder Templates fehlen**
  - Beim Serverstart laeuft ein Setup-Preflight:
  - fehlende Template-Ordner werden angelegt
  - fehlende JARs werden aus `templates*`, `templates_backup`, `jars` oder Projektroot nachgezogen

---

## Lizenz

Interne Entwicklung / projektspezifisch.
