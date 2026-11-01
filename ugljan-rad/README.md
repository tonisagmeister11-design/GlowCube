# Velora

Fahrrad-Navigation weltweit für Android (APK) und Windows (EXE), mit der KI Soul (echtes Sprachmodell direkt auf dem Gerät) und dem Maskottchen Velo (Fuchs).

- Fertige Dateien: `dist/Velora.apk` (`./build.sh`, braucht Android SDK mit build-tools 36.1.0 und platform android-36, JDK 17+) und `dist/Velora.exe` (`./build-desktop.sh`)
- Paketname: `app.velora.bike`
- Signiert wird mit dem Schlüssel aus `$KEYSTORE` (Standard `~/.ugljan-rad.keystore`). Updates müssen mit demselben Schlüssel signiert sein. Der Schlüssel liegt bewusst nicht im Repository.

Aufbau: eine WebView-App (`app/java`) mit MapLibre GL. Karte, Routing mit Höhen- und Fahrphysik, Touren und Navigation in `app/assets/app.js`,
Maskottchen in `app/assets/fox.js`, Animationen, Abzeichen, Credits und Plus in `app/assets/plus.js` / `plus.css`.
Karte und Routen kommen online: OpenFreeMap (OpenMapTiles), AWS Terrain Tiles, waymarkedtrails.org, BRouter (brouter.de),
Ortssuche Photon/Nominatim, Tagesplaner-Orte über Overpass (OpenStreetMap). `app/assets/data.js` enthält eine kleine Offline-Testregion
(Daten-Werkzeuge in `tools/`), die nur benutzt wird, wenn man sich dort befindet.

## KI "Soul" (ab 6.4, in der App)

`app/assets/soul.js` ist die eigene KI von Velora, nach dem Prinzip des Projekts "Soul": Absichten erkennen (Deutsch und Englisch,
mit Tippfehler-Korrektur), Orte, Zeiten und Zahlen herauslesen, Werkzeuge der App ausführen (Route, Anpassen, Tour, Navigation),
Fahrten auswerten, Wetter (Open-Meteo), Orte in der Nähe (Karte, Overpass), Rad-Hilfe, Training, Plaudern mit Emojis und
Gesprächskontext. Wissensfragen recherchiert sie im Hintergrund (Wikipedia, auf Android zusätzlich DuckDuckGo über `fetchText`)
und wählt die passendsten Sätze aus. `app/assets/soul-data.js` enthält ihr Wissen: Fahrrad-Wissensbuch (rund 75 Themen),
Plauder-Sammlung, Fakten, Rätsel, Quiz, Geschichten und Gedichte. Soul merkt sich, was Nutzer über sich erzählen (nur auf dem Gerät),
versteht Folgefragen zum letzten Thema und wiederholt Formulierungen nicht direkt. Kein Server, keine fremde KI, keine Kosten. Der frühere Server-Weg (unten) bleibt nur für alte App-Versionen.

## Soul über den Server (ab 6.7, Standard)

Soul fragt zuerst den Velora-Server: `POST /soul` (`server/soul.js`) rechnet mit Cloudflare Workers AI (`@cf/qwen/qwen3-30b-a3b-fp8`,
ohne "Nachdenken") und streamt die Antwort. Das geht auf jedem Handy in etwa 1 bis 2 Sekunden.

Kostenbremse, damit nie mehr als der Workers-Tarif (5 US-Dollar im Monat) anfällt:
- Cloudflare schenkt 10.000 Neuronen pro Tag (UTC). Der Server erlaubt höchstens `SOUL_DAY_CAP` = 9.000 (wrangler.toml).
- Gezählt wird exakt in einem Durable Object (`SoulBudget`): vor jeder Antwort werden 25 Neuronen reserviert, danach der echte Verbrauch abgerechnet.
- Pro Person und Tag: ohne Konto 10, mit Konto 30, mit Plus 120 Antworten.
- Ist eine Grenze erreicht, antwortet die App bis Mitternacht (UTC) regelbasiert. Das Admin-Dashboard zeigt den Tagesverbrauch.
- Der alte Juno-Weg `/chat` (teures Modell ohne Bremse) ist abgeschaltet.

Reihenfolge in der App: Server, dann (falls geladen) das Modell auf dem Gerät als Offline-Modus, dann die Regel-KI.

## Soul-Sprachmodell (ab 6.6, auf dem Gerät, jetzt "Offline-Modus")

Soul kann ein echtes KI-Sprachmodell nutzen, das komplett auf dem Gerät läuft (llama.cpp, Modelle von Qwen, Apache-2.0).
Kein Cloudflare, keine fremde KI, keine laufenden Kosten. Der Nutzer lädt das Modell einmalig im Soul-Chat herunter:

| Modell | Datei | Größe | Für |
|---|---|---|---|
| Soul | Qwen3-1.7B Q4_K_M | 1,1 GB | jedes Handy, schnell |
| Soul Pro | Qwen3-4B-Instruct-2507 Q4_K_M | 2,5 GB | ab etwa 6 GB RAM empfohlen, klüger |

- Android: `native/velora_llm.cpp` (JNI um llama.cpp), gebaut mit `./build-native.sh` (NDK, llama.cpp fest auf b12032) zu
  `app/lib/arm64-v8a/libvelora_llm.so` (ARMv8.2 mit dotprod) und `libvelora_llm_base.so` (ältere Handys). `Llm.java` lädt die passende
  Bibliothek, lädt das Modell herunter (fortsetzbar, SHA-256-Prüfung) und streamt Antworten an die App.
- Windows: `desktop/llm.js` startet den mitgelieferten `llama-server.exe` nur auf 127.0.0.1 und reicht die Antworten über IPC weiter.
- `app/assets/soul-llm.js`: Modellkarte im Chat (Download, Fortschritt, Verwalten), Prompt (Persönlichkeit, Info, Suchergebnisse, Verlauf), Streaming.
- `soul.js` entscheidet: App-Aktionen (Route, Tour, Wetter, Orte, Navigation, Statistik, Rechnen, Gedächtnis) bleiben regelbasiert;
  Gespräch, Wissen und Rad-Fragen beantwortet das Sprachmodell, bei Faktenfragen mit Wikipedia und Websuche als Grundlage.
  Ohne Modell (oder bei einem Fehler) antwortet Soul wie bisher regelbasiert.
- Später ein eigener Server: `localStorage.soulServer = "https://..."` (llama-server-kompatibles `/completion`), `soulServerModel` = `std` oder `pro`.

## KI "Juno" (Server, alt)

Der Server liegt in `server/` (Cloudflare Workers AI, Modell GPT-OSS 120B, Ersatzmodell Llama 3.3 70B).
Juno bekommt die Live-Daten der App (Standort, Tempo, Höhe, Route, Navigation, Wetter, echte Orte in der Nähe)
und hat Werkzeuge für echte Daten: Ortssuche und Umkreissuche (OpenStreetMap/Nominatim/Photon),
Recherche (Wikivoyage/Wikipedia) und Wetter (Open-Meteo). Sie kann der App Aktionen geben:
Route planen, Tour vorschlagen, Route anpassen, Navigation starten, Ort zeigen.
Hochladen: im Ordner `server/` `npx wrangler deploy` (Cloudflare-Konto nötig).
Juno-Credits: jede Rechenrunde der KI kostet einen Credit (`credits` in der Antwort); gratis 10 am Tag, mit Plus 100.

## Velora Plus, Velo und Haptik (ab 5.4)

`app/assets/plus.js` und `plus.css` hängen sich über `window.VX` an die Haken (`HOOK`) in `app.js`:
Vibrationen (Android `haptic`), Maskottchen Velo (Fuchs) mit Animationen (Serien-Flamme, Fahrt, Abzeichen),
Nachtmodus, Farbthemen, 3D-Pfeil, Abzeichen in Stufen, Credits und Velora Plus. Plus wird bis zur Play-Store-Bezahlung lokal zum Testen freigeschaltet.
Live-Standort: `server/worker.js` (`/live`, `/l/<id>`, KV-Speicher `LIVE`).
Browser-Tester: `python3 tools/build_tester.py` (Demo-Juno) bzw. `--real server/public/index.html` (echte Juno, liegt auf dem Server unter `/`).
Schrift: Inter (SIL Open Font License, `app/assets/fonts/OFL.txt`).
