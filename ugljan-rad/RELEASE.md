# Velora – Release im Google Play Store

Stand: Version 7.1 (versionCode 27), Paket `app.velora.bike`, targetSdk 36, minSdk 24, nur arm64-v8a.

## 1. Bauen

```bash
./build-native.sh        # nur nötig, wenn sich native/velora_llm.cpp ändert (llama.cpp b12032)
./build-release.sh       # dist/Velora.aab (für die Play Console) + dist/Velora-release.apk (gleiche Signatur, zum Testen)
./build.sh               # dist/Velora.apk mit dem Entwickler-Schlüssel (nur zum Ausprobieren)
```

`build-release.sh` signiert mit dem **Upload-Schlüssel**, der nicht im Repository liegt:

```bash
VELORA_UPLOAD_KS=/sicherer/ort/velora-upload.jks VELORA_UPLOAD_PASS='…' ./build-release.sh
```

Den Upload-Schlüssel (`velora-upload.jks`, Alias `upload`) und sein Passwort sicher aufbewahren (zum Beispiel im Passwort-Manager und auf einem USB-Stick).
Mit „Play App Signing“ verwaltet Google den eigentlichen App-Signaturschlüssel. Geht der Upload-Schlüssel verloren, kann er über den Play-Support zurückgesetzt werden.

SHA-256 des Upload-Zertifikats: `13:6F:8B:10:98:4C:50:7F:5A:3E:67:65:7E:E1:30:20:73:4A:18:B2:7A:7A:62:01:CB:CB:93:B9:AB:62:FE:DC`

## 2. Was vor dem Veröffentlichen noch fehlt (nur der Kontoinhaber kann das)

- [ ] **Entwicklerkonto** bei Google Play (Inhaber muss volljährig sein, einmalig 25 US-Dollar).
      Neue private Konten müssen vor der Veröffentlichung einen **geschlossenen Test mit mindestens 12 Testern über 14 Tage** durchführen.
- [ ] **Datenschutzerklärung vervollständigen:** In `server/datenschutz.html` Name, Anschrift und E-Mail des Verantwortlichen eintragen, Text rechtlich prüfen lassen, dann `npx wrangler deploy` im Ordner `server`.
- [ ] **ADMIN_EMAIL** als Secret im Cloudflare-Dashboard setzen (für das Admin-Dashboard).
- [ ] **Video für die Vordergrunddienst-Erklärung** aufnehmen (siehe unten) und z. B. als nicht gelistetes YouTube-Video verlinken.
- [ ] Auf einem echten Android-Gerät testen: Start, GPS, eine Navigation mit gesperrtem Bildschirm, Sprachansagen, Widget.

## 3. Angaben in der Play Console

**Store-Eintrag:** Texte und Grafiken in `store/` (`listing.md`, `icon-512.png`, `feature-1024x500.png`, `screen-*.png`).

**Kategorie:** Karten & Navigation. **Kontakt-E-Mail:** die des Kontoinhabers.

**Datenschutzerklärung (URL):** https://velora-ki.toni-sagmeister11.workers.dev/datenschutz

**Konto löschen (URL, Pflicht für Apps mit Konten):** https://velora-ki.toni-sagmeister11.workers.dev/konto-loeschen
(In der App: Einstellungen → Konto löschen.)

**Werbung:** Nein. **In-App-Käufe:** derzeit keine (siehe Abschnitt 5).

**Zielgruppe:** 13–15, 16–17 und 18+ (nicht unter 13, damit gilt nicht die Familienrichtlinie). Für ein Konto verlangt die Datenschutzerklärung mindestens 14 Jahre.

**Inhaltsbewertung (IARC):** keine Gewalt, keine sexuellen Inhalte, kein Glücksspiel. Nutzer können mit einer KI chatten (Soul), Live-Standort kann mit einem Link geteilt werden. Keine Kommunikation zwischen Nutzern.

**KI-Inhalte:** Soul erzeugt Antworten mit einem Sprachmodell. Unter jeder KI-Antwort gibt es „Antwort melden“. Meldungen erscheinen im Admin-Dashboard.

**Vordergrunddienst (Android 14+):** Typ `location`, Anwendungsfall „Navigation“.
Beschreibung: „Während einer vom Nutzer gestarteten Navigation oder Fahrtaufzeichnung bekommt Velora weiter GPS-Positionen, damit Abbiegehinweise und die Aufzeichnung auch bei gesperrtem Bildschirm funktionieren. Ohne den Dienst bricht die Navigation ab, sobald der Bildschirm ausgeht.“
Video: Ziel suchen → „Hierhin fahren“ → „Los fahren“ → Bildschirm sperren → Benachrichtigung und Ansage zeigen.

**Berechtigungen:** Standort (genau und ungefähr; nur im Vordergrund, kein Hintergrund-Standort), Mikrofon (nur für die Spracheingabe bei Soul, wird erst beim Antippen abgefragt), Benachrichtigungen (Navigation läuft), Vibration, Internet.

**Datensicherheit (Data safety):**

| Datentyp | Erhoben | Geteilt | Zweck | Freiwillig |
|---|---|---|---|---|
| Genauer Standort | Ja (an Routen-/Wetterdienste für die Funktion; an den Velora-Server nur beim Live-Teilen) | Nein | App-Funktionen | Teilweise (Navigation braucht ihn) |
| Name, E-Mail-Adresse | Ja, nur mit Konto | Nein | Kontoverwaltung | Ja |
| Nutzungsdaten (Funktionen, Gegenden als 2-km-Kacheln) | Ja, nur mit Zustimmung, anonym | Nein | Analyse | Ja |
| Andere Nutzerinhalte (Fragen an Soul) | Ja, nur kurzzeitig verarbeitet, nicht gespeichert (gemeldete Antworten 90 Tage) | Nein | App-Funktionen | Ja |

Daten werden verschlüsselt übertragen (HTTPS). Nutzer können die Löschung ihrer Daten beantragen (in der App und über die Web-Seite).

## 4. Server (Cloudflare Worker `velora-ki`)

- `/soul` KI-Antworten mit Kostenbremse (höchstens 9.000 von 10.000 Gratis-Neuronen pro Tag, exakter Zähler im Durable Object), `/soul/report` Meldungen
- `/auth/*`, `/me` Konten (Passwörter nur als PBKDF2-Hash), `/konto-loeschen` Löschseite, `/datenschutz`, `/admin` Dashboard
- `/admin/api/summary` nur anonyme Summen, mit Statistik-Schlüssel (für das Dashboard-Artefakt)
- Deploy: `cd server && npx wrangler deploy`

## 5. Velora Plus (noch nicht verkaufsbereit)

Der Kauf über Google Play Billing ist noch nicht eingebaut. Deshalb zeigt die App „Bald über Google Play“ und schaltet nichts frei; der Server ignoriert Plus-Anfragen der App.
Zum Einbauen nötig:
1. In der Play Console ein Abo-Produkt anlegen (z. B. `velora_plus`, monatlich 2,99 € und jährlich 19,99 €).
2. Die Play Billing Library einbinden (benötigt den Gradle-Build, weil die Bibliothek viele Abhängigkeiten hat).
3. Kauf in der App über `Android.buyPlus()` und `Android.billingAvailable()` (in `plus.js` schon vorbereitet).
4. Kauf auf dem Server mit der Google Play Developer API prüfen (Dienstkonto als Cloudflare-Secret) und erst dann `plus` setzen.

## 6. Bekannte Einschränkungen

- Nur arm64-Geräte (64-Bit). Ältere reine 32-Bit-Handys sehen die App im Store nicht.
- Sprachansagen brauchen die entsprechende Stimme auf dem Gerät; fehlt sie, wird Deutsch verwendet.
- Offline-Karten und der Offline-Modus von Soul brauchen viel Speicher; beides ist freiwillig.
