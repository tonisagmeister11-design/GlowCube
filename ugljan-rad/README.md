# Ugljan Rad

Offline-Fahrradkarte mit Navigation für die Insel Ugljan (Kroatien), als Android-App.

- Fertige APK: `dist/UgljanRad.apk`
- Bauen: `./build.sh` (braucht Android SDK mit build-tools 36.1.0 und platform android-36, JDK 17+)
- Daten neu erzeugen:
  1. OSM-Kacheln von `api.openstreetmap.org/api/0.6/map` und `relation/6756342/full` (als `island.osm`) in einen Ordner laden
  2. `python3 tools/fetch_dem.py <ordner>/island.osm tools/dem_ugljan.json` (Höhenraster)
  3. `python3 tools/build_data.py <ordner> tools/dem_ugljan.json app/assets` (braucht shapely, numpy, pillow, matplotlib)

Signiert wird mit dem Schlüssel aus `$KEYSTORE` (Standard `~/.ugljan-rad.keystore`, wird beim ersten Bauen erzeugt).
Updates müssen mit demselben Schlüssel signiert sein, sonst lässt Android sie nicht über die installierte App installieren.
Der Schlüssel liegt bewusst nicht im Repository.

Aufbau: eine WebView-App (`app/java`) mit MapLibre GL. Karte, Routing (A* mit Fahrphysik), Höhenprofile,
Touren und Navigation laufen in `app/assets/app.js`, die Inseldaten liegen in `app/assets/data.js`.

Ugljan ist komplett offline. Außerhalb der Insel kommen Karte und Routen online:
Karte OpenFreeMap (OpenMapTiles), Relief AWS Terrain Tiles, beschilderte Radrouten waymarkedtrails.org,
Fahrrad-Routing BRouter (brouter.de), Ortssuche Photon mit Nominatim als Ersatz.
