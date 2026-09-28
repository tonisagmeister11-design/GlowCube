# GTA City – Fabric-Mod für Minecraft 26.3

Die ganze Overworld ist eine riesige Stadt im Stil von Los Santos. Das Stadtgebiet ist rund 3 × 3 km groß.
Rundherum liegen Promenade, Strand und Meer, und die World Border begrenzt die Stadt.

## Was drin ist

**Stadt (eigener Weltgenerator)**
- Straßenraster mit Asphalt, gelben Mittellinien, Zebrastreifen, Haltelinien, Ampeln, Straßenlaternen,
  Hydranten und Mülleimern
- Downtown mit Wolkenkratzern bis ca. 290 Blöcke hoch: gestufte Türme, runde Glastürme, Scheibenhochhäuser,
  Antennen, Hubschrauberlandeplätze und Aufzüge (Lobby ↔ Dach)
- Midtown mit Bürohäusern, Parkplätzen und Tankstellen
- Wohngebiet mit Einfamilienhäusern (Garten, Hecke, Einfahrt, Pool, Satteldach) und Innenhöfen
- „Hills“ im Norden mit modernen Villen, Pools und Palmen
- Hafen im Südosten mit Lagerhallen, Containern und Portalkränen
- Parks mit Brunnen, Teich, Bänken, Bäumen und Blumen
- Spezialgebäude mit Leuchtschildern: Bank (mit Tresorraum), Ammu-Nation, 24/7, LSPD-Revier, Krankenhaus,
  Tankstelle (Zapfsäulen explodieren), Autohaus
- Strand mit Palmen, danach offenes Meer

**Leben in der Stadt**
- Passanten mit 24 eigenen Skins laufen über die Gehwege. Sie fliehen bei Schüssen, manche wehren sich,
  und im Hafen gibt es Gangs.
- Verkehr: KI-Autos fahren auf der rechten Spur und biegen an Kreuzungen ab
- Autos: Limousinen, SUVs, Sportwagen, Taxis und Polizeiautos, jeweils in mehreren Farben
- Geparkte Autos auf Parkplätzen, in Einfahrten, beim Autohaus und vor der Polizei

**GTA-Gameplay**
- Fahndungslevel mit 1–5 Sternen. Die Polizei kommt zu Fuß, im Streifenwagen mit Sirene und ab 4 Sternen als SWAT.
  Bei 1 Stern nehmen dich die Cops fest („BUSTED“), ab 2 Sternen schießen sie.
- Cops abhängen: Wenn dich kein Polizist mehr sieht, blinken die Sterne, und nach einiger Zeit ist die Fahndung vorbei.
- „WASTED“: Nach dem Tod wachst du im nächsten Krankenhaus auf und zahlst die Rechnung.
- Waffen: Pistole, Micro-SMG, Karabiner, Pumpgun, Scharfschützengewehr, Minigun, Raketenwerfer, Granaten,
  Messer und Baseballschläger. Dazu gibt es Munition, Magazine, Nachladen, Kopfschüsse, Rückstoß und Mündungsfeuer.
  Glas geht bei Treffern kaputt.
- Autos klauen: Rechtsklick auf ein Auto zieht den Fahrer raus. Du fährst mit WASD und hupst mit H.
  Bei Unfällen gibt es Schaden, am Ende brennt das Auto und explodiert.
- Geld: Startkapital $500. Passanten lassen Geld fallen, Geldautomaten zeigen deinen Kontostand.
- Läden: Ammu-Nation (Waffen und Munition), 24/7 (Essen, Medikits, Schutzweste) und das Autohaus
- Überfälle: Schleichen + Rechtsklick mit Waffe auf die Ladentheke
- Banküberfall: Mit dem Thermobohrer (gibt es bei Ammu-Nation) den Tresor aufbohren, 30 Sekunden durchhalten,
  dann liegt die Beute im Tresor.
- HUD: Geld, Fahndungssterne, Munition, Tacho und eine Minikarte mit Polizei-Punkten

Story-Missionen folgen später.

## Steuerung

| Taste | Aktion |
|---|---|
| Linksklick (Waffe) | Schießen (automatische Waffen: gedrückt halten) |
| Rechtsklick halten (Waffe) | Zielen (Fadenkreuz, genauer) |
| R | Nachladen |
| Rechtsklick auf Auto | Einsteigen / Auto klauen |
| WASD | Fahren |
| H | Hupe |
| Shift | Aussteigen |
| Rechtsklick auf Theke | Laden öffnen |
| Schleichen + Rechtsklick mit Waffe auf Theke | Laden ausrauben |
| Rechtsklick auf Aufzug | Lobby ↔ Dach |

## Bauen

Du brauchst Java 25. Gradle musst du nicht installieren, der Wrapper lädt die passende Fassung (9.8) selbst:

```
cd gta-city-mod
./gradlew build          # Windows: gradlew.bat build
```

Die fertige Mod liegt danach in `build/libs/gta-city-0.1.0.jar`. Eine fertig gebaute Fassung liegt auch
in `dist/`. Kopier sie zusammen mit der [Fabric API](https://modrinth.com/mod/fabric-api) (0.160.5+26.3
oder neuer) in den `mods`-Ordner einer Fabric-Installation (Loader 0.19.5) für Minecraft 26.3. Erstell dann
eine neue Welt mit dem Welttyp „Standard“: Der ist durch die Stadt ersetzt.

## Spieltest

Ein automatischer Test startet das echte Minecraft 26.3, legt eine Stadtwelt an und spielt die Mod durch:
Stadt und Weltgrenze, Startausrüstung, Passanten und Verkehr, Auto klauen und fahren, Schießen, Nachladen,
Fahndung und Polizei, Laden und Überfall, Raketenwerfer und Granate, Geldautomat und Aufzug, „WASTED“ mit
Aufwachen im Krankenhaus. Zu jeder Station entsteht ein Screenshot unter
`build/run/clientGameTest/screenshots/`, im Protokoll steht pro Prüfung eine Zeile `GTACITY-TEST OK|FEHLER`.

```
./gradlew runClientGameTest -Pspieltest
```

Ohne Bildschirm (Server, CI) mit einem virtuellen: `xvfb-run -a ./gradlew runClientGameTest -Pspieltest`.
Ohne Grafikkarte braucht Minecraft 26.3 dafür einen Software-Vulkan-Treiber (unter Ubuntu `mesa-vulkan-drivers`).

## Werkzeuge

- `tools/gen_items.py`, `tools/gen_skins.py`, `tools/gen_cars.py`: pixeln alle Texturen (Python + Pillow)
- `tools/gen_resources.py`: erzeugt Modelle, Blockzustände, Sprachdateien und das Welt-Preset
- `tools/preview/`: lässt den Stadtgenerator ohne Minecraft laufen und rendert Vorschaubilder
  (Draufsicht oder 3D-Isometrie)
