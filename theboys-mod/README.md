# The Boys – Fabric-Mod für Minecraft 26.3

Compound V, Vought-Labore und die Kräfte von **Homelander**, **Soldier Boy**, **A-Train**,
**Billy Butcher**, **Starlight**, **The Deep**, **Black Noir**, **Stormfront** und **MiniMaus** (Stand: Ende Staffel 5).

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
* **Compound V** gibt dir zufällig die Kräfte von **A-Train**, **Billy Butcher**, **Starlight**, **The Deep**
  oder **Black Noir**.
* **V-One** (viel seltener) gibt dir die Kräfte von **Homelander**, **Soldier Boy** oder **Stormfront**
  (Stormfront gibt es nur mit V-One).
* **Mini V** (rosa Spritze, etwa jeder zwölfte Kühlschrank und manche Laborkisten) macht dich zu **MiniMaus**.
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
* **Packen / Werfen (G):** einen Mob oder Spieler packen und in der Luft halten. Die Tentakel
  **schlingen sich um das Opfer** (Brust und Beine). Nochmal G wirft es.
* **In zwei Hälften reißen (C):** Das Opfer wird hochgehoben, die Tentakel ziehen an beiden Seiten,
  und es **reißt in zwei Hälften**. Dabei wird das echte Modell des Opfers durchgeschnitten, egal ob
  Zombie, Kuh, Spinne, Eisengolem, Spieler oder Mob aus einer anderen Mod. Jede Hälfte sieht also aus
  wie eine halbe Kuh, ein halber Zombie und so weiter. An jeder Schnittstelle sieht man Fleisch und
  Knochen. Die Hälften fliegen auseinander, bleiben mit der Wunde nach oben liegen, und es spritzt noch
  eine Weile Blut heraus.
* **Cancer Walk (X):** Die Tentakel werden zu Beinen, tragen dich **4–5 Blöcke hoch** und laufen
  für dich. Du springst höher, bekommst keinen Fallschaden, und alles vor dir wird zertrümmert.
  **Schleichen + X** ist der Tentakel-Enterhaken.
* **Super Cancer rettet dich – jedes Mal:** Fällst du im Survival unter etwa 3,5 Herzen, brechen viele
  Tentakel aus dir heraus. Sie umschlingen jedes Monster und jeden, der dich angegriffen hat (auch
  Spieler), in 20 Blöcken Umkreis und **zerreißen sie**. Solange die Tentakel draußen sind, fangen sie
  den tödlichen Schlag ab. Danach bekommst du kurz Widerstand und Regeneration. Nach 2 Sekunden ist
  Super Cancer wieder bereit und kommt jedes Mal, wenn du wieder in Gefahr bist.
* Passiv: deutlich stärker und zäher als ein Mensch. Dazu gibt es sein **Brecheisen** als Waffe.

### Starlight (Compound V)
Weißer Anzug mit großem Silberstern, hellblauer Gürtel, Faltenrock, weiße Stiefel, kurzer weißer Umhang,
blonde Haare (schlankes Modell). Sie lebt von Licht und Strom: Die **Licht-Ladung** (Leiste im HUD) füllt sich im
Hellen von selbst, und ein **Blitz** lädt sie sofort ganz auf, statt ihr zu schaden.
* **Lichtstrahl (R halten):** Goldener Strahl aus beiden Händen. Er verbrennt, blendet kurz, stößt zurück und
  setzt Untote in Brand. Kostet Ladung.
* **Supernova (Schleichen + R):** Ab 65 % Ladung entlädt sie alles auf einmal. Eine Lichtkugel verbrennt und
  schleudert alles im Umkreis weg.
* **Schweben / Fliegen (G):** Sie schwebt aufrecht mit ausgebreiteten Armen auf einem Lichtschein. Das kostet
  Ladung. Ist sie leer, gleitet sie sanft zu Boden.
* **Blendblitz (C):** Ein Blitz so hell wie die Sonne. Wer sie sieht, ist geblendet, verlangsamt und geschwächt.
  Spieler sehen kurz nur Weiß. Untote brennen.
* **Licht absorbieren (X):** Sie saugt das Licht aller Lampen in der Nähe in ihre Hände (goldene Lichtströme).
  Kerzen, Lagerfeuer und Redstone-Lampen gehen dabei aus. Unter freiem Himmel trinkt sie Sonnenlicht.

### The Deep (Compound V)
Grün-türkiser Schuppenanzug mit Gold, Kiemen an den Flanken, zurückgegelte braune Haare.
* Passiv: atmet unter Wasser, sieht dort im Dunkeln (Meereskraft), schwimmt wie ein Delfin und ist im Wasser
  **deutlich stärker**. Kälte macht ihm nichts. An Land **trocknet er aus** (Leiste „Feuchtigkeit“) und wird
  schwach und langsam, bis er wieder ins Wasser oder in den Regen kommt.
* **Delfin-Angriff (R):** Er ruft drei Delfine, die nacheinander auf sein Ziel schießen und es rammen.
* **Sonar (G):** Echoortung. Alle Lebewesen im Umkreis von 48 Blöcken leuchten durch Wände hindurch.
* **Flutwelle (C):** Eine brechende Welle rollt vor ihm her, reißt alles mit und löscht Feuer.
* **Torpedo-Sprint (X):** Er schießt mit ausgestreckten Armen nach vorne (im Wasser viel schneller) und rammt
  alles um.

### Black Noir (Compound V)
Komplett schwarze Rüstung, runder Helm mit dunklen Linsen, ein **Katana auf dem Rücken**.
* Passiv: stark, zäh, heilt schnell. Er **spricht nicht**: Mit Anzug kann er nicht in den Chat schreiben.
  Und er hat eine **Nussallergie**: Kekse vergiften ihn fast.
* **Katana-Kombo (R):** Er zieht das Katana, springt zum Gegner und schlägt dreimal zu. Der letzte Hieb lässt
  ihn bluten (Verdorrung).
* **Schattentarnung (G):** Unsichtbar und schnell. Monster verlieren ihn aus den Augen. Der erste Schlag aus
  dem Schatten macht viel mehr Schaden.
* **Schattensprung (C):** In einer Rauchwolke weg und direkt hinter dem Gegner wieder da. Der nächste Schlag ist
  ein Hinterhalt. Ohne Ziel springt er ein Stück nach vorne.
* **Wurfmesser (X):** Drei Messer im Fächer, die sofort treffen und tief schneiden.

### Stormfront (nur V-One)
Dunkelblauer Anzug mit rot-weißen Streifen, Blitz-Emblem, Messing-Schnalle, US-Armbinden, dunkler Bob mit rotem
Lippenstift und ein blauer Umhang mit rotem Futter (schlankes Modell).
* Passiv: sehr stark und zäh, **immun gegen Blitze**. Ihre Schwäche ist **Feuer**: Sie nimmt doppelten
  Feuerschaden und brennt doppelt so lange.
* **Blitzstrom (R halten):** Blitze aus beiden Händen. Sie springen vom Ziel auf bis zu drei weitere Gegner
  über und lähmen sie. Hält zu lange, überhitzt der Strom (Leiste „Spannung“).
* **Fliegen (G):** Noch schneller als Homelander, mit Funken um den ganzen Körper.
* **Blitzschlag rufen (C):** Fünf echte Blitze schlagen nacheinander dort ein, wo sie hinzeigt. Sie folgen dem
  Gegner, den sie anvisiert hat.
* **EMP-Nova (X):** Eine elektrische Entladung um sie herum. Blitzbögen treffen jeden in der Nähe, lähmen ihn
  und schleudern ihn weg.

### MiniMaus (Mini V)
Du wirst zu einer Maus: grauer Pelz, cremefarbener Bauch, rosa Pfoten, Schnurrhaare, Hasenzähne und ein
Heldengürtel mit „M“. Dazu kommen **runde 3D-Ohren**, die zucken, und ein **langer rosa Schwanz**, der beim
Rennen hin und her schwingt.
* **Doppelt so schnell** wie ein normaler Spieler, und dabei sehr stark (viel Schaden und Rüstung), egal ob
  groß oder klein. Die Geschwindigkeit schrumpft mit dir: Als Winzling läufst du so schnell, wie es zu deiner
  Größe passt, damit du in Ruhe Pixel für Pixel knabbern kannst.
* **Giftbiss (R):** Danach ist dein nächster Angriff ein Biss. Leuchtende Kiefer schnappen zu, und das Opfer
  bekommt starkes Gift (Gift IV) und Schwäche, Spieler zusätzlich Übelkeit.
* **To the Moon (G):** Den nächsten Gegner, den du schlägst, hältst du fest. Dann holst du weit aus und
  haust ihn mit einem riesigen Uppercut samt Druckwelle **rund 120 Blöcke senkrecht in die Luft**.
* **Multi Smash (C):** Du packst den Gegner vor dir an den Füßen, reißt ihn hoch und schlägst ihn
  **fünfmal** abwechselnd links und rechts auf den Boden. Er wird dabei wirklich kopfüber über deinen Kopf
  geschleudert, und jeder Aufprall gibt eine Druckwelle. Zum Schluss schleuderst du ihn ein Stück weg. Das geht
  groß und klein, du kannst also als winzige Maus einen Eisengolem herumwirbeln.
* **Schrumpfen (X):** Du schrumpfst auf **1/16** deiner Größe, so klein wie Minecraft es erlaubt (knapp zwei
  Pixel hoch). Nochmal X macht dich wieder groß. Als Winzling **knabbert Linksklick einzelne Pixel aus Blöcken**
  statt den ganzen Block abzubauen. So kannst du Mauselöcher, Tunnel oder Muster in Blöcke fressen, und du
  läufst wirklich durch die Löcher. Ist ein Block ganz weggeknabbert, bekommst du ihn als Item.
  Du kannst Blöcke auch **aushöhlen und dich darin verstecken**: Knabber einen Tunnel hinein und eine Kammer
  aus, lauf hinein, und von außen sieht man nur den Block. Drinnen erstickst du nicht, und die Kamera schaut
  auch ganz nah an der Wand nicht durch die Pixel. Groß baust du ganz normal ganze Blöcke ab.

## Animationen

* **Kampf-Kombos für alle Spieler** (sieht man in F5 und bei anderen Spielern): Jeder Schlag ist ein
  eigener Move. Mit dem Schwert: Vorhand-Hieb, Rückhand, Überkopf-Hieb, Ausfallstich. Mit der Axt: beidhändiger
  Überkopf-Hieb und Rundumschlag. Mit den Fäusten: Jab, Cross mit der anderen Faust, Uppercut, Haken.
  Der Körper dreht sich mit, man macht einen Ausfallschritt und lehnt sich in den Schlag. Schwert- und
  Axtschläge ziehen eine leuchtende Schwung-Spur durch die Luft. Beim Abbauen bleibt der normale Schwung.
* **Homelander fliegt wie Superman:** Er legt sich in Flugrichtung. Fliegt er geradeaus, liegt er waagerecht
  mit der Faust nach vorn, beim Steigen steht er senkrecht mit der Faust nach oben, beim Schweben hängt er
  locker in der Luft.
* **A-Train** lehnt sich beim Supertempo nach vorn.

## Items und Blöcke

Im Inventar sieht man ein flaches Icon wie bei jedem Item. In der Hand sind Spritzen, Brecheisen
und Schild echte **3D-Modelle**: Die Spritzen haben Kolben, Glaskörper mit Serum und Nadel, das
Brecheisen eine gebogene Klaue.

Compound V, V-One, Mini V, Uran-Injektor, leere Spritze, Soldier Boys Schild (blockt wie ein normales
Schild), Butchers Brecheisen, Labor-Fliesen, Laborboden, Vought-Stahlplatte, Sicherheitsglas und
der Compound-V-Kühlschrank. Alles findest du im Kreativ-Tab „The Boys“.

## Befehle (für Operatoren)

```
/theboys power set <Spieler> <homelander|soldier_boy|a_train|butcher|minimaus|none>
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
