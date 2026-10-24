extends RefCounted
## Tests fuer lokales Dialogsystem und KI-Antwortvalidierung.

var runner


func test_sanitize_ai_reply() -> void:
	var t := GroqClient_sanitize("\"Hallo du! \U01F600 Schön hier.\"")
	runner.check(not t.contains("\U01F600"), "Emoji muss entfernt werden")
	runner.check(t.begins_with("Hallo"), "Anführungszeichen entfernt: " + t)
	var long := "Satz. ".repeat(200)
	runner.check(GroqClient_sanitize(long).length() <= 330, "Länge begrenzt")


func test_invalid_ai_structures() -> void:
	var script: GDScript = load("res://scripts/ai/groq_client.gd")
	runner.check_eq(script.call("_extract", null), "", "null")
	runner.check_eq(script.call("_extract", {"choices": []}), "", "leer")
	runner.check_eq(script.call("_extract", {"choices": [{"message": {"content": 5}}]}), "", "falscher Typ")
	runner.check_eq(script.call("_extract", {"choices": [{"message": {"content": " Hi "}}]}), "Hi", "gültig")


func test_local_dialogue_without_population() -> void:
	var w := WorldState.new()
	w.rules = GameRules.make("life")
	w.ledger = Ledger.new()
	var s := DialogueSession.new(w, 1)
	var st := s.start()
	runner.check(str(st.text) != "", "Begrüßung vorhanden")
	runner.check(st.options.size() >= 3, "Optionen vorhanden")
	var end := s.choose("bye")
	runner.check(end.end, "Verabschieden beendet Gespräch")


func test_temperament() -> void:
	runner.check_eq(DialogueLibrary.temperament({"traits": {"suspicious": 0.9}}), "suspicious", "misstrauisch")
	runner.check_eq(DialogueLibrary.temperament({"traits": {"social": 0.8, "suspicious": 0.1, "shy": 0.1}}), "social", "gesellig")


func GroqClient_sanitize(t: String) -> String:
	var script: GDScript = load("res://scripts/ai/groq_client.gd")
	return script.call("sanitize", t)
