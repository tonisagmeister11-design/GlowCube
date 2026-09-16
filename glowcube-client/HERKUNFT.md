# Herkunft und Lizenz

GlowCube steht unter der **GPL-3.0-or-later**.

Das war nicht immer so. Angefangen hat das Projekt unter MIT, mit Code, der
hier geschrieben wurde. Sobald aber Module nach dem Vorbild fremder Clients
entstehen, gilt deren Lizenz weiter - und beide Vorlagen stehen unter
GPL-3.0. Also gilt sie auch hier.

## Woraus geschoepft wurde

### BleachHack (GPL-3.0)

* <https://github.com/BleachDrinker420/BleachHack>
* Herangezogene Fassung: 1.2.6 fuer Minecraft 1.20.4

Von dort stammt die Einteilung der Kategorien (Combat, Movement, Render,
Player, World, Exploits, Misc) und die Idee zu diesen Modulen:

| GlowCube | Vorbild |
| --- | --- |
| `Search` | Search |
| `HoleESP` | HoleESP |
| `Trajectories` | Trajectories |
| `AutoWalk` | AutoWalk |
| `Criticals` | Criticals |

**Hinweis zur Lizenzangabe:** In der `fabric.mod.json` von BleachHack steht
`CC0-1.0`, die der JAR beigelegte Lizenzdatei ist aber die GPL-3.0. Bei
diesem Widerspruch wurde die strengere Angabe zugrunde gelegt.

### Meteor Rejects (GPL-3.0)

* <https://github.com/AntiCope/meteor-rejects>
* Herangezogene Fassung: 0.3 fuer Minecraft 1.21.11

Vorlage fuer das geplante `OreSim`: die Erzvorhersage aus dem Weltseed ueber
`WorldgenRandom.setDecorationSeed`.

### SeedCrackerX (MIT)

* <https://github.com/19MisterX98/SeedcrackerX>

Angebunden ueber die von dort vorgesehene Schnittstelle `SeedCrackerAPI`.
Kein Code uebernommen.

**Mitgeliefert:** Der Build legt die zur Spielfassung passende
SeedCrackerX-JAR unveraendert unter `META-INF/jars/` in die GlowCube-JAR, so
dass nur eine Datei in den `mods`-Ordner muss. Die MIT-Lizenz erlaubt das
ausdruecklich; die Lizenzdatei liegt in der eingebetteten JAR bei. Wer
SeedCrackerX lieber selbst pflegt, loescht `bundle-seedcracker.py` aus dem
Ablauf und legt es wie bisher daneben.

## Zwei Arten, ein Modul zu bauen

Anfangs habe ich die Module **aus dem Verstaendnis nachgebaut**: angesehen,
was das Vorbild tut, und es selbst geschrieben. Das ist schneller, faellt
aber schlechter aus - meine erste KillAura hatte sechs Einstellungen, Meteors
hat dreiundzwanzig.

Seitdem gilt der bessere Weg: **den echten Quelltext holen und
originalgetreu uebertragen.** Die Reihenfolge der Pruefungen, die
Zielauswahl, die Grenzfaelle - alles bleibt, wie es dort steht. Uebersetzt
wird nur das Geruest: ihr `Module` wird unseres, ihre `Setting.Builder`
werden unsere Setting-Klassen, ihr Event-Bus wird `onTick`.

Die Entscheidungen bleiben damit ihre, nicht meine. Das ist so nah an einer
Eins-zu-eins-Uebernahme, wie es geht, ohne ihr ganzes Geruest mitzuschleppen.

| Modul | Vorlage | Art |
| --- | --- | --- |
| `KillAura` | Meteor Client | originalgetreu uebertragen |
| alle uebrigen | BleachHack | aus dem Verstaendnis nachgebaut (wird nachgezogen) |

## Was das bedeutet

Der Quelltext hier ist eigener Code in GlowCubes eigenem Rahmen - kein
kopierter. Die Module *tun* dasselbe wie ihre Vorbilder, sie sind aber neu
geschrieben, weil die Vorlagen an fremden Modul-, Einstellungs- und
Zeichensystemen haengen und sich gar nicht uebertragen liessen.

Funktionen sind ohnehin nicht geschuetzt, nur konkreter Code. Die GPL greift
hier trotzdem, sobald sich Umsetzungen an den Vorlagen orientieren - und
lieber eine Lizenz zu viel beachtet als eine zu wenig.
