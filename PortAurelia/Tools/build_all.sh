#!/usr/bin/env bash
# Full rebuild: procedural assets -> Godot import -> validation -> tests -> Windows builds.
#   Tools/build_all.sh            everything
#   Tools/build_all.sh --assets   only regenerate assets + import
#   Tools/build_all.sh --no-tests skip the automated gameplay tests
set -euo pipefail
cd "$(dirname "$0")/.."
PY=Tools/Blender/venv/bin/python
GODOT=Tools/Godot/Godot_v4.4.1-stable_linux.x86_64
step() { echo; echo "==== $*"; }
step "1/9 textures";            $PY Tools/Textures/generate_textures.py
step "2/9 materials";           $PY Tools/Materials/build_materials.py
step "3/9 sound effects";       $PY Tools/Audio/generate_sfx.py
step "4/9 city plan + chunks";  $PY -u Blender/scripts/generate_city.py -- --plan
step "5/9 assets";              for s in build_asset_library generate_vehicles generate_weapons generate_characters generate_interiors; do $PY -u Blender/scripts/$s.py; done
step "6/9 map";                 $PY Tools/Map/render_map.py
step "7/9 Godot import";        $GODOT --headless --path Game --import
step "8/9 validation";          for s in export_glb setup_materials setup_uvs generate_lods generate_colliders; do $PY Blender/scripts/$s.py; done
[ "${1:-}" = "--assets" ] && exit 0
if [ "${1:-}" != "--no-tests" ]; then
  step "tests"
  for t in gameplay_test traffic_test ped_test police_test economy_test mission_test interior_test flow_test; do
    $GODOT --headless --path Game res://tests/$t.tscn 2>&1 | grep -E "FAIL|===" || true
  done
fi
step "9/9 export";              python3 Tools/Export/build_release.py
