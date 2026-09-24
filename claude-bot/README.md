# Claude als Mitspielerin in Minecraft

Claude kommt als echte Spielerin auf deinen Server. Du redest mit ihr ganz normal im
Chat, und sie **macht** die Dinge: laufen, folgen, abbauen, bauen, craften, schmelzen,
kämpfen, essen, schlafen, Truhen benutzen, mit Dorfbewohnern handeln, reiten, angeln
und Befehle eingeben.

```
<Toni> Claude, hol mal 10 Holz und bau mir eine Werkbank
<Claude> Klar, bin gleich wieder da!
<Claude> Fertig – Werkbank steht neben dir, Rest ist in meinem Inventar.
```

Zwei Teile gehören zusammen:

| Teil | Wo | Was |
| --- | --- | --- |
| `claude-bot/` | auf einem Rechner mit Node.js | Der Bot selbst: Spielerkörper (Mineflayer) + Gehirn (Claude-API) |
| `dist/ClaudeSpawn.jar` | im `plugins`-Ordner des Servers | `/spawn claude` holt sie neben dich, `/despawn claude` schickt sie weg |

Das Plugin ist optional. Ohne Plugin joint Claude einfach ganz normal, sobald du den
Bot startest.

## So funktioniert `/spawn claude`

Der Bot verbindet sich einmal mit dem Server und **wartet dann unsichtbar** (Zuschauer-
Modus, für niemanden sichtbar, keine Beitrittsmeldung, keine Tab-Liste). Tippt jemand
`/spawn claude`, macht das Plugin sie sichtbar, stellt sie auf Überleben und
teleportiert sie direkt neben dich. Claude begrüßt dich und hört ab dann zu.

`/despawn claude` – oder „Claude, du kannst gehen“ im Chat – schickt sie zurück ins
Warten. `/claude` zeigt den Status.

`/spawn claude` wird vor allen anderen Plugins abgefangen, ein vorhandenes
Essentials-`/spawn` funktioniert also weiter wie bisher.

## Einrichten

### 1. Server vorbereiten

1. `dist/ClaudeSpawn.jar` in den `plugins`-Ordner legen.
2. **Version:** Mineflayer (der Spielerkörper) spricht höchstens Minecraft
   **26.1**. Dein Server läuft auf 26.3 – installiere deshalb **ViaVersion** und
   **ViaBackwards** als Plugins, dann können 26.1-Clients auf den 26.3-Server. Der Bot
   merkt das selbst und verbindet sich automatisch als 26.1.
3. **Konto für den Bot** – eins von beiden:
   * `online-mode=false` in `server.properties` → `MC_AUTH=offline`, kein Konto nötig.
     (Achtung: dann kann sich jeder mit jedem Namen einloggen. Nur mit Whitelist oder
     Login-Plugin sinnvoll.)
   * Ein **zweites Minecraft-Konto** für Claude → `MC_AUTH=microsoft`. Beim ersten
     Start zeigt die Konsole einen Code, den du auf microsoft.com/link eingibst. Der
     Login wird unter `data/auth` gespeichert.
4. Server neu starten. In `plugins/ClaudeSpawn/config.yml` steht der Name des Bots
   (`bot-name: Claude`) – der muss zum Kontonamen passen.

### 2. Bot starten

Du brauchst **Node.js 22 oder neuer** und einen **API-Schlüssel** von
<https://console.anthropic.com>.

```bash
cd claude-bot
npm install
cp .env.example .env      # ANTHROPIC_API_KEY, MC_HOST usw. eintragen
npm start
```

Oder die Server-IP direkt mitgeben:

```bash
npm start -- --host 123.45.67.89:25565 --owner Toni
```

Am besten läuft der Bot auf demselben Rechner wie der Server (`MC_HOST=localhost`),
dann ist die Verbindung am schnellsten. Er geht aber genauso von deinem PC aus.

Solange das Programm läuft, bleibt Claude verbunden und verbindet sich nach einem
Server-Neustart selbst wieder. Beenden mit `Strg+C`.

### Konsole

Im Fenster, in dem der Bot läuft, kannst du direkt mit Claude schreiben (als Besitzer).
`!Text` schickt Text unverändert in den Spielchat, `/quit` beendet.

## Was Claude kann

| Bereich | Werkzeuge |
| --- | --- |
| Wahrnehmen | Status, Umgebung ansehen (Spieler, Mobs, Erze, Truhen …), Blöcke suchen, Block prüfen, Inventar |
| Bewegen | zu Koordinaten, zu Spielern, folgen (auch als Leibwache), Himmelsrichtung, hinschauen, Tasten halten (springen, schleichen, sprinten …) |
| Abbauen | Blöcke eines Typs suchen und abbauen (bestes Werkzeug automatisch), einzelne Blöcke, Items einsammeln |
| Bauen | einzelne Blöcke, ganze Baupläne bis 512 Blöcke (Claude rechnet die Koordinaten selbst) |
| Herstellen | craften (Werkbank wird bei Bedarf hingestellt), schmelzen/braten im Ofen |
| Inventar | ausrüsten, ablegen, beste Rüstung anziehen, wegwerfen, Spielern geben, essen |
| Kämpfen | Mobs eines Typs, „alle Monster“ oder Spieler (nur wenn ausdrücklich gewünscht) |
| Benutzen | Türen, Hebel, Knöpfe, Items auf Blöcken (Knochenmehl, Samen, Eimer, Feuerzeug, Hacke …), Items benutzen (Bogen, Schild, Trank, Enderperle …), Tiere füttern/scheren/melken, reiten, schlafen, Truhen, angeln, handeln |
| Chat | antwortet im Chat, flüstert, führt Serverbefehle aus (`/home`, `/tpa` …) |
| Gedächtnis | merkt sich Dinge dauerhaft (`data/memory.json`), z.B. „Merk dir, unsere Basis ist bei 100 64 -200“ |

Nebenbei, ohne nachzudenken: isst bei Hunger, wehrt sich gegen Monster, zieht bessere
Rüstung an, steht nach dem Tod wieder auf und sagt Bescheid.

**„Stop“** (auch „stopp“, „halt“, „warte“) bricht sofort ab, was sie gerade tut.

## Einstellungen (`.env`)

| Variable | Standard | Bedeutung |
| --- | --- | --- |
| `ANTHROPIC_API_KEY` | – | API-Schlüssel (Pflicht) |
| `BOT_MODEL` | `claude-opus-5` | `claude-sonnet-5` ist schneller und günstiger |
| `BOT_EFFORT` | `medium` | Nachdenk-Aufwand: `low`, `medium`, `high` |
| `MC_HOST` / `MC_PORT` | `localhost` / `25565` | Server-Adresse (`--host ip:port` geht auch) |
| `MC_USERNAME` | `Claude` | Name im Spiel |
| `MC_AUTH` | `offline` | `offline` oder `microsoft` |
| `MC_VERSION` | `auto` | feste Version, z.B. `26.1` |
| `BOT_OWNERS` | leer = alle | Nur diese Spieler dürfen Aufträge geben, z.B. `Toni,Max` |
| `BOT_REQUIRE_NAME` | `false` | Nur reagieren, wenn „Claude“ in der Nachricht steht |
| `BOT_STANDBY` | `true` | Unsichtbar warten bis `/spawn claude` (braucht das Plugin) |
| `BOT_SELF_DEFENSE` | `true` | Gegen Monster wehren |
| `BOT_AUTO_EAT` | `true` | Selbstständig essen |
| `BOT_DIG_WHILE_WALKING` | `false` | Beim Laufen Blöcke abbauen/setzen dürfen (aus = eure Bauten sind sicher) |
| `BOT_DEBUG` | `false` | Ausführliche Ausgaben |

Jede Einstellung geht auch als Parameter: `--model`, `--effort`, `--owner`,
`--name`, `--auth`, `--version`, `--standby false` …

## Kosten

Jede Chatnachricht an Claude und jeder Zwischenschritt einer Aufgabe ist eine Anfrage
an die Claude-API. Werkzeuge und Regeln werden zwischengespeichert (Prompt-Caching),
deshalb ist das meiste billig. Eine längere Aufgabe („bau ein Haus“) braucht trotzdem
viele Schritte. Wer sparen will: `BOT_MODEL=claude-sonnet-5`, `BOT_EFFORT=low`,
`BOT_REQUIRE_NAME=true`. Während Claude unsichtbar wartet, kostet sie nichts.

Das Programm fragt standardmäßig mit der Option `fallbacks: "default"` an: lehnt
Claude eine Anfrage aus Sicherheitsgründen ab, antwortet automatisch ein
Ersatzmodell. Kennt dein Konto das nicht, schaltet der Bot es selbst ab
(`BOT_FALLBACKS=false` tut das von Anfang an).

## Grenzen

* Mineflayer ist nicht der offizielle Client. Sehr neue Blöcke/Mechaniken kennt er
  eventuell nicht, und über ViaBackwards fehlt, was es in 26.1 noch nicht gab.
* Spieler außer Sichtweite (Render-Distanz) kann Claude nicht finden – dann einfach
  `/spawn claude`, das holt sie direkt zu dir.
* Crafting geht nur mit Rezepten der Werkbank/des Inventars. Amboss, Zaubertisch,
  Braustand und Schmiedetisch sind nicht eingebaut.
* Bogenschießen zielt ungefähr, nicht perfekt.

## Fehlersuche

| Problem | Lösung |
| --- | --- |
| „Server version … is not supported“ | ViaVersion + ViaBackwards auf dem Server installieren, oder `MC_VERSION=26.1` |
| Bot wird sofort gekickt („Failed to verify username“) | Server ist `online-mode=true` → `MC_AUTH=microsoft` mit eigenem Konto |
| Kick wegen „chat validation“ | In `server.properties` `enforce-secure-profile=false` |
| `/spawn claude` sagt „nicht verbunden“ | Bot-Programm läuft nicht, oder `bot-name` im Plugin passt nicht zu `MC_USERNAME` |
| Claude antwortet nicht | Konsole prüfen: API-Schlüssel? `BOT_OWNERS` gesetzt? `BOT_REQUIRE_NAME=true`? |
| Chat-Plugin mit eigenem Format | Wird meist erkannt (`<Name>`, `Name:`, `Name »`). Sonst `BOT_DEBUG=true` und Format melden |

## Aufbau

```
src/index.js       Start, Verbindung, Chat-Empfang, Konsole, Wiederverbinden
src/brain.js       Gespräch mit Claude: Ereignisse rein, Werkzeuge ausführen, Antworten in den Chat
src/tools.js       Alle Werkzeuge mit Beschreibung für Claude
src/control.js     Absprache mit dem Plugin (Kanal claude:control)
src/reflexes.js    Essen, Zurückschlagen, Rüstung, Leibwache
src/memory.js      Dauerhafte Notizen
src/skills/        Die eigentlichen Fähigkeiten (Laufen, Abbauen, Bauen, Craften, Kampf …)
test/              npm run check – Test ohne Server und ohne API-Schlüssel
```

Das Plugin liegt unter `claude-plugin/` (`./build.sh` → `dist/ClaudeSpawn.jar`).
