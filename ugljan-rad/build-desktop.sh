#!/bin/bash
# Baut die Windows-Version (eine einzelne EXE, ohne Installation) mit Electron und electron-builder.
# Gleicher App-Code wie die APK (app/assets), plus Testhilfen am PC (window.__desktop).
set -e -o pipefail
cd "$(dirname "$0")"
W=build/desktop
rm -rf "$W" && mkdir -p "$W" dist
cp desktop/main.js desktop/preload.js desktop/llm.js "$W"/
# Soul-Sprachmodell: llama-server fuer Windows (llama.cpp, MIT-Lizenz), passend zu build-native.sh
LLB=b12032
LLW=${LLAMA_WIN:-/tmp/llm/win/cpu}
if [ ! -f "$LLW/llama-server.exe" ]; then mkdir -p "$LLW" && curl -sSL -o /tmp/llm-win.zip https://github.com/ggml-org/llama.cpp/releases/download/$LLB/llama-$LLB-bin-win-cpu-x64.zip && unzip -o -q /tmp/llm-win.zip -d "$LLW"; fi
mkdir -p "$W"/llm && cp "$LLW"/llama-server.exe "$LLW"/llama-server-impl.dll "$LLW"/llama-common.dll "$LLW"/llama.dll "$LLW"/mtmd.dll "$LLW"/ggml*.dll "$LLW"/libomp.dll "$LLW"/LICENSE-LLVM-OpenMP "$W"/llm/
cp /tmp/llm/llama.cpp/LICENSE "$W"/llm/LICENSE-llama.cpp 2>/dev/null || true
cp -r app/assets "$W"/assets
cp desktop/icon.png desktop/icon.ico "$W"/
VER=$(grep -o "appVersionName = '[^']*'" app/build.gradle | cut -d"'" -f2)
cat > "$W"/package.json <<JSON
{
  "name": "velora", "productName": "BikeOra", "version": "$VER.0", "description": "Fahrrad-Navigation", "main": "main.js", "author": "BikeOra",
  "devDependencies": { "electron": "33.4.11", "electron-builder": "25.1.8" },
  "build": { "appId": "app.velora.bike", "files": ["main.js", "preload.js", "llm.js", "icon.png", "assets/**"], "extraResources": [{ "from": "llm", "to": "llm" }], "directories": { "output": "out" },
    "win": { "target": [{ "target": "portable", "arch": ["x64"] }], "icon": "icon.ico", "signAndEditExecutable": false },
    "portable": { "artifactName": "BikeOra.exe" } }
}
JSON
cd "$W"
ELECTRON_SKIP_BINARY_DOWNLOAD=1 npm install --no-audit --no-fund -q
npx electron-builder --win portable --x64
cp out/BikeOra.exe ../../dist/BikeOra.exe
ls -la ../../dist/BikeOra.exe
