#!/bin/bash
# Baut die Windows-Version (eine einzelne EXE, ohne Installation) mit Electron und electron-builder.
# Gleicher App-Code wie die APK (app/assets), plus Testhilfen am PC (window.__desktop).
set -e -o pipefail
cd "$(dirname "$0")"
W=build/desktop
rm -rf "$W" && mkdir -p "$W" dist
cp desktop/main.js desktop/preload.js "$W"/
cp -r app/assets "$W"/assets
cp desktop/icon.png desktop/icon.ico "$W"/
VER=$(grep -o 'versionName="[^"]*"' app/AndroidManifest.xml | cut -d'"' -f2)
cat > "$W"/package.json <<JSON
{
  "name": "ugljan-rad", "productName": "Velora", "version": "$VER.0", "description": "Fahrrad-Navigation", "main": "main.js", "author": "Ugljan Rad",
  "devDependencies": { "electron": "33.4.11", "electron-builder": "25.1.8" },
  "build": { "appId": "hr.ugljan.rad", "files": ["main.js", "preload.js", "icon.png", "assets/**"], "directories": { "output": "out" },
    "win": { "target": [{ "target": "portable", "arch": ["x64"] }], "icon": "icon.ico", "signAndEditExecutable": false },
    "portable": { "artifactName": "Velora.exe" } }
}
JSON
cd "$W"
ELECTRON_SKIP_BINARY_DOWNLOAD=1 npm install --no-audit --no-fund -q
npx electron-builder --win portable --x64
cp out/Velora.exe ../../dist/Velora.exe
ls -la ../../dist/Velora.exe
