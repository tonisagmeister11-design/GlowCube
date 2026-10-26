# MakePrank – Benutzeranleitung

## 1. Creator (Ersteller)

1. **Effektkatalog** – suchen/filtern, Effekt wählen, Risikohinweis und Parameter lesen, **Zum Paket hinzufügen** oder **Beispielvorschau**.
2. **Paket-Editor** – Name, Beschreibung, Absender, maximale Sitzungsdauer (1–1800 s). Aktionen verschieben (↑↓) oder entfernen.
   Pro Aktion: Effekt, Titel, Dauer (1–120 s), Text und Parameter (Farben `#RRGGBB`, Auswahllisten, Zahlen mit Grenzen). **Aktion übernehmen** nicht vergessen.
   Für das Screamer-Bild: *Bild importieren …* (PNG/GIF, ≤ 1 MB, ≤ 1920×1080, höchstens 4 Bilder pro Paket).
   Haken *Fernsteuerung per Pairing erlauben* nur setzen, wenn der Empfänger später Effekte aus der Ferne auslösen lassen soll.
3. **Vorschau** – spielt das Paket mit derselben Engine wie beim Empfänger, stumm, solange du den Ton nicht ausdrücklich einschaltest. Pause/Fortsetzen/Stopp.
4. **Export** – zeigt exakt die Zusammenfassung, die der Empfänger sieht (Effekte, Dauer, Audio, Bilder, Netzwerk). **Als .mpk exportieren** schreibt die Datei und zeigt ihren SHA-256.
   Gib dem Empfänger die `.mpk` **und** installiere nichts heimlich: er braucht den MakePrank-Client.
5. **Clients/Pairing** – siehe Abschnitt 3. **Sitzungsprotokoll**, **Einstellungen** (Creator-Name, öffentliche IPs standardmäßig verboten), **Hilfe**.
6. **ALLES STOPPEN** (oben rechts, auch Esc) beendet die Vorschau und sendet allen verbundenen Empfängern `STOP_SESSION`.

## 2. Empfänger-Client

1. **Paket öffnen …** (oder `.mpk` auf die EXE ziehen). Die Datei wird vollständig geprüft; bei Fehlern startet nichts.
2. Du siehst Absender, Effekte, Dauer, **Audio ja/nein**, Bilder und Netzwerkbedarf.
3. **Prüfen & starten …** öffnet den Zustimmungsdialog. Der Haken ist **nie vorausgewählt**; erst danach wird **Zustimmen und starten** aktiv. *Ablehnen* oder Esc startet nichts.
4. Während der Sitzung: Status, aktiver Effekt, Restdauer, **Pause/Fortsetzen**, **Ton stummschalten** und der große **STOPP**-Knopf.
   **STOPP, Esc und Schließen des Fensters** beenden sofort alles – unabhängig von Netzwerk und Creator.
5. Die Sitzung endet außerdem bei: Zeitablauf, Löschen/Verschieben/Ändern der Paketdatei, Trennen der Verbindung, Netzwerk-Timeout oder App-Fehler.
   Eine geänderte Datei braucht eine **neue** Zustimmung. Es gibt keinen Autostart und keine Wiederaufnahme nach Neustart.

## 3. Pairing und Fernsteuerung (optional, standardmäßig aus)

Nur für Pakete, die *Fernsteuerung per Pairing* erlauben.

1. **Empfänger:** *Nur Fernsteuerung erlauben …* (oder beim Start den zweiten Haken) → zustimmen. Dann *Pairing starten*.
   Standard ist **nur dieser PC (Test)**; für ein anderes Gerät im selben WLAN/LAN den Haken *Im lokalen Netzwerk erreichbar machen* setzen
   (Windows fragt eventuell nach der Firewall – MakePrank ändert nichts selbst). Der Empfänger zeigt Code, Adresse und einen **Pairing-Link**; sie gelten 120 s und einmalig.
2. **Creator:** Link bei *Clients/Pairing* einfügen → *Verbinden*. Der Empfänger muss den Creator-Namen **bestätigen**.
3. Der Empfänger sieht dauerhaft **● VERBUNDEN mit …** und den Knopf **Trennen**.
4. **Creator:** Aktion im Paket-Editor wählen → *Gewählte Aktion senden* (nur Aktionen des **exportierten, vom Empfänger geöffneten** Pakets) oder *Stopp senden*.
   Pro Sekunde höchstens ein Effektstart. Nach Änderung des Pakets zuerst neu exportieren.
5. Trennen, Timeout, Fehler oder Empfänger-**STOPP** beenden alle Fern-Effekte. Der Creator kann Empfänger *trennen* oder *sperren*.

Verbindungen sind standardmäßig nur zu Adressen im lokalen Netz erlaubt (127.x, 10.x, 172.16–31.x, 192.168.x, 169.254.x).
Internetbetrieb erfordert eine selbst konfigurierte Infrastruktur (z. B. VPN) und die Einstellung *öffentliche IP-Adressen erlauben*; ein Cloud-Relay oder automatische Portfreigaben gibt es nicht.

## 4. Dateien auf deinem PC

`%USERPROFILE%\.makeprank\`: `settings.json`, `consents.json` (nur Paket-Hashes), `sessions.jsonl` bzw. `creator-sessions.jsonl` (Protokoll ohne Tokens/Codes), `makeprank.log`.
Löschen entfernt alles; MakePrank legt sonst nichts ab.
