class_name AIConfig
extends RefCounted
## Verwaltung des optionalen Groq-API-Schluessels.
##
## * Gespeichert nur lokal in user://ai.cfg (verschluesselt mit einem
##   geraetebezogenen Passwort) - nie im Quellcode, in Logs oder Spielstaenden.
## * Alternativ wird die Umgebungsvariable GROQ_API_KEY gelesen.
## * Die Oberflaeche zeigt den Schluessel nur maskiert (masked_key()).
## * get_key() ist ausschliesslich fuer den KI-Client gedacht.
## Ob KI genutzt wird, steuert zusaetzlich Settings "ai/groq_enabled".

const PATH := "user://ai.cfg"
const ENV_VAR := "GROQ_API_KEY"
const SECTION := "groq"

## Zur Auswahl angebotene Modelle (Kennung -> Beschreibung).
const MODELS := {
	"llama-3.1-8b-instant": "Schnell und sparsam (empfohlen)",
	"llama-3.3-70b-versatile": "Ausführlicher, langsamer",
	"gemma2-9b-it": "Kompakt, alternative Formulierungen",
}

static var _cached := ""
static var _loaded := false


static func _password() -> String:
	var uid := OS.get_unique_id()
	if uid == "":
		uid = "lifesim-local"
	return ("lifesim:" + uid).sha256_text()


static func _load_file_key() -> String:
	if _loaded:
		return _cached
	_loaded = true
	_cached = ""
	if not FileAccess.file_exists(PATH):
		return ""
	var cfg := ConfigFile.new()
	if cfg.load_encrypted_pass(PATH, _password()) != OK:
		return ""
	_cached = str(cfg.get_value(SECTION, "api_key", "")).strip_edges()
	return _cached


## Speichert einen Schluessel. Gibt false zurueck, wenn er offensichtlich ungueltig ist.
static func set_key(k: String) -> bool:
	k = k.strip_edges()
	if not is_plausible(k):
		return false
	var cfg := ConfigFile.new()
	cfg.set_value(SECTION, "api_key", k)
	var err := cfg.save_encrypted_pass(PATH, _password())
	if err != OK:
		return false
	_cached = k
	_loaded = true
	return true


static func clear_key() -> void:
	if FileAccess.file_exists(PATH):
		DirAccess.remove_absolute(ProjectSettings.globalize_path(PATH))
	_cached = ""
	_loaded = true


static func has_key() -> bool:
	return get_key() != ""


## "file" (gespeichert), "env" (Umgebungsvariable) oder "".
static func key_source() -> String:
	if _load_file_key() != "":
		return "file"
	if OS.get_environment(ENV_VAR).strip_edges() != "":
		return "env"
	return ""


## Nur fuer den KI-Client. Niemals anzeigen oder loggen.
static func get_key() -> String:
	var k := _load_file_key()
	if k != "":
		return k
	return OS.get_environment(ENV_VAR).strip_edges()


## Maskierte Darstellung, z.B. "gsk_••••••••3fA9".
static func masked_key() -> String:
	var k := get_key()
	if k == "":
		return ""
	var tail := k.substr(maxi(0, k.length() - 4))
	var head := k.substr(0, 4) if k.length() > 12 else ""
	return head + "••••••••" + tail


static func is_plausible(k: String) -> bool:
	return k.length() >= 20 and not k.contains(" ") and not k.contains("\n")


## Ist KI aktiv nutzbar (eingeschaltet und Schluessel vorhanden)?
static func is_active() -> bool:
	return bool(Settings.get_value("ai", "groq_enabled")) and has_key()


## Statuszeile fuer die Oberflaeche.
static func status_text() -> String:
	var enabled := bool(Settings.get_value("ai", "groq_enabled"))
	match key_source():
		"file":
			return "Aktiv – Schlüssel lokal gespeichert" if enabled else "Schlüssel gespeichert, KI ist ausgeschaltet"
		"env":
			return "Aktiv – Schlüssel aus Umgebungsvariable %s" % ENV_VAR if enabled else "Schlüssel aus Umgebungsvariable vorhanden, KI ist ausgeschaltet"
	return "Nicht eingerichtet – das Spiel funktioniert vollständig ohne KI" if not enabled else "Eingeschaltet, aber kein Schlüssel hinterlegt"


## Fuer Tests: Zwischenspeicher verwerfen.
static func reset_cache() -> void:
	_loaded = false
	_cached = ""
