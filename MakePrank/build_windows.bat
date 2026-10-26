@echo off
rem MakePrank - Windows-Build: venv -> Abhaengigkeiten -> Tests -> PyInstaller (onedir, ohne Konsole) -> Release-ZIP
setlocal
cd /d "%~dp0"

where py >nul 2>nul
if %errorlevel%==0 ( set "PY=py -3" ) else ( set "PY=python" )

if exist .venv rmdir /s /q .venv
%PY% -m venv .venv
if errorlevel 1 goto :error
call .venv\Scripts\activate.bat
python -m pip install --upgrade pip
if errorlevel 1 goto :error
python -m pip install -e ".[dev,build]"
if errorlevel 1 goto :error

echo.
echo === Tests ===
python -m pytest -q --timeout=120 > build_test_log.txt 2>&1
set TESTCODE=%errorlevel%
type build_test_log.txt
if not "%TESTCODE%"=="0" goto :error
for /f "delims=" %%L in ('powershell -NoProfile -Command "(Get-Content build_test_log.txt | Select-Object -Last 1)"') do set "TESTSUMMARY=%%L"

echo.
echo === PyInstaller ===
if exist dist rmdir /s /q dist
if exist build rmdir /s /q build
python -m PyInstaller --noconfirm --clean --windowed --onedir --name MakePrank-Creator run_creator.py
if errorlevel 1 goto :error
python -m PyInstaller --noconfirm --clean --windowed --onedir --name MakePrank-Client run_client.py
if errorlevel 1 goto :error

echo.
echo === Start-Smoke-Test ===
python tools\smoke_test.py dist\MakePrank-Creator\MakePrank-Creator.exe 4
if errorlevel 1 goto :error
python tools\smoke_test.py dist\MakePrank-Client\MakePrank-Client.exe 4
if errorlevel 1 goto :error

python tools\make_release.py "%TESTSUMMARY%" "Creator und Client starteten und liefen je 4 s"
if errorlevel 1 goto :error

echo.
echo Fertig. Ergebnis: release\  (ZIP + Ordner mit SHA256SUMS.txt und BUILD_REPORT.txt)
echo Die Programme sind NICHT signiert.
goto :end
:error
echo.
echo Build fehlgeschlagen. Siehe Meldungen oben.
exit /b 1
:end
endlocal
