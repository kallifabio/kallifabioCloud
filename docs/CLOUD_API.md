# KalliCloud REST API Dokumentation

Diese Datei ist als eigene GitHub-Dokumentation für die Cloud-API gedacht.

## Basis

- Base URL (Beispiel): `http://<host>:8081`
- API Prefix: `/api/v1`
- Content-Type: `application/json`
- Auth Header: `X-API-Key: <DEIN_KEY>`

## Rollenmodell

- `VIEWER`: Read-Only Endpoints
- `ADMIN`: Mutierende Endpoints (`POST`, Konfigurationsänderungen, Serveraktionen)

## Authentifizierung

### `POST /api/v1/auth/session`
- Zweck: Erstellt eine kurzlebige Dashboard-Session aus einem API-Key
- Rolle: öffentlich mit gültigem Key
- Hinweis: Das Dashboard speichert danach nur noch den Session-Token in `sessionStorage`, nicht dauerhaft den API-Key.
- Body:
```json
{
  "apiKey": "<KEY>"
}
```
- Antwort:
```json
{
  "authenticated": true,
  "role": "ADMIN",
  "sessionToken": "...",
  "expiresAt": 1774910000000,
  "expiresInMs": 1800000,
  "wsTicket": "...",
  "wsUrl": "ws://<host>:8090/live"
}
```

### `POST /api/v1/auth/logout`
- Zweck: Entfernt die aktuelle Dashboard-Session
- Rolle: `VIEWER`

### `GET /api/v1/auth/me`
- Zweck: Prüft API-Key und liefert Rolle + WS-Infos
- Rolle: `VIEWER` oder `ADMIN`

Beispiel:
```bash
curl -H "X-API-Key: <KEY>" http://<host>:8081/api/v1/auth/me
```

Antwort (Beispiel):
```json
{
  "authenticated": true,
  "role": "ADMIN",
  "wsUrl": "ws://<host>:8090/live",
  "wsTicket": "..."
}
```

### `POST /api/v1/auth/rotate`
- Zweck: Rotiert `admin` oder `dashboard` Key
- Rolle: `ADMIN`
- Body:
```json
{
  "keyName": "dashboard"
}
```

## Health / Status

### `GET /api/v1/health`
- Zweck: Service-Health
- Rolle: öffentlich

### `GET /api/v1/readiness`
- Zweck: Betriebsbereitschaft der Cloud mit Komponentenstatus
- Rolle: öffentlich
- Liefert `ready`, `status`, `components`, `diagnosticsState` und `recommendedHttpStatus`
- Hinweis: Der Endpoint liefert bewusst JSON `200`, damit Dashboard und externe Tools den DEGRADED-Zustand voll auswerten koennen.

### `GET /api/v1/status`
- Zweck: Cloud-Master Status
- Rolle: `VIEWER`

### `GET /api/v1/dashboard/overview`
- Zweck: Aggregierte Dashboard-Daten (Server, Wrapper, Queue, Monitoring)
- Rolle: `VIEWER`

### `GET /api/v1/system/diagnostics`
- Zweck: Systemweite Health-Auswertung mit Score, Issues und Empfehlungen
- Rolle: `VIEWER`

### `GET /api/v1/system/capacity`
- Zweck: Maintenance & Capacity Planner fuer Groups und Wrapper
- Rolle: `VIEWER`
- Liefert unter anderem:
  - freie/gesamte Wrapper-RAM-Kapazitaet
  - startbare Groups
  - `canStartNow` pro Group
  - empfohlenen Server-Count
  - besten Wrapper und RAM-Shortfall
- Beispiel:
```json
{
  "summary": {
    "groups": 2,
    "startableGroups": 1,
    "healthyWrappers": 1,
    "availableWrapperMemoryMb": 2048
  },
  "groups": [
    {
      "groupName": "Lobby",
      "ramMb": 1024,
      "runningServers": 1,
      "recommendedServers": 2,
      "canStartNow": true,
      "bestWrapperId": "Wrapper-abc123"
    }
  ]
}
```

### `GET /api/v1/system/report`
- Zweck: Vollstaendiger Operations-Snapshot fuer Support, Dashboard-Export und externe Tools
- Rolle: `VIEWER`
- Kombiniert `health`, `readiness`, `overview`, `diagnostics`, `capacity`, `setup`, `logStats` und `recentAudit`
- Hinweis: Wenn die API ohne Master-Testkontext laeuft, bleiben masterabhaengige Felder leer und `masterAvailable=false`

### `GET /api/v1/logs/search`
- Zweck: Serverseitige Suche in den aktuellen zentralen Cloud-Logs
- Rolle: `VIEWER`
- Query Parameter:
  - `query`: Suchtext
  - `level`: `all`, `INFO`, `WARN`, `ERROR`, `DEBUG`
  - `limit`: 10 bis 1000

Beispiel:
```bash
curl -H "X-API-Key: <KEY>" "http://<host>:8081/api/v1/logs/search?query=Lobby-1&level=ERROR&limit=100"
```

### `GET /api/v1/audit/recent`
- Zweck: Letzte Audit-Eintraege fuer Admin-Aktionen und Konsolenbefehle
- Rolle: `VIEWER`
- Query Parameter:
  - `limit`: 10 bis 500

### `GET /api/v1/events/recent`
- Zweck: Live Event Timeline fuer Server, Wrapper, Incidents, Backups und Rollouts
- Rolle: `VIEWER`
- Query Parameter: `limit`, `type`, `severity`

### `GET /api/v1/signs`
- Zweck: Zentrale Cloud-Sign-Registry mit echten Schild-Selectoren, Layouts und Animation
- Rolle: `VIEWER`
- Hinweis: Liefert nur `selectorType=SIGN`. Fuer NPCs/Mobs nutze `/api/v1/entity-selectors`, fuer alles zusammen `/api/v1/selectors`.

### `GET /api/v1/signs/render`
- Zweck: Liefert alle aktiven Cloud-Signs inklusive fertig gerenderter Sign-Zeilen mit Live-Serverdaten
- Rolle: `VIEWER`
- Hinweis: Rendert nur `selectorType=SIGN`.
- Platzhalter: `{id}`, `{world}`, `{selector_type}`, `{entity_type}`, `{display_name}`, `{server_name}`, `{server}`, `{group}`, `{status}`, `{players_online}`, `{max_players}`, `{players}`, `{tps}`, `{port}`, `{wrapper}`, `{animation}`

### `POST /api/v1/signs/upsert`
- Zweck: Erstellt oder aktualisiert ein echtes Schild zentral in `Signs.yml`
- Rolle: `ADMIN`
- Hinweis: Erzwingt `selectorType=SIGN`. `id`, `world`, `x`, `y`, `z` sind optional; ohne ID wird automatisch eine `sign-*` ID erzeugt, ohne Position wird `locationMode=AUTO` gespeichert.
- Body:
```json
{
  "id": "lobby-spawn-1",
  "selectorType": "SIGN",
  "entityType": "VILLAGER",
  "displayName": "",
  "world": "world",
  "x": 10,
  "y": 65,
  "z": -4,
  "yaw": 180.0,
  "pitch": 0.0,
  "groupName": "Lobby",
  "layout": "Default",
  "enabled": true
}
```

### `POST /api/v1/signs/delete`
- Zweck: Entfernt einen Cloud-Selector anhand der ID
- Rolle: `ADMIN`
- Hinweis: Loescht anhand der ID, unabhaengig vom gespeicherten Typ.
- Body:
```json
{
  "id": "lobby-spawn-1"
}
```

### `GET /api/v1/entity-selectors`
- Zweck: Zentrale Cloud-Registry fuer NPC- und Mob-Server-Selectoren
- Rolle: `VIEWER`
- Hinweis: Liefert nur `selectorType=NPC` und `selectorType=MOB`.

### `GET /api/v1/entity-selectors/render`
- Zweck: Rendert NPC-/Mob-Hologrammzeilen mit Live-Serverdaten
- Rolle: `VIEWER`

### `POST /api/v1/entity-selectors/upsert`
- Zweck: Erstellt oder aktualisiert einen NPC- oder Mob-Server-Selector
- Rolle: `ADMIN`
- Hinweis: Erlaubt nur `selectorType=NPC` oder `selectorType=MOB`. `id`, `world`, `x`, `y`, `z` sind optional; ohne ID wird automatisch eine `npc-*` oder `mob-*` ID erzeugt, ohne Position wird `locationMode=AUTO` gespeichert.
- Body:
```json
{
  "id": "lobby-npc-1",
  "selectorType": "NPC",
  "entityType": "VILLAGER",
  "displayName": "&aLobby Selector",
  "world": "world",
  "x": 10,
  "y": 65,
  "z": -4,
  "yaw": 180.0,
  "pitch": 0.0,
  "groupName": "Lobby",
  "layout": "Npc",
  "enabled": true
}
```

### `POST /api/v1/entity-selectors/delete`
- Zweck: Entfernt einen NPC-/Mob-Selector anhand der ID
- Rolle: `ADMIN`

### `GET /api/v1/selectors`
- Zweck: Gemeinsame Gesamtansicht fuer Signs, NPCs und Mobs
- Rolle: `VIEWER`

### `GET /api/v1/selectors/render`
- Zweck: Gemeinsames Render-Payload fuer LobbySystem/Plugins, inklusive Signs, NPCs und Mobs
- Rolle: `VIEWER`
- Liefert zusaetzlich `categories`, `health`, `actionDecision`, `renderedLines` und `renderedHologramLines`.
- `health.status` kann unter anderem `OK`, `DISABLED`, `NO_TARGET`, `TARGET_OFFLINE`, `TARGET_FULL`, `MAINTENANCE`, `NOT_SPAWNED` oder `STALE_GROUP` sein.

### `GET /api/v1/selectors/templates`
- Zweck: Liefert zentrale Selector-Presets fuer Dashboard, Setup-Commands und Plugins
- Rolle: `VIEWER`
- Enthaltene Standard-Presets: `LobbyNPC`, `GameMob`, `MaintenanceSign`, `QueueSign`

### `GET /api/v1/selectors/preview`
- Zweck: Rendert eine Preview fuer einen vorhandenen Selector
- Rolle: `VIEWER`
- Query Parameter: `id`

Beispiel:
```bash
curl -H "X-API-Key: <KEY>" "http://<host>:8081/api/v1/selectors/preview?id=lobby-npc-1"
```

### `POST /api/v1/selectors/preview`
- Zweck: Rendert eine Preview fuer einen noch nicht gespeicherten Selector-Draft
- Rolle: `VIEWER`
- Body: gleicher Aufbau wie `/api/v1/selectors/upsert`

### `POST /api/v1/selectors/bulk`
- Zweck: Fuehrt Bulk-Aktionen auf Selector-Zielen aus
- Rolle: `ADMIN`
- Actions: `enable`, `disable`, `layout`, `permission`
- Filter: `groupName`, `serverName`, `selectorType`, `category`
- Body:
```json
{
  "action": "disable",
  "groupName": "BedWars",
  "selectorType": "NPC"
}
```

### `POST /api/v1/selectors/cleanup`
- Zweck: Bereinigt Selector-Ziele, deren Server/Group nicht mehr existiert
- Rolle: `ADMIN`
- Body:
```json
{
  "disableOnly": true
}
```

### `GET /api/v1/selectors/versions`
- Zweck: Listet automatisch angelegte Selector-Versionen aus `config/versions/selectors`
- Rolle: `VIEWER`

### `POST /api/v1/selectors/rollback`
- Zweck: Stellt eine alte Selector-Version wieder her
- Rolle: `ADMIN`
- Body:
```json
{
  "version": "20260826-184000-upsert_lobby-npc-1.yml"
}
```

### `POST /api/v1/selectors/heartbeat`
- Zweck: Lobby-/Spigot-Plugins melden, ob ein Sign/NPC/Mob wirklich gespawnt ist
- Rolle: `OPERATOR`
- Body:
```json
{
  "id": "lobby-npc-1",
  "spawned": true,
  "world": "world",
  "x": 12,
  "y": 65,
  "z": -4,
  "yaw": 180.0,
  "pitch": 0.0
}
```

### `GET /api/v1/signs/layouts`
- Zweck: Listet alle zentralen Selector-/Sign-Layouts aus `SignLayout.yml`
- Rolle: `VIEWER`
- Alias: `GET /api/v1/selectors/layouts` und `GET /api/v1/entity-selectors/layouts`

### `POST /api/v1/signs/layouts`
- Zweck: Erstellt oder aktualisiert ein zentrales Selector-/Sign-Layout
- Rolle: `ADMIN`
- Alias: `POST /api/v1/selectors/layouts` und `POST /api/v1/entity-selectors/layouts`
- Body:
```json
{
  "name": "Default",
  "lines": [
    "&7&m--- &e{server_name} &7&m---",
    "{status} &8{animation}",
    "&2{players_online} &8/ &4{max_players}",
    "&7&m--- &e{group} &7&m---"
  ]
}
```

### `GET /api/v1/lifecycle`
- Zweck: Server Lifecycle State-Machine und Transition-Historie
- Rolle: `VIEWER`
- Query Parameter: `serverName` optional, `limit`

### `GET /api/v1/recovery/state`
- Zweck: Zeigt Recovery-Konfiguration, Restart-Locks, Retry-Zaehler, Failure-Counter und quarantined Server
- Rolle: `VIEWER`

### `GET /api/v1/system/doctor`
- Zweck: Tiefer Config-/Operations-Check fuer Config-Dateien, Ports, Netzwerk-Bindings, Monitoring-/Recovery-Schwellen, AutoStart und ServerGroups
- Rolle: `VIEWER`
- Antwort: `summary` mit State/Counts und `findings[]` mit `severity`, `id`, `message`, `recommendation`
- Hinweis: Entspricht dem Console-Command `systemdoctor [--verbose]` und ist fuer Dashboard, externe Tools und Support-Dumps gedacht.

### `POST /api/v1/recovery/unquarantine`
- Zweck: Setzt Failure-Counter und Quarantine-State eines Servers manuell zurueck
- Rolle: `ADMIN`
- Body:
```json
{
  "serverName": "Lobby-1"
}
```

### `GET /api/v1/incidents`
- Zweck: Listet automatisch erzeugte Incident Reports nach Crash/Failure
- Rolle: `VIEWER`

### `GET /api/v1/backups`
- Zweck: Listet vorhandene Backup-ZIP-Snapshots
- Rolle: `VIEWER`

### `POST /api/v1/backups/create`
- Zweck: Erstellt Backup von `config`, `templates`, `templates_backup`, `templates_test`, `data` und optional `logs`
- Rolle: `OPERATOR`
- Body:
```json
{
  "name": "before-update",
  "includeLogs": false
}
```

### `POST /api/v1/backups/restore-staging`
- Zweck: Entpackt ein Backup sicher nach `restore_staging/...`
- Rolle: `ADMIN`
- Hinweis: Bewusst kein direkter Live-Overwrite, damit Restore vor Anwendung pruefbar bleibt.

### `POST /api/v1/rolling/restart`
- Zweck: Rolling Restart fuer eine Group oder alle laufenden Server
- Rolle: `OPERATOR`
- Body:
```json
{
  "groupName": "Lobby",
  "delaySeconds": 15
}
```

### `GET /api/v1/firewall/check`
- Zweck: TCP-Erreichbarkeit laufender Serverrouten pruefen
- Rolle: `VIEWER`

### `GET /api/v1/motd`
- Zweck: Zentrale Netzwerk-MOTD und Slot-Anzeige fuer Proxy, Plugins und Dashboard abrufen
- Rolle: `VIEWER`
- Liefert `motd`, `maintenance`, `fakeSlots`, `actualOnline` und `actualMax`

### `POST /api/v1/motd/update`
- Zweck: Zentrale Netzwerk-MOTD und optionale Fake-Slots aktualisieren
- Rolle: `OPERATOR`
- Body:
```json
{
  "enabled": true,
  "line1": "&bKalliCloud Network",
  "line2": "&7Powered by KalliCloud",
  "maintenanceLine1": "&cMaintenance",
  "maintenanceLine2": "&7Please try again later",
  "fakeSlotsEnabled": false,
  "fakeSlotsOnline": -1,
  "fakeSlotsMax": -1
}
```

### `GET /openapi.yml`
- Zweck: OpenAPI Einstiegspunkt fuer REST-Tools/Swagger

## Server Management

### `GET /api/v1/servers`
- Zweck: Alle laufenden Server mit Status/Metriken
- Rolle: `VIEWER`

### `POST /api/v1/servers/start`
- Zweck: Startet Server in einer Group
- Rolle: `ADMIN`
- Body:
```json
{
  "serverName": "Lobby-2",
  "groupName": "Lobby"
}
```

### `POST /api/v1/servers/stop`
- Zweck: Stoppt Server
- Rolle: `ADMIN`
- Body:
```json
{
  "serverName": "Lobby-2"
}
```

### `POST /api/v1/servers/restart`
- Zweck: Restart eines Servers
- Rolle: `ADMIN`
- Body:
```json
{
  "serverName": "Lobby-2"
}
```

## Wrapper Management

### `GET /api/v1/wrappers`
- Zweck: Wrapper-Liste
- Rolle: `VIEWER`

### `POST /api/v1/wrappers/drain`
- Zweck: Drain on/off je Wrapper
- Rolle: `ADMIN`
- Body:
```json
{
  "wrapperId": "Wrapper-abc123",
  "draining": true
}
```

## Monitoring / Alerts

### `GET /api/v1/metrics`
- Rolle: `VIEWER`

### `GET /api/v1/metrics/history`
- Rolle: `VIEWER`

### `GET /api/v1/metrics/prometheus`
- Rolle: `VIEWER`

### `GET /api/v1/alerts`
- Rolle: `VIEWER`

### `POST /api/v1/alerts/clear`
- Rolle: `ADMIN`

## Scaling

### `GET /api/v1/scaling/policies`
- Rolle: `VIEWER`

### `POST /api/v1/scaling/trigger`
- Rolle: `ADMIN`

## Queue / Player / Party

### `GET /api/v1/queue/status`
- Rolle: `VIEWER`

### `GET /api/v1/player/data?uuid=<playerUuid>`
- Rolle: `VIEWER`

### `POST /api/v1/player/data`
- Rolle: `ADMIN`

### `GET /api/v1/player/friends?uuid=<playerUuid>`
- Rolle: `VIEWER`

### `POST /api/v1/player/friends`
- Rolle: `ADMIN`

### `GET /api/v1/player/party?partyId=<partyId>`
- Rolle: `VIEWER`

### `POST /api/v1/player/party`
- Rolle: `ADMIN`

### `POST /api/v1/party/switch`
- Rolle: `ADMIN`

## Permissions

### `POST /api/v1/permissions/group`
- Zweck: Permission-Group erstellen/aktualisieren
- Rolle: `ADMIN`

### `POST /api/v1/permissions/assign`
- Zweck: Spieler einer Group zuweisen
- Rolle: `ADMIN`

### `POST /api/v1/permissions/temp`
- Zweck: Temporäre Permission setzen
- Rolle: `ADMIN`

## Groups

### `GET /api/v1/groups`
- Rolle: `VIEWER`

### `POST /api/v1/groups/create`
- Rolle: `ADMIN`

### `POST /api/v1/groups/delete`
- Rolle: `ADMIN`

### `POST /api/v1/groups/update`
- Rolle: `ADMIN`

## Templates

### `GET /api/v1/templates/diff?group=<group>`
- Rolle: `VIEWER`

### `GET /api/v1/templates/versions?group=<group>`
- Rolle: `VIEWER`

### `POST /api/v1/templates/rollback`
- Rolle: `ADMIN`

## Operations / Config

### `GET /api/v1/logs/recent`
- Rolle: `VIEWER`

### `GET /api/v1/setup/report`
- Rolle: `VIEWER`

### `GET /api/v1/config/get?key=<path>`
- Rolle: `VIEWER`

### `POST /api/v1/config/set`
- Rolle: `ADMIN`
- Body:
```json
{
  "key": "CloudMaster.API.Port",
  "value": "8081"
}
```

### `POST /api/v1/webhook/test`
- Rolle: `ADMIN`

## Console API (Dashboard Live Console)

### `GET /api/v1/console/screens`
- Zweck: Alle verfügbaren Screens
- Rolle: `VIEWER`

### `GET /api/v1/console/tail?serverName=<name>&limit=220`
- Zweck: Letzte Log-Zeilen je Screen
- Rolle: `VIEWER`

### `POST /api/v1/console/send`
- Zweck: Command an laufenden Server senden
- Rolle: `ADMIN`
- Body:
```json
{
  "serverName": "Lobby-1",
  "command": "say Hello from API"
}
```

## Fehlercodes

- `400`: Ungültige Parameter / fehlende Felder
- `401`: Nicht authentifiziert (fehlender/ungültiger Key)
- `403`: Rolle nicht ausreichend oder lokal-only Endpoint
- `404`: Ressource nicht gefunden
- `405`: Falsche HTTP-Methode
- `500`: Interner Fehler

## cURL Schnellbeispiele

```bash
# Serverliste
curl -H "X-API-Key: <KEY>" http://<host>:8081/api/v1/servers

# Server starten
curl -X POST -H "X-API-Key: <ADMIN_KEY>" -H "Content-Type: application/json" \
  -d '{"serverName":"Bedwars-1","groupName":"Bedwars"}' \
  http://<host>:8081/api/v1/servers/start

# Console tail
curl -H "X-API-Key: <KEY>" \
  "http://<host>:8081/api/v1/console/tail?serverName=Proxy-1&limit=120"

# Console command
curl -X POST -H "X-API-Key: <ADMIN_KEY>" -H "Content-Type: application/json" \
  -d '{"serverName":"Proxy-1","command":"glist"}' \
  http://<host>:8081/api/v1/console/send
```
