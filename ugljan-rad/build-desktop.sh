#!/bin/bash
# Baut die Windows-Version (eine einzelne EXE, ohne Installation) mit Electron und electron-builder.
# Gleicher App-Code wie die APK (app/assets), plus Testhilfen am PC (window.__desktop).
set -e -o pipefail
cd "$(dirname "$0")"
W=build/desktop
rm -rf "$W" && mkdir -p "$W" dist
cp desktop/main.js desktop/preload.js "$W"/
cp -r app/assets "$W"/assets
python3 - "$W" <<'PY'
import sys
from PIL import Image
im = Image.open('app/res/drawable-nodpi/ic_cover_fg.png').convert('RGBA')
w = im.size[0]; m = int(w * (1 - 72 / 108) / 2)
c = im.crop((m, m, w - m, w - m)).resize((256, 256), Image.LANCZOS)
c.save(sys.argv[1] + '/icon.png'); c.save(sys.argv[1] + '/icon.ico', sizes=[(16, 16), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
PY
VER=$(grep -o 'versionName="[^"]*"' app/AndroidManifest.xml | cut -d'"' -f2)
cat > "$W"/package.json <<JSON
{
  "name": "ugljan-rad", "productName": "Ugljan Rad", "version": "$VER.0", "description": "Fahrrad-Navigation", "main": "main.js", "author": "Ugljan Rad",
  "devDependencies": { "electron": "33.4.11", "electron-builder": "25.1.8" },
  "build": { "appId": "hr.ugljan.rad", "files": ["main.js", "preload.js", "icon.png", "assets/**"], "directories": { "output": "out" },
    "win": { "target": [{ "target": "portable", "arch": ["x64"] }], "icon": "icon.ico", "signAndEditExecutable": false },
    "portable": { "artifactName": "UgljanRad.exe" } }
}
JSON
cd "$W"
ELECTRON_SKIP_BINARY_DOWNLOAD=1 npm install --no-audit --no-fund -q
npx electron-builder --win portable --x64
cp out/UgljanRad.exe ../../dist/UgljanRad.exe
ls -la ../../dist/UgljanRad.exe
