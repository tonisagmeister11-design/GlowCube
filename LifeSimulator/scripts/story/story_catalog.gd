class_name StoryCatalog
extends RefCounted
## Inhalte des Story-Modus: Kapitel, Missionen, Schritte und Dialoge.
##
## Eine Mission besteht aus Schritten. Jeder Schritt hat ein Ziel in der Stadt
## (ueber einen Ortsschluessel, damit er in jeder erzeugten Stadt funktioniert)
## und ein Ereignis, das ihn abschliesst. Zwischensequenzen und Dialoge sind
## Daten, keine festen Szenen – so laeuft dieselbe Mission in jeder Stadt.
##
## Ortsschluessel (werden zur Laufzeit auf echte Gebaeude abgebildet):
##   hideout, bar, warehouse, harbor, market, shop, bank, apartment,
##   police, hospital, club, garage, parking, bridge, park, city_hall
##
## Schritt-Typen:
##   goto      – Ort erreichen
##   interact  – am Ort die Taste fuer Interaktion druecken
##   drive     – mit einem Fahrzeug zum Ort fahren
##   deliver   – etwas abliefern (interact mit Gegenstand)
##   collect   – Geld oder Ware einsammeln
##   survive   – ein Ereignis ueberstehen (siehe scripted)
##   flee      – Abstand gewinnen (Fahndung abschuetteln)
##   talk      – mit einer Person sprechen
##   choice    – Entscheidung treffen (verzweigt die Geschichte)

## Sprecher der Geschichte. voice = Dateiname-Praefix fuer eigene Aufnahmen
## unter assets/audio/voice/, falls vorhanden.
const SPEAKERS := {
	"luca":    {"name": "Luca Valcorvo", "voice": "luca", "color": Color(0.90, 0.80, 0.55)},
	"ramirez": {"name": "Ramírez", "voice": "ramirez", "color": Color(0.85, 0.35, 0.30)},
	"bruno":   {"name": "Bruno", "voice": "bruno", "color": Color(0.70, 0.60, 0.55)},
	"pate":    {"name": "Der Pate", "voice": "pate", "color": Color(0.80, 0.68, 0.30)},
	"nadia":   {"name": "Nadia Falk", "voice": "nadia", "color": Color(0.55, 0.75, 0.85)},
	"radio":   {"name": "Funk", "voice": "radio", "color": Color(0.60, 0.65, 0.70)},
	"player":  {"name": "", "voice": "player", "color": Color(0.75, 0.85, 1.00)},
}

## Kapitel des Gang-Strangs. unlock: Bedingungen, damit das Kapitel beginnt.
const CHAPTERS := [
	{
		"id": "ch1", "title": "Erste Schritte",
		"desc": "Du bist neu in der Organisation. Zeig, dass man dir Sachen anvertrauen kann.",
		"unlock": {"gang": true},
		"missions": ["m_paket", "m_schutzgeld"],
	},
	{
		"id": "ch2", "title": "Hinterhalt",
		"desc": "Ein einfacher Geldtransport. Eigentlich.",
		"unlock": {"gang_rank": 1, "missions_done": ["m_schutzgeld"]},
		"missions": ["m_hinterhalt", "m_spur"],
	},
	{
		"id": "ch3", "title": "Vergeltung",
		"desc": "Die Schwarzen Möwen haben dich auf der Hafenstraße fast erledigt. Jetzt bist du dran.",
		"unlock": {"missions_done": ["m_spur"]},
		"missions": ["m_werkstatt", "m_container"],
	},
	{
		"id": "ch4", "title": "Machtwechsel",
		"desc": "Der Pate wird alt. In der Familie wird über die Nachfolge geredet.",
		"unlock": {"gang_rank": 4, "missions_done": ["m_container"]},
		"missions": ["m_entscheidung"],
	},
]

## Alle Missionen. reward: {money, rep, skill} – Geld kommt immer aus einer
## echten Quelle (Bandenkasse), nie aus dem Nichts.
const MISSIONS := {
# ---------------------------------------------------------------- Kapitel 1
"m_paket": {
	"title": "Die erste Lieferung",
	"brief": "Hol ein Paket im Unterschlupf ab und bring es zum Lager. Keine Fragen stellen.",
	"giver": "pate",
	"reward": {"money": 250.0, "rep": 8.0},
	"fail_text": "Das Paket ist weg. So etwas spricht sich herum.",
	"steps": [
		{"type": "goto", "place": "hideout", "text": "Geh zum Unterschlupf.",
			"dialog": [["pate", "Du bist also der Neue. Nimm das Paket und frag nicht, was drin ist."]]},
		{"type": "collect", "place": "hideout", "text": "Nimm das Paket entgegen.", "item": "paket"},
		{"type": "deliver", "place": "warehouse", "text": "Bring das Paket zum Lager.",
			"dialog": [["bruno", "Spät dran. Aber heil. Geht in Ordnung."]]},
	],
},
"m_schutzgeld": {
	"title": "Freundliche Erinnerung",
	"brief": "Drei Läden im Viertel zahlen diese Woche nicht. Erinnere sie daran.",
	"giver": "bruno",
	"reward": {"money": 400.0, "rep": 12.0},
	"fail_text": "Die Läden haben die Polizei gerufen. Schlecht gelaufen.",
	"steps": [
		{"type": "collect", "place": "shop", "text": "Kassiere im ersten Laden.", "amount": 150.0,
			"dialog": [["player", "Diese Woche war schwer, ja? Nächste Woche wird besser."]]},
		{"type": "collect", "place": "shop", "text": "Kassiere im zweiten Laden.", "amount": 150.0, "exclude_last": true},
		{"type": "collect", "place": "shop", "text": "Kassiere im dritten Laden.", "amount": 150.0, "exclude_last": true},
		{"type": "deliver", "place": "hideout", "text": "Bring das Geld in den Unterschlupf.",
			"dialog": [["bruno", "Sauber. Der Pate hört so was gern."]]},
	],
},
# ---------------------------------------------------------------- Kapitel 2
# Die Hinterhalt-Mission: der Spieler ist der Fahrer, der springen muss.
"m_hinterhalt": {
	"title": "Hinterhalt an der Hafenstraße",
	"brief": "Hol das Geld aus dem Unterschlupf und bring es zum Hafen. Zehn Minuten, mehr nicht.",
	"giver": "pate",
	"reward": {"money": 1200.0, "rep": 25.0},
	"fail_text": "Das Geld ist verbrannt. Und du fast mit ihm.",
	"music": "night",
	"steps": [
		{"type": "collect", "place": "hideout", "text": "Hol die Tasche mit dem Geld.", "item": "geldtasche",
			"dialog": [
				["pate", "Zwölftausend. Der Mann am Hafen wartet. Fahr, red nicht, komm zurück."],
				["player", "Zehn Minuten."],
			]},
		{"type": "drive", "place": "harbor", "text": "Fahr mit dem Wagen zum Hafen.", "need_vehicle": true,
			"dialog": [
				["luca", "Ich hab das Geld. In zehn Minuten bin ich am Hafen."],
				["luca", "Nein, mir ist keiner gefolgt. Entspann dich."],
			],
			"scripted": "ambush"},
		{"type": "flee", "place": "hideout", "text": "Verschwinde von der Straße und erreiche den Unterschlupf.",
			"distance": 180.0,
			"dialog": [["pate", "Das Geld ist weg. Aber du lebst. Wer war das?"]]},
	],
	## Inszenierter Abschnitt: der Hinterhalt auf der Hafenstraße.
	## Zeiten in Sekunden ab Beginn des Abschnitts.
	"scripted_ambush": {
		"warn_after": 6.0,          # erst fahren lassen, dann taucht der Verfolger auf
		"aim_time": 4.5,            # Zeitfenster zum Absprung, nachdem angelegt wurde
		"jump_action": "jump",
		"beats": [
			{"t": 0.0, "speaker": "radio", "line": "Hinter dir hält sich ein dunkler Pick-up. Seit drei Kreuzungen."},
			{"t": 2.2, "speaker": "luca", "line": "Moment … wer zur Hölle ist das?"},
			{"t": 4.0, "speaker": "ramirez", "line": "Bleib dran. Nicht zu nah.", "radio": true},
			{"t": 5.2, "speaker": "bruno", "line": "Ich weiß, was ich tu.", "radio": true},
			{"t": 6.4, "speaker": "ramirez", "line": "Näher ran. Ich hab ihn.", "radio": true},
			{"t": 7.6, "speaker": "ramirez", "line": "Gute Reise.", "radio": true},
		],
		"prompt": "SPRINGEN",
		"prompt_hint": "Raus aus dem Wagen, sofort!",
	},
},
"m_spur": {
	"title": "Eine Spur",
	"brief": "Jemand hat die Möwen informiert. Finde heraus, wer geredet hat.",
	"giver": "nadia",
	"reward": {"money": 600.0, "rep": 18.0, "skill": ["logic", 6.0]},
	"fail_text": "Die Spur ist kalt.",
	"steps": [
		{"type": "talk", "place": "bar", "text": "Sprich in der Bar mit Nadia.",
			"dialog": [
				["nadia", "Zwei Tage vorher hat jemand nach der Route gefragt. Jemand von uns."],
				["player", "Name?"],
				["nadia", "Noch nicht. Aber sein Wagen steht oft an der Werkstatt am Hafen."],
			]},
		{"type": "goto", "place": "garage", "text": "Sieh dir die Werkstatt am Hafen an."},
		{"type": "interact", "place": "garage", "text": "Durchsuche die Werkstatt.",
			"dialog": [["player", "Quittungen, Zündschnur, ein Zettel mit meiner Route. Da haben wir's."]]},
		{"type": "deliver", "place": "hideout", "text": "Bring die Beweise zum Paten.",
			"dialog": [["pate", "Ramírez. Natürlich. Dann wissen wir, wo wir anfangen."]]},
	],
},
# ---------------------------------------------------------------- Kapitel 3
"m_werkstatt": {
	"title": "Die Werkstatt",
	"brief": "Die Möwen schrauben ihre Wagen in einer Halle am Industriehafen um. Mach sie unbrauchbar.",
	"giver": "pate",
	"reward": {"money": 900.0, "rep": 22.0},
	"fail_text": "Die Halle steht noch. Und die Möwen wissen jetzt, dass du es warst.",
	"steps": [
		{"type": "drive", "place": "warehouse", "text": "Fahr zur Halle im Industriegebiet."},
		{"type": "interact", "place": "warehouse", "text": "Setz die Wagen außer Gefecht.", "crime": "vandalism",
			"dialog": [["player", "Keine Räder, keine Hinterhalte."]]},
		{"type": "flee", "place": "hideout", "text": "Verschwinde, bevor die Polizei kommt.", "distance": 220.0},
	],
},
"m_container": {
	"title": "Der Deal am Containerhafen",
	"brief": "Ein Treffen am Hafen. Das Geld gegen die Ware. Diesmal passt du auf.",
	"giver": "pate",
	"reward": {"money": 2500.0, "rep": 35.0},
	"fail_text": "Der Deal ist geplatzt.",
	"steps": [
		{"type": "collect", "place": "hideout", "text": "Hol das Geld für den Deal.", "item": "geldtasche"},
		{"type": "drive", "place": "harbor", "text": "Fahr zum Containerhafen.",
			"dialog": [["pate", "Ramírez wird da sein. Er weiß noch nicht, dass du weißt."]]},
		{"type": "choice", "place": "harbor", "text": "Entscheide, wie du mit Ramírez umgehst.",
			"dialog": [["ramirez", "Du lebst noch. Das überrascht mich ehrlich."]],
			"options": [
				{"id": "deal", "label": "Den Deal sauber abwickeln",
					"text": "Du machst das Geschäft. Ramírez bleibt am Leben – und du hast etwas gegen ihn in der Hand.",
					"rep": 10.0, "money": 0.0},
				{"id": "revenge", "label": "Abrechnen", "text": "Du stellst ihn. Die Ware bleibt, der Ruf steigt, die Möwen haben einen Grund mehr.",
					"rep": 25.0, "heat": 2},
				{"id": "police", "label": "Ihn der Polizei überlassen",
					"text": "Ein anonymer Hinweis genügt. Sauber, aber in der Familie mag man so etwas nicht.",
					"rep": -10.0, "money": 0.0},
			]},
		{"type": "deliver", "place": "hideout", "text": "Melde dich beim Paten."},
	],
},
# ---------------------------------------------------------------- Kapitel 4
"m_entscheidung": {
	"title": "Machtwechsel",
	"brief": "Der Pate hat dich rufen lassen. Es geht um die Nachfolge.",
	"giver": "pate",
	"reward": {"rep": 50.0},
	"fail_text": "Die Gelegenheit ist vorbei.",
	"steps": [
		{"type": "goto", "place": "hideout", "text": "Geh zum Paten."},
		{"type": "choice", "place": "hideout", "text": "Triff deine Entscheidung.",
			"dialog": [
				["pate", "Ich werde alt. Einer übernimmt. Die Frage ist nur, wer – und wie."],
			],
			"options": [
				{"id": "wait", "label": "Warten, bis er dich benennt",
					"text": "Du bleibst loyal. Es dauert länger, aber niemand stellt dich infrage.", "rep": 15.0},
				{"id": "challenge", "label": "Ihn herausfordern",
					"text": "Du forderst ihn offen heraus. Entweder bist du morgen Boss – oder gar nichts.",
					"rep": 40.0, "heat": 3},
				{"id": "leave", "label": "Aussteigen",
					"text": "Du gehst. Ein ruhiges Leben ist auch ein Leben.", "rep": -40.0, "leave_gang": true},
			]},
	],
},
}


static func mission(id: String) -> Dictionary:
	return MISSIONS.get(id, {})


static func chapter(id: String) -> Dictionary:
	for c in CHAPTERS:
		if c.id == id:
			return c
	return {}


static func speaker(id: String) -> Dictionary:
	return SPEAKERS.get(id, {"name": id.capitalize(), "voice": id, "color": Color.WHITE})


## Alle Missions-Kennungen in Spielreihenfolge.
static func ordered_missions() -> Array:
	var out := []
	for c in CHAPTERS:
		for m in c.missions:
			out.append(m)
	return out


static func chapter_of(mission_id: String) -> String:
	for c in CHAPTERS:
		if mission_id in c.missions:
			return c.id
	return ""
