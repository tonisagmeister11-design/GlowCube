# Velora

Fahrrad-Navigation weltweit für Android (APK) und Windows (EXE), mit der KI Juno und dem Maskottchen Velo (Fuchs).

- Fertige Dateien: `dist/Velora.apk` (`./build.sh`, braucht Android SDK mit build-tools 36.1.0 und platform android-36, JDK 17+) und `dist/Velora.exe` (`./build-desktop.sh`)
- Paketname: `app.velora.bike`
- Signiert wird mit dem Schlüssel aus `$KEYSTORE` (Standard `~/.ugljan-rad.keystore`). Updates müssen mit demselben Schlüssel signiert sein. Der Schlüssel liegt bewusst nicht im Repository.

Aufbau: eine WebView-App (`app/java`) mit MapLibre GL. Karte, Routing mit Höhen- und Fahrphysik, Touren und Navigation in `app/assets/app.js`,
Maskottchen in `app/assets/fox.js`, Animationen, Abzeichen, Credits und Plus in `app/assets/plus.js` / `plus.css`.
Karte und Routen kommen online: OpenFreeMap (OpenMapTiles), AWS Terrain Tiles, waymarkedtrails.org, BRouter (brouter.de),
Ortssuche Photon/Nominatim, Tagesplaner-Orte über Overpass (OpenStreetMap). `app/assets/data.js` enthält eine kleine Offline-Testregion
(Daten-Werkzeuge in `tools/`), die nur benutzt wird, wenn man sich dort befindet.

## KI "Juno"

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
