# Audio – Klänge, Musik, Ambiente

Alle Klänge des Spiels sind **eigene, synthetisch erzeugte Werke** dieses
Projekts. Es werden keine Samples, Loops oder Musikstücke Dritter verwendet;
rechtlich gibt es daher keine Lizenzfragen. Erzeugt werden sie reproduzierbar
mit `tools/gen_audio.py` (Python 3 + numpy, keine weiteren Abhängigkeiten).

> **Ehrlicher Hinweis:** Die Klänge wurden technisch geprüft (Pegel, Clipping,
> Gleichanteil, Schleifennähte, Spektrum, Lautheit), aber **nicht angehört**.
> Sie sind als solide Grundausstattung gedacht. Wer bessere, lizenzrechtlich
> saubere Aufnahmen hat, kann jede Datei einfach ersetzen (siehe unten).

## Bestandteile

| Datei | Aufgabe |
|---|---|
| `scripts/audio/audio_manager.gd` | Autoload `Audio`: UI-Klänge, 2D/3D-Effekte (Pools), Musik mit Überblendung, Ambiente-Schichten, EventBus-Anbindung |
| `scripts/audio/sound_registry.gd` | `SoundRegistry`: Name → Datei(en) + Parameter, Liste fehlender Inhalte (`PLANNED`) |
| `scripts/audio/ambience_director.gd` | `AmbienceDirector`: wählt Ambiente/Musik nach Ort, Uhrzeit, Innenraum, Wetter |
| `scripts/audio/ui_sounds.gd` | `UISounds.attach(control)`: Hover/Klick für alle Buttons eines Menüs |
| `scripts/audio/engine_sound.gd` | `EngineSound`: Motorklang für Fahrzeuge (Leerlauf/Drehzahl-Überblendung) |
| `tools/gen_audio.py` | Generator + Analyse aller WAV-Dateien |
| `tests/test_audio.gd` | Tests (Dateien, Schleifen, API headless, fehlende Klänge, Director) |

### Busse und Lautstärkeregler

`Settings.apply_audio()` legt `Master`, `Music`, `SFX`, `Ambient` an; ihre
Lautstärken stehen in `user://settings.cfg`, Abschnitt `audio`
(`master`, `music`, `sfx`, `ambient`) – das sind die drei getrennten Regler
für Musik, Effekte und Ambiente plus Gesamtlautstärke. Der AudioManager
ergänzt `AmbientOutdoor` (sendet an `Ambient`, also vom Ambiente-Regler
erfasst) mit einem Tiefpass, der in Innenräumen eingeschaltet wird.

| Bus | Inhalt |
|---|---|
| `Music` | Musiktitel |
| `SFX` | UI-Klänge, Schritte, Türen, Fahrzeuge, alle Effekte |
| `Ambient` | Innenraum-Ambiente (Laden) und Summe von `AmbientOutdoor` |
| `AmbientOutdoor` | Stadt, Park, Wohngebiet, Regen, Fluss |

## API (Autoload `Audio`)

```gdscript
Audio.play_ui("click")                       # "ui_click"; auch "hover", "confirm", "back", "error",
                                             # "notification", "money_gain", "money_spend",
                                             # "phone_open", "phone_close", "levelup"
Audio.play_sfx("footstep_grass", global_position)        # 3D (Vector3 oder Node3D)
Audio.play_sfx("door_open", door_node, -3.0, 1.0)        # name, pos, volume_db, pitch
Audio.play_sfx("cash_register")                          # ohne Position = 2D
Audio.play_music("menu" | "day" | "night" | "", fade := 2.0)
Audio.set_ambience({"city_day": 1.0, "park": 0.4}, fade := 3.0)
Audio.attach_loop("siren", police_car) -> AudioStreamPlayer3D   # Dauerklang, folgt dem Knoten
Audio.set_outdoor_muffled(true)              # Aussengeräusche dämpfen
Audio.stop_all()
Audio.preload_all()                          # optional: alle Effekte vorladen
```

* Namen mit mehreren Dateien (z.B. `footstep_concrete`) wählen zufällig eine
  Variante, nie zweimal dieselbe hintereinander; dazu leichte
  Tonhöhenvariation (`pitch` in der Registry).
* Pools: 6 UI-, 10 2D- und 24 3D-Spieler. Sind alle belegt, wird der älteste
  Klang abgebrochen. Gleiche 2D-Klänge innerhalb von 35 ms werden verworfen.
* Musik und Ambiente blenden mit Gleichleistungskurve über; der Autoload läuft
  auch bei pausiertem Spiel (`PROCESS_MODE_ALWAYS`).
* Beim Start (nicht headless) beginnt automatisch die Menümusik
  (`Audio.auto_menu_music`).

### Automatische EventBus-Anbindung

| Signal | Reaktion |
|---|---|
| `notify(text, kind)` | `money` → Geld-Klang, `danger` → Fehler, sonst Benachrichtigung |
| `player_money_changed(balance)` | Geld erhalten/ausgegeben je nach Vorzeichen (ab 0,50; max. alle 0,3 s) |
| `game_started(world)` | Menümusik → Tag- oder Nachtmusik je nach Uhrzeit |
| `returned_to_menu()` | Ambiente aus, Menümusik |
| `player_job_changed(job_id)` | neuer Job/Beförderung → Jingle `levelup` |
| `open_panel("phone", …)` / `close_panels()` | Telefon auf/zu |

### Ambiente und Musik in der Welt (`AmbienceDirector`)

Die Weltszene (andere Zuständigkeit) bindet den Director so ein:

```gdscript
var amb := AmbienceDirector.new()
add_child(amb)
amb.setup(Game.world, func() -> Vector3: return player.global_position)
# beim Betreten/Verlassen eines Gebäudes:
amb.set_indoors(true, "shop")   # Art: shop, supermarket, cafe, restaurant, ... oder "home"
amb.set_indoors(false)
amb.set_weather("rain")         # sobald es Wetter gibt
```

Regeln (jede Sekunde neu berechnet, Überblendung 4 s):

* Zone des Stadtteils (`world.city.district_at(Vector2(x, z)).zone`):
  städtische Zonen → `city_day`/`city_night`; `residential`, `suburb`,
  `rich`, `estates` → `residential` + leise Stadt.
* Parks (`world.city.parks`): innen volles Park-Ambiente, bis 45 m außerhalb
  ausblendend; die Stadt wird entsprechend leiser.
* Fluss (`world.city.river`): bis 70 m Entfernung Wasserrauschen.
* Tageszeit (`world.get_hour_f()`): Übergänge 5–7 Uhr und 19–21 Uhr; nachts
  Grillen statt Kinder/Vögel.
* Innenräume: Laden-Ambiente bei Ladenarten, Außengeräusche leise und per
  Tiefpass gedämpft.
* Musik: Tag (6:00–19:30) bzw. Abend/Nacht (Lo-Fi). Dezent: nach 2,5–5 min
  Musik folgt eine Pause von 45–120 s (`music_rest`, abschaltbar).

### Menüs

```gdscript
func _ready() -> void:
	UISounds.attach(self)                     # alle Buttons, auch später hinzugefügte
	$Buttons/Start.set_meta("ui_sound", "confirm")
	$Buttons/Zurueck.set_meta("ui_sound", "back")
	$Buttons/Liste.set_meta("ui_hover", false)
```

### Fahrzeuge (`EngineSound`)

```gdscript
var eng := EngineSound.new()
eng.base_pitch = 0.7        # Bus tiefer
add_child(eng)
eng.set_load(absf(speed) / max_speed, throttle)   # pro Frame
eng.horn("bus")
```

Leerlauf- und Drehzahlschleife werden nach Last überblendet, die Drehzahl
verschiebt die Tonhöhe (`pitch_scale` 0,7 … 1,7 × `base_pitch`).

## Klänge hinzufügen oder ersetzen

1. Datei (WAV, OGG oder MP3) nach `assets/audio/sfx|ambience|music/` legen.
   Ersetzen: gleicher Dateiname – fertig.
2. Neuer Name: eine Zeile in `scripts/audio/sound_registry.gd` (`SFX`,
   `AMBIENCE` oder `MUSIC`), z.B.
   `"thunder": {"files": ["sfx/thunder_1.wav", "sfx/thunder_2.wav"], "vol": -4.0, "pitch": 0.05, "dist": 400.0, "unit": 30.0},`
3. Projekt importieren (`$GODOT --headless --path . --import`) und
   `.import`-Datei mit einchecken.
4. Schleifen: Ambiente/Musik/`loop: true` laufen immer als Schleife. Fehlen in
   der Datei Schleifenpunkte, setzt der AudioManager sie selbst (ganze Datei).
   Die Naht muss in der Datei selbst stetig sein.
5. Fehlt eine Datei oder ein Name, gibt es genau eine Warnung im Log
   (`[audio]`) und der Klang bleibt stumm – das Spiel stürzt nicht ab.

Komprimierung: Alle Dateien werden als PCM importiert (Godot-Standard,
`compress/mode=0`). Godot 4.3 bietet zusätzlich QOA (`compress/mode=2` in der
`.import`-Datei, ca. 5:1); das wurde geprüft und funktioniert, ist aber nicht
aktiviert, weil saubere Schleifennähte mit QOA nicht ohne Abhören bestätigt
werden konnten. Für eine kleinere Exportgröße kann man Musik/Ambiente auf QOA
umstellen und die Nähte anhören.

## Wie die Klänge entstanden sind

`tools/gen_audio.py` enthält kleine DSP-Bausteine in numpy: Filter im
Frequenzbereich (nullphasig, zirkulär für Schleifen), kausale Biquads (für
Resonanzen ohne Vorschwingen), FFT-Faltung, synthetischer Nachhall
(frequenzabhängig abklingendes Rauschen + frühe Reflexionen), Lautheitsmessung
(BS.1770-Näherung) und ein weicher Begrenzer.

* **UI:** Glocken-/Marimba-Teiltöne mit kurzen Hüllkurven, leiser Raumanteil;
  Fehler-Ton weich (Dreieck-ähnlich, tiefpassgefiltert), nicht schrill.
* **Schritte:** Beton = gefilterter Rauschimpuls + tiefer Aufprall + Abrollen;
  Gras = dichte Mikroimpulse (Rascheln); Holz = Modalsynthese (Brettmoden),
  eine Variante mit leichtem Knarzen. Je 4 Varianten mit zufälligen Parametern.
* **Türen:** Metallklicks (Modalsynthese), Knarzen als Stick-Slip-Impulsfolge
  durch Resonanzfilter, Luftzug, dumpfer Schlag. Ladenglocke mit
  Messingglocken-Teiltönen, Kasse = Schublade + Glocke + Münzen.
* **Fahrzeuge:** Motoren als Zündimpulsfolge durch Auspuffresonanzen (exakt
  ganzzahlige Periodenzahl → nahtlose Schleife); Hupen als zweistimmige
  Obertonklänge mit Horn-Formant; Bus-Tür als Druckluftzischen;
  Sirene = **fiktiver** Zweiton 494/659 Hz (bewusst nicht das reale
  Martinshorn), Phase auf ganze Perioden korrigiert.
* **Ambiente (36 s Schleifen):** Rauschbetten zirkulär im Frequenzbereich
  geformt, langsame periodische Modulation, Ereignisse (Autos mit
  Doppler-Andeutung, ferne Hupen, Vögel mit vier Rufmustern, Grillen mit exakt
  in die Schleife passenden Intervallen, Hundebellen, Regentropfen,
  Wasserblasen) modulo Schleifenlänge eingefügt, Nachhall zirkulär gefaltet.
  Stimmengemurmel/Kinderstimmen = Formant-gewichtete Obertöne mit Silben- und
  Phrasenhüllkurven – bewusst unverständlich und impressionistisch.
* **Musik:** komplett zirkulär gerendert (auch Nachhallfahnen laufen über die
  Naht).
  * `menu` – D-Dur, 80 BPM, 32 Takte (96 s): warme Flächen, additives Klavier
    (leicht inharmonisch, zweistufiges Abklingen), Arpeggien, Melodie in Teil A/B.
  * `day` – G-Dur, 90 BPM, 24 Takte (64 s): Karplus-Strong-Gitarre im
    Fingerpicking, Marimba-Melodie, Bass, leiser Shaker.
  * `night` – F-Dur/Lo-Fi, 72 BPM mit Swing, 20 Takte (67 s): FM-E-Piano,
    weiche Kick/Snare/Hi-Hat, Tiefpass.

Neu erzeugen: `python3 tools/gen_audio.py` (ca. 1 min), einzelne Klänge mit
`--only name1,name2`, nur Analyse mit `--analyze` (nutzt `ffmpeg` für LUFS,
falls vorhanden). Die Ausgabe ist deterministisch.

## Technische Prüfung (Analyse-Ergebnis)

Gemessen mit `tools/gen_audio.py --analyze` (Lautheit: ffmpeg `ebur128`):

* 53 Dateien, zusammen **32,9 MB** (Effekte 32 kHz mono 1,6 MB; Ambiente
  22,05 kHz mono 11 MB; Musik 22,05 kHz stereo 20 MB).
* Spitzenpegel: alle Effekte ≤ −2 dBFS (UI −6 … −20 dBFS), Musik −3,0 …
  −3,5 dBFS, Ambiente −8 … −12 dBFS. Kein Clipping.
* Gleichanteil: max. 0,0002 (Schwelle 0,002).
* Schleifennähte: Sprung zwischen letztem und erstem Sample stets kleiner als
  das 99,9-%-Perzentil der normalen Sample-Differenzen (z.B. Musik
  0,0005–0,004 gegen 0,06–0,12).
* Lautheit: Musik −16,5 / −16,7 / −17,1 LUFS (night/menu/day); Ambiente
  −24 (Regen) … −29 LUFS (Stadt nachts); Siren-Schleife −9 LUFS, wird per
  Registry um 10 dB abgesenkt.
* Spektrum: Schritte 0,27–0,30 s; Holz unter 600 Hz (95 % Energie), Beton
  bis ~9 kHz, Gras als Rascheln bis ~12 kHz; Motor-Leerlauf 95 % unter 230 Hz.
* Godot-Import: alle Schleifen als `LOOP_FORWARD` mit `loop_end` = Länge
  erkannt (Test `test_loop_flags`).

## Liste aller Klänge

| Name (Registry) | Datei(en) | Art |
|---|---|---|
| ui_hover, ui_click, ui_confirm, ui_back, ui_error | sfx/ui_*.wav | UI |
| ui_notification, ui_money_gain, ui_money_spend | sfx/ui_*.wav | UI |
| ui_phone_open, ui_phone_close, ui_levelup | sfx/ui_*.wav | UI (Jingle 2,6 s) |
| interact, pickup, eat, drink, keys_jingle, camera_shutter | sfx/*.wav | Interaktion |
| footstep_concrete / footstep_asphalt | sfx/footstep_concrete_1..4.wav | Schritte |
| footstep_grass | sfx/footstep_grass_1..4.wav | Schritte |
| footstep_wood / footstep_indoor | sfx/footstep_wood_1..4.wav | Schritte |
| door_open, door_close | sfx/door_*.wav | Türen |
| shop_bell, cash_register, scanner_beep, bag_rustle | sfx/*.wav | Laden |
| car_horn, bus_horn, bus_door, brake_squeal, car_door | sfx/*.wav | Verkehr |
| engine_idle, engine_rev, siren | sfx/*.wav | Verkehr (Schleifen) |
| city_day, city_night, park, residential, shop, rain, river | ambience/amb_*.wav | Ambiente (36 s Schleifen) |
| menu, day, night | music/mus_*.wav | Musik (Schleifen) |

## Fehlende Inhalte

Diese Klänge fehlen noch (auch in `SoundRegistry.PLANNED` gelistet; Aufrufe
warnen einmal und bleiben stumm):

* NPC-Stimmen (Begrüßung, Reaktionen) – brauchen echte Sprachaufnahmen.
* Schritte auf Schnee und Metall.
* Eigene Busmotor-Schleife (derzeit `EngineSound` mit `base_pitch` 0,7).
* Donner, Innenraum-Ambiente für Büro/Wohnung, Industriegebiet.
* Musik für Spannung (Polizeiverfolgung) und traurige Momente.
* Die Stimmen/Kinder/Hunde im Ambiente sind nur angedeutet (synthetisch);
  echte Feldaufnahmen würden deutlich natürlicher klingen.
