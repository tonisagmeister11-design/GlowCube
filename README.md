# AdminField – Mob-Verkleidung

Erweitert das bestehende AdminField-Plugin um eine **Mob-Verkleidung**: Du oder ein
beliebiger anderer Spieler wird für alle anderen zu einem echten Mob – nicht nur ein
anderer Skin.

Das fertige Plugin liegt unter `dist/AdminField.jar`.

## Was die Verkleidung macht

* Der verkleidete Spieler wird für alle anderen **ausgeblendet**. An seiner Stelle läuft
  ein **echter Mob** mit, der jeden Tick auf seine Position gesetzt wird. Wer ihn ansieht,
  sieht wirklich einen Creeper – inklusive Modell, Animation und Hitbox.
* **Kein Nametag.** Weder beim Verkleideten noch bei dir, wenn du dich selbst verwandelst –
  der Spieler ist als Entity komplett weg, und die Hülle trägt keinen Namen.
* **Das Ziel merkt nichts.** Es gibt keine Chat-Nachricht, keine Meldung, keinen Hinweis.
  Der Betroffene sieht sich selbst weiterhin ganz normal. Auffallen kann es ihm erst, wenn
  er sich von außen sieht oder jemand es ihm sagt.
* **Nur für den Owner.** Der Menüpunkt steckt im Owner-Block des Hauptmenüs. Ein Admin
  bekommt ihn gar nicht erst zu sehen, und alle drei Untermenüs prüfen die Owner-Rolle
  noch einmal selbst.
* Nichts davon landet im Verlauf/Log, den Admins lesen können.

Die Hülle ist ohne KI, unverwundbar, lautlos, schwerelos, nicht kollidierbar, ohne
Namensschild und für den Verkleideten selbst unsichtbar. Wither und Enderdrache bekommen
zusätzlich ihre Bossleiste abgeschaltet, Zombies und Skelette brennen nicht in der Sonne.

## Auswahl

Rund 80 Verkleidungen in drei Gruppen:

* **Monster** – Creeper, Zombie, Baby-Zombie, Skelett, Spinne, Enderman, Wither, Warden,
  Ravager, Hexe, Ghast, Schleim, Enderdrache …
* **Tiere** – Kuh, Baby-Kuh, Schwein, Baby-Schwein, Schaf, Huhn, Wolf, Katze, Fuchs, Panda,
  Biene, Axolotl, Ziege, Frosch, Kamel, Papagei, Delfin …
* **Besondere** – Dorfbewohner, Baby-Dorfbewohner, Wandernder Händler, Eisengolem,
  Schneegolem, Allay, Rüstungsständer

Mobs, die die jeweilige Serverversion nicht kennt, fallen automatisch aus der Liste.

## Bedienung

`/admin` → **Mob-Verkleidung** (nur als Owner sichtbar)

* **Dich selbst verkleiden** → Mob aussuchen
* **Anderen Spieler verkleiden** → Spieler aussuchen → Mob aussuchen
* Die Liste der gerade Verkleideten steht im selben Menü; ein Klick hebt sie auf
* **Reste aufräumen** entfernt Mob-Hüllen, die ein Serverabsturz zurückgelassen hat
* **Alle Verkleidungen aufheben** setzt alles zurück

Verkleidungen überstehen einen Rejoin (die Hülle wird beim Join neu aufgebaut) und werden
beim Deaktivieren des Plugins sauber abgeräumt.

## Aufbau des Repos

| Pfad | Inhalt |
| --- | --- |
| `lib/AdminField-original.jar` | Das unveränderte Original-Plugin |
| `lib/GlowCubeUtils-original.jar` | Das eingegliederte Home-Plugin, unverändert |
| `resources/` | Zusammengeführte `plugin.yml` und `config.yml` |
| `decompiled/` | Der dekompilierte Originalcode – nur als Nachschlagewerk |
| `src/` | Nur das, was wirklich neu übersetzt wird |
| `tools/` | Der Stub-Generator (siehe unten) |
| `build.sh` | Baut `dist/AdminField.jar` |

Neu bzw. geändert:

* `src/de/adminfield/disguise/MobDisguise.java` – der Verkleidungs-Verwalter
* `src/de/adminfield/disguise/MobKind.java` – die Liste der Mobs
* `src/de/adminfield/menu/MobDisguiseMenu.java` – Hauptmenü der Verkleidung
* `src/de/adminfield/menu/MobTargetMenu.java` – Spielerauswahl
* `src/de/adminfield/menu/MobPickMenu.java` – Mobauswahl
* `src/de/adminfield/disguise/NameDisguise.java` – Verkleidung als fremdes Konto
* `src/de/adminfield/disguise/SkinFetch.java` – Skin-Abfrage bei Mojang
* `src/de/adminfield/menu/NameDisguiseMenu.java`, `NameTargetMenu.java` – deren Menüs
* `src/de/adminfield/menu/MainMenu.java` – geänderte Bestandsklasse: zwei Menüpunkte
  (Slot 36 und 44) im Owner-Block
* `src/de/adminfield/AdminFieldPlugin.java` – geänderte Bestandsklasse: je eine Zeile in
  `onEnable` und `onDisable`, damit die Verkleidungen beim Serverstart aufräumen und
  beim Herunterfahren sauber zurücksetzen
* `src/de/adminfield/OwnerItems.java`, `src/de/adminfield/menu/OwnerStuffMenu.java` –
  geänderte Bestandsklassen: Glück 255 auf der Spitzhacke, zwei Knockback-Sticks
* `src/de/adminfield/homes/Homes.java` – hängt GlowCubeUtils in dieses Plugin ein
* `src/de/adminfield/menu/HomeListMenu.java` – Homes eines Spielers ansehen und hinspringen
* `src/de/adminfield/menu/MyHomesMenu.java`, `src/de/adminfield/homes/HomeMenuCommand.java` –
  `/homemenu`: eigenes Home-Menü für jeden Spieler
* `src/de/adminfield/menu/PlayerActionMenu.java` – geänderte Bestandsklasse: ein
  Menüpunkt auf Slot 42
* `src/de/adminfield/offline/OfflineStore.java` – Inventare abwesender Spieler
* `src/de/adminfield/menu/OfflinePlayerMenu.java`, `OfflineInspectMenu.java` – deren Menüs
* `src/de/adminfield/menu/PlayerListMenu.java` – geänderte Bestandsklasse: ein
  Menüpunkt auf Slot 46

## Offline-Inventare

Beim Verlassen des Servers wird ein Abbild von Inventar und Enderkiste in
`plugins/AdminField/offline/<uuid>.yml` abgelegt. Der Admin bearbeitet dieses Abbild,
beim nächsten Einloggen landet es im echten Inventar.

Bewusst **nicht** direkt in `playerdata/<uuid>.dat` geschrieben: dieses Format hängt eng
an der Serverversion, und ein Fehler dort wäre unwiederbringlicher Itemverlust. Nötig ist
es auch nicht – solange jemand offline ist, kann sich sein Inventar nicht ändern, das
Abbild bleibt also die ganze Zeit exakt richtig. Die echte Spielerdatei wird nie angefasst.

Grenze: Wer seit dieser Fassung nicht online war, hat noch kein Abbild und taucht deshalb
nicht auf.

## GlowCubeUtils ist eingegliedert

Alle neun Klassen des früheren GlowCubeUtils liegen Byte für Byte unverändert in der
JAR. Möglich ist das, weil seine Hilfsklassen ein schlichtes `JavaPlugin` entgegen-
nehmen und nie die alte Hauptklasse verlangen – die wird gar nicht gebraucht.
`Homes.start()` baut in `onEnable` genau dieselbe Reihenfolge auf wie das alte
`GlowCubeUtils.onEnable`, `Homes.stop()` räumt wie dessen `onDisable` ab.

Für Spieler kommt `/homemenu` dazu – ein eigener Befehl mit eigenem Menü. Jede Aktion
darin führt den passenden Home-Befehl aus, deshalb gelten überall dieselben Regeln.
`/home`, `/homes`, `/sethome`, `/delhome` und `/movehome` bleiben unangetastet.

Beim ersten Start werden `homes.yml` und die Home-Einstellungen aus dem alten
Ordner `plugins/GlowCubeUtils/` übernommen. Die alte JAR muss aus dem `plugins`-
Ordner entfernt werden, sonst streiten sich zwei Plugins um dieselben Befehle.

## Bauen

```bash
./build.sh          # -> dist/AdminField.jar
```

Voraussetzungen: JDK 25 oder neuer, `curl`, `unzip`, `zip`.

### Warum das so umständlich aussieht

Vom Plugin gibt es nur die JAR, keinen Quellcode – und die Paper-API, gegen die es
übersetzt wurde, ist hier nicht erreichbar. Der Build löst das so:

1. `tools/StubGen.java` liest die Original-JAR mit ASM und schreibt kompilierbare
   Platzhalter für jede Klasse, die die JAR *benutzt*, aber nicht *enthält* (Bukkit,
   Paper, Adventure, NMS). Die Signaturen kommen direkt aus dem Bytecode, sind also
   exakt die, die auch zur Laufzeit gebraucht werden. `tools/Overrides.java` ergänzt
   von Hand, was aus reinen Aufrufen nicht ableitbar ist (Vererbung, Generics und die
   API-Teile, die erst der neue Code benutzt).
2. Übersetzt werden **nur** die Dateien unter `src/` – gegen die echten Original-Klassen
   plus die Platzhalter.
3. Die neuen `.class`-Dateien werden in eine Kopie der Original-JAR gelegt.

Ergebnis: keine der 68 Original-Dateien fehlt, und nur die wenigen oben genannten
werden neu übersetzt. Bei jeder davon wird geprüft, dass sie danach exakt dieselbe
öffentliche Signatur und dieselben externen Aufrufe hat wie vorher – der Quelltext-Diff
zeigt jeweils nur die bewusst eingefügten Zeilen. `config.yml` und `plugin.yml` sind
unangetastet.
