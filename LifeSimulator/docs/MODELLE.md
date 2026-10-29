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
