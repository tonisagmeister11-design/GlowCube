# GlowCube-Agent-Plugin

Server-Plugin fuer **Paper 1.21.11**. Damit koennen Spieler mit dem
GlowCube-Client auf deinem Server Agenten losschicken - genau wie in der
Einzelspielerwelt: NPCs in Spielergestalt, die Erz, Stein oder Holz abbauen,
mit Tempo, Abbau-Tempo und X-Ray, und die Beute beim Zurueckschicken
zuwerfen.

## Einbauen

1. `glowcube-agent-plugin.jar` aus dem Release herunterladen
2. In den `plugins`-Ordner des Paper-Servers legen
3. Server neu starten

Spieler brauchen nur den GlowCube-Client (1.21.11). Im Menue unter
"Kein Hack" -> "Agent" erkennt GlowCube das Plugin von selbst.

## Einstellungen

`plugins/GlowCubeAgent/config.yml`:

| Schluessel | Standard | Bedeutung |
| --- | --- | --- |
| `max-agenten-je-spieler` | 3 | Agenten gleichzeitig je Spieler (einer je Sorte) |
| `max-tempo` | 4.0 | Hoechstes Lauftempo |
| `max-abbau-tempo` | 20.0 | Hoechstes Abbau-Tempo |
| `max-xray-chunks` | 6 | Wie weit X-Ray hoechstens reicht |
| `xray-erlaubt` | true | X-Ray ueberhaupt zulassen |

Berechtigung: `glowcube.agent` (Standard: jeder).

## Wie es spricht

Kanal `glowcube:agent`, ein Text (UTF-8 mit VarInt-Laenge):
`start;ERZ|STEIN|HOLZ;erzart;tempo;abbau;xray;chunks`, `werte;...`,
`zurueck;AUFTRAG`, `alle`. Zurueck an den Client: `aus;AUFTRAG`.
