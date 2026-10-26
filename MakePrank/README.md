# MakePrank 0.3.0

Windows-Desktop-App für **sichtbare, zustimmungsbasierte Scherz-Simulationen** (inspiriert von den in
[`docs/FEATURE_MATRIX.md`](docs/FEATURE_MATRIX.md) dokumentierten Funktionen des Minecraft-Mods *Your PC Mine*).
Zwei Programme:

| Programm | Zweck |
|---|---|
| **MakePrank Creator** | Effekte auswählen, Paket (`.mpk`) bauen, Vorschau, Export, gekoppelte Empfänger verwalten |
| **MakePrank Empfänger-Client** | Paket öffnen, prüfen, **ausdrücklich zustimmen**, Effekte ansehen; immer sichtbar, immer stoppbar |

## Was es kann (alles in eigenen, schließbaren MakePrank-Fenstern)

16 Effekte: Fehlerdialog, MsgBox (6 Varianten), Toast, Browser-/Editor-Attrappe, Herunterfahren/Neustart-**Simulation**,
Farbbalken (7 Presets), Screamer-Bild (eingebaut oder eigenes PNG/GIF), Soundeffekt (kurz, leise, stummschaltbar),
Fensterwackeln (nur eigenes Fenster), Text-Overlay, Konsolen-Attrappe, Invertieren/Spiegeln (nur eigene Zeichenfläche),
Minimieren/Einfrieren-Simulation, Wallpaper-Vorschau, Chat-Anzeige. Dazu: Sitzungen mit Pause/Stopp/Restdauer,
Paketformat v2 mit Hash und Ressourcenlimits, optionales **TLS-Pairing** (lokales Netz / Loopback) mit fester Whitelist.

## Was es bewusst **nie** tut

Systembefehle ausführen, echtes Herunterfahren/Neustarten/Sperren/Abmelden (auch nicht aus der Ferne), Dateien ändern oder
löschen, echte URLs öffnen, fremde Fenster oder Eingaben steuern, versteckt/dauerhaft laufen, Autostart, Dialogflut,
Vollbild-Zwang. Der Empfänger kann jederzeit mit **STOPP**, **Esc** oder Fenster schließen alles beenden – ohne Netzwerk.

## Schnellstart

Fertige Windows-Programme: siehe [Windows-Build](#windows-build). Aus dem Quellcode (Python 3.10+ mit Tkinter):

```bat
py -3 -m venv .venv && .venv\Scripts\activate
pip install -e ".[dev]"
run_windows.bat            :: Creator
run_client_windows.bat     :: Empfänger (optional: Pfad zu einer .mpk)
```

Beispielpakete: [`examples/`](examples) (`WelcomeDemo.mpk`, `AlleEffekte.mpk`). Anleitung: [`docs/USER_GUIDE_DE.md`](docs/USER_GUIDE_DE.md).

## Tests

```bash
pip install -e ".[dev]"
python -m pytest -q          # unter Linux ohne Display: xvfb-run -a python -m pytest -q
```

GUI-/Effekttests brauchen eine Anzeige und werden ohne Display **übersprungen** (nicht als bestanden gezählt).
Aktueller Stand und Grenzen: [`docs/STATUS.md`](docs/STATUS.md).

## Windows-Build

```bat
build_windows.bat
```

Legt `.venv` an, installiert Abhängigkeiten, führt alle Tests aus, baut per PyInstaller (onedir, ohne Konsolenfenster)
`MakePrank-Creator` und `MakePrank-Client`, startet beide als Smoke-Test und erzeugt `release\MakePrank-<Version>-Windows.zip`
inkl. `SHA256SUMS.txt`, `BUILD_REPORT.txt`, Handbuch und Beispielen. Dasselbe läuft als GitHub-Actions-Workflow
(`.github/workflows/makeprank-windows.yml`, Runner `windows-latest`); das Ergebnis ist das Artefakt **MakePrank-Windows**.
Die Programme sind **nicht codesigniert**; Windows SmartScreen kann warnen.

**`.mpk` öffnen:** `.mpk` auf `MakePrank-Client.exe` ziehen oder *Rechtsklick → Öffnen mit*. Es wird keine Registry geändert.
Ein Paket startet nie ohne ausdrückliche Zustimmung.

## Lizenz

MIT. Aus der Mod-JAR wurden weder Font noch Shader noch Code übernommen; die JAR liegt dem Repository nicht bei.
