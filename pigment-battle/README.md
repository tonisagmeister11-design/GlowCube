# Pigment Krieg – Schwarz gegen Weiß

Strategiespiel für Handy und PC (reines HTML5-Canvas, keine Installation).

**Starten:** `index.html` im Browser öffnen (oder den Ordner auf einen Webserver legen).
Auf dem Handy: Seite öffnen → „Zum Startbildschirm hinzufügen“ → läuft im Vollbild, quer.

## Spielen
* **Team wählen:** Schwarz oder Weiß.
* **Arena wählen:** Graue Steppe, Vulkankrater, Eiswüste, Neon-Kolosseum, Goldener Tempel (schwerer = mehr Münzen).
* **7 Krieger:** Klinge, Speer, Bogen, Schild (blockt), Ninja, Magier (explodierende Kugeln), Titan.
* **Fähigkeiten:** Meteor (Ziel antippen), Kriegsschrei (schneller & stärker), Heilwelle.
* **Boss:** Fällt die feindliche Burg unter die Hälfte, kommt der Boss-Titan.
* **Sterne:** 1–3 Sterne pro Arena, je nachdem wie viel Burgleben übrig bleibt.
* **Befehle:** Angriff / Verteidigen. Zerstöre die feindliche Burg.
* **Helme** im Shop kaufen – inklusive **Gesichts-Helm** (`assets/face.png`).
* **Waffen schmieden:** Holz → Eisen → Gold → Diamant → Plasma, dazu Rüstung, Mine und Burg-Turm.

## Steuerung
| PC | Handy |
|---|---|
| `1`–`7` Krieger, `Q` Verteidigen, `E` Angriff | Knöpfe unten antippen |
| `R` Meteor, `F` Kriegsschrei, `H` Heilwelle | Runde Knöpfe rechts |
| `A`/`D` oder Pfeiltasten, Mausrad, Ziehen = Kamera | Mit dem Finger über das Schlachtfeld wischen |
| `Esc`/`P` Pause | ⏸ |

Fortschritt (Münzen, Helme, Waffen) wird im Browser gespeichert.

## Als PC-Programm bauen (für Steam / itch.io)
Braucht [Node.js](https://nodejs.org). Im Ordner `pigment-battle`:

```
npm install
npm start            # testen
npm run build:win    # fertiges Windows-Programm in release/
```
`F11` oder `Alt+Enter` schaltet Vollbild um.
