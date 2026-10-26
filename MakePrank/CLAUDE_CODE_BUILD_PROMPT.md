# Claude Code — Startprompt für MakePrank

Arbeite direkt im bestehenden Repository. Lies zuerst `CLAUDE_CODE_MASTER_SPEC.md`, dann `docs/FEATURE_MATRIX.md`, `docs/ARCHITECTURE.md`, `docs/STATUS.md` und den vorhandenen Code. Der Master-Spec ist die vollständige Arbeitsanweisung und definiert Produktumfang, Reihenfolge, Tests und verbindliche Grenzen.

**Auftrag:** Implementiere die Spezifikation im vorhandenen Projekt. Schreibe nicht nur einen Plan. Arbeite in kleinen Schritten, ändere Dateien, führe Tests aus und aktualisiere die Feature-Matrix ehrlich. Priorität: robustes Paketformat und Stop/Consent-Verhalten → lokale app-interne Effekte → Creator/Client UX → begrenztes, gepaartes Netzwerk → Windows-Build und Dokumentation.

Das Ergebnis soll ein reproduzierbar baubares Windows-Projekt sein. Behaupte nicht, dass eine Windows-EXE gebaut oder getestet wurde, wenn du nicht auf Windows gebaut/getestet hast. Keine erfundenen Testresultate. Halte dich an die Sicherheitsgrenzen im Master-Spec; insbesondere keine versteckte Persistenz, keine beliebigen Remote-Kommandos und keine Remote-Auslösung echter Shutdown-/Reboot-Aktionen.

Am Ende liefere den Pflicht-Abschlussbericht aus Abschnitt 10 von `CLAUDE_CODE_MASTER_SPEC.md`.
