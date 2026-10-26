@echo off
rem Startet den Empfaenger-Client direkt aus dem Quellcode. Optional: run_client_windows.bat "C:\Pfad\paket.mpk"
setlocal
cd /d "%~dp0"
where py >nul 2>nul
if %errorlevel%==0 ( py -3 run_client.py %* ) else ( python run_client.py %* )
endlocal
