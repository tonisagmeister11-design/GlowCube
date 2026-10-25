# Leistungsstufen und Leistungsmodus

Das Spiel hat fünf Qualitätsstufen. Alle teuren Systeme holen ihre Grenzwerte
aus einer einzigen Stelle (`scripts/core/performance.gd`, Autoload `Perf`),
damit eine Stufe sofort überall wirkt – nicht nur an der Grafik.

| Stufe | Name | Für wen |
|---|---|---|
| 0 | Leistungsmodus | Sehr schwache PCs, Laptops mit Onboard-Grafik |
| 1 | Niedrig | Ältere PCs |
| 2 | Mittel | Übliche PCs (Standard) |
| 3 | Hoch | Kräftige PCs |
| 4 | Ultra | Starke PCs |

## Was der Leistungsmodus tut

| Bereich | Leistungsmodus | Mittel |
|---|---|---|
| Sichtweite | 220 m (dafür dichterer Nebel) | 600 m |
| Fernansicht der Stadt | aus | an |
| Schatten | aus | an |
| Renderauflösung | 58 % (Standard-Renderer) | 100 % |
| Fensterbreite | höchstens 1280 px | bis 1920 px |
| Passanten | 10 | 55 |
| Fahrzeuge | 14 | 65 |
| Geparkte Autos | 15 % | alle |
| Straßenlaternen mit echtem Licht | 0 | 10 |
| Figuren | einfachste Stufe | volle Stufe |
| Fenster in Fassaden | flach | mit Innenraum-Illusion |
| Laub | ohne Windbewegung | mit Wind |
| Bäume im Umland | 35 %, näher am Rand | alle |
| Simulationsschritte | halb so oft, dafür größer | normal |

Gemessen in der fertigen Welt (gleiche Stelle, gleicher Blick):

* **Dreiecke je Bild: 567.000 statt 4.379.000** (7,7-mal weniger)
* **Zeichenaufrufe je Bild: 718 statt 3.952** (5,5-mal weniger)
* Aufbauzeit der Umgebung: 321 ms statt 1.130 ms

Die Bildrate selbst lässt sich in der Entwicklungsumgebung nicht messen, weil
dort nur ein Software-Renderer ohne Grafikkarte zur Verfügung steht.

## Kompatibilitäts-Renderer (der größte Hebel)

Standardmäßig läuft das Spiel mit Vulkan. Ältere Rechner und viele Laptops
haben keine brauchbare Vulkan-Unterstützung – dort startet das Spiel entweder
gar nicht oder sehr langsam. Für diese Rechner gibt es den
Kompatibilitäts-Renderer (OpenGL):

* **Im Spiel:** Einstellungen → Grafik → Renderer → „Auf Kompatibilität
  umstellen". Das Spiel sichert den Spielstand und startet selbst neu.
* **Beim Start:** `Leistungsmodus.bat` im Spielordner doppelklicken. Sie startet
  `LifeSimulator.exe --rendering-method gl_compatibility`.

Hinweis: In diesem Modus wirkt die interne Renderauflösung nicht. Deshalb
begrenzt der Leistungsmodus dort zusätzlich die Fensterbreite auf 1280 Pixel;
im Einstellungsmenü gibt es dafür einen Knopf.

## Automatik

* **Beim ersten Start** schätzt das Spiel die Hardware am Namen der Grafikkarte
  ein und wählt eine passende Stufe (`level_for_adapter`). Erkennt es eine
  schwache Karte oder einen Software-Renderer, startet es gleich im
  Leistungsmodus.
* **Während des Spielens** senkt die Nachregelung Sichtweite, Passanten und
  Verkehr schrittweise (bis auf 45 %), wenn die Bildrate unter 32 fällt, und
  hebt sie wieder an, sobald wieder Luft ist. Abschaltbar unter
  Einstellungen → Grafik → „Automatisch anpassen".

## Für Entwickler

Werte abfragen: `Perf.f("view_distance")`, `Perf.i("max_peds")`,
`Perf.b("wind")`; fertige Werte inklusive Nutzerwunsch und Nachregelung:
`Perf.view_distance()`, `Perf.max_peds()`, `Perf.max_vehicles()`,
`Perf.shadows_enabled()`. Auf Änderungen reagieren: Signal `Perf.applied`.
Neue Stellschrauben kommen als Zeile in `Perf.TABLE` dazu (ein Wert je Stufe);
`tests/test_performance.gd` prüft, dass jede Zeile alle Stufen abdeckt und
dass die Werte gestaffelt sind.

Messen: `tests/visual/perf_bench.tscn` (alle Stufen an einer Stelle) und
`tests/visual/perf_live.tscn -- --level=N` (echtes Spiel).
