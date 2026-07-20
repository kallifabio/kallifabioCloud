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
