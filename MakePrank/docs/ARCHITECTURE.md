# MakePrank – Architektur

## Schichten

| Modul | Aufgabe | Kennt Tk? | Kennt Netzwerk? |
|---|---|---|---|
| `catalog.py` | **Zentrale Effekt-Registry**: Namen, Parameter (Typ/Grenzen), Kategorie, Risiko, Audio-Kennzeichnung | nein | nein |
| `package_format.py` | `.mpk` v2: Schema-Validierung, Ressourcenlimits, Zip-Slip-Schutz, Inhalts-Hash, v1-Migration, Zusammenfassung, `ConsentStore` | nein | nein |
| `session.py` | `SessionManager` auf **virtueller Zeit**: Start/Pause/Stopp, Timeout, Integritätsprüfung, Scopes mit garantiertem Cleanup | nein | nein |
| `effects.py` | Tk-Implementierung aller 16 Effekte (`TkEffectRunner`) | ja | nein |
| `audio.py` | Töne synthetisieren (Länge/Lautstärke gedeckelt), `winsound`-Backend, Null-Backend | nein | nein |
| `protocol.py` | Typisiertes Wire-Protokoll, Framing, Pairing-Link, Host-Allowlist | nein | ja |
| `certs.py` | Ephemeres EC-Zertifikat (`cryptography`), TLS-Kontexte, Fingerprint | nein | ja |
| `receiver_server.py` | Empfänger-TLS-Server: Pairing, Auth, Replay-/Rate-/Timeout-Schutz | nein | ja |
| `creator_client.py` | Creator-Verbindung mit Fingerprint-Pinning, Heartbeat | nein | ja |
| `client_app.py` / `creator_app.py` | Oberflächen; `ui_common.py`: Theme, `ConsentDialog`, `TkHost`, `MainThreadCalls` | ja | nutzt |
| `audit.py`, `settings.py` | Sitzungsprotokoll (ohne Geheimnisse), Einstellungen | nein | nein |

UI, Paketschema, Effekt-Engine, Sitzungsmanager und Netzwerktransport sind getrennt; die Sitzungslogik ist ohne Tk und ohne Netzwerk testbar.

## Paketformat `.mpk` (Version 2)

ZIP mit `manifest.json`, `NOTICE.txt` und optional `resources/<name>.png|gif|wav`. Das Manifest enthält Format/Version, Name, Beschreibung,
Absender, `network` (`none`|`pairing`), `max_duration_seconds`, abgeleitete Felder `audio`, `estimated_duration_seconds`, `resources[]`
(je nach `kind`: Bild mit Maßen oder Audio mit Länge/Kanälen/Abtastrate, plus Name, MIME, Größe, SHA-256) und `content_sha256`, dazu die Aktionen (`type`, `title`, `text`, `duration_seconds`, `params`).

* Strikte Allowlist: unbekannte Felder/Effekte/Parameter, falsche Typen, Bereichs-/Längenverletzungen und Steuerzeichen werden abgelehnt.
* Limits: Datei ≤ 12 MiB, Manifest ≤ 256 KB, ≤ 6 Ressourcen, Bild ≤ 1 MiB/1920×1080, WAV ≤ 2 MiB/10 s (8/16-Bit PCM, 8–48 kHz), Ressourcen gesamt ≤ 6 MiB, ≤ 30 Aktionen, Aktion ≤ 120 s, Sitzung ≤ 1800 s.
* Archiv: nur `manifest.json`, `NOTICE.txt`, `resources/<a-z0-9_->.(png|gif|wav)`; keine Verzeichnisse, Symlinks, Verschlüsselung, doppelte Namen, `..`, absolute/Laufwerkspfade; Größenangaben werden beim Lesen gegengeprüft (Zip-Bomb). WAV wird nur als unkomprimiertes PCM akzeptiert (Pythons `wave`-Leser); keine Dekodier-Bibliothek.
* `content_sha256` erkennt Veränderungen am Manifest; Ressourcen-Hashes erkennen Austausch von Bildern und Sounds. **Das ist keine Signatur** – wer ein Paket erstellt hat, beweist es nicht.
* Version 1 (MakePrank 0.1/0.2) wird beim Laden migriert; höhere Versionen werden mit verständlicher Meldung abgelehnt.
* Nie: `pickle`, `eval`/`exec`, dynamische Imports, Subprozesse, `webbrowser`, ausführbare Inhalte (ein Test scannt alle Module per AST darauf).

## Zustimmung und Beenden

`ConsentDialog` zeigt `summarize_config()`; Checkbox startet `False`, der Start-Knopf ist bis dahin deaktiviert, Ablehnen/Esc/Schließen → kein Start.
Zustimmung wird je Datei-SHA-256 gespeichert (`ConsentStore`), Netzwerk-Zustimmung getrennt. Vor jedem Start wird die Datei neu gehasht; Abweichung = Paket verworfen, neue Zustimmung nötig.

Stoppwege (`StopReason`): STOPP-Knopf, Esc (global und in jedem Effektfenster), Hauptfenster schließen, Timeout, Datei gelöscht/verschoben/verändert
(Prüfung alle 0,75 s), Disconnect, Netzwerk-Timeout, Remote-Stopp, Fehler, Ersetzen, „Alles stoppen“. `SessionManager.stop()` ist idempotent, schließt Timer und Cleanups LIFO
(auch wenn ein Cleanup fehlschlägt) und braucht weder Netzwerk noch Creator. Nutzer-geschlossene Effektfenster lassen Timer ins Leere laufen, ohne die Sitzung zu beschädigen.

## Netzwerkprotokoll (Prototyp)

```
Empfänger: [Pairing starten] → Zertifikat+Schlüssel nur im Speicher, Code (8 Zeichen aus 32, 40 Bit), Link makeprank://pair?host=&port=&fp=&code=
Creator:   TLS (≥1.2) → Fingerprint des Serverzertifikats gegen fp pinnen → PAIR_REQUEST{creator_name, code}
Empfänger: Code prüfen (konstante Zeit, einmalig, 120 s, 3 Fehlversuche) → Creator lokal bestätigen lassen → PAIR_OK{client_id, token}
Danach:    jede Nachricht trägt token + streng steigende seq: PING, STATUS, START_EFFECT, STOP_SESSION, DISCONNECT
```

* Nachrichten: genau 6 Creator→Empfänger-Typen; Felder werden einzeln validiert, unbekannte Felder abgelehnt, ≤ 16 KiB (vor dem Lesen geprüft), Protokollversion 1.
* `START_EFFECT{package_sha256, action_index, effect_id}`: der Empfänger prüft, ob Fernsteuerung erlaubt ist, die Paketdatei unverändert ist, der Hash zum **geöffneten und akzeptierten** Paket passt, der Index existiert und `effect_id` dem Typ dieser Aktion entspricht – und startet **die Aktion aus seinem eigenen Paket**. Mindestabstand 1 s.
* Schutz: Token zufällig (256 Bit), nur im Speicher, 30 min gültig, widerrufen bei Trennung; Replay (seq ≤ letzte) und falsches Token beenden die Sitzung; Token-Bucket 10 msg/s; Heartbeat-Timeout 15 s (Creator pingt alle 5 s); ≤ 10 Verbindungsversuche/min; ein Peer zugleich; Listener schließt nach der Sitzung, nach Ablauf oder nach 3 falschen Codes.
* Standard: Bind auf `127.0.0.1` (Testmodus). LAN nur auf ausdrücklichen Haken (`0.0.0.0`); keine Firewall-/Port-Änderungen, kein Relay, kein Wiederverbinden. Creator verbindet nur zu IP-Literalen in privaten/Loopback/Link-Local-Bereichen, öffentliche nur per Einstellung.
* Trennen/Timeout/Fehler → `on_disconnected` → laufende Remote-Sitzung wird gestoppt. Lokales STOPP/Trennen wirkt ohne Netzwerk.
* Keine eigene Kryptografie: `ssl` + `cryptography` für das Zertifikat. Tokens/Codes werden nicht protokolliert (Test).

## Offene Punkte / Grenzen (ehrlich)

* Kein QR-Code (Text-Link). Keine Signaturen für Pakete. Kein Cloud-/Internet-Relay (nur selbst konfigurierte Infrastruktur, nicht Teil dieses Prototyps).
* Windows-Soundausgabe (`winsound`) und die Windows-EXE sind hier nicht auf Windows geprüft; siehe `docs/STATUS.md`.
