# The Boys – Fabric-Mod für Minecraft 26.3

Compound V, Vought-Labore und die Kräfte von **Homelander**, **Soldier Boy**, **A-Train** und
**Billy Butcher** (Stand: Ende Staffel 5).

## Installation

1. Fabric Loader ≥ 0.19.5 für Minecraft **26.3** installieren.
2. **Fabric API** (0.161.0+26.3 oder neuer) in den `mods`-Ordner legen.
3. `theboys-1.0.0.jar` in den `mods`-Ordner legen.

Die Mod muss auf dem Server **und** bei jedem Spieler installiert sein (bei Essential-/LAN-Welten
also bei allen Mitspielern).

## So bekommst du Kräfte

* In der Oberwelt spawnen **Vought-Labore**, ungefähr so häufig wie zerstörte Netherportale.
* Darin stehen **Compound-V-Kühlschränke**. Ein Rechtsklick auf einen vollen Kühlschrank gibt dir
  eine Dosis. Meistens ist das **Compound V**, sehr selten **V-One**. Dazu kommen Truhen mit Laborbeute.
* **Compound V** gibt dir zufällig die Kräfte von **A-Train** oder **Billy Butcher**.
* **V-One** (viel seltener) gibt dir die Kräfte von **Homelander** oder **Soldier Boy**.
* Spritze in die Hand nehmen und **[V]** drücken (oder Rechtsklick halten). Du setzt dir die Spritze
  dann mit einer Animation in den Arm, in der Ego-Perspektive wie in der Third-Person-Ansicht.
* **Kraft entfernen:** Der **Uran-Injektor** brennt das V wieder aus dem Blut. Das passt zur Serie,
  dort entfernt nur Strahlung Kräfte. Danach kannst du dir neues V setzen.
  Auch Soldier Boys **Nuke** brennt anderen Supes in der Nähe die Kräfte aus.

## Tasten (änderbar unter Steuerung → The Boys)

| Taste | Funktion |
| --- | --- |
| R | Fähigkeit 1 |
| G | Fähigkeit 2 |
| C | Fähigkeit 3 |
| X | Fähigkeit 4 |
| V | Spritze setzen |
| J | Anzug an/aus (du trägst den Anzug des Charakters als Skin) |

Rechts am Bildschirm siehst du deine Fähigkeiten mit Abklingzeiten und der jeweiligen Anzeige
(Hitze, Nuke-Ladung).

## Die Kräfte

Alle Effekte werden als echte 3D-Objekte in der Welt gezeichnet. Andere Spieler sehen sie also
genauso, und auch in der Third-Person-Ansicht (F5) sind sie zu sehen.

### Homelander
* **Hitzeblick (R halten):** zwei rote Laserstrahlen aus den Augen, bis 64 Blöcke weit. Er
  verbrennt Gegner, schmilzt sich durch Blöcke und setzt Dinge in Brand. Nach etwa 7 Sekunden
  überhitzt er.
* **Fliegen (G):** schnelles Fliegen. Bei hoher Geschwindigkeit gibt es einen Überschallknall mit
  Druckwelle. Fliegt er mit voller Geschwindigkeit, **bricht er durch Wände** und rammt alles weg,
  was im Weg ist.
* **Röntgenblick (C):** Lebewesen leuchten 12 Sekunden lang durch Wände.
* **Donnerklatscher (X):** eine Druckwelle als Kegel nach vorne.
* Passiv: enorme Stärke und Härte, kein Fallschaden, immun gegen Feuer.

### Soldier Boy
* **Strahlenstoß (R halten):** ein oranger Strahl aus der Brust. Er brennt durch Wände und
  steckt Gegner in Brand.
* **Nuke (G halten zum Laden, loslassen):** eine riesige Strahlungsexplosion. Je länger du lädst,
  desto größer wird sie. Sie **brennt anderen Supes die Kräfte aus**. Hältst du zu lange, geht sie
  von selbst los. Danach bist du erschöpft.
* **Schildwurf (C):** wirf das Schild. Es springt zwischen bis zu drei Gegnern hin und her und
  kommt zurück (Schild in der Hand nötig).
* **Schild-Sturmangriff (X):** ein Sprint nach vorne, der alles umwirft.
* Passiv: Supersoldat mit Stärke, Härte und Regeneration.

### A-Train
* **Supertempo (R):** extrem schnelles Laufen mit Tacho (km/h) und Blitzen hinter dir.
  Du läufst über Stufen einfach hinweg (Auto-Step). Über Bäume und Mauern springst du in einem
  flachen Bogen genau so hoch wie nötig, statt abzubremsen. Du kannst sogar übers Wasser rennen.
  **Wer dir im Weg steht, explodiert in einer Blutwolke.**
* **Zeitsprung (G):** Du rennst mit rund 1000 km/h los, und für alle anderen läuft die Zeit
  **rückwärts**. Spieler und Mobs laufen ihre Wege rückwärts, Leben kommt zurück, abgebaute Blöcke
  tauchen wieder auf. Gedroppte Items **schweben zurück** in die Hand, die sie geworfen hat.
  Nur du selbst läufst weiter vorwärts.
* **Blitzsprint (C):** ein kurzer, extrem schneller Sprint nach vorne.
* **Schlaghagel (X):** ein Trommelfeuer aus Schlägen auf das Ziel vor dir.

### Billy Butcher (Staffel 5) – „Super Cancer“
Die Tentakel, die aus seiner Brust wachsen, heißen in der Serie **Super Cancer**:
* **Tentakelhieb (R):** Ein Tentakel schießt heraus und zertrümmert Gegner und Blöcke. Trifft er
  einen Baumstamm, **fällt der ganze Baum**.
* **Packen / Werfen (G):** einen Mob oder Spieler packen und in der Luft halten. Nochmal G wirft ihn.
* **In zwei Hälften reißen (C):** Das Opfer wird hochgehoben, die Tentakel ziehen an beiden Seiten,
  und es **reißt in zwei Hälften**. Die Hälften fliegen auseinander, und aus der Rissstelle mit
  Wirbelsäule und Rippen spritzt noch eine Weile Blut.
* **Cancer Walk (X):** Die Tentakel werden zu Beinen, tragen dich **4–5 Blöcke hoch** und laufen
  für dich. Du springst höher, bekommst keinen Fallschaden, und alles vor dir wird zertrümmert.
  **Schleichen + X** ist der Tentakel-Enterhaken.
* **Super Cancer rettet dich:** Fällst du im Survival unter etwa 3,5 Herzen, brechen viele Tentakel
  aus dir heraus. Sie packen jedes Monster und jeden Gegner in 20 Blöcken Umkreis und **zerreißen sie**.
  Danach bekommst du kurz Widerstand und Regeneration. Abklingzeit: 45 Sekunden.
* Passiv: deutlich stärker und zäher als ein Mensch. Dazu gibt es sein **Brecheisen** als Waffe.

## Items und Blöcke

Im Inventar sieht man ein flaches Icon wie bei jedem Item. In der Hand sind Spritzen, Brecheisen
und Schild echte **3D-Modelle**: Die Spritzen haben Kolben, Glaskörper mit Serum und Nadel, das
Brecheisen eine gebogene Klaue.

Compound V, V-One, Uran-Injektor, leere Spritze, Soldier Boys Schild (blockt wie ein normales
Schild), Butchers Brecheisen, Labor-Fliesen, Laborboden, Vought-Stahlplatte, Sicherheitsglas und
der Compound-V-Kühlschrank. Alles findest du im Kreativ-Tab „The Boys“.

## Befehle (für Operatoren)

```
/theboys power set <Spieler> <homelander|soldier_boy|a_train|butcher|none>
/theboys power clear <Spieler>
/theboys power get <Spieler>
```

## Bauen

```bash
./gradlew build      # -> build/libs/theboys-1.0.0.jar
```

Benötigt JDK 25. Gebaut wird auch automatisch über GitHub Actions
(`.github/workflows/theboys-mod.yml`).

Texturen, Labor-Struktur und JSON-Dateien werden aus Skripten erzeugt:

```bash
python3 tools/textures/gen_skins.py && python3 tools/textures/gen_misc.py && python3 tools/textures/gen_extra.py
python3 tools/structure/gen_lab.py
python3 tools/gen_resources.py
```

Die Recherche zu allen Kräften (Stand Staffel 5) steht in `docs/RESEARCH.md`.
