# GlowCube

Ein clientseitiger Werkzeugkasten fuer Minecraft Java 26.2 auf Fabric:
X-Ray, Fullbright, Flight, Speed, KillAura und mehr.

**Stand:** Minecraft 26.2 hat die Zeichen-API ausgetauscht (kein `GuiGraphics`
und kein `MultiBufferSource` mehr, stattdessen ein Einreiche-Modell). Deshalb
kommen die zwoelf Module, die nichts zeichnen, zuerst - bedient ueber Tasten.
ClickGUI, HUD und die ESP-Module liegen unter `spaeter/` und folgen, sobald
sie auf das neue Modell umgeschrieben sind; dort steht auch die vollstaendige
Gegenueberstellung der alten und neuen Namen.

> Auf oeffentlichen Servern faellt das sofort auf und ist dort Bannmaterial.
> Gedacht ist es fuer Einzelspieler und den eigenen Testserver.

## An die fertige Datei kommen

Du brauchst **keine** Entwicklungsumgebung. GitHub uebersetzt den Client bei
jeder Aenderung selbst und haengt ihn an den Release **neueste**:

**<https://github.com/tonisagmeister11-design/GlowCube/releases/latest>**

Dort unter *Assets* auf `glowcube-1.0.0.jar` klicken - fertig. Keine ZIP, kein
Entpacken, keine Anmeldung noetig.

### Der andere Weg (nur mit GitHub-Konto)

Unter **Actions** liegt zu jedem Lauf dasselbe als *Artifact* `GlowCube-Mod`.
Das ist eine ZIP und laesst sich **nur herunterladen, wenn man bei GitHub
angemeldet ist** - ohne Konto ist der Name kein Link. Deshalb ist der Release
oben der bequemere Weg.

## Einbauen

1. **Fabric installieren** - den Installer von `fabricmc.net/use/installer`
   holen, starten, als Spielfassung **26.2** waehlen, *Install* druecken.
2. Minecraft-Launcher oeffnen. Es gibt jetzt ein Profil **fabric-loader-26.2**.
3. **Fabric API** herunterladen - such auf `modrinth.com` nach "Fabric API"
   und nimm die Fassung fuer 26.2. Das ist eine `.jar`.
4. Den `mods`-Ordner oeffnen:
   * Windows: `Windows-Taste + R`, `%appdata%\.minecraft\mods` eingeben, Enter.
   * macOS: `~/Library/Application Support/minecraft/mods`
   * Linux: `~/.minecraft/mods`

   Gibt es den Ordner nicht, legst du ihn mit genau diesem Namen an.
5. **Beide** JARs dort hineinlegen: die Fabric API und `glowcube-1.0.0.jar`.
6. Im Launcher das Profil **fabric-loader-26.2** starten.

Beides muss zusammenpassen: Fabric API fuer 26.2, Loader fuer 26.2, GlowCube
fuer 26.2. Eine Fassung daneben und das Spiel startet nicht.

## Bedienen

Jedes Modul hat eine Taste. Was geschaltet wurde, steht kurz ueber der Hotbar.

| Taste | Modul | | Taste | Modul |
| --- | --- | --- | --- | --- |
| `Rechte Umschalt` | ClickGUI | | `R` | KillAura |
| `X` | X-Ray | | `K` | AutoTool |
| `H` | Fullbright | | `M` | AntiAFK |
| `C` | Zoom | | `B` | SeedHunt |
| `F` | Flight | | `N` | NoFall |
| `G` | Speed | | `J` | AutoSprint |
| `V` | Step | | `L` | Search |

Ohne Taste, ueber das ClickGUI erreichbar: StorageESP, EntityESP, Tracers,
HoleESP, Trajectories, AutoWalk, Criticals, AutoRespawn.

Die Belegungen meiden alles, was Minecraft selbst benutzt - mit einer
Ausnahme: `F` tauscht in Vanilla die Zweithand. Wem das dazwischenkommt, der
aendert `key` fuer Flight in `.minecraft/config/glowcube.json` (GLFW-Nummern,
`-1` heisst keine Taste).

Im Chat und in Menues schalten die Tasten nicht - das wird am Mauszeiger
erkannt.

Dieselbe Datei enthaelt unter X-Ray die Liste der sichtbaren Bloecke. Voreingestellt
sind alle Erze, Kisten, Spawner und Portale.

## Die Module

| Modul | Was es tut |
| --- | --- |
| X-Ray | Blendet alles aus, was nicht auf der Liste steht |
| Fullbright | Keine Dunkelheit mehr |
| Zoom | Fernglas |
| Flight | Fliegen ohne Kreativmodus (Motion oder Abilities) |
| Speed | Schneller laufen |
| Step | Bloecke hochlaufen ohne Sprung |
| NoFall | Kein Sturzschaden |
| AutoSprint | Immer sprinten |
| KillAura | Greift Ziele in Reichweite an |
| AutoTool | Bestes Werkzeug beim Abbauen |
| AntiAFK | Haelt dich auf dem Server |
| SeedHunt | Fliegt selbsttaetig eine Spirale ab, damit SeedCrackerX schnell genug Daten bekommt |
| StorageESP | Kisten, Faesser, Shulker durch Waende |
| EntityESP | Kaesten um Spieler, Monster, Tiere, Items |
| Tracers | Linien vom Fadenkreuz zu Entities |
| Search | Markiert gesuchte Bloecke, ohne die Sicht zu veraendern |
| HoleESP | Zeigt Loecher, die Explosionen standhalten |
| Trajectories | Zeigt, wo Pfeil, Perle oder Trank landen |
| AutoWalk | Laeuft von allein geradeaus |
| Criticals | Treffer zaehlen als kritisch |
| AutoRespawn | Sofort wieder einsteigen |
| ClickGUI | Das Fenster |
| Nuker | Baut alles im Umkreis ab - drei Betriebsarten |
| Spammer | Schickt regelmaessig eine Chatnachricht |
| AutoTotem | Haelt ein Totem in der Zweithand |
| Scaffold | Baut den Boden unter dir mit |

Die Kategorien folgen der Einteilung von BleachHack: Combat, Movement,
Render, Player, World, Exploits, Misc. Woher was stammt, steht in
[HERKUNFT.md](HERKUNFT.md).

## Zusammenspiel mit SeedCrackerX

[SeedCrackerX](https://github.com/19MisterX98/SeedcrackerX) rechnet aus
Merkmalen der Welt - Erzadern, Dungeons, Strukturen - den Weltseed zurueck.
Die Fassung 2.16.1 ist fuer genau Minecraft 26.2 gebaut und laeuft ohne
Zutun: einfach mit in den `mods`-Ordner legen. Sie bringt ihre eigenen
Abhaengigkeiten (cloth-config und die noetigen Fabric-Module) selbst mit.

GlowCube haengt sich an zwei Stellen ein:

* **Der Seed geht nicht mehr verloren.** SeedCrackerX bietet anderen Mods die
  Schnittstelle `SeedCrackerAPI` an und ruft sie auf, sobald es fertig ist.
  GlowCube meldet sich dort an. Der Seed erscheint ueber der Hotbar **und im
  Chat** - dort bleibt er stehen und laesst sich markieren - und wandert mit
  Zeitstempel nach `.minecraft/config/glowcube-seeds.txt`.

## Was X-Ray auf Servern kann und was nicht

Kurz: **Der Weltseed hilft X-Ray nicht.** Das sind zwei verschiedene Dinge.

X-Ray entscheidet nur, ob ein Block *gezeichnet* wird. Es kann ausschliesslich
das zeigen, was der Client ohnehin schon hat.

* **Server ohne Anti-X-Ray** schicken die Chunks samt Erzen. Dort wirkt X-Ray
  bereits jetzt - ohne Seed, ohne Zutun.
* **Server mit Anti-X-Ray** (Paper bringt es mit, fast jeder groessere Server
  schaltet es ein) schicken gefaelschte Daten: Stein, wo Erz liegt, oder Erz
  ueberall. Die echten Erzpositionen erreichen den Client nie. Kein
  Client-Mod kann zeigen, was er nicht bekommen hat - auch nicht mit Seed.

Was der Seed koennte: *ausrechnen*, wo Erze bei der Weltgenerierung entstanden
waeren. Das ist aber kein X-Ray, sondern eine **Vorhersage**, und sie
verlangt, die Erzgenerierung von 26.2 nachzubauen. Ein eigenes, grosses
Vorhaben - kein Schalter an X-Ray.

Fuer **Strukturen** gibt es das bereits fertig: SeedCrackerX bringt eigene
Finder mit, die aus dem bekannten Seed Festungen, Tempel und anderes
errechnen. Dafuer braucht es GlowCube nicht.
* **SeedHunt fliegt die Arbeit ab.** Mit der Einstellung `AutoStart` geht es
  beim Betreten einer Welt von selbst los. SeedCrackerX sieht nur, was der Client
  ohnehin geladen bekommt - wer stehen bleibt, wartet ewig. `B` startet eine
  quadratische Spirale um den Startpunkt, die nach aussen waechst und dabei
  Chunk um Chunk laedt. Sobald der Seed da ist, schaltet sich SeedHunt selbst
  ab. Abstand der Bahnen, Hoehe, Tempo und Laenge stehen in der
  Konfigurationsdatei.

Ohne SeedCrackerX laeuft GlowCube unveraendert weiter - die Verbindung ist
freiwillig, keine Voraussetzung.

**Im Einzelspieler** ist das eine Spielerei: `/seed` sagt es dir sofort.
Interessant ist es als Probe, ob der Cracker die Welt auch von aussen
errechnen kann. **Auf fremden Servern** ist der Weltseed ein handfester
Vorteil (Festungen, Slime-Chunks, Strukturen) und praktisch ueberall
verboten - dort kostet es den Zugang.

## Wenn etwas nicht klappt

| Was passiert | Woran es liegt |
| --- | --- |
| Spiel startet, nichts passiert | Falsches Profil gestartet - es muss `fabric-loader-26.2` sein |
| Absturz beim Start, `fabric-api` im Text | Fabric API fehlt im `mods`-Ordner |
| Absturz, `mixin` im Text | Fassungen passen nicht zusammen - alle drei auf 26.2 bringen |
| Der Build bei GitHub ist rot | Im Lauf auf **Bauen** klicken, die rote Zeile ist die Ursache |

## Fuer den Fall, dass du doch selbst bauen willst

JDK 25 und Gradle vorausgesetzt:

```bash
cd glowcube-client
python3 resolve-versions.py   # traegt die aktuellen Fabric-Versionen ein
gradle build                  # -> build/libs/glowcube-1.0.0.jar
gradle runClient              # Testinstanz mit dem Mod
```

## Aufbau

| Pfad | Inhalt |
| --- | --- |
| `src/main/java/.../core/` | Modul-Grundgeruest, Einstellungen, Konfiguration |
| `src/main/java/.../util/` | Farben, Animation, 2D- und 3D-Zeichnen |
| `src/main/java/.../gui/` | ClickGUI und Block-Auswahl |
| `src/main/java/.../hud/` | Wasserzeichen und Modulliste |
| `src/main/java/.../module/` | Die 16 Module |
| `src/main/java/.../mixin/` | Die drei Eingriffe ins Spiel |
| `einzeldatei/` | Derselbe Code in einer einzigen Datei - nicht Teil des Builds |
| `resolve-versions.py` | Holt die aktuellen Fabric-Versionen |
