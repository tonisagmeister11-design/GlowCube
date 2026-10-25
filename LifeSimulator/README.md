# Life Simulator

*Dein Leben. Deine Entscheidungen. Deine Welt.*

Ein 3D-Lebenssimulator in Godot 4.3 (GDScript) für Windows 10/11. Jede Partie spielt in
einer prozedural generierten, validierten Stadt (Havenbrück) mit Fluss, Brücken,
16 Stadtteiltypen, über 1.700 Gebäuden, rund 2.400 simulierten Bewohnern, Verkehr,
Bussen, Wirtschaft, Kriminalität und Politik. Alle Inhalte (Grafik, Texturen, Klänge,
Musik, Namen) sind selbst erstellt bzw. fiktiv; Schriften: Inter und Montserrat (SIL OFL).

## Spielen (Windows)
1. ZIP entpacken, `LifeSimulator.exe` starten (`LifeSimulator.pck` muss daneben liegen).
2. Hauptmenü → **Neues Spiel**: Modus (Leben / Sandbox / Frei) → Stadt (Seed, Größe,
   Einwohner) → Charakter (Aussehen, freies Alter 0–100) → Startsituation → Start.

### Steuerung (änderbar unter Einstellungen → Steuerung)
| Taste | Aktion |
|---|---|
| W A S D, Maus, Mausrad | Laufen, Kamera, Zoom |
| Shift / Leertaste | Rennen / Springen |
| E | Interagieren (Türen, Läden, Bushaltestellen, Personen ansprechen) |
| F | Fahrzeug ein-/aussteigen |
| Tab | Smartphone (Status, Inventar, Jobs, Wohnen, Bank, Bildung, Karte, Nachrichten, Kontakte, Polizei, Gang, Politik, Regeln) |
| M | Stadtkarte mit Filtern und Routenplanung |
| I | Inventar |
| Esc | Pause, Speichern, Laden, Hauptmenü |
| , / . | Zeit langsamer / schneller |
| V | Ich- / Schulterperspektive |
| F5 / F9 | Schnellspeichern / -laden |
| F3 / F4 | Debug-Anzeige / Stadt-Validierung anzeigen |

## Entwickeln
* Godot **4.3** öffnen und den Ordner `LifeSimulator/` importieren.
* Tests: `godot --headless --path . res://tests/test_runner.tscn` (optional `-- --filter=city`).
* Windows-Build: Export-Templates 4.3 installieren, dann
  `tools/build_windows.sh <godot>` (Linux/macOS) bzw. `tools\build_windows.bat <godot.exe>` →
  `build/windows/LifeSimulator.exe` + `.pck`.
* Optionale KI-Dialoge (Groq): Einstellungen → KI → Schlüssel eintragen (oder Umgebungsvariable
  `GROQ_API_KEY`). Ohne Schlüssel/Internet laufen alle Systeme mit lokalen Dialogen.
  Hinweis: Ein in einer verteilten Anwendung hinterlegter Schlüssel ist nie vollständig geheim;
  für echten Schutz wäre ein eigener Server-Proxy nötig.

## Dokumentation
* `docs/ARCHITECTURE.md` – Module und Schnittstellen
* `docs/STATUS.md` – Umsetzungsstand, Tests, bekannte Einschränkungen
* `docs/ECONOMY.md`, `docs/CRIME_POLITICS.md`, `docs/AUDIO.md` – Systemdetails
