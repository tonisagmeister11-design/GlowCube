# GlowCube

Ein clientseitiger Werkzeugkasten fuer Minecraft Java 1.21.11 auf Fabric:
X-Ray, OreSim, KillAura, Scaffold, Nuker und mehr - 32 Module, ein ClickGUI
aus verschiebbaren Fenstern, HUD und SeedCrackerX fest eingebaut.

Die meisten Module sind **nicht nachgebaut, sondern uebertragen**: aus dem
echten Quelltext von Meteor Client, BleachHack und Meteor Rejects, mit
deren Reihenfolgen, Grenzfaellen und krummen Zahlen. Wer was von wo hat,
steht in [HERKUNFT.md](HERKUNFT.md).

**Zwei Spielfassungen.** Es gibt eine JAR fuer **1.21.11** und eine fuer
**26.3** (`glowcube-1.21.11.jar` / `glowcube-26.3.jar`). Alle Funktionen -
Module, Agenten, Orbital Strike, `/strike`, `/coordinates`, ESP, OreSim -
laufen auf beiden. Einzige Ausnahme: SeedCrackerX (und damit SeedHunt) gibt
es nur fuer 1.21.11; auf 26.3 laesst sich der Seed fuer OreSim mit
`/glowcube seed <zahl>` von Hand eintragen.

**Eine Datei.** SeedCrackerX steckt als eingebettete JAR mit drin (1.21.11). In den
`mods`-Ordner kommen nur GlowCube und die Fabric API, sonst nichts.

> Auf oeffentlichen Servern faellt das sofort auf und ist dort Bannmaterial.
> Gedacht ist es fuer Einzelspieler und den eigenen Testserver.

## An die fertige Datei kommen

Du brauchst **keine** Entwicklungsumgebung. GitHub uebersetzt den Client bei
jeder Aenderung selbst und haengt ihn an den Release **neueste**:

**<https://github.com/tonisagmeister11-design/GlowCube/releases/latest>**

Dort unter *Assets* auf die JAR deiner Fassung klicken (`glowcube-1.21.11.jar`
oder `glowcube-26.3.jar`) - fertig. Keine ZIP, kein
Entpacken, keine Anmeldung noetig.

### Der andere Weg (nur mit GitHub-Konto)

Unter **Actions** liegt zu jedem Lauf dasselbe als *Artifact* `GlowCube-Mod`.
Das ist eine ZIP und laesst sich **nur herunterladen, wenn man bei GitHub
angemeldet ist** - ohne Konto ist der Name kein Link. Deshalb ist der Release
oben der bequemere Weg.

## Einbauen

1. **Fabric installieren** - den Installer von `fabricmc.net/use/installer`
   holen, starten, als Spielfassung **1.21.11** waehlen, *Install* druecken.
2. Minecraft-Launcher oeffnen. Es gibt jetzt ein Profil **fabric-loader-1.21.11**.
3. **Fabric API** herunterladen - such auf `modrinth.com` nach "Fabric API"
   und nimm die Fassung fuer 1.21.11. Das ist eine `.jar`.
4. Den `mods`-Ordner oeffnen:
   * Windows: `Windows-Taste + R`, `%appdata%\.minecraft\mods` eingeben, Enter.
   * macOS: `~/Library/Application Support/minecraft/mods`
   * Linux: `~/.minecraft/mods`

   Gibt es den Ordner nicht, legst du ihn mit genau diesem Namen an.
5. **Beide** JARs dort hineinlegen: die Fabric API und `glowcube-1.0.0.jar`.
6. Im Launcher das Profil **fabric-loader-1.21.11** starten.

Beides muss zusammenpassen: Fabric API fuer 1.21.11, Loader fuer 1.21.11, GlowCube
fuer 1.21.11. Eine Fassung daneben und das Spiel startet nicht.

## Bedienen

**Rechte Umschalttaste** oeffnet das Menue. Es besteht aus sieben Fenstern,
eines je Kategorie:

* **Linksklick** auf eine Zeile schaltet das Modul.
* **Rechtsklick** klappt seine Einstellungen auf und zu.
* **Mittelklick** belegt die Taste neu.
* Die **Titelleiste** zieht das Fenster; ein Rechtsklick darauf klappt es ein.
* **Tippen** sucht ueber alle Kategorien hinweg.
* **Pos1** raeumt die Fenster wieder ins Raster, falls eines verlegt wurde.

Die Anordnung bleibt ueber den Neustart hinweg erhalten - sie steht in
`.minecraft/config/glowcube.json`.

### Vorbelegte Tasten

| Taste | Modul | | Taste | Modul |
| --- | --- | --- | --- | --- |
| `Rechte Umschalt` | ClickGUI | | `R` | KillAura |
| `X` | X-Ray | | `K` | AutoTool |
| `O` | OreSim | | `M` | AntiAFK |
| `H` | Fullbright | | `B` | SeedHunt |
| `C` | Zoom | | `N` | NoFall |
| `Linke Alt` | Freelook (halten) | | | |
| `F` | Flight | | `J` | AutoSprint |
| `G` | Speed | | `L` | Search |
| `V` | Step | | | |

Alles Uebrige ist ohne Taste und ueber das Menue erreichbar. Die Belegungen
meiden, was Minecraft selbst benutzt - mit einer Ausnahme: `F` tauscht in
Vanilla die Zweithand. Wem das dazwischenkommt, der belegt Flight im Menue
per Mittelklick neu.

Im Chat und in Menues schalten die Tasten nicht - das wird am Mauszeiger
erkannt.

### Chatbefehle

| Befehl | Was er tut |
| --- | --- |
| `/glowcube seed <zahl>` | Setzt den Weltseed von Hand - das braucht OreSim |
| `/glowcube seed` | Zeigt, welcher Seed gerade bekannt ist |
| `/glowcube <modul>` | Schaltet ein Modul, ohne eine Taste zu belegen - Namen mit Leer- oder Bindestrich zusammengeschrieben, z. B. `keinwackeln`, `tnttimer` |
| `/glowcube fenster` | Raeumt die Menuefenster ins Raster |
| `/coordinates` (`/koordinaten`) | Deine Position im Chat - Klick kopiert sie; Knoepfe fuer Strike-Ziel und Einsatzort |
| `/strike [x y z]` | Ziel fuer den Orbital Strike |
| `/agentort [x y z \| weg]` | Einsatzort der Agenten (ohne Zahlen: wo du stehst) |
| `/agentkiste [weg]` | Die Kiste, auf die du schaust, wird Sammelkiste |

Die ersten vier sind rein clientseitig - der Server sieht davon nichts.

## Die Module

### Combat
| Modul | Was es tut |
| --- | --- |
| KillAura | Greift Ziele in Reichweite an - 16 Einstellungen, getrennte Reichweite durch Waende, Waffenwechsel mit Ruecktausch |
| Criticals | Erzwingt kritische Treffer - fuenf Betriebsarten, plus Schmetterschlag mit der Keule |
| AutoTotem | Haelt ein Totem in der Zweithand, sobald es rechnerisch eng wird |

**PvP Pro** (Combat) kaempft fuer dich gegen einen Spieler oder Mob: Ziel ist
das erste Lebewesen, das du schlaegst - Spieler oder Mob (oder der naechste
Spieler). Modi Schwert, Axt und
Crystal. Die Kamera dreht sich sichtbar und weich zum Gegner, geschlagen
wird nur mit dem Fadenkreuz auf ihm und vollem Cooldown; kritische Treffer
aus dem Sprung, Schild brechen mit der Axt, Laufen und seitliches Kreisen.
Crystal setzt Kristalle (und notfalls Obsidian) nur, wenn es dem Gegner
genug und dir hoechstens den eingestellten Schaden macht - nie toedlich.
Dazu: Essen bei Hunger, goldener Apfel bei wenig Leben, Totem in der
Nebenhand, Rueckzug. **Nur mit Erlaubnis:** in der eigenen Welt/LAN und auf
Servern, die es ueber das GlowCube-Plugin freigeben (`pvp-pro-erlaubt: true`,
Berechtigung `glowcube.pvppro`). Sonst schaltet es sich selbst ab.

### Movement
| Modul | Was es tut |
| --- | --- |
| Flight | Fliegen ohne Kreativmodus |
| Speed | Schneller laufen |
| Step | Bloecke hochlaufen ohne Sprung |
| NoFall | Kein Sturzschaden |
| AutoSprint | Immer sprinten |
| AutoWalk | Laeuft von allein weiter, in vier Richtungen |
| Scaffold | Baut den Boden unter dir mit, samt Schnellturm |
| PacketFly | Fliegt ueber Pakete statt ueber die Physik |

### Render
| Modul | Was es tut |
| --- | --- |
| X-Ray | Blendet alles aus, was nicht auf der Liste steht |
| Fullbright | Keine Dunkelheit mehr |
| Zoom | Fernglas |
| StorageESP | Kisten, Faesser, Shulker durch Waende |
| EntityESP | Kaesten um Spieler, Monster, Tiere, Items |
| Tracers | Linien vom Fadenkreuz zu Entities |
| Search | Markiert gesuchte Bloecke, ohne die Sicht zu veraendern |
| HoleESP | Zeigt Loecher, die Explosionen standhalten - getrennt nach Bedrock, Obsidian und gemischt |
| Trajectories | Zeigt, wo Pfeil, Perle oder Trank landen |

### Player
| Modul | Was es tut |
| --- | --- |
| AutoTool | Bestes Werkzeug beim Abbauen |
| AutoRespawn | Sofort wieder einsteigen |
| AntiAFK | Haelt dich auf dem Server |
| NoInteract | Sperrt einzelne Arten von Klicks und Schlaegen - gegen Betten im Nether |

### World
| Modul | Was es tut |
| --- | --- |
| Nuker | Baut alles im Umkreis ab - drei Formen, drei Betriebsarten, vier Reihenfolgen |
| OreSim | Rechnet aus dem Weltseed, wo die Erze liegen |

### Exploits
| Modul | Was es tut |
| --- | --- |
| Timer | Beschleunigt die Weltuhr |
| FakeLag | Haelt Bewegungspakete zurueck und laesst sie gebuendelt los |
| AntiChunkBan | Nimmt auch uebergrosse Chunk- und Buchpakete an |

### Misc
| Modul | Was es tut |
| --- | --- |
| SeedHunt | Faehrt selbsttaetig Flaeche ab, damit SeedCrackerX Daten bekommt |
| Spammer | Schickt regelmaessig Chatnachrichten |
| ClickGUI | Das Menue |

Die Kategorien folgen der Einteilung von BleachHack.

### Agent (Bereich "Kein Hack")

Ein NPC in Spielergestalt, der fuer dich abbaut. Geht in der eigenen Welt
(Einzelspieler oder LAN-Host, mit **Cheats an**) und auf jedem Paper-Server
mit dem **GlowCube-Agent-Plugin** (`glowcube-agent-plugin.jar` im Release,
siehe `glowcube-plugin/README.md`).

| Modul | Was es tut |
| --- | --- |
| Erz-Agent | Baut Erz ab - alle oder gezielt eine Sorte (Diamant, Eisen, Gold, Redstone, Lapis, Kohle, Kupfer, Smaragd, Antiker Schrott, Quarz) |
| Stein-Agent | Baut Stein ab |
| Holz-Agent | Faellt Baeume |
| Guardian-Agent | Leibwaechter in Eisen-, Diamant- oder Netherite-Ruestung: folgt dir und bekaempft alles Feindliche um dich |
| Builder-Agent | Baut ein Schematic genau auf deiner Hoehe direkt vor dir - mehrere Builder nebeneinander |
| Farm-Agent | Erntet reifes Getreide, Karotten, Kartoffeln, Rote Bete und Netherwarzen im einstellbaren Radius, pflanzt sofort neu |
| Tunnel-Agent | Graebt einen Tunnel (1x2, 2x2 oder 3x3) in deine Blickrichtung, bis 512 Bloecke, mit Fackeln alle 8 Bloecke; dichtet Lava und Wasser ab und nimmt Erze aus den Waenden mit |
| Jaeger-Agent | Jagt Tiere in deiner Naehe (alle oder Kuh/Schwein/Schaf/Huhn/Kaninchen), laesst je Sorte einstellbar viele uebrig, nie Jungtiere oder benannte Tiere, und bringt dir die Beute |
| Agenten-Uebersicht | HUD-Kasten mit allen deinen Agenten: was sie tun, wie viel Beute, Entfernung und Richtungspfeil |
| Agent zurueckschicken | Ruft alle Agenten zurueck |

**Mehrere Agenten je Art:** Unter **Anzahl** (1-5) stellst du ein, wie viele
losgeschickt werden. Unter **Einstellen fuer** waehlst du Agent 1 bis 5 -
darunter stehen dann nur dessen Einstellungen (Erzart, Tempo, Schematic,
Radius ...). So sucht etwa Erz-Agent 1 Diamanten und Erz-Agent 2 Eisen.
Mehrere Agenten schwaermen in verschiedene Richtungen aus und nehmen sich nie
denselben Block vor. Anzahl waehrend der Arbeit hochdrehen schickt weitere
los, runterdrehen ruft die ueberzaehligen zurueck. **Leuchten** laesst alle
Agenten der Art durch Waende leuchten.

**Einsatzort** (`/agentort`): Neue Abbau-, Farm- und Tunnel-Agenten arbeiten
dort statt bei dir, der Builder baut dort. Guardian und Jaeger bleiben bei dir.

**Sammelkiste** (`/agentkiste`, auf eine Kiste oder ein Fass schauen): Ist ein
Agent voll, bringt er alles dorthin und arbeitet danach an derselben Stelle
weiter - so koennen Agenten endlos arbeiten. Ist die Kiste voll, kommt er zu dir.

Die Abbau-Agenten haben dazu je Agent vier Einstellungen, die auch waehrend der
Arbeit sofort gelten: **Tempo** (1-4, wie schnell er laeuft), **Abbau-Tempo**
(1-20, wie viel schneller als mit Diamantwerkzeug), **X-Ray** (an: er sieht
Zielbloecke durch Stein in allen Chunks um sich herum, ueber die ganze
Welthoehe, und geht direkt hin) und **X-Ray-Weite** (1-6 Chunks).

So arbeitet er: Er sucht im Umkreis die naechsten Zielbloecke und plant
einen Weg dorthin - durch Hoehlen oder, wenn kuerzer, als Tunnel und Treppe
durch den Stein. Lava meidet er, Bloecke neben Lava baut er nicht ab, Truhen
und Spawner laesst er stehen. Findet er nichts, graebt er sich in die beste
Hoehe fuer das gewaehlte Erz (Diamant: Y -58) und legt dort einen Gang an;
der Holz-Agent zieht ueber die Oberflaeche weiter. Ueber Luecken baut er
Bruecken, zu hohen Staemmen einen Turm. Die Chunks um ihn herum bleiben
geladen, solange er arbeitet.

Zurueckschicken (Modul ausschalten oder "Agent zurueckschicken"): Ist er
nah genug, laeuft oder graebt er sich zu dir, sonst teleportiert er sich.
Dann wirft er dir seine Beute Stapel fuer Stapel zu und verschwindet. Ist
sein Inventar voll, kommt er von selbst. Wird die Welt geschlossen, landet
die Beute direkt in deinem Inventar.

**Guardian-Agent:** Er folgt dir und greift an: jedes Monster im Umkreis von
16 Bloecken um dich, jeden Mob, der dich ins Visier nimmt, und wer dich
gerade getroffen hat - den dir naechsten zuerst. Hast du nur noch ein Herz,
laesst er den Gegner stehen, stellt sich neben dich und wehrt nur noch ab,
was direkt an dir dran ist, bis du wieder drei Herzen hast. Zurueckschicken
laesst ihn verschwinden.

**Builder-Agent:** Er baut das gewaehlte Schematic auf deiner Hoehe (nicht
darunter, nicht darueber), beginnend zwei Bloecke vor dir, in deine
Blickrichtung gedreht. Alle Bloecke hat er dabei; was im Weg ist, raeumt er
ab. Gebaut wird Schicht fuer Schicht im **Bau-Tempo** (1-100 Bloecke pro
Sekunde, Standard 20); er schwebt dabei ueber der Baustelle. Wenn er fertig
ist, steht es im Chat. Eingebaut: Starter-Haus, Japanischer Tempel, Orbital
Strike Cannon, Portal Protegido, Arc de Triomphe. Eigene Schematics
(`.schem`, `.litematic`, `.nbt`) gehoeren nach
`.minecraft/config/glowcube/schematics/` und stehen nach einem Neustart in
der Auswahl.

Er baut **1:1 wie im Schematic**: jeder Block in genau seinem Zustand,
Truhen, Trichter, Faesser, Schilder, Banner usw. mit genau dem Inhalt aus
dem Schematic. Beim Bauen und Abraeumen loest er keine Nachbar-Updates aus -
kein Wasser fliesst nach, kein Sand faellt, keine Tuer, Fackel oder
Redstone-Leitung bricht weg. Beim Abraeumen hat er das passende Werkzeug in
der Hand (Spitzhacke, Axt, Schaufel, Hacke, Schere).

**Orbital Strike Cannon:** Die Kanone muss stehen - der Hebel feuert nur,
wenn rund um ihn wirklich die Orbital Strike Cannon gebaut ist (GlowCube
vergleicht sie mit dem Schematic, auch gedreht). Der Builder setzt ganz oben
einen Leitstein mit Hebel darauf; wer die Kanone anders baut, setzt die
beiden selbst an dieselbe Stelle. So geht's:

1. Zum Ziel gehen und `/coordinates` (oder `/koordinaten`) eingeben. Im Chat
   steht die Position - ein Klick auf die Zahlen kopiert sie, ein Klick auf
   `[Als Strike-Ziel]` schreibt gleich `/strike x y z` ins Chatfeld.
2. `/strike x y z` eingeben (kopierte Zahlen einfuegen). `/strike` allein
   nimmt den Block, auf den man schaut (bis 500 Bloecke).
3. Den Hebel oben auf der Kanone umlegen. Dann sieht man die Kanone
   arbeiten (Schritt fuer Schritt auch in der Anzeige ueber der Leiste):
   das Signal laeuft rot leuchtend durch die Redstone-Leitungen, das TNT in
   den vier Ladearmen zuendet von aussen nach innen, die Portale leuchten,
   im Kern knallt es und die Salve schiesst senkrecht aus der Kanone in den
   Himmel. Nach der Flugzeit fallen am Ziel Ringe aus TNT herab.

Laeuft in der eigenen Welt und auf Servern mit dem GlowCube-Agent-Plugin -
ohne Bestaetigen, direkt.

In der eigenen Welt laufen alle Agenten auch im Survival ohne Cheats.

### Performance (Bereich "Kein Hack")

**Ultra-Performance** dreht alles herunter, was Bild kostet: Sichtweite
(einstellbar, Standard 2 Chunks), Wolken, Schatten, Partikel, Wetter,
Blattdurchsicht, Umgebungsverdeckung, Mipmaps, Bildschirmeffekte, Bildsync
und Bildratengrenze. Dazu schaltet es das Ressourcenpaket **GlowCube-Pixel**
zu: jeder Block wird ein einziges Pixel in seiner Durchschnittsfarbe,
Spieler, Mobs und Items bleiben normal. Das Paket wird beim Einschalten aus
den gerade aktiven Texturen erzeugt (Minecraft laedt dabei kurz neu).

Nochmals draufdruecken stellt jeden Wert und die Paketliste genau wieder her.
In `options.txt` landet vom Modus nichts.

### PvP-HUD und Info-HUD (Bereich "Kein Hack")

Anzeigen wie in PvP-Clients, uebertragen aus AxolotlClient. Sie laufen auf
1.21.11 und 26.3 und stehen in zwei Fenstern: **PvP-HUD** (CPS, Keystrokes,
Reichweite, Combo, Ruestung, Traenke, Pfeile, Bewegung, Mausbewegung) und
**Info-HUD** (alles uebrige). Jede Anzeige hat die Einstellung **Seite**:
Links stapelt sie unter das Wasserzeichen, Rechts unter die Modulliste.

| Modul | Was es zeigt |
| --- | --- |
| CPS | Klicks pro Sekunde, auf Wunsch links und rechts |
| FPS | Bildrate |
| Ping | Latenz zum Server (Einzelspieler: 0 ms) |
| Tempo | Geschwindigkeit in Bloecken pro Sekunde |
| Reichweite | Wie weit dein letzter Schlag reichte |
| Combo | Treffer in Folge |
| Uhrzeit | Echte Uhrzeit |
| Koordinaten | X/Y/Z mit Himmelsrichtung, oder kompakt in einer Zeile |
| Keystrokes | WASD, Maustasten und Leertaste, gedrueckt hell |
| Ruestung | Ruestung und Hand mit Haltbarkeit |
| Traenke | Laufende Trankwirkungen mit Restzeit |
| Kompass | Kompassband mit Himmelsrichtungen und Gradzahl |
| TPS | Tickrate des Servers |
| Arbeitsspeicher | Belegter Arbeitsspeicher |
| Server-IP | Adresse des Servers (Einzelspieler: "Einzelspieler") |
| Spielerzahl | Wie viele Spieler online sind |
| Bewegung | Ob du schleichst oder sprintest |
| Eigener Text | Ein Text, den du selbst festlegst |
| Pfeile | Wie viele Pfeile du dabeihast |
| Inventar | Dein Inventar dauerhaft am Bildschirmrand |
| Aufgesammelt | Was gerade ins Inventar kam oder es verliess |
| Mausbewegung | Wie du die Maus gerade bewegst |
| Ressourcenpakete | Die aktiven Ressourcenpakete |

### Optik (Bereich "Kein Hack")

Einstellungen fuer die Darstellung, ebenfalls aus AxolotlClient.

| Modul | Was es tut |
| --- | --- |
| Freelook | Linke Alt-Taste halten: umsehen, ohne dass sich die Figur mitdreht |
| Kein Wackeln | Die Kamera wackelt nicht, wenn du getroffen wirst |
| Niedriges Feuer | Das Feuer am Bildschirmrand verdeckt weniger Sicht |
| Keine Vignette | Kein dunkler Rand am Bildschirm |
| Kein Regen | Kein Regen und kein Gewitter auf deinem Bildschirm |
| Eigener Name | Dein Namensschild in der dritten Person |
| Kein Beacon-Strahl | Blendet die Strahlen von Leuchtfeuern aus |
| TNT-Timer | Restzeit ueber gezuendetem TNT |

## Zusammenspiel mit SeedCrackerX

[SeedCrackerX](https://github.com/19MisterX98/SeedcrackerX) rechnet aus
Merkmalen der Welt - Erzadern, Dungeons, Strukturen - den Weltseed zurueck.
Die passende Fassung (derzeit 2.15.6) steckt **schon in der GlowCube-JAR**
unter `META-INF/jars` - Fabric laedt eingebettete Mods beim Start von selbst
mit. Es ist also nichts danebenzulegen. Welche Fassung genau eingebaut wird,
entscheidet `bundle-seedcracker.py` bei jedem Build neu: es liest aus jeder
Kandidaten-JAR das eigene Manifest und nimmt die neueste, deren
`depends.minecraft` auf die gebaute Spielfassung passt.

GlowCube haengt sich an zwei Stellen ein:

* **Der Seed geht nicht mehr verloren.** SeedCrackerX bietet anderen Mods die
  Schnittstelle `SeedCrackerAPI` an und ruft sie auf, sobald es fertig ist.
  GlowCube meldet sich dort an. Der Seed erscheint ueber der Hotbar **und im
  Chat** - dort bleibt er stehen und laesst sich markieren - und wandert mit
  Zeitstempel nach `.minecraft/config/glowcube-seeds.txt`.

## Was X-Ray auf Servern kann und was nicht

Kurz: **Der Weltseed hilft X-Ray nicht.** Das sind zwei verschiedene Dinge.

X-Ray entscheidet nur, ob ein Block *gezeichnet* wird. Es kann ausschliesslich
das zeigen, was der Client ohnehin schon hat.

* **Server ohne Anti-X-Ray** schicken die Chunks samt Erzen. Dort wirkt X-Ray
  bereits jetzt - ohne Seed, ohne Zutun.
* **Server mit Anti-X-Ray** (Paper bringt es mit, fast jeder groessere Server
  schaltet es ein) schicken gefaelschte Daten: Stein, wo Erz liegt, oder Erz
  ueberall. Die echten Erzpositionen erreichen den Client nie. Kein
  Client-Mod kann zeigen, was er nicht bekommen hat - auch nicht mit Seed.

**Dafuer gibt es OreSim** - und das ist ein anderes Verfahren. Es zeigt nicht,
was der Client bekommen hat, sondern rechnet aus, wo die Erze bei der
Weltgenerierung entstanden *waeren*. Ein Server kann daran nichts faelschen;
die Rechnung braucht nur den Weltseed und laeuft vollstaendig im eigenen
Client. Auf einem Server mit Anti-X-Ray ist OreSim also das, was X-Ray dort
nicht sein kann.

Was OreSim braucht:

1. Den Weltseed. Entweder findet ihn SeedCrackerX (SeedHunt faehrt die
   Arbeit ab), oder man kennt ihn und gibt ihn mit
   `/glowcube seed <zahl>` ein.
2. Taste `O`. Alle zehn Erzarten sind voreingestellt an und einzeln
   abschaltbar.

Die Einstellung **Luftpruefung** entscheidet, ob gegen die wirklich geladene
Welt geprueft wird. Steht sie auf *Beim Laden*, verschwindet Erz, das jemand
laengst abgebaut hat; auf *Aus* zeigt OreSim die Welt so, wie sie einmal
erzeugt wurde - auch dort, wo der Server luegt.

Fuer **Strukturen** braucht es GlowCube gar nicht: SeedCrackerX bringt eigene
Finder mit, die aus dem bekannten Seed Festungen, Tempel und anderes
errechnen.

## Zusammenspiel mit SeedHunt

* **SeedHunt fliegt die Arbeit ab.** Mit der Einstellung `AutoStart` geht es
  beim Betreten einer Welt von selbst los. SeedCrackerX sieht nur, was der Client
  ohnehin geladen bekommt - wer stehen bleibt, wartet ewig. `B` startet eine
  quadratische Spirale um den Startpunkt, die nach aussen waechst und dabei
  Chunk um Chunk laedt. Sobald der Seed da ist, schaltet sich SeedHunt selbst
  ab. Abstand der Bahnen, Hoehe, Tempo und Laenge stehen in der
  Konfigurationsdatei.

Ohne SeedCrackerX laeuft GlowCube unveraendert weiter - die Verbindung ist
freiwillig, keine Voraussetzung.

**Im Einzelspieler** ist das eine Spielerei: `/seed` sagt es dir sofort.
Interessant ist es als Probe, ob der Cracker die Welt auch von aussen
errechnen kann. **Auf fremden Servern** ist der Weltseed ein handfester
Vorteil (Festungen, Slime-Chunks, Strukturen) und praktisch ueberall
verboten - dort kostet es den Zugang.

## Wenn etwas nicht klappt

| Was passiert | Woran es liegt |
| --- | --- |
| Spiel startet, nichts passiert | Falsches Profil gestartet - es muss `fabric-loader-1.21.11` sein |
| Absturz beim Start, `fabric-api` im Text | Fabric API fehlt im `mods`-Ordner |
| Absturz, `mixin` im Text | Fassungen passen nicht zusammen - alle drei auf 1.21.11 bringen |
| Der Build bei GitHub ist rot | Im Lauf auf **Bauen** klicken, die rote Zeile ist die Ursache |

## Fuer den Fall, dass du doch selbst bauen willst

JDK 21 und Gradle vorausgesetzt:

```bash
cd glowcube-client
python3 resolve-versions.py   # traegt die aktuellen Fabric-Versionen ein
gradle build                  # -> build/libs/glowcube-1.0.0.jar
gradle runClient              # Testinstanz mit dem Mod
```

## Aufbau

| Pfad | Inhalt |
| --- | --- |
| `src/main/java/.../core/` | Modul-Grundgeruest, Einstellungen, Konfiguration |
| `src/main/java/.../util/` | Farben, Zeichnen, und der aus Meteor uebertragene Werkzeugkasten (Rotations, InvUtils, BlockUtils, DamageUtils) |
| `src/main/java/.../gui/` | ClickGUI und Block-Auswahl |
| `src/main/java/.../hud/` | Wasserzeichen und Modulliste |
| `src/main/java/.../module/` | Die 32 Module |
| `src/main/java/.../mixin/` | Die Eingriffe ins Spiel |
| `einzeldatei/` | Derselbe Code in einer einzigen Datei - nicht Teil des Builds |
| `resolve-versions.py` | Holt die aktuellen Fabric-Versionen |
