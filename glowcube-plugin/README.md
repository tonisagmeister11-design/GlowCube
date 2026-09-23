# GlowCube-Agent-Plugin

Server-Plugin fuer **Paper 1.21.11**. Damit koennen Spieler mit dem
GlowCube-Client auf deinem Server Agenten losschicken - genau wie in der
Einzelspielerwelt: Abbau-Agenten fuer Erz, Stein und Holz (mit Tempo,
Abbau-Tempo und X-Ray, Beute wird zugeworfen), den Guardian-Agenten als
Leibwaechter und den Builder-Agenten, der Schematics baut.

Schematics fuer den Builder: die fuenf eingebauten, dazu alles in
`plugins/GlowCubeAgent/schematics/` (`.schem`, `.litematic`, `.nbt`). Der
Client schickt nur den Namen - ein eigenes Schematic muss also sowohl beim
Spieler (fuer die Auswahl) als auch auf dem Server liegen.

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
| `max-agenten-je-spieler` | 5 | Agenten gleichzeitig je Spieler (einer je Sorte) |
| `max-tempo` | 4.0 | Hoechstes Lauftempo |
| `max-abbau-tempo` | 20.0 | Hoechstes Abbau-Tempo |
| `max-xray-chunks` | 6 | Wie weit X-Ray hoechstens reicht |
| `max-bau-tempo` | 100.0 | Builder: hoechstens so viele Bloecke pro Sekunde |
| `xray-erlaubt` | true | X-Ray ueberhaupt zulassen |

| `pvp-pro-erlaubt` | false | PvP Pro (Kampf-Automatik im Client) auf diesem Server erlauben |

Berechtigungen: `glowcube.agent` (Agenten, Standard: jeder),
`glowcube.pvppro` (PvP Pro, Standard: nur OPs - und nur, wenn
`pvp-pro-erlaubt: true` gesetzt ist).

**PvP Pro** laeuft im Client, nicht im Plugin - das Plugin gibt es nur frei.
Ohne diese Freigabe schaltet es sich auf einem Server von selbst ab. Gedacht
fuer Testserver, auf denen alle Mitspieler Bescheid wissen.

## Wie es spricht

Kanal `glowcube:agent`, ein Text (UTF-8 mit VarInt-Laenge):
`start;ERZ|STEIN|HOLZ|WAECHTER|BAUMEISTER;art;tempo;abbau;xray;chunks` (art = Erzart, Ausruestung oder Schematic-Name), `werte;...`,
`zurueck;AUFTRAG`, `alle`. Zurueck an den Client: `aus;AUFTRAG`.
