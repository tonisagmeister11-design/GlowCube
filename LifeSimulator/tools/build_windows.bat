@echo off
REM Erstellt die Windows-Version unter build\windows\.
REM Aufruf: tools\build_windows.bat C:\Pfad\zu\Godot_v4.3-stable_win64.exe
setlocal
cd /d "%~dp0\.."
set GODOT=%1
if "%GODOT%"=="" set GODOT=godot
if not exist build\windows mkdir build\windows
"%GODOT%" --headless --path . --import
"%GODOT%" --headless --path . --export-release "Windows Desktop" build\windows\LifeSimulator.exe
dir build\windows
endlocal
