# GlowCube

Ein clientseitiger Werkzeugkasten fuer Minecraft Java 26.2 auf Fabric:
X-Ray, ESP, Tracers, Flight, KillAura und ein ClickGUI.

> Auf oeffentlichen Servern faellt das sofort auf und ist dort Bannmaterial.
> Gedacht ist es fuer Einzelspieler und den eigenen Testserver.

## An die fertige Datei kommen

Du brauchst **keine** Entwicklungsumgebung. GitHub uebersetzt den Client bei
jeder Aenderung selbst:

1. Oben im Repo auf **Actions** klicken.
2. Links **GlowCube bauen** auswaehlen, dann den obersten (neuesten) Lauf anklicken.
   Ein gruener Haken heisst: fertig.
3. Ganz unten unter **Artifacts** liegt **GlowCube-Mod**. Herunterladen.
4. Das ist eine ZIP-Datei. Entpacken - darin liegt `glowcube-1.0.0.jar`.
   Die brauchst du. (`glowcube-1.0.0-sources.jar` kannst du wegwerfen, das ist
   nur der Quelltext.)

Laeuft gerade kein Build? Auf **Run workflow** klicken, dann startet einer.

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

| Taste | Wirkung |
| --- | --- |
| `Rechte Umschalt` | ClickGUI oeffnen |
| `X` | X-Ray |
| `F` | Flight |
| `C` | Zoom |

Im ClickGUI:

* **Linksklick** schaltet ein Modul an oder aus
* **Rechtsklick** klappt seine Einstellungen auf
* **Mittelklick** belegt die Taste neu - danach die gewuenschte Taste druecken
  (`ESC` nimmt die Belegung weg)
* Einfach **lostippen** sucht ueber alle Kategorien
* Bei X-Ray auf **Blocks** klicken: dort suchst du Bloecke und klickst sie an
  oder ab

Alles wird in `.minecraft/config/glowcube.json` gemerkt.

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
