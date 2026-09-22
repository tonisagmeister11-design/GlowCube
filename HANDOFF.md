# GlowCube – Übergabe / Handoff

> Diese Datei ist die Übergabe an eine **neue Claude-Code-Sitzung** (anderes
> Modell), die an GlowCube weiterarbeitet. Sie fasst zusammen, wie das Projekt
> gebaut ist, was zuletzt passiert ist, welche Fallstricke es gibt und was als
> Nächstes ansteht. Zuerst diese Datei lesen, dann `glowcube-client/README.md`.

---

## 1. Repo & Branch

- **Repo:** `https://github.com/tonisagmeister11-design/GlowCube`
- **Arbeits-Branch:** `claude/sweet-goodall-oscs5n` – **hier** entwickeln und
  hierhin pushen (`git push -u origin claude/sweet-goodall-oscs5n`).
- **Kein PR offen** (Stand dieser Übergabe). Falls einer gewünscht ist, erst
  fragen.
- **GitHub-Zugang:** läuft über das verbundene Claude-GitHub-Konto/den
  GitHub-Connector der Sitzung – **keine Tokens nötig oder im Repo abgelegt**.
  Fehlt Zugriff: Nutzer verbindet GitHub unter `https://claude.ai/connect-github`
  bzw. installiert die Claude-GitHub-App aufs Repo.
- GitHub-Aktionen laufen über die `mcp__github__*`-Tools (kein `gh`-CLI).

## 2. Was GlowCube ist

Ein clientseitiger Fabric-Werkzeugkasten für Minecraft (ClickGUI, X-Ray, ESP,
Movement/Combat/World-Module, OreSim usw.). Wird für **zwei Spielfassungen**
gebaut:

- **1.21.11** (Java 21)
- **26.3** (Java 25)

Eine gemeinsame Codebasis, versionsabhängige Teile getrennt (siehe §3).

## 3. Build-System / Versions-Split (WICHTIG)

Der Umschalter ist **eine Zeile** in `glowcube-client/gradle.properties`:
`minecraft_version=…`. `resolve-versions.py` füllt daraus die restlichen
Fabric-/Mappings-/Java-Angaben auf.

`glowcube-client/build.gradle` wählt je nach Fassung genau **einen** Quellordner
pro versionsabhängiger Klasse (nie doppelt):

| Zweck | 1.21.x | 26.x |
|---|---|---|
| Render/GUI/HUD | `src/versionen/render_1_21` | `src/versionen/render_26` |
| SlotKlick | `src/versionen/slotklick_1_21` | `src/versionen/slotklick_26` |
| Erz/OreSim-Generierung | `src/versionen/erz_1_21_26_2` | `src/versionen/erz_26_3` |

- `alteFassung = mcv.startsWith("1.")` unterscheidet 1.21.x von 26.x.
- **Alles in `src/main/java` muss auf BEIDEN Fassungen kompilieren.** Was sich
  zwischen den Fassungen unterscheidet, gehört in `src/versionen/...`.
- `jar { exclude 'kaptainwutax/**' }` – die nachgebaute SeedCracker-Schnittstelle
  ist nur zum Kompilieren da, kommt nie in die JAR.

## 4. Bauen & Prüfen

- **Lokales Bauen geht in dieser Umgebung NICHT:** der Proxy blockt
  Mojang/Fabric (`403 Forbidden`), also scheitern `resolve-versions.py` und
  Loom am Download. **Verifikation läuft über CI.** Einzelne
  Minecraft-freie `.java`-Dateien lassen sich mit `javac` prüfen (z.B.
  `Category.java` hat keine MC-Importe).
- **CI:** `.github/workflows/glowcube.yml` baut bei jedem Push auf
  `glowcube-client/**` die **Matrix `["1.21.11", "26.3"]`**. Schritte u.a.:
  Zielfassung eintragen → `resolve-versions.py` → `bundle-seedcracker.py` →
  `probe-imports.py` / `probe-api.py` / `verify-api.py` (melden umbenannte
  Klassen/Signaturen, brechen aber nicht ab) → Gradle-Build → Release der Jars.
- **Nach jedem Push das CI-Ergebnis prüfen** (beide Jars grün?), da lokal nicht
  gebaut werden kann. Dafür `mcp__github__*` (actions/list, get_job_logs) nutzen.

## 5. Zuletzt erledigt (dieser Branch)

Von neu nach alt:

- **`005dd55` – 26.3: SeedCracker raus (behebt Absturz beim Menü-Öffnen).**
  Ursache: `SeedBridge implements SeedCrackerAPI`; diese Schnittstelle liegt
  nicht in der JAR und wird auf 26.x nicht von SeedCrackerX nachgeliefert →
  `NoClassDefFoundError`, sobald etwas `SeedBridge` anfasst (z.B. die Fußzeile
  beim Öffnen des ClickGUI → „Maus/Menü geht nicht"). Lösung:
  - `SeedBridge` ist jetzt **fassungsgetrennt**:
    `src/versionen/render_1_21/.../integration/SeedBridge.java` (voll, mit
    `implements SeedCrackerAPI`) und
    `src/versionen/render_26/.../integration/SeedBridge.java` (schlank, **ohne**
    Schnittstelle – nur Seed-Speicher für OreSim). Die shared Fassung in
    `src/main` wurde entfernt.
  - **SeedCracker gibt es auf 26.x gar nicht mehr:** `SeedHunt` wird auf 26.x
    nicht registriert (`ModuleManager` + `GlowCubeClient.seedCrackerFassung()`),
    die SeedCracker-Fußzeile im 26.3-ClickGUI weicht der laufenden Fassung,
    `bundle-seedcracker.py` baut auf allem, was nicht mit `1.` beginnt, nichts
    ein und nimmt den `seedcrackerx`-Einstiegspunkt aus dem Manifest.
  - **1.21.x unverändert** mit SeedCracker.
  - Bestätigt durch die vom Nutzer hochgeladene, von Hand reparierte 26.3-JAR:
    dort ist `SeedBridge` ebenfalls ohne Schnittstelle und kein SeedCrackerX
    gebündelt; sonst war die JAR byte-gleich zu unserem Code.

- **`a23b00a` – Menü in „Hacks / Kein Hack" geteilt + Modul Ultra-Performance.**
  - `Category` hat jetzt eine `Bereich`-Ebene (`HACKS`, `KEIN_HACK`); neue
    Kategorie `PERFORMANCE` liegt in `KEIN_HACK`.
  - ClickGUI öffnet zuerst eine **Bereichswahl** (zwei Kacheln); danach das
    gewohnte Fenster-Menü, gefiltert auf den Bereich. Zurück-Chip + Esc führen
    zurück; Suche/Zähler gelten je Bereich. Umgesetzt in **beiden**
    `ClickGuiScreen`-Fassungen identisch (`render_1_21`/`render_26`), plus
    `Layout.imBereich(...)`.
  - **Ultra-Performance** (`module/performance/UltraPerformance.java`, Kategorie
    `PERFORMANCE`): dreht für maximale FPS alles herunter (Sichtweite 4 Chunks,
    keine Schatten/Wolken, kaum Partikel, kein VSync, Mipmaps/Biome-Blend/
    Bildschirmeffekte aus, schnellstes Bild) und stellt beim Ausschalten
    **jeden** Wert genau zurück. Kern: `util/Leistung.java` – fasst
    `Options`-Werte **reflektiv** an (`OptionInstance.set(..)`), Zielwert nach
    Laufzeittyp (Zahl/Bool/Enum), fehlende Optionen je Fassung still übergangen.

- **`8e6bd31` – X-Ray auf 26.3 gefixt + BunnyHop.**
  `allChanged()` wanderte auf 26.x von `LevelRenderer` nach
  `Minecraft.levelExtractor` → `Netz.chunksNeuZeichnen()` (26er Fassung) greift
  das per Reflection ab. `BunnyHop` neues Movement-Modul (läuft auf beiden
  Fassungen).

## 6. Architektur-Kurzüberblick

- **Module:** `core/Module` (Basis: `onEnable/onDisable/onTick/onWorldRender`,
  Settings, Taste), `core/ModuleManager` (registriert alle Module, verteilt
  Ticks/Events/Tasten), `core/Category` (mit `Bereich`), Settings unter
  `core/setting/*`.
- **ClickGUI:** `gui/Fenster` + `gui/Layout` (statische Fenster-Anordnung, in
  Config gespeichert) und **zwei** `gui/ClickGuiScreen`-Fassungen. Unterschiede
  nur: Grafiktyp (`GuiGraphics` vs `GuiGraphicsExtractor`), Render-Methode
  (`render` vs `extractRenderState`), Tastencodes (`GLFW.*` vs `Netz.TASTE_*`),
  Screen-Wechsel (`minecraft.setScreen` vs `Netz.bildschirmSetzen`).
  **Beide Fassungen bei GUI-Änderungen synchron halten.**
- **Render-Anbindung:** `render/RenderBruecke` (HUD/Welt-Render je Fassung),
  `render/Netz` (versionsabhängige Kleinteile + Reflection: Chunk-Neuzeichnen,
  Screen holen/setzen, Tasten). `render/WeltRender` für 3D.
- **Optionen roh schreiben:** Mixin `OptionInstanceAccessor` + `util/Gamma`
  (Muster: Originalwert merken, roh setzen, beim Loslassen zurück). Ultra-
  Performance nutzt dagegen `OptionInstance.set()` (im erlaubten Bereich, löst
  Folgen wie Chunk-Reload aus).
- **Seed:** `integration/SeedBridge` (jetzt fassungsgetrennt, s. §5),
  `module/misc/SeedHunt` (nur 1.x), `module/world/OreSim` (nutzt nur
  `SeedBridge.seed()`, funktioniert auch mit von Hand gesetztem Seed).

## 7. Fallstricke / Regeln

- **Immer beide Fassungen bedenken** (1.21.11 **und** 26.3). Nichts in
  `src/main` einbauen, das nur auf einer Fassung existiert – sonst nach
  `src/versionen/...` splitten.
- **GUI-Änderungen in beiden `ClickGuiScreen`-Dateien** identisch machen.
- **SeedCracker nur auf 1.x.** Auf 26.x nie wieder einbauen (Absturzquelle).
- Bei 26.x-API-Unsicherheiten hilft der Blick in die Wurst-26.3-Jars und
  Reflection (wie in `Netz`); `verify-api.py`/`probe-*.py` prüfen Klassennamen
  gegen die echten Jars im CI.
- **Commits:** deutschsprachige Messages im Repo-Stil, nur auf den Arbeits-
  Branch pushen. **Keine Modell-IDs** in Commits/Code/PRs.

## 8. Offene Punkte / Nächste Schritte

1. **CI der letzten Pushes prüfen** – bauen 1.21.11 **und** 26.3 grün? (Nicht
   lokal baubar, s. §4.)
2. **Ultra-Performance real testen** (schwacher Laptop, Ziel 60+ FPS); ggf.
   weitere Optionen aufnehmen oder Werte justieren. Vorsicht: `Options.save()`
   nicht erzwingen, sonst könnten aggressive Werte bei hartem Beenden in
   `options.txt` landen (Weltverlassen ruft bereits `onDisable`→Restore).
3. **Bereichswahl-Menü visuell testen** (Maus/Hover/Zurück/Esc, beide
   Fassungen).
4. **Angekündigter Nicht-Hack-Client:** der Bereich „Kein Hack" ist dafür
   vorbereitet (bisher nur Ultra-Performance darin). Der Nutzer will dafür
   separaten Client-Code liefern; **erst dann** dort Features ergänzen.
5. **3D-ESP auf 26.3** über das neue Submit-Node-Rendersystem – weiterhin offen
   (siehe `render/RenderBruecke` 26er Javadoc; unsicher/blind ohne laufende
   26.3-Instanz).

## 9. Startprompt für den neuen Chat

> „Arbeite an GlowCube weiter (Branch `claude/sweet-goodall-oscs5n` auf
> `github.com/tonisagmeister11-design/GlowCube`). Lies zuerst `HANDOFF.md` im
> Repo-Root und danach `glowcube-client/README.md`. Beachte: zwei Zielfassungen
> (1.21.11 + 26.3), versionsabhängiger Code in `src/versionen/...`, lokal nicht
> baubar (Proxy blockt Mojang/Fabric) – Verifikation über CI. Als Nächstes: <dein
> Auftrag>."
