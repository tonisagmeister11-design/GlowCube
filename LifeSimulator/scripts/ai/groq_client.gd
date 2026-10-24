extends Node
## Optionale Groq-Anbindung (OpenAI-kompatible Chat-API) fuer reichere
## NPC-Dialoge. Das Spiel funktioniert vollstaendig ohne: Jede Anfrage hat ein
## Timeout, wird begrenzt und validiert; bei jedem Fehler liefert der Aufrufer
## den lokalen Text (DialogueLibrary).
##
## Sicherheit: Der Schluessel kommt aus AIConfig (user://ai.cfg oder
## Umgebungsvariable GROQ_API_KEY), wird nie geloggt, nie gespeichert und
## nie in Fehlermeldungen ausgegeben. Hinweis: In einer verteilten
## Client-Anwendung kann ein Schluessel nicht vollstaendig geheim bleiben;
## fuer eine sichere Loesung waere ein eigener Backend-Proxy noetig.

const ENDPOINT := "https://api.groq.com/openai/v1/chat/completions"
const TIMEOUT_S := 8.0
const MIN_INTERVAL_S := 2.5
const MAX_PER_MINUTE := 15
const MAX_REPLY_CHARS := 320

signal status_changed(status: String)

var status := "Deaktiviert"
var last_error := ""
var _http: HTTPRequest
var _busy := false
var _last_request_ms := -100000
var _recent: Array[int] = []
var _ai_config_script: Script


func _ready() -> void:
	_http = HTTPRequest.new()
	_http.timeout = TIMEOUT_S
	_http.use_threads = true
	add_child(_http)
	if ResourceLoader.exists("res://scripts/ai/ai_config.gd"):
		_ai_config_script = load("res://scripts/ai/ai_config.gd")
	Settings.changed.connect(func(_s, _k, _v): _update_status())
	_update_status()


func _api_key() -> String:
	if _ai_config_script == null:
		return OS.get_environment("GROQ_API_KEY")
	var k = _ai_config_script.call("get_key") if _ai_config_script.has_method("get_key") else ""
	if typeof(k) != TYPE_STRING or k == "":
		var cfg = _ai_config_script.new()
		if cfg.has_method("get_key"):
			k = cfg.get_key()
	return str(k)


func is_enabled() -> bool:
	return bool(Settings.get_value("ai", "groq_enabled")) and _api_key() != ""


func _update_status() -> void:
	var s: String
	if not bool(Settings.get_value("ai", "groq_enabled")):
		s = "Deaktiviert (lokale Dialoge)"
	elif _api_key() == "":
		s = "Kein API-Schlüssel hinterlegt (lokale Dialoge)"
	elif _busy:
		s = "Anfrage läuft ..."
	elif last_error != "":
		s = "Fehler: %s (lokale Dialoge als Ersatz)" % last_error
	else:
		s = "Bereit"
	if s != status:
		status = s
		status_changed.emit(status)


func _rate_ok() -> bool:
	var now := Time.get_ticks_msec()
	if now - _last_request_ms < int(MIN_INTERVAL_S * 1000.0):
		return false
	var kept: Array[int] = []
	for t in _recent:
		if now - t < 60000:
			kept.append(t)
	_recent = kept
	return _recent.size() < MAX_PER_MINUTE


## Fragt eine Antwort an. Gibt "" zurueck, wenn die KI nicht verfuegbar ist
## oder die Antwort ungueltig war - der Aufrufer nutzt dann seinen lokalen Text.
func complete(system_prompt: String, user_prompt: String, max_tokens := 110) -> String:
	if not is_enabled() or _busy or not _rate_ok():
		return ""
	_busy = true
	_last_request_ms = Time.get_ticks_msec()
	_recent.append(_last_request_ms)
	_update_status()
	var body := {
		"model": str(Settings.get_value("ai", "groq_model")),
		"messages": [
			{"role": "system", "content": system_prompt},
			{"role": "user", "content": user_prompt},
		],
		"max_tokens": max_tokens,
		"temperature": 0.8,
	}
	var headers := PackedStringArray([
		"Content-Type: application/json",
		"Authorization: Bearer " + _api_key(),
	])
	var err := _http.request(ENDPOINT, headers, HTTPClient.METHOD_POST, JSON.stringify(body))
	if err != OK:
		_finish("Verbindung nicht möglich")
		return ""
	var res: Array = await _http.request_completed
	var result: int = res[0]
	var code: int = res[1]
	var raw: PackedByteArray = res[3]
	if result != HTTPRequest.RESULT_SUCCESS:
		_finish("Keine Verbindung" if result != HTTPRequest.RESULT_TIMEOUT else "Zeitüberschreitung")
		return ""
	if code == 401 or code == 403:
		_finish("Schlüssel ungültig")
		return ""
	if code == 429:
		_finish("Anfragelimit erreicht")
		return ""
	if code < 200 or code >= 300:
		_finish("Serverfehler %d" % code)
		return ""
	var parsed = JSON.parse_string(raw.get_string_from_utf8())
	var text := _extract(parsed)
	if text == "":
		_finish("Ungültige Antwort")
		return ""
	_finish("")
	return text


func _finish(error: String) -> void:
	_busy = false
	last_error = error
	if error != "":
		Log.warn("ai", "Groq-Anfrage fehlgeschlagen: %s" % error)
	_update_status()


## Antwort als unzuverlaessige Eingabe behandeln: Struktur pruefen, Laenge
## begrenzen, Steuerzeichen und Emojis entfernen.
static func _extract(parsed) -> String:
	if typeof(parsed) != TYPE_DICTIONARY:
		return ""
	var choices = parsed.get("choices")
	if typeof(choices) != TYPE_ARRAY or choices.is_empty() or typeof(choices[0]) != TYPE_DICTIONARY:
		return ""
	var msg = choices[0].get("message")
	if typeof(msg) != TYPE_DICTIONARY or typeof(msg.get("content")) != TYPE_STRING:
		return ""
	return sanitize(msg.content)


static func sanitize(text: String) -> String:
	var out := ""
	for i in text.length():
		var c := text.unicode_at(i)
		if c < 32 and c != 10:
			continue
		# Emojis und Bildsymbole entfernen
		if (c >= 0x1F000 and c <= 0x1FAFF) or (c >= 0x2600 and c <= 0x27BF) or (c >= 0xFE00 and c <= 0xFE0F) or c == 0x200D:
			continue
		out += String.chr(c)
	out = out.strip_edges().replace("\n\n", "\n")
	# Anfuehrungszeichen am Rand entfernen, Laenge begrenzen
	if out.begins_with("\"") and out.ends_with("\"") and out.length() > 2:
		out = out.substr(1, out.length() - 2)
	if out.length() > MAX_REPLY_CHARS:
		var cut := out.substr(0, MAX_REPLY_CHARS)
		var dot := maxi(cut.rfind(". "), maxi(cut.rfind("! "), cut.rfind("? ")))
		out = cut.substr(0, dot + 1) if dot > 40 else cut + " ..."
	return out
