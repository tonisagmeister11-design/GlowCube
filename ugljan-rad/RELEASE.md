# BikeOra – Release im Google Play Store

Stand: Version 8.0 (versionCode 30), Paket `app.velora.bike`, targetSdk 36, minSdk 24, nur arm64-v8a.
Version und Code stehen nur noch an einer Stelle: `app/build.gradle` (`appVersionCode`, `appVersionName`).

## 1. Bauen

```bash
./build-native.sh        # nur nötig, wenn sich native/velora_llm.cpp ändert (llama.cpp b12032)
./build-release.sh       # Gradle (AGP 8.13, Google Play Billing 8.3): dist/BikeOra.aab (Play Console) + dist/BikeOra-release.apk (gleiche Signatur)
./build.sh               # dist/BikeOra.apk ohne Gradle, mit dem Entwickler-Schlüssel (nur zum Ausprobieren, ohne Kaufen)
./build-desktop.sh       # dist/BikeOra.exe (Windows)
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

**Werbung:** Nein. **In-App-Käufe:** Ja, ein Abo „BikeOra Plus“ (siehe Abschnitt 5).

**Zielgruppe:** 13–15, 16–17 und 18+ (nicht unter 13, damit gilt nicht die Familienrichtlinie). Für ein Konto verlangt die Datenschutzerklärung mindestens 14 Jahre.

**Inhaltsbewertung (IARC):** keine Gewalt, keine sexuellen Inhalte, kein Glücksspiel. Nutzer können mit einer KI chatten (Juno), Live-Standort kann mit einem Link geteilt werden. Keine Kommunikation zwischen Nutzern.

**KI-Inhalte:** Juno erzeugt Antworten mit einem Sprachmodell. Unter jeder KI-Antwort gibt es „Antwort melden“. Meldungen erscheinen im Admin-Dashboard.

**Vordergrunddienst (Android 14+):** Typ `location`, Anwendungsfall „Navigation“.
Beschreibung: „Während einer vom Nutzer gestarteten Navigation oder Fahrtaufzeichnung bekommt BikeOra weiter GPS-Positionen, damit Abbiegehinweise und die Aufzeichnung auch bei gesperrtem Bildschirm funktionieren. Ohne den Dienst bricht die Navigation ab, sobald der Bildschirm ausgeht.“
Video: Ziel suchen → „Hierhin fahren“ → „Los fahren“ → Bildschirm sperren → Benachrichtigung und Ansage zeigen.

**Berechtigungen:** Standort (genau und ungefähr; nur im Vordergrund, kein Hintergrund-Standort), Mikrofon (nur für die Spracheingabe bei Juno, wird erst beim Antippen abgefragt), Benachrichtigungen (Navigation läuft), Vibration, Internet.

**Datensicherheit (Data safety):**

| Datentyp | Erhoben | Geteilt | Zweck | Freiwillig |
|---|---|---|---|---|
| Genauer Standort | Ja (an Routen-/Wetterdienste für die Funktion; an den BikeOra-Server nur beim Live-Teilen) | Nein | App-Funktionen | Teilweise (Navigation braucht ihn) |
| Name, E-Mail-Adresse | Ja, nur mit Konto | Nein | Kontoverwaltung | Ja |
| Nutzungsdaten (Funktionen, Gegenden als 2-km-Kacheln) | Ja, nur mit Zustimmung, anonym | Nein | Analyse | Ja |
| Andere Nutzerinhalte (Fragen an Juno) | Ja, nur kurzzeitig verarbeitet, nicht gespeichert (gemeldete Antworten 90 Tage) | Nein | App-Funktionen | Ja |

Daten werden verschlüsselt übertragen (HTTPS). Nutzer können die Löschung ihrer Daten beantragen (in der App und über die Web-Seite).

## 4. Server (Cloudflare Worker `velora-ki`)

- `/soul` KI-Antworten mit Kostenbremse (höchstens 9.000 von 10.000 Gratis-Neuronen pro Tag, exakter Zähler im Durable Object), `/soul/report` Meldungen
- `/billing/verify` prüft ein Play-Abo (mit `GOOGLE_PLAY_SA`), `/me` prüft abgelaufene Abos erneut
- `/auth/*`, `/me` Konten (Passwörter nur als PBKDF2-Hash), `/konto-loeschen` Löschseite, `/datenschutz`, `/admin` Dashboard
- `/admin/api/summary` nur anonyme Summen, mit Statistik-Schlüssel (für das Dashboard-Artefakt)
- Deploy: `cd server && npx wrangler deploy`

## 5. BikeOra Plus (Abo über Google Play)

Der Kauf ist fertig eingebaut (Google Play Billing Library 8.3). Die App fragt Google Play nach den Preisen, startet den Kauf,
bestätigt ihn (sonst erstattet Google nach 3 Tagen automatisch), stellt Käufe auf einem neuen Handy wieder her („Kauf wiederherstellen“)
und öffnet zum Kündigen die Abo-Verwaltung von Google Play. Ein gekündigtes oder abgelaufenes Abo schaltet Plus beim nächsten Start ab.

**In der Play Console anlegen (einmalig, erst nach dem ersten Hochladen eines AAB möglich):**
1. *Monetarisieren → Produkte → Abos → Abo erstellen*: Produkt-ID **`bikeora_plus`**, Name „BikeOra Plus“.
2. Zwei **Basis-Abos** anlegen und aktivieren:
   - ID **`monthly`**, automatisch verlängernd, Abrechnungszeitraum 1 Monat, Preis z. B. 2,99 €
   - ID **`yearly`**, automatisch verlängernd, Abrechnungszeitraum 1 Jahr, Preis z. B. 19,99 €
3. Optional ein Angebot (z. B. 7 Tage gratis) für ein Basis-Abo: die App nimmt es automatisch.
4. *Einrichtung → Lizenztests*: eigene Google-Konten als Lizenztester eintragen. Testkäufe kosten dann nichts und verlängern sich im Minutentakt.

Die IDs müssen genau so heißen, sonst zeigt die App „Produkt bikeora_plus nicht gefunden“ und verkauft nichts.

**Prüfung auf dem Server (empfohlen):** Damit Plus auch am Konto hängt (zweites Gerät, Desktop) und niemand den Kauf vortäuschen kann,
prüft der Server das Abo bei Google:
1. In der Google Cloud Console ein Dienstkonto anlegen und einen JSON-Schlüssel erzeugen.
2. In der Play Console unter *Nutzer und Berechtigungen* das Dienstkonto einladen, Berechtigung „Finanzdaten ansehen“ und „Bestellungen und Abos verwalten“.
3. Den Inhalt der JSON-Datei im Cloudflare-Dashboard als Secret **`GOOGLE_PLAY_SA`** beim Worker `velora-ki` eintragen (nicht in den Chat, nicht ins Repository).

Ohne dieses Secret funktioniert der Kauf in der App trotzdem; der Server merkt sich den Kauf dann nur als „noch nicht geprüft“.

## 6. Bekannte Einschränkungen

- Nur arm64-Geräte (64-Bit). Ältere reine 32-Bit-Handys sehen die App im Store nicht.
- Sprachansagen brauchen die entsprechende Stimme auf dem Gerät; fehlt sie, wird Deutsch verwendet.
- Offline-Karten und der Offline-Modus von Juno brauchen viel Speicher; beides ist freiwillig.
- 3D-Gelände ist aus Akku-Gründen standardmäßig aus (Einstellungen → Karte). 3D-Gebäude und der 3D-Fuchs lassen sich dort ebenfalls abschalten.
- Ohne 3D-Grafik (WebGL) zeigt die App den 2D-Fuchs; die Karte braucht WebGL.
