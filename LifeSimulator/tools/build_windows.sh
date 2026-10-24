#!/bin/bash
# Erstellt die Windows-Version (EXE + PCK) unter build/windows/.
# Aufruf: tools/build_windows.sh [pfad/zu/godot]
# Benoetigt Godot 4.3 und die passenden Export-Templates (4.3.stable).
set -euo pipefail
cd "$(dirname "$0")/.."
GODOT="${1:-${GODOT:-godot}}"
mkdir -p build/windows
"$GODOT" --headless --path . --import
"$GODOT" --headless --path . --export-release "Windows Desktop" build/windows/LifeSimulator.exe
ls -la build/windows
