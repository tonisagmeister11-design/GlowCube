# PANDEMIC · GlowCube

Ein globales Krankheits-Strategiespiel im Browser. Der Spieler entwickelt einen
Erreger und versucht, die Menschheit zu besiegen, bevor ein Heilmittel fertig
wird. Alle 3D-Krankheitsmodelle werden mit **Blender** erzeugt, animiert,
optimiert und als echte GLB-Assets in das Spiel geladen.

## Spielen

Die fertige Datei ist **`dist/index.html`** – eine einzige Datei ohne externe
Abhängigkeiten. Einfach im Browser öffnen. Kein Server, kein Blender, keine
Installation nötig. Alle 3D-Modelle und Weltdaten sind eingebettet.

## Was drin ist

- **12 Erregertypen** mit eigenen Modellen und Mechaniken: Bakterie, Virus,
  Pilz, Parasit, Prion, Nano-Virus, Bio-Waffe, Neurax-Wurm, Necroa-Virus,
  Simian Flu, Shadow Plague, Xenolith.
- **Sondersiege**: Neurax (Gedankenkontrolle), Necroa (Zombies), Simian (Affen),
  Shadow (Vampire), Xenolith (Xenoforming) – plus der normale Auslöschungssieg.
- **Lebende Weltsimulation**: 173 Länder mit Bevölkerung, Klima, Wohlstand,
  Medizin, Urbanisierung, Nachbarn, Flughäfen und Häfen. Übertragung über Land,
  echte Flugrouten und Seewege (animierte Flugzeuge & Schiffe). Länder schließen
  bei Gefahr Flughäfen, Häfen und Grenzen.
- **Evolution**: Übertragungswege, Symptom-Hexraster, Fähigkeiten & Resistenzen,
  Rückentwicklung. DNA-Punkte durch Infektionen, neue Länder und anklickbare
  DNA-Blasen.
- **Heilmittelforschung** abhängig von medizinischer Infrastruktur, plus über
  100 zustandsabhängige deutsche Nachrichten und Ereignisse.
- **3D**: Startbildschirm und Krankheitsbildschirm zeigen das rotierende,
  pulsierende Blender-Modell; ein Körper-Hologramm lässt die zu den Symptomen
  gehörenden Organe aufleuchten. Modellwechsel mit Partikel-Übergang.

## Blender-Pipeline

Die Modelle entstehen prozedural über die Blender-Python-API (Blender 5.2, `bpy`).

```
blender/scripts/build_all.py     # baut alle Modelle, exportiert GLB, prüft (QA)
blender/scripts/lib.py           # Mesh-/Material-/Rig-/Animations-Helfer
blender/scripts/pathogens/*.py   # je ein Modul pro Erreger
blender/PANDEMIC_GAME.blend      # gespeicherte Szene (Collections je Erreger)
```

Jedes Modell hat echte Geometrie, Materialien, Armatures/Rigging, Shape Keys und
benannte Keyframe-Animationen (Idle + Spezialanimationen wie Mutation, Auswahl,
Sporenausstoß, Kontrolle …). Der Export läuft als GLB (NLA-Tracks → benannte
Animationen) und wird mit `gltfpack` (Meshopt) für den Browser komprimiert.

## Neu bauen

```bash
npm install
python blender/scripts/build_all.py   # (bpy) Modelle -> blender/export/*.glb
node tools/optimize-models.mjs         # gltfpack -> assets/models/*.glb
node tools/build-data.mjs              # Länder-/Kartendaten -> src/data/world.json
node tools/gen-assets.mjs              # GLBs als base64 -> src/generated/
node tools/build-single.mjs            # -> dist/index.html
```

## Tests

```bash
node tools/sim-test.mjs        # Headless-Simulation: Sieg/Niederlage, Balance, Reaktionen
node tools/build-single.mjs
node tools/browser-test.mjs    # Playwright: alle Modelle laden, komplette Partie, Screenshots
```

## Steuerung

- Maus: Karte ziehen, Mausrad zoomen, Land anklicken (Info), DNA-Blasen anklicken.
- Leertaste: Pause. Tasten 1–5: Geschwindigkeit. „Krankheit"-Knopf: Evolution.
