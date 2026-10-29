# Zugelieferte 3D-Modelle einbauen

Eigene oder zugekaufte 3D-Modelle lassen sich als Fahrzeuge ins Spiel übernehmen.
Fertige Modelle aus Modellierprogrammen bestehen meist aus hunderten Einzelteilen –
jedes kostet einen Zeichenaufruf, 500 Teile für ein Auto wären für die Bildrate untragbar.
Deshalb gibt es ein Aufbereitungswerkzeug.

## Schritt 1: Modell ins Projekt legen

Godot 4.3 liest **GLB/glTF** und **FBX** direkt. Leg die Datei nach
`assets/models/vehicles/` und importiere einmal:

```
godot --headless --path . --import
```

Fehlen Texturen (häufig, weil sie in einem Nebenordner liegen), ist das kein Problem:
Das Werkzeug legt eigene Materialien an (Lack, Glas, Chrom, Carbon, Gummi, Innenraum).

## Schritt 2: Aufbereiten

```
godot --headless --path . --script tools/prepare_vehicle.gd -- \
    --in=res://assets/models/vehicles/<datei>.fbx \
    --out=res://assets/models/vehicles/<name>.scn \
    --name="Erfundener Name" --color=1,0.72,0.05
```

Das Werkzeug:

* trennt die Räder ab, damit sie sich drehen (erkennt `Wheel_FL`, `Tire_RR`, `Rim_*` …),
* führt alles Übrige nach Materialgruppe zu wenigen Meshes zusammen,
* legt PBR-Materialien an,
* erzeugt eine vereinfachte Außenhülle für große Entfernungen und den Leistungsmodus,
* speichert die fertige Szene.

Beim Beispielwagen wurden aus **504 Teilen 10 Gruppen plus 4 Räder** – statt 504
Zeichenaufrufen nur noch 14.

## Schritt 3: Als Fahrzeug eintragen

Drei Stellen:

1. `scripts/traffic/imported_vehicles.gd` → Eintrag unter `VEHICLES` mit Szenenpfad,
   Leistungsdaten, **Herkunft und Lizenz**.
2. `scripts/economy/goods_catalog.gd` → Ware fürs Autohaus (`car_…`).
3. `scripts/traffic/vehicle_catalog.gd` → `GOOD_TYPES` (Fahrzeugtyp) und
   `GOOD_IMPORTED` (Verbindung Ware → Modell).

Danach steht das Auto im Autohaus zum Kauf, lässt sich fahren, parkt beim Zuhause
und erscheint im Leistungsmodus automatisch in der vereinfachten Fassung.

## Rechtliches – bitte lesen

* **Markennamen echter Hersteller werden im Spiel nicht verwendet.** Alle Marken sind
  erfunden. Ein Modell eines echten Fahrzeugs bekommt deshalb einen eigenen Namen.
* **Kläre die Lizenz, bevor du das Spiel weitergibst.** Trag in `VEHICLES` unter `source`
  und `license` ein, woher das Modell stammt und was erlaubt ist. Viele kostenlose Modelle
  verlangen eine Namensnennung (CC-BY), andere verbieten die kommerzielle Nutzung.
* Modelle, die aus anderen Spielen entnommen wurden, gehören nicht in ein Spiel, das du
  weitergibst – auch nicht kostenlos.
* Für dein privates Spielen auf dem eigenen Rechner ist das unkritisch.

## Grenzen

* Sehr dichte Modelle (über etwa 400.000 Dreiecke) solltest du vorher in Blender mit
  „Decimate" reduzieren; das Werkzeug fasst nur zusammen, es vereinfacht keine Geometrie.
* Animationen (Türen, Lenkung) werden nicht übernommen, nur die Räder drehen sich.
* Innenräume bleiben erhalten, werden aber ab 25 m Entfernung ausgeblendet.

## Warum die Rohdatei nicht im Projektarchiv liegt

Die FBX/GLB-Quelldatei wird **nicht** mit eingecheckt (`.gitignore`) – sie ist mit knapp
40 MB grösser als das fertige Spielmodell und wird nach der Aufbereitung nicht mehr gebraucht.
Im Projekt liegt nur die fertige `.scn` (binär, deutlich kleiner als die Textfassung).
Willst du ein Modell erneut aufbereiten, lege die Quelldatei wieder in
`assets/models/vehicles/` und starte Schritt 1 und 2.


## Was das Werkzeug im Einzelnen tut

| Schritt | Warum |
|---|---|
| Zerlegen in Teil **und Oberflaeche** | Manche Modelle bestehen aus einem einzigen Netz mit zwanzig Materialien. Erst getrennt lassen sich Lack, Glas und Reifen auseinanderhalten. |
| Maßstab und Ausrichtung (`--length`) | Modelle kommen in Zentimetern, Zoll oder Einheiten und zeigen in beliebige Richtung. Das Werkzeug dreht die laengste waagerechte Achse nach vorn und skaliert auf die angegebene Laenge. |
| Raeder abtrennen | Ueber die Teilenamen (`Wheel_FL`, `Tire_RR` …), sonst ueber die Geometrie: unten, seitlich aussen, in der Seitenansicht rund. Bei Modellen aus einem einzigen Netz werden die Raddreiecke einzeln herausgeschnitten. |
| Nach Materialgruppe zusammenfuehren | Aus hunderten Zeichenaufrufen werden zehn. |
| Vereinfachen (`--budget`) | Erst meshoptimizer (den Godot mitbringt), bei Netzen, die der verweigert — typisch Glasscheiben — eine Rasterung. An Lack und Carbon geht die Rasterung nie ran, sonst knittert die Karosserie. |
| Materialien | Eigene PBR-Materialien je Gruppe, oder mit `--textures` bzw. `--albedo=` die mitgelieferte Textur. Rueck- und Frontleuchten leuchten selbst. |
| Fernfassung | Ein zusaetzliches, hart reduziertes Mesh fuer grosse Entfernungen und den Leistungsmodus. |

### Wie viel Budget?

Die Stufenleiter von meshoptimizer springt um etwa das Vierfache, und das
Werkzeug naehert sich dem Budget von **oben** an — lieber ein paar Dreiecke zu
viel als eine aufgerissene Karosserie. Anhaltspunkte:

| Verwendung | Budget | Beispiel aus diesem Projekt |
|---|---|---|
| Stadtverkehr (viele gleichzeitig) | 6.000–12.000 | Borek Trail: 4.734 Dreiecke |
| Normales Kauffahrzeug | 18.000–30.000 | Kastor Vento 16.564, Aurel Saluto 26.030 |
| Spitzenfahrzeug (eines zur Zeit) | 60.000–120.000 | Vanari Furio 96.155 |

Modelle, die als Standbild gerechnet wurden, vertragen manchmal fast keine
Reduktion: der Vanari Lucente kommt aus 2,53 Millionen Dreiecken und sieht
erst ab etwa 230.000 wieder sauber aus. Solche Fahrzeuge bleiben dem Spieler
vorbehalten; im Verkehr und im Leistungsmodus greift die Fernfassung mit
14.000 Dreiecken.

## Als Verkehrsfahrzeug freigeben

Ein Eintrag in `ImportedVehicles.VEHICLES` mit `"traffic": true` darf auch im
Stadtverkehr auftauchen — aber nur, wenn er wirklich leicht ist; ein Test
(`tests/test_imported_vehicles.gd`) besteht auf hoechstens 12.000 Dreiecken.
Der Verkehr setzt solche Modelle ab Leistungsstufe 2 und nur bei jedem dritten
Fahrzeug ein, damit nicht die ganze Stadt dasselbe Auto faehrt.
