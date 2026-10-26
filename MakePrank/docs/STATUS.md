# MakePrank – Status (Version 0.3.0)

Stand dieser Datei = Stand der tatsächlich ausgeführten Prüfungen unten. Nichts davon ist geschätzt.

## Ausgeführte Prüfungen (Linux, Python 3.12.3, Xvfb als virtuelle Anzeige)

| Kommando | Ergebnis |
|---|---|
| `xvfb-run -a python -m pytest -q --timeout=60` | **238 passed** (≈ 39 s) |
| `python -m pytest -q` (ohne Display) | 143 passed, **95 übersprungen** (GUI-/Effekt-/Startup-Tests brauchen Anzeige) |
| `python -m compileall -q makeprank tests tools run_*.py` | fehlerfrei |
| PyInstaller-Build unter **Linux** (`--onedir --windowed`) für Creator und Client + `tools/smoke_test.py` unter Xvfb | beide Programme starteten und liefen 4 s – **das ist keine Windows-Prüfung** |
| Screenshots aller 16 Effekte und der 9 Creator-Seiten unter Xvfb | visuell geprüft |

Testumfang: Paketformat/Validierung/Limits/Zip-Slip/Migration, Sitzungs-Engine (virtuelle Zeit), jeder Effekt (Fenster entsteht und verschwindet, Stop/Esc/Fenster-Schließen, Nutzer schließt Fenster selbst),
Audio-Caps und Mute, Zustimmung (nicht vorausgewählt, Ablehnen, Hash-Änderung), Stoppwege im Client (Button, Esc, Schließen, Timeout, Datei löschen/ändern),
Netzwerk mit echtem TLS auf Loopback (falscher/abgelaufener/wiederverwendeter Code, Fingerprint-Pinning, Klartext, Replay, manipulierte/übergroße/unbekannte Nachrichten, Rate-Limit, Heartbeat, Token-Ablauf, Disconnect, lokales Trennen), Creator-Oberfläche inkl. Ende-zu-Ende-Fernsteuerung, Start der echten Programme.

## Windows

* **Nicht auf Windows ausgeführt** in dieser Arbeitsumgebung (Linux-Container). `build_windows.bat` und der Workflow `.github/workflows/makeprank-windows.yml` sind geschrieben, aber ein Windows-Lauf liegt hier nicht vor, bis der Workflow auf GitHub (`windows-latest`) gelaufen ist. Erst dessen Ergebnis (Tests, Build, Smoke-Test, Artefakt `MakePrank-Windows`) belegt einen Windows-Build.
* Windows-spezifisch ungeprüft: `winsound`-Wiedergabe von Tönen **und importierten WAV-Clips** (Hörtest auf echtem Windows), DPI/Schriftdarstellung (Segoe UI), Verhalten von `focus_set`/Esc mit Windows-Fenstermanager, SmartScreen. Die Tests prüfen, dass die exakten Clip-Bytes an das Audio-Backend übergeben werden; ob der Lautsprecher sie hörbar abspielt, muss auf Windows geprüft werden.
* Programme sind **nicht signiert**.

## Bekannte Grenzen / nicht implementiert

* Kein QR-Code für das Pairing (Link/Code als Text). Kein Internet-Relay. Keine Paketsignaturen (Hash ≠ Echtheitsnachweis).
* Screamer: kein Vollbild/Noise/Restore (Absicht). Browser-Attrappe ohne optionalen externen Link (nicht umgesetzt).
* Mehrere Empfänger je Creator möglich, aber keine Gruppenbefehle.
* Auswahllisten im Editor zeigen technische englische Werte (z. B. `error`, `yes_no`).
* SMPTE/HD-Testbild vereinfacht (Farbfolgen angenähert, keine normgenauen Pegel).

## Sicherheitsentscheidungen

Echtes Herunterfahren/Neustarten/Sperren/Abmelden gibt es **nirgends** – weder lokal noch per Netzwerk; es existiert kein Codepfad dafür (AST-Test verbietet `subprocess`, `os.system`, `webbrowser`, `ctypes`, `eval`/`exec`).
Das Netzwerkprotokoll kennt keine Befehls-, Pfad-, URL- oder Skriptfelder; Fernauslösung ist auf Aktionen des vom Empfänger geöffneten, akzeptierten Pakets beschränkt. Details: `docs/ARCHITECTURE.md`.
