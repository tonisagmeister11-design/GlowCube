# GlowCube-Agent-Plugin

Server-Plugin fuer **Paper 1.21.11**. Damit koennen Spieler mit dem
GlowCube-Client auf deinem Server Agenten losschicken - genau wie in der
Einzelspielerwelt: Abbau-Agenten fuer Erz, Stein und Holz (mit Tempo,
Abbau-Tempo und X-Ray, Beute wird zugeworfen), den Guardian-Agenten als
Leibwaechter, den Builder-Agenten, der Schematics baut, sowie Farm-,
Tunnel- und Jaeger-Agenten. Bis zu fuenf Agenten je Art, jeder einzeln
eingestellt; dazu Einsatzort (`/agentort`), Sammelkiste (`/agentkiste`) und
die Agenten-Uebersicht im HUD.

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
| `max-agenten-je-spieler` | 20 | Agenten gleichzeitig je Spieler, insgesamt |
| `max-agenten-je-art` | 5 | Agenten gleichzeitig je Spieler und Art |
| `max-tunnel-laenge` | 512 | Tunnel-Agent: hoechstens so viele Bloecke |
| `max-tempo` | 4.0 | Hoechstes Lauftempo |
| `max-abbau-tempo` | 20.0 | Hoechstes Abbau-Tempo |
| `max-xray-chunks` | 6 | Wie weit X-Ray hoechstens reicht |
| `max-bau-tempo` | 100.0 | Builder: hoechstens so viele Bloecke pro Sekunde |
| `xray-erlaubt` | true | X-Ray ueberhaupt zulassen |
| `pvp-pro-erlaubt` | true | PvP Pro (Kampf-Automatik im Client) auf diesem Server erlauben |
| `orbital-strike-erlaubt` | true | Orbital Strike (Hebel auf der Orbital Strike Cannon) erlauben |

Berechtigungen: `glowcube.agent` (Agenten, Standard: jeder),
`glowcube.pvppro` (PvP Pro, Standard: jeder - wirkt nur, solange
`pvp-pro-erlaubt: true` gesetzt ist), `glowcube.orbital` (Orbital Strike,
Standard: jeder).

**PvP Pro** laeuft im Client, nicht im Plugin - das Plugin gibt es nur frei.
Ohne diese Freigabe schaltet es sich auf einem Server von selbst ab. Gedacht
fuer Testserver, auf denen alle Mitspieler Bescheid wissen.

**Orbital Strike:** Ziel im Client mit `/strike x y z` setzen (Position
bekommt man mit `/coordinates`, Klick kopiert sie), dann den Hebel auf dem
Leitstein oben auf der Orbital Strike Cannon umlegen. Das geht nur, wenn die
Kanone dort wirklich steht (Vergleich mit dem Schematic, auch gedreht). Man
sieht sie arbeiten: Signal durch die Leitungen, TNT in den Ladearmen, Portale,
Knall im Kern, dann schiesst die Salve senkrecht aus der Kanone. Kurz darauf
fallen am Ziel Ringe aus TNT herab.

## Wie es spricht

Kanal `glowcube:agent`, ein Text (UTF-8 mit VarInt-Laenge):
`start;ERZ|STEIN|HOLZ|WAECHTER|BAUMEISTER|BAUER|TUNNEL|JAEGER;nummer;art;tempo;abbau;xray;chunks;leuchten;zahl` (art = Erzart, Ausruestung, Schematic, Tunnelgroesse oder Tierart; zahl = Farm-Radius, Tunnel-Laenge oder Tiere uebrig lassen), `werte;AUFTRAG;nummer;...`,
`ort;x;y;z` / `ort;weg`, `kiste;x;y;z` / `kiste;weg`,
`zurueck;AUFTRAG;nummer` (0 = alle), `alle`, `ziel;x;y;z` (Orbital-Strike-Ziel). Zurueck an den Client: `aus;AUFTRAG;nummer` und alle halbe Sekunde `status;...` fuer die Agenten-Uebersicht.
