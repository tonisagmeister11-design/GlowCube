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

Ab Werk sind drei Tasten belegt:

| Taste | Wirkung |
| --- | --- |
| `X` | X-Ray |
| `F` | Flight |
| `C` | Zoom |

Was geschaltet wurde, steht kurz ueber der Hotbar.

Die uebrigen neun Module haben noch keine Taste. Bis das ClickGUI wieder da
ist, werden sie in `.minecraft/config/glowcube.json` eingeschaltet: dort steht
zu jedem Modul `enabled` und `key`. Als `key` traegt man die GLFW-Nummer der
Taste ein (`71` ist G, `72` H, `82` R, `86` V); `-1` heisst keine Taste.
Die Datei entsteht beim ersten Start und wird beim Beenden zurueckgeschrieben -
also Minecraft schliessen, bearbeiten, wieder starten.

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
