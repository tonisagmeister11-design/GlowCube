# Pigment Krieg – Roblox-Version

Schwarz gegen Weiß als Roblox-Spiel. Es ist ein Strategiespiel mit Seitenansicht:
Du beschwörst Stickmen, schmiedest Waffen, kaufst Helme und wählst die Arena.
Wenn zwei Spieler in verschiedenen Teams sind, kämpfen sie gegeneinander.
Ist ein Team leer, übernimmt der Computer.

## So testest du das Spiel (10 Minuten)

1. **Roblox Studio installieren:** auf <https://create.roblox.com> → „Studio herunterladen“.
   Mit deinem Roblox-Konto anmelden.
2. **`PigmentKrieg.rbxlx` herunterladen** und doppelklicken. Studio öffnet das Spiel.
   Geht das nicht: Roblox Studio öffnen → *Datei → Öffnen aus Datei…* → die Datei wählen.
3. Oben auf **Play** (▶) klicken oder **F5** drücken.
4. Im Menü eine Arena antippen und dann **SCHWARZ** oder **WEISS** wählen. Der Kampf startet nach 3, 2, 1.
5. Mit **Stop** (■) beendest du den Test.

**Mit 2 Spielern testen:** Reiter *Test* → bei *Clients and Servers* „2 Players“ einstellen → *Start*.
Es gehen zwei Fenster auf. Wähle in jedem Fenster ein anderes Team.

**Handy-Ansicht testen:** Reiter *Test* → *Device* → zum Beispiel „iPhone“ wählen.

## Steuerung

| PC | Handy / Tablet |
|---|---|
| `1` – `7` Krieger beschwören | Knöpfe unten antippen |
| `R` Meteor, dann aufs Feld klicken | Meteor antippen, dann aufs Feld tippen |
| `F` Kriegsschrei, `H` Heilwelle | runde Knöpfe |
| `Q` Verteidigen, `E` Angriff | Knöpfe oben rechts |
| `A` / `D`, Pfeiltasten, Mausrad, Ziehen = Kamera | übers Feld wischen |
| `M` Menü (Shop, Waffen) | MENÜ-Knopf |

## Was drin ist

- **7 Krieger:** Klinge, Speer, Bogen, Schild (blockt von vorne), Ninja, Magier (explodierende Kugeln), Titan.
- **3 Fähigkeiten:** Meteor, Kriegsschrei, Heilwelle.
- **Boss-Titan:** Fällt die Burg des Computers unter die Hälfte, kommt er mit Krone.
- **5 Arenen** mit eigenem Licht: Graue Steppe, Vulkankrater, Eiswüste, Neon-Kolosseum, Goldener Tempel.
- **Waffen schmieden:** Holz → Eisen → Gold → Diamant → Plasma. Dazu Rüstung, Pigment-Mine und Burg-Turm.
- **Helme:** Ritter, Ninja-Band, Wikinger, Spartaner, Krone und Gesichts-Helm.
- **Animationen:** Schwung-Spuren an den Waffen, Ragdoll-Physik beim Tod, abfliegende Köpfe, Tintenspritzer und Tintenflecken.
  Außerdem Krieger, die aus Tintenpfützen steigen, eine Burg, die in Trümmer zerfällt, Kamera-Wackeln und Schadenszahlen.
- **Speichern:** Münzen, Helme und Waffen bleiben erhalten, sobald das Spiel veröffentlicht ist (siehe unten).

## Gesichts-Helm mit deinem Foto (optional)

Ohne eigenes Bild zeigt der Gesichts-Helm das klassische Roblox-Gesicht.
So kommt dein Foto drauf:

1. In Studio: *Fenster → Asset Manager* öffnen → **Bulk Import** → `assets/face.png` wählen.
2. Rechtsklick auf das hochgeladene Bild → **Copy Asset ID**.
3. Im *Explorer*: `ReplicatedStorage → PK → Config` doppelklicken. In der Zeile
   `Config.FACE_IMAGE = ""` die ID zwischen die Anführungszeichen setzen, zum Beispiel
   `Config.FACE_IMAGE = "rbxassetid://1234567890"`.

Roblox prüft jedes hochgeladene Bild. Fotos von echten Gesichtern werden dabei oft abgelehnt.
Und wenn das Spiel öffentlich ist, kann jeder das Foto sehen. Sprich das vorher mit deinen Eltern ab.

## Veröffentlichen (kostenlos)

1. *Datei → In Roblox veröffentlichen…* → Namen eingeben, zum Beispiel „Pigment Krieg“ → *Erstellen*.
2. *Startseite → Spieleinstellungen → Sicherheit* → **„Studio-Zugriff auf API-Dienste“** einschalten.
   Erst dann werden Münzen, Helme und Waffen gespeichert.
3. Auf <https://create.roblox.com> beim Spiel unter *Zugriff* auf **Öffentlich** stellen.

## Für Bastler

- `src/` enthält den Lua-Code (Luau). `tools/build_place.py` baut daraus `PigmentKrieg.rbxlx`.
  Mit [Rojo](https://rojo.space) geht es auch über `default.project.json`.
- `tests/` testet ohne Roblox (braucht Python mit `lupa` und Node mit `luaparse`):
  - `sim_core.py` spielt komplette Kämpfe der Kampflogik in allen Arenen durch.
  - `test_pose.py` prüft alle Animationen auf Rechenfehler.
  - `run_game.py` spielt das ganze Spiel in einer Roblox-Attrappe durch: Server, Client, Menü-Knöpfe, Kampf, Shop und zweite Runde.
  - `parse_all.js` und `globals.js` prüfen auf Syntaxfehler und Tippfehler in Variablennamen.
