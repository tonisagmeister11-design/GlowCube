#!/usr/bin/env bash
# Installs the portable development toolchain into Tools/ (nothing is installed system-wide):
#   Tools/Godot/   Godot 4.4.1 editor (self-contained mode) + export templates
#   Tools/Blender/venv/  Python venv with the `bpy` module (Blender 4.2 as a Python module),
#                        numpy and Pillow for the procedural asset pipeline
set -euo pipefail
cd "$(dirname "$0")"
GV=4.4.1
BASE="https://github.com/godotengine/godot/releases/download/${GV}-stable"
mkdir -p Godot Blender
if [ ! -x "Godot/Godot_v${GV}-stable_linux.x86_64" ]; then
  echo "[toolchain] Godot ${GV} editor"
  curl -fL -o /tmp/godot.zip "${BASE}/Godot_v${GV}-stable_linux.x86_64.zip"
  unzip -o /tmp/godot.zip -d Godot && rm /tmp/godot.zip
  touch "Godot/._sc_"                       # self-contained: editor data stays in Tools/Godot
fi
TPL="Godot/editor_data/export_templates/${GV}.stable"
if [ ! -f "${TPL}/windows_release_x86_64.exe" ]; then
  echo "[toolchain] export templates"
  curl -fL -o /tmp/tpl.tpz "${BASE}/Godot_v${GV}-stable_export_templates.tpz"
  mkdir -p "${TPL}" && unzip -o -j /tmp/tpl.tpz "templates/*" -d "${TPL}" && rm /tmp/tpl.tpz
fi
if [ ! -x "Blender/venv/bin/python" ]; then
  echo "[toolchain] Blender (bpy) venv"
  python3.11 -m venv Blender/venv || python3 -m venv Blender/venv
  Blender/venv/bin/pip install --upgrade pip
  Blender/venv/bin/pip install "bpy==4.2.*" numpy pillow
fi
echo "[toolchain] ready"
