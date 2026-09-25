# Full rebuild on Windows: procedural assets -> Godot import -> validation -> Windows builds.
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")
$py = "Tools\Blender\venv\Scripts\python.exe"
$godot = "Tools\Godot\Godot_v4.4.1-stable_win64_console.exe"
& $py Tools\Textures\generate_textures.py
& $py Tools\Materials\build_materials.py
& $py Tools\Audio\generate_sfx.py
& $py -u Blender\scripts\generate_city.py -- --plan
foreach ($s in "build_asset_library","generate_vehicles","generate_weapons","generate_characters","generate_interiors") {
    & $py -u "Blender\scripts\$s.py"
}
& $py Tools\Map\render_map.py
& $godot --headless --path Game --import
foreach ($s in "export_glb","setup_materials","setup_uvs","generate_lods","generate_colliders") { & $py "Blender\scripts\$s.py" }
& $py Tools\Export\build_release.py
