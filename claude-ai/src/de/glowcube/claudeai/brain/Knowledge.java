package de.glowcube.claudeai.brain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Minecraft-Wissen fuer Fragen wie "wo finde ich Diamanten?" oder "wie baue ich ein Netherportal?". */
final class Knowledge {

    private Knowledge() {}

    private static final Map<Pattern, String> FACTS = new LinkedHashMap<>();

    private static void fact(String regex, String answer) {
        FACTS.put(Pattern.compile(regex), answer);
    }

    static {
        fact("diamant", "Diamanten findest du am besten ganz unten, so um Hoehe -58. Du brauchst mindestens eine Eisenspitzhacke. Sag 'hol mir Diamanten', dann such ich welche!");
        fact("eisen", "Eisenerz gibt's fast ueberall im Stein, besonders um Hoehe 16 und in Bergen. Mit Steinspitzhacke abbauen, dann im Ofen schmelzen.");
        fact("gold(?!en apfel)", "Gold liegt tief unten (um Hoehe -16) oder in Badlands-Biomen. Du brauchst eine Eisenspitzhacke.");
        fact("kohle", "Kohle ist das haeufigste Erz - in fast jedem Berg und jeder Hoehle. Eine Holzspitzhacke reicht.");
        fact("smaragd", "Smaragde gibt's nur in Berg-Biomen oder beim Handeln mit Dorfbewohnern.");
        fact("netherit|antiker schrott|ancient", "Netherit bekommst du aus antikem Schrott im Nether, so um Hoehe 15. Vier Schrott plus vier Gold ergeben einen Barren.");
        fact("nether ?portal|portal", "Ein Netherportal: Rahmen aus Obsidian, mindestens 4 breit und 5 hoch (Ecken duerfen fehlen), dann mit Feuerzeug anzuenden.");
        fact("obsidian", "Obsidian entsteht, wenn Wasser auf stehende Lava fliesst. Abbauen nur mit Diamantspitzhacke - dauert etwas.");
        fact("verzauber|zaubertisch", "Zaubertisch: 1 Buch, 2 Diamanten, 4 Obsidian. Mit 15 Buecherregalen drumherum bekommst du die besten Verzauberungen.");
        fact("brau|trank|traenke", "Fuer Traenke brauchst du einen Braustand (Lohenrute + Bruchstein), Wasserflaschen und Netherwarzen als Basis.");
        fact("creeper", "Creeper schleichen sich an und explodieren. Hau sie schnell und geh dann zurueck. Katzen vertreiben sie!");
        fact("enderman|endermen", "Schau Endermen nicht direkt an! Wenn doch: Stell dich unter einen zwei Bloecke hohen Unterstand oder ins Wasser.");
        fact("ender ?drache|drache", "Den Enderdrachen findest du im End. Zerstoer zuerst die Endkristalle auf den Saeulen, dann wird er schwach.");
        fact("end ?portal|ins end", "Das Endportal liegt in einer Festung. Wirf Enderaugen, sie fliegen in die richtige Richtung.");
        fact("dorfbewohner|villager|handel", "Dorfbewohner handeln, wenn sie einen Beruf haben - stell ihnen z.B. einen Lesepult oder eine Kompostkiste hin.");
        fact("schlaf|bett", "Ein Bett: 3 Wolle und 3 Bretter. Schlafen geht nur nachts und setzt deinen Spawnpunkt.");
        fact("farm|weizen|anbau", "Weizen waechst auf Ackerboden neben Wasser. Sag 'bau eine Farm', dann mache ich dir eine!");
        fact("redstone", "Redstone ist wie Strom in Minecraft. Findest du tief unten, Abbau mit Eisenspitzhacke.");
        fact("hunger|essen", "Das beste Essen am Anfang: Steaks oder Koteletts. Goldene Karotten sind noch besser!");
        fact("monster|nacht", "Nachts spawnen Monster im Dunkeln. Fackeln helfen - sag 'stell Fackeln auf'.");
        fact("wolf|hund", "Woelfe zaehmst du mit Knochen. Die helfen dann beim Kaempfen - so wie ich!");
        fact("katze", "Katzen zaehmst du mit rohem Fisch. Creeper haben Angst vor ihnen.");
        fact("pferd", "Ein Pferd zaehmst du, indem du immer wieder aufsteigst. Mit Sattel kannst du es lenken.");
        fact("elytra|fliegen", "Die Elytra findest du in Endschiffen hinter den Endstaedten. Mit Feuerwerk fliegst du richtig schnell.");
        fact("mending|reparatur", "Mending (Reparatur) repariert dein Werkzeug mit Erfahrung. Gibt's beim Angeln, in Truhen oder vom Bibliothekar.");
        fact("xp|erfahrung|level", "Erfahrung bekommst du fuers Kaempfen, Erze schmelzen und Handeln. Mob-Farmen sind am schnellsten.");
        fact("slime|schleim", "Schleime spawnen in Sumpfbiomen nachts und in bestimmten Chunks ganz unten.");
        fact("warden|waechter", "Der Waechter lebt in der Tiefen Dunkelheit. Sei leise und schleich! Kaempfen lohnt sich kaum.");
        fact("schild", "Ein Schild: 6 Bretter und 1 Eisenbarren. Rechtsklick haelt Pfeile und Creeper-Explosionen auf.");
        fact("biom|wuste|wueste|dschungel", "Es gibt ueber 60 Biome. Jedes hat eigene Bloecke und Mobs - erkunde sie!");
    }

    static String answer(String text) {
        for (Map.Entry<Pattern, String> e : FACTS.entrySet()) {
            if (e.getKey().matcher(text).find()) return e.getValue();
        }
        return null;
    }
}
