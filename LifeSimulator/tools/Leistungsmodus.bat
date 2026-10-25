@echo off
REM Startet Life Simulator im Kompatibilitaetsmodus (OpenGL).
REM Fuer aeltere PCs und Laptops ohne Vulkan-Unterstuetzung.
cd /d "%~dp0"
start "" "LifeSimulator.exe" --rendering-method gl_compatibility
