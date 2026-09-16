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

## Was das bedeutet

Der Quelltext hier ist eigener Code in GlowCubes eigenem Rahmen - kein
kopierter. Die Module *tun* dasselbe wie ihre Vorbilder, sie sind aber neu
geschrieben, weil die Vorlagen an fremden Modul-, Einstellungs- und
Zeichensystemen haengen und sich gar nicht uebertragen liessen.

Funktionen sind ohnehin nicht geschuetzt, nur konkreter Code. Die GPL greift
hier trotzdem, sobald sich Umsetzungen an den Vorlagen orientieren - und
lieber eine Lizenz zu viel beachtet als eine zu wenig.
