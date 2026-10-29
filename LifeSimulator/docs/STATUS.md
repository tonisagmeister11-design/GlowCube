# Umsetzungsstand

Stand dieser Version. „Getestet" = automatisierter Test im Testrunner (92 Tests, alle
bestanden) bzw. Screenshot-Prüfung im OpenGL-Renderer unter Linux. Unter echtem Windows mit
Grafikkarte wurde das Spiel in dieser Umgebung **nicht** ausgeführt; Bildraten auf echter
Hardware sind nicht gemessen.

## Umgesetzt
| Bereich | Inhalt | Prüfung |
|---|---|---|
| Stadtgenerator | Seed-reproduzierbar, Straßennetz mit Ampeln/Kreisverkehren, Fluss mit Brücken, Blöcke/Grundstücke, 30 Gebäudearten, 16 benannte Märkte, Buslinien, Requisiten; mehrstufige Validierung + Reparatur (Überlappungen, Eingänge, Erreichbarkeit zu Fuß/Straße, Spawnpunkte) | 8 Seeds ohne Fehler, Reproduzierbarkeit |
| 3D-Darstellung | Chunk-Streaming, prozedurale Fassaden/Dächer, Ladenschilder, Markierungen, Wasser, Tag/Nacht, Wetter, Innenräume (20 Arten), Menü-Hintergrund | Tests + Screenshots |
| Simulation | Wirtschaft (160 Waren, Geschäfte mit Bestand/Preisen/Nachschub/Insolvenz, Firmen, Bank mit Krediten/Hypotheken), Immobilien, 45 Berufe mit Karriere, Bildung, ~2.400 NPCs mit Tagesablauf/Alterung/Geburt/Tod, Staatshaushalt | 60-Tage-Langlauf, Geldmengenerhalt |
| Spielerleben | Bedürfnisse, Lebensphasen, freies Startalter, Startsituationen (Eltern, Wohnung, WG, obdachlos, wohlhabend), Krankheit, Tod/Erbe gemäß Regeln | Tests |
| Kriminalität | 8 Delikte, Fahndung, Festnahme/Haft/Strafen, NPC-Kriminalität, Polizeilaufbahn, 4 Organisationen mit Aufstieg bis zum Paten | Tests |
| Politik | Parteien, Wahlen, Ämter bis Präsident, 17 wirksame Stellschrauben | Tests |
| Verkehr | Spurgraph, Ampeln, Vorfahrt, Kreisverkehre, Busse nach Fahrplan (zusteigen), Einsatzfahrzeuge, fahrbare und kaufbare Autos, Fußgänger aus der Bevölkerung | 271 Fahrzeuge/240 s ohne Kollision/Blockade |
| Charaktere | Prozeduraler Humanoid mit Skelett, altersabhängigen Proportionen, Animationen, LOD; Charaktereditor mit 3D-Vorschau | Tests + Screenshots |
| UI | Hauptmenü, Assistent, Laden/Speichern, Einstellungen inkl. Tastenbelegung, HUD, Smartphone-Apps, Stadtkarte, Minimap, Shop, Dialoge | Tests + Screenshots |
| Audio | 53 selbst synthetisierte Klänge/Musikstücke, getrennte Lautstärken | technisch geprüft, nicht angehört |
| Leistung | Fünf Stufen inkl. Leistungsmodus für schwache PCs, automatische Hardware-Einschätzung, Nachregelung bei Bildratenabfall, Kompatibilitäts-Renderer mit Neustart | Tests + Messungen (siehe docs/PERFORMANCE.md) |
| Story-Modus | 7 Missionen in 4 Kapiteln, darunter der Hinterhalt an der Hafenstraße mit Absprung aus dem fahrenden Wagen; Untertitel, Kinobalken, Zeitlupe; Vorbereitung für eigene Sprachaufnahmen | Tests + Bilder |
| Zugelieferte Modelle | Werkzeug `tools/prepare_vehicle.gd` bereitet FBX/GLB auf (Räder trennen, nach Material zusammenführen, eigene Materialien, einfache Fernfassung); ein Supersportwagen ist als kaufbares Fahrzeug eingebaut | Tests + Bilder |
| Speichern | Versioniert, komprimiert, Sicherungskopie, Autosave | Roundtrip-Test |
| Spielerführung | Begrüßung je Startsituation, Lebensziele mit Hinweis und Kartenmarkierung, Hilfe auf F1, Tagesübersicht | Tests + Screenshots |
| Kriminalität in der Welt | Missionsziele vor Ort, Stehlen im Laden, Taschendiebstahl, Autoaufbruch, Haft mit Absitzen und Kaution, Polizeidienst an der Wache | Tests |

## Bekannte Einschränkungen
* Busfahrten dauern Spielstunden (Fahrplan in realer Geschwindigkeit).
* Fenster sind Shader-Effekte (aus nächster Nähe flach); Haare ohne Strähnen-Geometrie.
* Kaufpreise für Immobilien sind im Verhältnis zur verdichteten Lebenszeit hoch.
* Start-Kamera kann ungünstig stehen (mit Maus drehen).
* Beim eingebauten Supersportwagen ist die Lizenz des zugelieferten Modells ungeklärt; vor einer Weitergabe des Spiels prüfen (siehe docs/MODELLE.md).
* Bedürfnisse: Ein voller Vorrat hält rund zwei Spieltage; wer tagelang gar nichts tut, stirbt – vorher warnt das Spiel mehrfach.
* Die EXE trägt das Standard-Godot-Symbol (Symbolwechsel erfordert rcedit unter Windows).
