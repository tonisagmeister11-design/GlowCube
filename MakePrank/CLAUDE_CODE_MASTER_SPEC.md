# MakePrank — Master-Spezifikation für Claude Code

> **Auftrag:** Dieses Dokument zusammen mit dem gesamten Repository an Claude Code übergeben. Claude Code soll den vorhandenen Code untersuchen, verbessern und lauffähig machen — nicht nur einen Plan schreiben. Jede erledigte Funktion muss implementiert und getestet sein. Fehlende oder auf der aktuellen Plattform nicht prüfbare Dinge müssen ausdrücklich als offen markiert werden.

## 0. Produktziel

MakePrank ist eine Windows-Desktop-App, inspiriert von den in `docs/FEATURE_MATRIX.md` dokumentierten Funktionen des Minecraft-Mods Your PC Mine 1.0.5. Sie besteht aus:

1. **Creator/Control Center** auf dem Ersteller-PC: Pranks auswählen, konfigurieren, Vorschau ansehen, ein Paket exportieren, einen bekannten/gekoppelten Test-Client verwalten und erlaubte Effekte starten/stoppen.
2. **Empfänger-Client** für Windows: eine vom Ersteller geteilte Paketdatei öffnen, die enthaltenen Effekte und Grenzen sichtbar anzeigen, die Nutzungsbedingungen ausdrücklich bestätigen und anschließend die freigegebenen Effekte ausführen. Der Client bleibt sichtbar und hat stets einen gut erreichbaren Stop-/Trennen-Knopf.
3. **Paketformat `.mpk`**: versionierte, streng validierte Daten; keine eingebetteten Skripte oder beliebigen ausführbaren Inhalte.
4. **Optionales Netzwerkmodul** für Geräte, deren Nutzer das Pairing bewusst und separat durchführen. Netzwerkfunktionen dürfen ausschließlich die unten definierte Whitelist an App-internen Effekten auslösen.

Das Ziel ist ein robustes, nachvollziehbar getestetes Windows-Projekt mit reproduzierbarem Build. Keine Behauptung „vollständig“, solange die Feature-Matrix nicht ehrlich aktualisiert wurde.

## 1. Nicht verhandelbare Grenzen

Diese Spezifikation erlaubt keine heimliche Fernsteuerungssoftware. Daher:

- Kein Tarnen des Clients als harmloses Dokument, keine verdeckte Ausführung, keine versteckten Prozesse, keine Persistenz, kein Autostart und kein Dienst ohne separate, bewusste Installation.
- Keine Fernsteuerung des Windows-Desktops oder fremder Anwendungen; keine Maus-/Tastatur-Injektion, kein Credential-Zugriff, keine Prozessinjektion, kein Sicherheits-Bypass.
- Keine Remote-Auslösung echter Systemaktionen wie Herunterfahren, Neustarten, Sperren oder Abmelden. Diese Aktionen werden als App-interne Simulation umgesetzt. Wenn eine echte lokale Systemaktion später ergänzt wird, muss sie lokal am betroffenen Gerät separat bestätigt werden und darf nicht aus einem Netzwerkkommando gestartet werden.
- Keine Datei-/Datenlöschung, keine Dialogflut, keine endlose Overlays, kein absichtliches Festhängen, keine globale Fenster-Minimierung oder Bildschirmblockade.
- Keine Netzwerkverbindung ohne sichtbares Pairing und Statusanzeige. Kein stilles Cloud-Relay, keine automatische Portfreigabe und keine Verbindung zu beliebigen fremden Hosts.
- Empfänger kann jederzeit sofort stoppen, die Sitzung beenden und die Verbindung trennen. Ein Disconnect, Timeout, Fehler oder Paketänderung stoppt die laufenden Effekte.
- Screamer-/Audioeffekte müssen angekündigt sein, dürfen nicht erzwungenes Vollbild nutzen und müssen sofort schließbar sein. Keine Lautstärke-Maximierung und keine Endlosschleifen.
- Nutzungsbedingungen dürfen nicht nur ein vorgeschobener Ein-Klick-Schritt sein: Vor Start werden die konkret ausgewählten Effekte, Dauer, Audio und Netzwerkstatus angezeigt. Zustimmung ist freiwillig, nicht vorausgewählt, und Ablehnung startet nichts.

## 2. Original-Mod: Feature-Inventar und Portierungsregeln

Nutze `docs/FEATURE_MATRIX.md` und `analysis/YPM-1.0.5.jar` als Referenz. Übernimm keine Minecraft-spezifischen Funktionen so, als wären sie bereits Windows-Funktionen. Für jedes Feature dokumentiere: `implemented`, `partial`, `simulated`, `not implemented`, `not applicable` oder `blocked by design`.

### 2.1 Funktionen, die als echte MakePrank-App-Effekte umgesetzt werden sollen

- **Error dialog:** konfigurierbarer Titel, Text, Icon, Schaltflächen; innerhalb der App.
- **MsgBox:** Varianten OK, OK/Cancel, Yes/No, Yes/No/Cancel, Retry/Cancel, Abort/Retry/Ignore; keine Aktion darf außerhalb der App ausgeführt werden.
- **Toast:** sichtbare App-Benachrichtigung mit konfigurierbarem Text und Dauer.
- **Fake browser:** Browserfenster-Attrappe mit frei konfigurierbarer Beispielseite/Text. Keine echte URL öffnen, außer der Empfänger klickt ausdrücklich auf einen klar gekennzeichneten externen Link.
- **Fake notepad/text editor:** Editor-Attrappe. Nicht ungefragt auf echte Dateien schreiben.
- **Fake shutdown/reboot:** optisch überzeugende, klar als Simulation definierte Szene in einem normalen, schließbaren App-Fenster; niemals tatsächliche Systemaktion.
- **Color bars:** SMPTE, HD, EBU, PLUGE, mono, RGB, static als App-interne Animationen. Optionaler Ton nur nach Anzeige und Zustimmung.
- **Screamer-like visual:** auswählbares, mitgeliefertes oder vom Nutzer importiertes Bild; normales schließbares Fenster, keine erzwungene Vollbildansicht, keine extremen Lichtblitze, keine automatische externe Datei-/URL-Ausführung.
- **Sound effects:** kurze, begrenzte System-/TV-ähnliche Töne mit Vorschau, Lautstärkebegrenzung und Stummschalter. Audio muss vor Start klar ausgewiesen werden.
- **Window shake:** nur das eigene MakePrank-Fenster bewegen; nicht den gesamten Desktop oder andere Apps.
- **Text overlay:** innerhalb eines MakePrank-Fensters, Farbe/Größe/Position/Dauer konfigurierbar.
- **Overlay session:** verwaltete, zeitbegrenzte Effekt-Sitzung mit Start/Pause/Stop und Restdauer.
- **Console overlay:** animierte Terminal-Attrappe; niemals echte Shell-Kommandos ausführen.
- **Visual effects invert/flip:** ausschließlich auf dem MakePrank-eigenen Canvas/Renderbereich.
- **Minimize/freeze concepts:** nur innerhalb des MakePrank-Fensters als zeitlich begrenzte, abbrechbare Simulation; niemals andere Fenster minimieren oder das Betriebssystem/UI blockieren.
- **Wallpaper:** Vorschau/Simulation innerhalb der App. Keine Änderung der echten Windows-Hintergrundkonfiguration.
- **Text/chat:** Text in MakePrank anzeigen; niemals ungefragt Text in fremde Apps, Chats oder Eingabefelder schreiben.
- **Stop/restore:** alle eigenen Fenster, Sounds, Animationen und Timer müssen zuverlässig beendet und Ressourcen freigegeben werden.

### 2.2 Nicht übertragbar oder absichtlich nicht als Remote-Funktion umgesetzt

- Minecraft-Perspektive/Kamera und Possess-/Spielersteuerung sind Minecraft-spezifisch.
- Reale Shutdown-/Reboot-/Logout-Kommandos aus der Ferne sind ausgeschlossen.
- Globale Fenster-/Eingabesteuerung, systemweite Overlays, Bildschirm-/Eingabe-Lock, Prozesssteuerung, Dialogspam und versteckte Persistenz sind ausgeschlossen.
- Netzwerk-Payloads des Mods sind keine Blaupause für unbeschränkte Remote-Command-Ausführung. Verwende ein enges typisiertes Protokoll mit fester Whitelist.

## 3. Creator UI

Implementiere ein übersichtliches Windows-Dashboard mit:

- Seiten/Ansichten: Dashboard, Effektkatalog, Paket-Editor, Vorschau, Export, Clients/Pairing, Sitzungsprotokoll, Einstellungen, Hilfe.
- Effektkatalog mit Suche, Kategorien, Beschreibungen, Risikohinweisen, Beispielvorschau und Parametern.
- Paket-Editor: Effekte hinzufügen/entfernen/sortieren, Parameter ändern, maximale Laufzeit und Reihenfolge festlegen.
- Live-Vorschau, die keine echte Aktion ausführt.
- Paketexport als `.mpk` mit Name, Version, Beschreibung, Effektliste, Parametern, geschätzter Dauer, Audiokennzeichnung und Hash.
- Vor Export eine lesbare Zusammenfassung anzeigen. Exportierte Pakete enthalten ausschließlich Daten und optional geprüfte, begrenzte Medienressourcen.
- Clients/Pairing-Ansicht mit Status, Client-ID, Berechtigungsumfang, Sitzungsdauer, letzter Aktivität, Trennen und Sperren/Widerrufen.
- Sitzungsprotokoll: Zeit, Paket-Hash, Effektname, Start/Stop, Fehler, Pairing-/Disconnect-Ereignisse; keine Erfassung fremder Inhalte oder Tastatureingaben.
- Globale „Alles stoppen“-Aktion für lokale Effekte und alle verwalteten Test-Sitzungen.

## 4. Empfänger-Client UI

- Beim Öffnen eines `.mpk`-Pakets Datei und Schema vollständig validieren, bevor irgendein Effekt startet.
- Paketübersicht anzeigen: Absender/Creator-Label sofern vorhanden, Paketname, alle Effekte, Dauer, Sound-/Bildhinweise, Netzwerkbedarf und Stop-Möglichkeiten.
- Zustimmung durch eine nicht vorausgewählte Checkbox und separaten Startknopf. Ablehnen/Schließen startet nichts.
- Sichtbarer laufender Sitzungsstatus mit großem **STOPP**-Knopf, Restdauer und aktiven Effekten.
- Escape und Fenster-Schließen stoppen alle Effekte; Stop darf nicht von Netzwerk oder Creator abhängen.
- Bei Paketdatei-Löschung, Änderung, Hashabweichung, ungültigem Status, Disconnect, Timeout oder App-Fehler sauber stoppen.
- Nach Sitzungsende alle eigenen Fenster, Audioquellen, Timer und Netzwerk-Sockets schließen. Keine automatische Wiederaufnahme nach Neustart.
- Ein optionaler Client kann beim Start den Creator-Status anzeigen; er darf nicht unsichtbar im Hintergrund laufen.

## 5. Netzwerk: sicherer Pairing-Prototyp

Implementiere Netzwerk erst nach stabiler lokaler App und Paketschema. Netzwerkprotokoll:

- Standardmäßig deaktiviert. Der Empfänger muss „Pairing starten“ ausdrücklich anklicken und einen zeitlich begrenzten Pairing-Code/QR-Code anzeigen.
- Creator muss diesen Code bewusst eingeben/scannen und die angezeigte Client-Identität bestätigen. Codes sind zufällig, kurzlebig, einmalig und gegen Brute-Force geschützt.
- TLS oder eine gleichwertige, etablierte authentifizierte Verschlüsselung. Keine eigene Kryptografie. Keine Klartext-Tokens in Logs oder Dateien.
- Nach erfolgreichem Pairing zeigt der Empfänger dauerhaft einen sichtbaren Verbindungsindikator, den Creator-Namen und die Möglichkeit zum sofortigen Trennen.
- Keine beliebigen Shell-/OS-Kommandos. Nachrichten sind streng typisiert (z. B. `PING`, `STATUS`, `START_EFFECT`, `STOP_SESSION`, `DISCONNECT`) und jede Aktion wird server- und clientseitig gegen eine feste Whitelist validiert.
- `START_EFFECT` enthält nur eine Effekt-ID und validierte Parameter, die in einem vorher angezeigten/akzeptierten Paket enthalten sind. Kein beliebiger Python-Code, keine URL-Fetches, keine Pfade oder Shell-Strings.
- Sitzungs-Token zufällig, kurzlebig, widerrufbar und nur im Arbeitsspeicher, soweit möglich. Rate-Limits, Größenlimits, Protokollversion, Heartbeat, Timeout und sichere Fehlerbehandlung.
- Bei Disconnect/Timeout sofort laufende Remote-Effekte beenden; lokale Stop-Funktion funktioniert ohne Netzwerk.
- Keine automatische Portweiterleitung, keine ungefragte Firewalländerung, kein Standard-Cloud-Relay und kein verstecktes Wiederverbinden. Remote über Internet nur über eine ausdrücklich konfigurierte, dokumentierte Infrastruktur; zunächst LAN-/Loopback-Testmodus.
- Tests für ungültige Authentifizierung, abgelaufene Codes, Replay, manipulierte Pakete, zu große Nachrichten, unbekannte Aktions-IDs, Disconnect und Timeout.

## 6. `.mpk`-Paketformat und Sicherheit

- Versioniertes JSON-Manifest plus Ressourcenarchiv oder gleichwertiges dokumentiertes Format.
- Keine `pickle`-Deserialisierung, kein `eval`, keine dynamischen Imports, keine Skript-Ausführung und kein Start eingebetteter EXE/DLL-Dateien.
- JSON-Schema strikt validieren; unbekannte Aktionen ablehnen; alle Parameter auf Typ, Wertebereich, Länge und erlaubte Zeichen prüfen.
- Ressourcenlimits: maximale Gesamtgröße, Einzeldateigröße, Anzahl Ressourcen, Bilddimensionen und Audiolänge; erlaubte MIME-Typen/Erweiterungen; Pfad-Traversal und Zip-Slip verhindern.
- SHA-256 für Paketintegrität, aber Hash nicht als alleiniger Echtheitsnachweis ausgeben. Hashänderung muss eine neue Zustimmung auslösen.
- Keine Ressourcen automatisch aus dem Internet laden. Eingebettete Medien als Daten behandeln und validieren.
- Kompatibilität: Versionierung und verständliche Fehlermeldung bei inkompatiblen Paketen.

## 7. Windows Build & Distribution

- Python-Version/Abhängigkeiten im `pyproject.toml` pinnen oder kompatible Grenzen angeben.
- `build_windows.bat` soll eine saubere virtuelle Umgebung anlegen, Abhängigkeiten installieren, Tests laufen lassen und PyInstaller-Build erzeugen.
- Baue GUI ohne zusätzliches Konsolenfenster; liefere einen Onedir-Build als primäre Variante, optional Onefile nur nach Prüfung.
- Artefakte: Creator EXE, Client/Launcher EXE falls getrennt, README_DE, Benutzerhandbuch, Buildbericht, SHA-256-Manifest und ZIP.
- Doppelklick auf `.mpk`: dokumentiere eine sichere portable Lösung oder eine explizite Dateizuordnung bei Installation; keine Registry-Änderung ohne Nutzeraktion.
- Unter Nicht-Windows-Umgebung darf keine native Windows-Buildvalidierung behauptet werden. Stattdessen Build-Schritte und offene Plattformchecks auflisten.
- Keine Codesignatur erfinden. Falls nicht signiert, klar dokumentieren.

## 8. Tests und Qualitätsanforderungen

Unit-/Integrationstests mindestens für:

1. Paket erstellen, exportieren, importieren und round-trip.
2. Ungültiges JSON, fehlende Felder, unbekannte Aktionen, falsche Typen und Grenzwerte.
3. Ressourcenlimits, Zip-Slip/Pfad-Traversal, unerlaubte MIME-Typen.
4. Zustimmung abgelehnt, Checkbox nicht vorausgewählt, Hashänderung erfordert neue Zustimmung.
5. Stop per Button, Escape, Fenster-Schließen, Timeout, Paketänderung und Datei-Löschung.
6. Jeder Effekt erstellt/entfernt Fenster und Ressourcen sauber; kein Effekt überlebt Stop.
7. Netzwerk: Pairing/Unpairing, Authentifizierung, TLS-Konfiguration, Replay/Expired-Code, ungültige Payload, Timeout, Disconnect, Rate-Limit.
8. Build-Smoke-Test und App-Start; Windows-spezifische Tests nur auf Windows als solche kennzeichnen.
9. Keine Testbehauptung ohne tatsächlich ausgeführten Befehl und Ergebnis.

Qualitätsregeln:
- Typannotationen, Logging ohne Geheimnisse, klare Fehlerdialoge, keine leeren `except`-Blöcke.
- Effekt-Registry als zentrale Quelle von Namen, Parametern, Berechtigungen und UI-Metadaten.
- Trenne UI, Paketschema, Effekt-Engine, Sitzungsmanager und Netzwerktransport.
- Tests automatisieren; nichtfunktionale Menüpunkte entfernen oder als deaktiviert/offen markieren.
- Schreibe keine „TODO“-Attrappe und markiere sie als fertig.

## 9. Arbeitsplan — in dieser Reihenfolge

1. Repository, Status, vorhandene Tests und `docs/FEATURE_MATRIX.md` prüfen. Vorhandene Architektur beibehalten, wo sinnvoll.
2. Aktionskatalog und Feature-Matrix auf den realen Ist-Stand bringen.
3. Paketformat und Consent-/Session-Engine härten.
4. Alle lokalen, app-internen Effekte aus Abschnitt 2.1 implementieren und einzeln testen.
5. Creator-UI und Empfänger-Client vervollständigen.
6. Netzwerk-Pairing und begrenztes Effektprotokoll ergänzen; Sicherheits-/Integrationstests schreiben.
7. Windows-Buildskript und Dokumentation aktualisieren.
8. Tests und Build ausführen; Ergebnis mit tatsächlichen Kommandos, Zahlen, Pfaden und offenen Punkten berichten.

## 10. Abschlussbericht — Pflichtformat

Am Ende gib eine Tabelle mit jeder Funktion aus `docs/FEATURE_MATRIX.md` und Status aus. Zusätzlich:

- geänderte/neu erstellte Dateien;
- exakt ausgeführte Testkommandos und tatsächliche Ergebnisse;
- tatsächliche Artefaktpfade und SHA-256-Hashes, sofern Dateien existieren;
- genaue Windows-Build-Schritte;
- bekannte Bugs, Plattformgrenzen und nicht implementierte Funktionen;
- Sicherheitsentscheidungen, insbesondere dass echte Shutdown-/Reboot-Aktionen nicht remote ausführbar sind.

**Wichtig:** Nicht nur erklären, was getan werden könnte. Code ändern, Tests ausführen, Build versuchen, konkrete Ergebnisse berichten. Niemals eine Funktion als fertig bezeichnen, nur weil sie im UI auftaucht.
