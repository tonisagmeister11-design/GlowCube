class_name SoundRegistry
extends RefCounted
## Zuordnung Klangname -> Datei(en) und Wiedergabeparameter.
##
## Neuen Klang hinzufuegen: Datei (WAV, OGG oder MP3) unter assets/audio/<ordner>/
## ablegen und hier EINE Zeile ergaenzen. Fehlt eine Datei, warnt der
## AudioManager genau einmal und spielt nichts - das Spiel laeuft weiter.
##
## Felder je Eintrag:
##   files  Array[String] relativ zu BASE; mehrere Dateien = Varianten (Zufallswahl,
##          nie zweimal dieselbe hintereinander)
##   vol    Grundlautstaerke in dB (Abmischung der Klaenge untereinander)
##   pitch  zufaellige Tonhoehenabweichung +/- (0.05 = 5 %)
##   dist   maximale Hoerweite in Metern (nur 3D)
##   unit   Abstand in Metern, ab dem die Daempfung einsetzt (nur 3D, unit_size)
##   loop   Endlosschleife (Schleifenpunkte stehen im smpl-Chunk der WAV-Datei;
##          fehlen sie, setzt der AudioManager sie selbst)
##   bus    Ausgabebus (Standard: SFX / Ambient / Music)

const BASE := "res://assets/audio/"

## Effekte und UI-Klaenge (Bus SFX). UI-Namen beginnen mit "ui_";
## Audio.play_ui("click") sucht zuerst "ui_click", dann "click".
const SFX := {
	# Benutzeroberflaeche
	"ui_hover": {"files": ["sfx/ui_hover.wav"], "vol": -4.0},
	"ui_click": {"files": ["sfx/ui_click.wav"], "vol": -2.0, "pitch": 0.03},
	"ui_confirm": {"files": ["sfx/ui_confirm.wav"], "vol": -2.0},
	"ui_back": {"files": ["sfx/ui_back.wav"], "vol": -2.0},
	"ui_error": {"files": ["sfx/ui_error.wav"], "vol": -2.0},
	"ui_notification": {"files": ["sfx/ui_notification.wav"], "vol": -3.0},
	"ui_money_gain": {"files": ["sfx/ui_money_gain.wav"], "vol": -3.0},
	"ui_money_spend": {"files": ["sfx/ui_money_spend.wav"], "vol": -3.0},
	"ui_phone_open": {"files": ["sfx/ui_phone_open.wav"], "vol": -2.0},
	"ui_phone_close": {"files": ["sfx/ui_phone_close.wav"], "vol": -2.0},
	"ui_levelup": {"files": ["sfx/ui_levelup.wav"], "vol": -2.0},
	# Interaktion, Gegenstaende
	"interact": {"files": ["sfx/interact.wav"], "vol": -2.0, "pitch": 0.04},
	"pickup": {"files": ["sfx/pickup.wav"], "vol": -3.0, "pitch": 0.05, "dist": 15.0, "unit": 2.0},
	"eat": {"files": ["sfx/eat.wav"], "vol": -3.0, "pitch": 0.06, "dist": 12.0, "unit": 1.5},
	"drink": {"files": ["sfx/drink.wav"], "vol": -3.0, "pitch": 0.06, "dist": 12.0, "unit": 1.5},
	"keys_jingle": {"files": ["sfx/keys_jingle.wav"], "vol": -4.0, "pitch": 0.08, "dist": 15.0, "unit": 1.5},
	"camera_shutter": {"files": ["sfx/camera_shutter.wav"], "vol": -4.0},
	# Schritte (3D); "asphalt" und "indoor" sind Aliasse
	"footstep_concrete": {"files": ["sfx/footstep_concrete_1.wav", "sfx/footstep_concrete_2.wav", "sfx/footstep_concrete_3.wav", "sfx/footstep_concrete_4.wav"], "vol": -8.0, "pitch": 0.06, "dist": 25.0, "unit": 2.0},
	"footstep_asphalt": {"files": ["sfx/footstep_concrete_1.wav", "sfx/footstep_concrete_2.wav", "sfx/footstep_concrete_3.wav", "sfx/footstep_concrete_4.wav"], "vol": -9.0, "pitch": 0.08, "dist": 25.0, "unit": 2.0},
	"footstep_grass": {"files": ["sfx/footstep_grass_1.wav", "sfx/footstep_grass_2.wav", "sfx/footstep_grass_3.wav", "sfx/footstep_grass_4.wav"], "vol": -6.0, "pitch": 0.06, "dist": 20.0, "unit": 2.0},
	"footstep_wood": {"files": ["sfx/footstep_wood_1.wav", "sfx/footstep_wood_2.wav", "sfx/footstep_wood_3.wav", "sfx/footstep_wood_4.wav"], "vol": -9.0, "pitch": 0.05, "dist": 25.0, "unit": 2.0},
	"footstep_indoor": {"files": ["sfx/footstep_wood_1.wav", "sfx/footstep_wood_2.wav", "sfx/footstep_wood_3.wav", "sfx/footstep_wood_4.wav"], "vol": -10.0, "pitch": 0.05, "dist": 20.0, "unit": 2.0},
	# Tueren, Laden
	"door_open": {"files": ["sfx/door_open.wav"], "vol": -3.0, "pitch": 0.05, "dist": 30.0, "unit": 3.0},
	"door_close": {"files": ["sfx/door_close.wav"], "vol": -4.0, "pitch": 0.05, "dist": 35.0, "unit": 3.0},
	"shop_bell": {"files": ["sfx/shop_bell.wav"], "vol": -5.0, "pitch": 0.02, "dist": 30.0, "unit": 3.0},
	"cash_register": {"files": ["sfx/cash_register.wav"], "vol": -5.0, "pitch": 0.03, "dist": 25.0, "unit": 3.0},
	"scanner_beep": {"files": ["sfx/scanner_beep.wav"], "vol": -6.0, "dist": 20.0, "unit": 2.0},
	"bag_rustle": {"files": ["sfx/bag_rustle.wav"], "vol": -4.0, "pitch": 0.08, "dist": 12.0, "unit": 1.5},
	# Fahrzeuge
	"car_horn": {"files": ["sfx/car_horn.wav"], "vol": -4.0, "pitch": 0.04, "dist": 160.0, "unit": 10.0},
	"bus_horn": {"files": ["sfx/bus_horn.wav"], "vol": -3.0, "pitch": 0.02, "dist": 200.0, "unit": 12.0},
	"bus_door": {"files": ["sfx/bus_door.wav"], "vol": -4.0, "pitch": 0.03, "dist": 40.0, "unit": 4.0},
	"brake_squeal": {"files": ["sfx/brake_squeal.wav"], "vol": -8.0, "pitch": 0.08, "dist": 70.0, "unit": 6.0},
	"car_door": {"files": ["sfx/car_door.wav"], "vol": -4.0, "pitch": 0.06, "dist": 40.0, "unit": 4.0},
	"engine_idle": {"files": ["sfx/engine_idle.wav"], "vol": -8.0, "loop": true, "dist": 60.0, "unit": 5.0},
	"engine_rev": {"files": ["sfx/engine_rev.wav"], "vol": -10.0, "loop": true, "dist": 90.0, "unit": 6.0},
	"siren": {"files": ["sfx/siren.wav"], "vol": -10.0, "loop": true, "dist": 300.0, "unit": 20.0},
}

## Ambiente-Schichten (Schleifen). bus "AmbientOutdoor" wird in Innenraeumen
## per Tiefpass gedaempft und endet im Bus Ambient (Lautstaerkeregler).
const AMBIENCE := {
	"city_day": {"files": ["ambience/amb_city_day.wav"], "vol": 0.0, "bus": "AmbientOutdoor"},
	"city_night": {"files": ["ambience/amb_city_night.wav"], "vol": 0.0, "bus": "AmbientOutdoor"},
	"park": {"files": ["ambience/amb_park.wav"], "vol": 0.0, "bus": "AmbientOutdoor"},
	"residential": {"files": ["ambience/amb_residential.wav"], "vol": 0.0, "bus": "AmbientOutdoor"},
	"rain": {"files": ["ambience/amb_rain.wav"], "vol": -1.0, "bus": "AmbientOutdoor"},
	"river": {"files": ["ambience/amb_river.wav"], "vol": -1.0, "bus": "AmbientOutdoor"},
	"shop": {"files": ["ambience/amb_shop.wav"], "vol": 0.0, "bus": "Ambient"},
}

## Musiktitel (Schleifen, Bus Music).
const MUSIC := {
	"menu": {"files": ["music/mus_menu.wav"], "vol": 0.0},
	"day": {"files": ["music/mus_day.wav"], "vol": -1.0},
	"night": {"files": ["music/mus_night.wav"], "vol": -1.0},
}

## Gewuenschte, aber noch fehlende Inhalte. Aufrufe dieser Namen warnen einmal
## und bleiben stumm. Siehe docs/AUDIO.md, Abschnitt "Fehlende Inhalte".
const PLANNED := {
	"voice_greeting": "Kurze NPC-Stimmen (Begruessung, Reaktion) - echte Sprachaufnahmen noetig",
	"footstep_snow": "Schritte auf Schnee (es gibt noch kein Wetter mit Schnee)",
	"footstep_metal": "Schritte auf Metall (Treppen, Bruecken)",
	"bus_engine": "Eigene Busmotor-Schleife (bisher engine_idle/engine_rev mit tieferer Tonhoehe)",
	"thunder": "Donner fuer Gewitter",
	"amb_office": "Buero-Innenraum (Tastaturen, Klimaanlage)",
	"amb_home": "Wohnung innen (Kuehlschrank, Uhr)",
	"amb_industrial": "Industriegebiet (Maschinen, Lueftung)",
	"mus_tension": "Spannungsmusik fuer Verfolgungen durch die Polizei",
	"mus_sad": "Ruhige Musik fuer traurige Ereignisse (Tod, Verlust)",
}


static func sfx(sound: String) -> Dictionary:
	return SFX.get(sound, {})


static func ambience(layer: String) -> Dictionary:
	return AMBIENCE.get(layer, {})


static func music(track: String) -> Dictionary:
	return MUSIC.get(track, {})


static func path_of(file: String) -> String:
	return BASE + file


## Alle referenzierten Dateien (res://-Pfade), ohne Duplikate.
static func all_files() -> Array[String]:
	var out: Array[String] = []
	for table in [SFX, AMBIENCE, MUSIC]:
		for key in table.keys():
			var e: Dictionary = table[key]
			for f in e.get("files", []):
				var p := path_of(str(f))
				if not out.has(p):
					out.append(p)
	return out


## Namen aller Eintraege mit loop = true (Effekte) bzw. alle Ambiente/Musik.
static func loop_files() -> Array[String]:
	var out: Array[String] = []
	for key in SFX.keys():
		var e: Dictionary = SFX[key]
		if e.get("loop", false):
			for f in e.files:
				out.append(path_of(str(f)))
	for table in [AMBIENCE, MUSIC]:
		for key in table.keys():
			var e: Dictionary = table[key]
			for f in e.files:
				out.append(path_of(str(f)))
	return out
