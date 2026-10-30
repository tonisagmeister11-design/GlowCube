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

## Die Marke KALDRA

Alle fünf zugelieferten Fahrzeuge gehören im Spiel zur erfundenen Marke
**KALDRA**: KALDRA Furio, Lucente, Saluto, Vento und Trail.
`scripts/traffic/brand_kaldra.gd` baut das Emblem (ein liegendes Sechseck mit
kantigem K, 96 Dreiecke) als Mesh und als Bild für Texturflächen.

Beim Aufbereiten werden fremde Markenzeichen auf drei Wegen entfernt:

| Weg | Wann | Beispiel |
|---|---|---|
| **Nach Namen** (immer an) | Das Teil heißt `Logo`, `Emblem`, `Badge`, `Nameplate`, `Lettering` oder `Marque` | Heckemblem des Furio, `Front_Logo_Part1/2` des Saluto |
| **`--drop-box`** | Das Zeichen steckt in einem gemeinsamen Netz, liegt aber nicht in der Lackfläche | Zierteile in Chrom oder Metall |
| **`--cover`** | Das Zeichen steckt in **derselben** Lackfläche wie die Karosserie | Die vier Ringe des Lucente: Herausschneiden hätte 149.000 Dreiecke und damit ein Loch im Kühlergrill gekostet, deshalb liegt dort eine dunkle Zierblende mit dem KALDRA-Zeichen darauf |

Steckt das Zeichen in der **Textur**, hilft keiner dieser Wege:
`tools/retexture_vehicle.py` malt es dort um. Beim Trail sind so der
Sieben-Schlitz-Grill zu waagerechten Stegen und die kreuzförmigen Rückleuchten
zu einem Lichtband geworden; das KALDRA-Zeichen sitzt mitten im Grill.
Mit `--only-patches` lässt sich damit jede Textur stellenweise übermalen, auch
die eines Kleidungsstücks.

`--stretch=L,H,B` ändert die Maßverhältnisse um wenige Prozent. Dem einzelnen
Wagen sieht man das nicht an, aber seine Maße stimmen dann nicht mehr mit einem
Serienfahrzeug überein.

### Wie nah ist welches Fahrzeug noch am Ausgangsmodell?

| Fahrzeug | Fremde Zeichen | Form | Einschätzung |
|---|---|---|---|
| KALDRA Trail | entfernt (Textur neu: Grill, Leuchten, Schriftzüge) | 3 % geändert | Grill und Leuchtbild sind die Erkennungszeichen und jetzt eigene; die Silhouette bleibt die des Ausgangsmodells |
| KALDRA Lucente | abgedeckt (Blende vorn und hinten) | 2 % geändert | Silhouette unverändert, Zeichen nicht mehr sichtbar |
| KALDRA Furio | 7 Teile entfernt, eigenes Emblem | 3 % geändert | Silhouette unverändert |
| KALDRA Saluto | 2 Teile entfernt, eigenes Emblem | 3 % geändert | Silhouette unverändert |
| KALDRA Vento | eigenes Emblem | 3 % geändert | Silhouette unverändert |

**Das betrifft nur Markenzeichen und Form.** Am Urheberrecht des Netzes ändert
keine dieser Bearbeitungen etwas; dazu der nächste Abschnitt.

## Rechtliches – bitte lesen

* **Markennamen echter Hersteller werden im Spiel nicht verwendet.** Alle Marken sind
  erfunden. Ein Modell eines echten Fahrzeugs bekommt deshalb einen eigenen Namen.
* **Eine Bearbeitung verschafft keine Rechte.** Wer ein 3D-Modell verändert,
  schafft eine Bearbeitung – die Rechte bleiben beim Urheber des Modells, egal
  wie stark die Änderung ist. Markenzeichen zu entfernen löst das Markenrecht,
  nicht das Urheberrecht am Netz. Wirklich saubere Rechte gibt es nur über eine
  Lizenz oder über ein selbst gebautes Modell (dafür steht
  `scripts/traffic/vehicle_mesh_builder.gd` bereit).
* **Die Form eines echten Serienautos** kann als Geschmacksmuster geschützt
  sein, auch unabhängig vom Modell. Deshalb ändert `--stretch` die
  Maßverhältnisse und werden Grill und Leuchtbild ersetzt.
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

### Vorn ist -Z

Im ganzen Spiel zeigt die Fahrzeugfront nach **-Z**. Zugelieferte Modelle
kommen oft andersherum; dann fährt das Fahrzeug im Spiel rückwärts. Alle fünf
hier waren so. Das Werkzeug erkennt das an den Leuchten (rot gehört nach
hinten, weiß nach vorn) und dreht das Fahrzeug; hat ein Modell keine
unterscheidbaren Leuchten, hilft `--flip`. Ein Test prüft die Lage
(`test_vehicles_face_forward`).

### Scheinwerfer leuchten nicht

Die Leuchtenmaterialien tragen **kein** Eigenleuchten. Ein zugeliefertes Modell
lässt sich nachts nicht umschalten, und am Tag sähe ein leuchtender Scheinwerfer
aus wie eine Glühbirne. Stattdessen gibt es einen hellen, glatten Reflektor.

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

---

# Personenmodelle einbauen

Für Figuren gilt ein anderer Weg als für Fahrzeuge: Die Meshes hängen über
Knochengewichte am Skelett. Würde man sie wie eine Karosserie zusammenführen
oder vereinfachen, gingen Skelett und Gewichte verloren und die Figur bliebe
starr. Das Werkzeug `tools/prepare_person.gd` lässt die Geometrie deshalb in
Ruhe und repariert stattdessen, was beim Import schiefgeht.

```
godot --headless --path . --script tools/prepare_person.gd -- \
    --in=res://assets/models/people/<datei>.fbx \
    --out=res://assets/models/people/<name>.scn \
    --name="Bruno" --height=1.82
```

Es tut vier Dinge:

1. **Unsichtbares wegwerfen** – Zähne, Zunge, Tränenkanal und Augenschatten
   sieht man bei geschlossenem Mund nie. Beim Beispielmodell waren das 5.994
   Dreiecke von 73.016.
2. **Aufrichten und skalieren** – Modelle kommen oft mit Z nach oben und
   liegen dann auf dem Rücken; `--height` setzt die Körpergröße in Metern.
3. **Materialien reparieren.** Zwei Schalter macht der FBX-Import falsch:
   * `vertex_color_use_as_albedo` ist an, die Eckpunktfarben sind aber
     schwarz – **die Haut wird dadurch komplett schwarz.**
   * `emission_enabled` ist mit weißer Farbe an, die Figur würde leuchten.
   Zusätzlich bekommen Haare, Wimpern und Hornhaut eine Alphaschwelle;
   ohne sie legen sich die Haarbüschel als dunkle Fläche über das Gesicht.
4. **Knochen zuordnen** – 18 Rollen (Becken, Ober-/Unterschenkel, Fuß,
   Wirbelsäule, Brust, Hals, Kopf, Ober-/Unterarm, Hand) werden gegen die
   üblichen Benennungen geprüft: Unreal/Rigify (`thigh_l`), Mixamo
   (`LeftUpLeg`), Character Creator (`CC_Base_L_Thigh`), Blender (`thigh.l`).
   Die Zuordnung steht danach in der Szene, es wird nichts geraten.

## Bewegung ohne mitgelieferte Animationen

Zugelieferte Figuren kommen fast immer in T-Haltung und ohne Bewegungsablauf.
`scripts/people/body_animator.gd` dreht die Knochen selbst: erst in eine
Grundhaltung mit herabhängenden Armen, dann im Gehtakt (Schrittweite und
Takt hängen an der Geschwindigkeit), dazu Atmen im Stand und eine Sitzhaltung.

Die Drehachse eines Knochens ist in jedem Modell anders. Deshalb wird aus der
Ruhelage ausgerechnet, welche Achse im Knochenraum der Weltachse entspricht –
"Oberschenkel nach vorn" dreht dadurch auch wirklich nach vorn.

## Wo die Figuren im Spiel auftauchen

Sie **ersetzen die prozeduralen Figuren nicht.** Der Charaktereditor braucht
Alter von 0 bis 100 mit passenden Proportionen; ein fertiges
Erwachsenenmodell kann ein Kind nicht darstellen. Die zugelieferten Modelle
kommen dort zum Einsatz, wo Nähe zählt:

* als Passanten in unmittelbarer Spielernähe (Entfernungsstufe 0, ab 18
  Jahren, passendes Geschlecht),
* begrenzt durch `PersonModels.MAX_BY_LEVEL` – in den unteren
  Leistungsstufen gar keine, sonst 3 bis 10 gleichzeitig. Eine Figur kostet
  mit 67.000 Dreiecken so viel wie ein halbes Spielerfahrzeug.

Ab Entfernungsstufe 2 wird der Bewegungsablauf nicht mehr gerechnet; das
Verformen des Skeletts ist der teuerste Teil.

## Modelle ohne Skelett

Eine Figur ohne Skelett kann nicht gehen. `animated: false` in
`PersonModels.MODELS` hält sie aus dem Passantenverkehr heraus; sie bleibt für
stehende Rollen brauchbar.


## Spielarten: aus einem Modell werden fünf Leute

Ein fertiges Personenmodell ist teuer – 66.000 Dreiecke und 32 MB Texturen.
Statt mehrere Modelle zu brauchen, wird dasselbe beim Aufbauen eingefärbt:
`PersonModels.VARIANTS` hält je Spielart einen Hautton, Kleidungsfarben je
Mesh, Statur und Körpergröße. `DetailedPerson` legt dafür Materialkopien an und
multipliziert deren Albedo-Farbe mit der Textur – die Texturdateien bleiben
unangetastet.

Beim ersten Modell sind das fünf Leute (Bruno, Teo, Marek, Idris, Sören) mit
Körpergrößen von 1,75 m bis 1,87 m. `PersonModels.pick_variant()` wählt immer
die Spielart, die gerade am seltensten zu sehen ist – sonst liefen fünfmal
dieselben Leute durch die Stadt.

Was die Reparatur am Beispielmodell gebraucht hat:

| Problem | Ursache | Behebung |
|---|---|---|
| Figur stand ohne Kleidung da | Eine frühere Fassung blendete ab mittlerer Entfernung alles außer dem Körper aus | Es wird nichts mehr ausgeblendet; auf Entfernung wird nur der Bewegungsablauf angehalten |
| Komplett in die Kapuze eingepackt | Die Kapuze gehört zum Mesh `Vest` und liegt über dem Kopf | `--drop-region=Vest:0,0.845,0,1,1,1` schneidet sie heraus (782 Dreiecke) |
| Auffallend dünn | Das Modell ist schlank gebaut | `--bulk=1.07` verbreitert in X und Z, nicht in Y |
| Handflächen standen ab | In der T-Haltung zeigen die Handflächen nach unten; bei gesenkten Armen dann nach vorn | Grundhaltung dreht die Hände um die Armachse |
| Sitzhaltung geknickt | Zu kleine Winkel an Hüfte und Knie | Oberschenkel waagerecht, Unterschenkel senkrecht |

## Eigenes Aussehen für die Spielerfigur

Ein Modell mit der Rolle `skin` lässt sich im Charaktereditor unter „Aussehen"
als Spielerfigur wählen. Voraussetzung ist ein Skelett – ohne eines stünde der
Spieler bewegungslos da; ein Test besteht darauf
(`test_skin_models_can_walk`).

Unter 18 Jahren ist die Auswahl gesperrt: Ein fertiges Erwachsenenmodell kann
kein Kind darstellen. Dafür bleibt die prozedurale Figur zuständig, die jedes
Alter von 0 bis 100 mit passenden Proportionen baut.

Damit die Schrittgeräusche weiterspielen, meldet `BodyAnimator` über das Signal
`footstep`, wenn ein Fuß aufsetzt – die Spielerfigur hängt ihre Geräusche
daran.
