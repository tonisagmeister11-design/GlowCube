@echo off
rem Startet den Creator direkt aus dem Quellcode (Python 3.10+ mit Tkinter und "pip install -e ." noetig).
setlocal
cd /d "%~dp0"
where py >nul 2>nul
if %errorlevel%==0 ( py -3 run_creator.py %* ) else ( python run_creator.py %* )
endlocal
