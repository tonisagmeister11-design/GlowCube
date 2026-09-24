# ClaudeAI – die KI-Mitspielerin als Plugin

Ein eigenständiges Paper-Plugin für **Minecraft 26.3**. Claude erscheint mit
`/spawn claude` als Spielerfigur (Steve-Skin) neben dir. Du redest mit ihr im Chat, und
sie macht es: folgen, beschützen, Holz holen, Erze abbauen, Werkzeuge craften, Häuser
bauen, kämpfen, ernten und vieles mehr.

**Kein API-Schlüssel, kein Zusatzprogramm, keine Kosten.** Sprachverständnis, Planung,
Wegfindung und Bauen stecken komplett im Plugin.

## Installation

1. `dist/ClaudeAI.jar` in den `plugins`-Ordner legen.
2. Server neu starten.
3. Im Spiel `/spawn claude` tippen.

Mehr braucht es nicht. Kein ViaVersion, kein zweites Konto.

> Nicht zusammen mit `ClaudeSpawn.jar` (dem Plugin für den externen Bot) verwenden –
> beide reagieren auf `/spawn claude`.

## Mit Claude reden

Schreib einfach in den Chat. Claude hört zu, wenn du ihren Namen sagst, wenn du gerade
mit ihr redest oder wenn du der einzige Spieler in ihrer Nähe bist. Tippfehler sind
okay ("folg mier" versteht sie).

| Das sagst du | Das macht Claude |
| --- | --- |
| `folge mir`, `komm mit` | läuft dir hinterher |
| `komm her`, `tp dich zu mir` | kommt zu dir / teleportiert sich |
| `bleib hier`, `warte` | bleibt stehen |
| `stopp`, `hör auf` | bricht alles ab |
| `verteidige mich`, `beschütze Max` | folgt der Person und erledigt Monster, die ihr zu nahe kommen oder sie angreifen |
| `bewache die Basis` | bleibt an einem Ort und verteidigt ihn |
| `greif die Zombies an`, `töte alle Monster`, `jag Kühe` | kämpft (mit dem besten Schwert, das sie hat) |
| `hol mir 20 Holz`, `fäll ein paar Bäume` | fällt ganze Bäume und bringt dir das Holz |
| `bau Eisen ab`, `hol mir 5 Diamanten`, `hol Kohle` | sucht Erze und gräbt sich hin |
| `mach mir eine Eisenspitzhacke`, `mach mir Fackeln` | plant selbst: Holz → Werkbank → Holzspitzhacke → Stein → Ofen → Eisen … |
| `bau ein Haus`, `bau ein großes Haus aus Stein` | baut ein eingerichtetes Haus vor dir (Tür zu dir) |
| `bau einen Turm / Zaun / eine Mauer / Farm / Brücke / einen Pool / eine Plattform` | weitere Baupläne |
| `bau da ein Haus` | baut dort, wo du hinschaust |
| `mach das rückgängig` | reißt den letzten Bau wieder ab, alles wie vorher |
| `ernte das Feld` | erntet reifes Getreide und pflanzt neu |
| `stell Fackeln auf` | leuchtet die Umgebung aus |
| `sammel die Items ein` | hebt herumliegende Sachen auf |
| `leg alles in die Kiste`, `hol das Holz aus der Kiste` | nutzt die nächste Kiste |
| `gib mir alles`, `gib Max 10 Holz` | gibt Sachen weiter |
| `was hast du dabei` | öffnet ihr Inventar (Rechtsklick auf Claude geht auch) |
| `ich hab Hunger` | gibt dir Essen – oder jagt und brät welches |
| `brat das Fleisch`, `schmelz das Eisen` | schmilzt/brät |
| `merk dir diesen Ort als Basis` … `geh zur Basis` | Orte merken und hingehen |
| `wie crafte ich ein Schild?`, `wo finde ich Diamanten?` | erklärt Rezepte und Spielwissen |
| `wenn ich hopp sage, meine ich spring` | lernt neue Wörter |
| `ich heiße Toni`, `merk dir, dass ich gerne baue` | merkt sich Dinge über dich |
| `tanz`, `spring`, `wink`, `erzähl einen Witz` | Quatsch machen |
| `hol 5 Holz und dann bau ein Haus` | mehrere Aufträge hintereinander |
| `verschwinde` | geht (bis zum nächsten `/spawn claude`) |

Nachts fragt sie von sich aus, ob sie Fackeln aufstellen soll. Sie warnt dich bei wenig
Leben und bietet Essen an, wenn du hungrig bist.

## Befehle

| Befehl | Wirkung |
| --- | --- |
| `/spawn claude` | Claude neben dich holen (bzw. zu dir teleportieren) |
| `/despawn claude` | Claude wegschicken |
| `/claude` | Status |
| `/claude inventar` | ihr Inventar öffnen |
| `/claude stop` | alles abbrechen |
| `/claude reload` | Einstellungen neu laden (OP) |

Ein vorhandenes `/spawn` (z.B. von Essentials) funktioniert weiter wie gewohnt; nur
genau `/spawn claude` wird abgefangen.

## Einstellungen (`plugins/ClaudeAI/config.yml`)

| Einstellung | Standard | Bedeutung |
| --- | --- | --- |
| `name` | `Claude` | Name über dem Kopf und im Chat |
| `skin` | `steve` | `steve`, `alex`, `ari`, `efe`, `kai`, `makena`, `noor`, `sunny`, `zuri` oder `player:Spielername` |
| `owners` | `[]` (alle) | Nur diese Spieler dürfen Befehle geben |
| `require-name` | `false` | Nur reagieren, wenn "Claude" in der Nachricht steht |
| `invulnerable` | `true` | Claude kann nicht sterben |
| `allow-pvp` | `false` | Darf sie auf Befehl Spieler angreifen? |
| `defend-against-players` | `true` | Verteidigt sie ihren Schützling auch gegen Spieler? |
| `build.needs-materials` | `false` | `false` = baut ohne Material, `true` = braucht die Blöcke im Inventar |
| `build.blocks-per-tick` | `2` | Baugeschwindigkeit |
| `respect-protection` | `true` | Fragt Schutz-Plugins (WorldGuard, GriefPrevention …) im Namen des Auftraggebers |
| `realistic-tools` | `true` | Für Erze braucht sie die richtige Spitzhacke (und baut sie sich selbst) |
| `walk-speed` | `0.23` | Laufgeschwindigkeit |
| `search-radius` | `40` | So weit sucht sie nach Holz, Erzen usw. |

## Wie das ohne KI-Dienst funktioniert

* **Sprachverständnis** (`brain/`): Nachrichten werden vereinheitlicht (klein, ohne
  Umlaute), Tippfehler per Editierdistanz korrigiert, in Teilsätze zerlegt ("… und dann
  …") und gegen über 100 Regeln geprüft. Dazu kommen Wortlisten für über 140 Items
  (inklusive aller Werkzeuge und Rüstungen) und über 30 Mobs, Zahlen in Worten ("zwanzig", "ein Stack"), Spielernamen, Pronomen ("verteidige
  ihn"), offene Ja/Nein-Fragen und ein Gedächtnis für Orte, gelernte Wörter und Fakten.
* **Planer** (`plan/`): kennt Rezepte, Schmelzen, Abbauen, Jagen und Ernten und plant
  rekursiv mit einem gedachten Inventar – inklusive Werkbank, Ofen, Brennstoff und der
  richtigen Spitzhacke.
* **Körper** (`npc/`, `move/`): ein Mannequin (das Spielermodell seit 1.21.9), bewegt
  von einer eigenen A*-Wegsuche. Sie steigt Stufen, springt runter, schwimmt, öffnet
  und schließt Türen und gräbt sich beim Abbauen durch.
* **Bauen** (`build/`): Baupläne werden in deine Blickrichtung gedreht. Natur wird
  weggeräumt, fremde Bauwerke bleiben unangetastet, und alles lässt sich rückgängig machen.

## Grenzen – ehrlich gesagt

* Claude versteht sehr viele Formulierungen, aber keine freie Unterhaltung wie ein
  großes Sprachmodell. Versteht sie etwas nicht, sagt sie es – und du kannst es ihr mit
  "wenn ich X sage, meine ich Y" beibringen.
* Getestet ist das Plugin in einer nachgebauten Testwelt (`./test.sh`: 57 Prüfungen, von
  "hol mir Holz" bis "bau ein Haus und reiß es wieder ab"). Auf einem echten Paper-Server
  lief es hier noch nicht, weil sich der hier nicht herunterladen ließ. Jeder Aufruf der
  Paper-Schnittstelle ist gegen die echten 26.3-Signaturen geprüft.
* Der Skin wird über den Vanilla-Befehl `/data` gesetzt. Klappt das nicht, trägt
  Claude den Standard-Skin des Mannequins.

## Bauen und Testen

```bash
./build.sh   # -> ../dist/ClaudeAI.jar (JDK 21+)
./test.sh    # Simulationstest in einer nachgebauten Welt
```
