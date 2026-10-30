# Eigene Sprachaufnahmen einfügen

Das Spiel erzeugt **keine** künstliche Stimme – eine Computerstimme würde die Szenen
billig wirken lassen. Stattdessen zeigt es Untertitel mit Sprechernamen und spielt eine
Sprachaufnahme ab, **sobald eine passende Datei vorhanden ist**. Fehlt sie, bleibt es beim
Untertitel; es gibt keine Fehlermeldung.

## So geht es

1. Sprich die Zeile auf (Handy genügt) und speichere sie als **WAV, mono, 32 kHz**.
2. Lege die Datei unter `assets/audio/voice/` mit genau diesem Namen ab:

| Datei | Sprecher | Rolle |
|---|---|---|
| `voice_pate.wav` | Der Pate | Auftraggeber, ruhig und bestimmt |
| `voice_luca.wav` | Luca Valcorvo | Fahrer, am Telefon, später in Panik |
| `voice_ramirez.wav` | Ramírez | Schütze der Gegenseite, kalt |
| `voice_bruno.wav` | Bruno | Fahrer der Gegenseite, genervt |
| `voice_nadia.wav` | Nadia Falk | Kontakt in der Bar |
| `voice_radio.wav` | Funk | Durchsagen, verrauscht |
| `voice_player.wav` | Deine Figur | eigene Antworten |

3. Spiel starten. Die Aufnahme läuft automatisch zur passenden Zeile.

## Mehrere Aufnahmen je Sprecher

Trage in `scripts/audio/sound_registry.gd` unter `VOICE` weitere Dateien in dieselbe Liste
ein, zum Beispiel:

```gdscript
"voice_pate": {"files": ["voice/voice_pate.wav", "voice/voice_pate_2.wav"], "vol": 0.0},
```

Das Spiel wählt dann abwechselnd eine davon – so klingt es weniger wiederholt.

## Zeilen pro Szene

Die Texte stehen in `scripts/story/story_catalog.gd`. Der Hinterhalt an der Hafenstraße
verwendet diese Reihenfolge:

1. Funk: „Hinter dir hält sich ein dunkler Pick-up. Seit drei Kreuzungen."
2. Luca: „Moment … wer zur Hölle ist das?"
3. Ramírez: „Bleib dran. Nicht zu nah."
4. Bruno: „Ich weiß, was ich tu."
5. Ramírez: „Näher ran. Ich hab ihn."
6. Ramírez: „Gute Reise."

Willst du Zeile für Zeile eigene Dateien, benenne sie `voice_<sprecher>_<nummer>.wav`,
trage sie wie oben ein und sag mir Bescheid – dann ordne ich sie den einzelnen Zeilen zu.

## Technisches

* Abspielweg: `scripts/story/story_runner.gd`, Methode `_speak()`. Sie fragt
  `Audio.has_sound("voice_<sprecher>")` und spielt nur ab, was wirklich da ist.
* Lautstärke regelt der Effekte-Regler unter Einstellungen → Audio.
* Lege keine fremden Aufnahmen hinein, an denen du keine Rechte hast.
