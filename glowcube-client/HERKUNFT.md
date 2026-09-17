# Herkunft und Lizenz

GlowCube steht unter der **GPL-3.0-or-later**.

Das war nicht immer so. Angefangen hat das Projekt unter MIT, mit Code, der
hier geschrieben wurde. Sobald aber Module aus fremden Clients uebertragen
werden, gilt deren Lizenz weiter - und alle drei Vorlagen stehen unter
GPL-3.0. Also gilt sie auch hier.

## Woraus geschoepft wurde

### Meteor Client (GPL-3.0)

* <https://github.com/MeteorDevelopment/meteor-client>
* Herangezogener Zweig: `1.21.11` - also genau die Fassung, fuer die
  GlowCube gebaut wird

### BleachHack (GPL-3.0)

* <https://github.com/BleachDrinker420/BleachHack>
* Herangezogene Fassung: 1.2.6 fuer Minecraft 1.20.4

Von dort stammt die Einteilung der Kategorien (Combat, Movement, Render,
Player, World, Exploits, Misc).

**Hinweis zur Lizenzangabe:** In der `fabric.mod.json` von BleachHack steht
`CC0-1.0`, die der JAR beigelegte Lizenzdatei ist aber die GPL-3.0. Bei
diesem Widerspruch wurde die strengere Angabe zugrunde gelegt.

### Meteor Rejects (GPL-3.0)

* <https://github.com/AntiCope/meteor-rejects>
* Herangezogene Fassung: fuer Minecraft 1.21.11

Vorlage fuer `OreSim`.

### SeedCrackerX (MIT)

* <https://github.com/19MisterX98/SeedcrackerX>

Angebunden ueber die von dort vorgesehene Schnittstelle `SeedCrackerAPI`.
Kein Code uebernommen.

**Mitgeliefert:** Der Build legt die zur Spielfassung passende
SeedCrackerX-JAR unveraendert unter `META-INF/jars/` in die GlowCube-JAR, so
dass nur eine Datei in den `mods`-Ordner muss. Die MIT-Lizenz erlaubt das
ausdruecklich; die Lizenzdatei liegt in der eingebetteten JAR bei.

## Wie uebertragen wird

Anfangs wurden Module **aus dem Verstaendnis nachgebaut**: angesehen, was
das Vorbild tut, und selbst geschrieben. Das ist schneller, faellt aber
schlechter aus - die erste KillAura hier hatte sechs Einstellungen, Meteors
hat dreiundzwanzig.

Seitdem gilt der bessere Weg: **den echten Quelltext holen und
originalgetreu uebertragen.** Die Reihenfolge der Pruefungen, die
Zielauswahl, die Grenzfaelle, die krummen Zahlen - alles bleibt, wie es dort
steht. Uebersetzt wird nur das Geruest: ihr `Module` wird unseres, ihre
`Setting.Builder` werden unsere Setting-Klassen, ihr Event-Bus wird
`onTick`. Die Entscheidungen bleiben damit ihre, nicht meine.

Wo Meteors Zweig fuer 1.21.11 Yarn-Namen benutzt und GlowCube auf Mojangs
offiziellen Namen baut, wurde beides nebeneinandergelegt: derselbe Quelltext
liegt im Zweig `master` in Mojang-Namen vor. Und damit kein falscher Name
erst beim Nutzer auffaellt, nagelt `verify-api.py` im Build jeden Namen
fest, an dem ein Mixin haengt - stimmt einer nicht, faellt der Lauf um.

## Wer woher kommt

| Modul | Vorlage | Art |
| --- | --- | --- |
| `KillAura` | Meteor Client | originalgetreu uebertragen |
| `Criticals` | Meteor Client | originalgetreu uebertragen |
| `AutoTotem` | Meteor Client | originalgetreu uebertragen |
| `AutoWalk` | Meteor Client | originalgetreu uebertragen |
| `Scaffold` | Meteor Client | originalgetreu uebertragen |
| `Nuker` | Meteor Client | originalgetreu uebertragen |
| `Spammer` | Meteor Client (`Spam`) | originalgetreu uebertragen |
| `Trajectories` | Meteor Client | originalgetreu uebertragen |
| `HoleESP` | Meteor Client | originalgetreu uebertragen |
| `NoInteract` | Meteor Client | originalgetreu uebertragen |
| `Search` | BleachHack | originalgetreu uebertragen |
| `Timer` | BleachHack | originalgetreu uebertragen |
| `FakeLag` | BleachHack | originalgetreu uebertragen |
| `AntiChunkBan` | BleachHack | originalgetreu uebertragen |
| `PacketFly` | BleachHack | originalgetreu uebertragen |
| `OreSim` | Meteor Rejects | originalgetreu uebertragen |
| `XRay`, `FullBright`, `Zoom`, `Flight`, `Speed`, `Step`, `NoFall`, `AutoSprint`, `AutoTool`, `AutoRespawn`, `AntiAfk`, `StorageESP`, `EntityESP`, `Tracers` | - | eigener Code |
| `SeedHunt`, `ClickGUI` | - | eigener Code |

Das Geruest darunter stammt ebenfalls aus Meteor: `Rotations`, `InvUtils`,
`SlotUtils`, `BlockUtils`, `PlayerUtils`, `DamageUtils`. Und `Erz` (die
Erztabelle fuer OreSim) aus Meteor Rejects.

## Was bewusst nicht uebernommen wurde

**Die Crash-Module aus BleachHack** - BookCrash, PlayerCrash, OffhandCrash,
BowBot. Sie schicken absichtlich fehlerhafte Daten, um andere Spieler oder
den Server abstuerzen zu lassen. Das richtet sich gegen andere Leute, nicht
gegen die eigene Welt. Die client-seitigen Exploits (Timer, FakeLag,
AntiChunkBan, PacketFly, NoInteract) sind alle da.

Dazu einzelne Teile, die an fremden Systemen haengen, die es hier nicht
gibt - jeweils im Quelltext an Ort und Stelle vermerkt:

| Weggelassen | Warum |
| --- | --- |
| Freundesliste in `NoInteract` | haengt an Meteors eigenem Freundes-System |
| Schutz-Verzauberungen in `DamageUtils` | braucht einen Registry-Zugriff, den GlowCube nicht mitschleppt; AutoTotem greift dadurch eher zu frueh als zu spaet |
| Baritone in `AutoWalk` und `OreSim` | ohne Baritone auch im Original nur eine Fehlermeldung |
| Multischuss in `Trajectories` | zweiter und dritter Pfeil der Armbrust |
| Mitschreiben in `Search` | schrieb die Funde in eine Datei |

## Was das bedeutet

Der Quelltext hier ist eigener Code in GlowCubes eigenem Rahmen - kein
kopierter. Die Module *tun* dasselbe wie ihre Vorbilder und treffen
dieselben Entscheidungen, sie sind aber neu geschrieben, weil die Vorlagen
an fremden Modul-, Einstellungs- und Zeichensystemen haengen.

Funktionen sind ohnehin nicht geschuetzt, nur konkreter Code. Die GPL greift
hier trotzdem, sobald sich Umsetzungen an den Vorlagen orientieren - und
lieber eine Lizenz zu viel beachtet als eine zu wenig.
