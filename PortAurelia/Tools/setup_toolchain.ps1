# Installs the portable development toolchain into Tools\ (Windows, PowerShell 5+).
#   Tools\Godot\          Godot 4.4.1 editor (self-contained) + export templates
#   Tools\Blender\venv\   Python venv with bpy (Blender 4.2 module), numpy, Pillow
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
$gv = "4.4.1"
$base = "https://github.com/godotengine/godot/releases/download/$gv-stable"
New-Item -ItemType Directory -Force Godot, Blender | Out-Null
if (-not (Test-Path "Godot\Godot_v$gv-stable_win64.exe")) {
    Write-Host "[toolchain] Godot $gv editor"
    Invoke-WebRequest "$base/Godot_v$gv-stable_win64.exe.zip" -OutFile "$env:TEMP\godot.zip"
    Expand-Archive "$env:TEMP\godot.zip" -DestinationPath Godot -Force
    New-Item -ItemType File -Force "Godot\._sc_" | Out-Null
}
$tpl = "Godot\editor_data\export_templates\$gv.stable"
if (-not (Test-Path "$tpl\windows_release_x86_64.exe")) {
    Write-Host "[toolchain] export templates"
    Invoke-WebRequest "$base/Godot_v$gv-stable_export_templates.tpz" -OutFile "$env:TEMP\tpl.zip"
    Expand-Archive "$env:TEMP\tpl.zip" -DestinationPath "$env:TEMP\tpl" -Force
    New-Item -ItemType Directory -Force $tpl | Out-Null
    Copy-Item "$env:TEMP\tpl\templates\*" $tpl -Force
}
if (-not (Test-Path "Blender\venv\Scripts\python.exe")) {
    Write-Host "[toolchain] Blender (bpy) venv - requires Python 3.11 (py launcher)"
    py -3.11 -m venv Blender\venv
    Blender\venv\Scripts\python.exe -m pip install --upgrade pip
    Blender\venv\Scripts\python.exe -m pip install "bpy==4.2.*" numpy pillow
}
Write-Host "[toolchain] ready"
