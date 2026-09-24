package de.glowcube.claudeai.brain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Text vorbereiten: klein, ohne Umlaute und Satzzeichen, Tippfehler korrigiert, Zahlen
 * erkannt - damit die Regeln einfach bleiben.
 */
public final class Language {

    private Language() {}

    /** Woerter, auf die Tippfehler korrigiert werden. */
    private static final Set<String> VOCAB = new HashSet<>(List.of((
            "faell faelle fell faellen komm her hierher folge folg folgen begleite begleiten bleib bleibe warte stehen stopp stop halt hoer aufhoeren "
            + "verteidige verteidigen beschuetze beschuetzen schuetze bewache aufpassen greif greife angreifen toete toeten "
            + "kill bekaempfe erledige besiege jage hol hole holen besorge besorgen sammel sammle sammeln bring bringe "
            + "baue bauen errichte abbauen abreissen crafte craften herstellen mach mache machen schmelze schmelzen brate "
            + "braten koche kochen gib gibst geben wirf ernte ernten pflanze pflanzen fackeln fackel licht leg lege "
            + "kiste truhe inventar teleportiere teleportier verschwinde tanze tanzen springe spring drehe dreh winke "
            + "haus huette turm zaun mauer farm bruecke pool plattform saeule unterstand villa klein gross riesig mittel "
            + "holz bretter stein steine bruchstein eisen gold diamant diamanten kohle sand erde kies glas fackeln werkbank "
            + "ofen kiste leiter tuer bett schwert spitzhacke axt schaufel hacke helm ruestung brustpanzer stiefel hose "
            + "fleisch steak brot essen hunger weizen karotte kartoffel wolle leder knochen faden federn "
            + "zombie zombies skelett skelette creeper spinne spinnen enderman hexe schleim monster kuh kuehe schwein "
            + "schweine schaf schafe huhn huehner hase "
            + "danke hallo tschuess bitte wieso warum wie viel wieviel spaet uhrzeit wetter status machst kannst "
            + "rueckgaengig nochmal zuhause basis merk merke vergiss alles hier dort mir mich "
            // Alltagswoerter, die nie "korrigiert" werden duerfen ("hast" ist kein "hase")
            + "hast habe hat dann wird wenn eine einen einem einer mein meine meinen dein deine deinen sein seine "
            + "noch auch oder aber bist sind kann will soll gerne heute jetzt schon sehr ganz nicht keine kein alle "
            + "dich euch unsere unser gute toll cool super nice mega geil echt haha okay klar genau doch einfach "
            + "schnell langsam zurueck weiter oben unten links rechts vorne hinten neben fuer ueber unter gehen gehe "
            + "lauf laufe renn zeig zeige sage weisst kennst magst liebst heisst bisschen etwas nichts mehr weniger "
            + "wieder damit dafuer darum wegen weil dass also trotzdem eigentlich vielleicht bestimmt wirklich eben "
            + "gerade name nimm nehme oeffne schlaf wach trink nacht morgen abend welt spiel minecraft server "
            + "spieler freund freundin bruder schwester papa mama stell setz sitz lieg mache machst hilfe helfen "
            + "brauche brauchst brauch danach anschliessend diesen dieses diese ort stelle lieblings wetter regnet "
            + "wirklich leben lebst alter alt jahre witz lustig langweilig traurig muede sorry entschuldigung liebe "
            + "sprich redest verstehst versteh lernen lern heissen nenn nenne").split(" ")));

    public static void learn(Collection<String> words) {
        for (String w : words) if (w.length() >= 4) VOCAB.add(w);
    }

    public static String normalize(String raw) {
        String s = raw.toLowerCase(Locale.ROOT)
                .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss");
        s = s.replaceAll("(?<![0-9])-(?![0-9])", " ");
        s = s.replaceAll("[^a-z0-9\\- ]", " ");
        return s.replaceAll("\\s+", " ").trim();
    }

    /** Tippfehler korrigieren; Spielernamen und Zahlen bleiben unangetastet. */
    public static String correct(String text, Collection<String> protectedWords) {
        String[] tokens = text.split(" ");
        List<String> out = new ArrayList<>();
        for (String t : tokens) {
            if (t.length() < 4 || VOCAB.contains(t) || protectedWords.contains(t) || t.matches("-?[0-9]+")) {
                out.add(t);
                continue;
            }
            // Gebeugte Formen ("kleines", "haeuser", "zombies") sind keine Tippfehler
            boolean inflected = false;
            for (String v : VOCAB) {
                if (v.length() >= 4 && t.startsWith(v)) {
                    inflected = true;
                    break;
                }
            }
            if (inflected) {
                out.add(t);
                continue;
            }
            int limit = t.length() <= 6 ? 1 : 2;
            String best = null;
            int bestD = limit + 1;
            int bestPrefix = -1;
            for (String v : VOCAB) {
                if (Math.abs(v.length() - t.length()) > limit) continue;
                int d = distance(t, v, limit);
                int prefix = commonPrefix(t, v);
                // Bei Gleichstand gewinnt das Wort mit dem laengeren gemeinsamen Anfang
                if (d < bestD || d == bestD && d <= limit && prefix > bestPrefix) {
                    bestD = d;
                    best = v;
                    bestPrefix = prefix;
                }
            }
            out.add(best != null ? best : t);
        }
        return String.join(" ", out);
    }

    private static int commonPrefix(String a, String b) {
        int n = Math.min(a.length(), b.length()), i = 0;
        while (i < n && a.charAt(i) == b.charAt(i)) i++;
        return i;
    }

    /** Levenshtein mit Abbruch, sobald die Grenze ueberschritten ist. */
    public static int distance(String a, String b, int limit) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            int rowMin = cur[0];
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
                rowMin = Math.min(rowMin, cur[j]);
            }
            if (rowMin > limit) return limit + 1;
            int[] tmp = prev;
            prev = cur;
            cur = tmp;
        }
        return prev[b.length()];
    }

    // ================================================================== Zahlen

    private static final Map<String, Integer> NUMBERS = Map.ofEntries(
            Map.entry("ein", 1), Map.entry("eine", 1), Map.entry("einen", 1), Map.entry("einem", 1), Map.entry("eins", 1),
            Map.entry("zwei", 2), Map.entry("drei", 3), Map.entry("vier", 4), Map.entry("fuenf", 5), Map.entry("sechs", 6),
            Map.entry("sieben", 7), Map.entry("acht", 8), Map.entry("neun", 9), Map.entry("zehn", 10), Map.entry("elf", 11),
            Map.entry("zwoelf", 12), Map.entry("dreizehn", 13), Map.entry("vierzehn", 14), Map.entry("fuenfzehn", 15),
            Map.entry("sechzehn", 16), Map.entry("siebzehn", 17), Map.entry("achtzehn", 18), Map.entry("neunzehn", 19),
            Map.entry("zwanzig", 20), Map.entry("dreissig", 30), Map.entry("vierzig", 40), Map.entry("fuenfzig", 50),
            Map.entry("sechzig", 60), Map.entry("siebzig", 70), Map.entry("achtzig", 80), Map.entry("neunzig", 90),
            Map.entry("hundert", 100), Map.entry("paar", 4), Map.entry("einige", 8), Map.entry("mehrere", 6),
            Map.entry("viele", 32), Map.entry("viel", 32), Map.entry("massig", 64), Map.entry("haufen", 32),
            Map.entry("stack", 64), Map.entry("stacks", 64), Map.entry("stapel", 64), Map.entry("dutzend", 12));

    private static final Pattern DIGITS = Pattern.compile("(?<![0-9a-z-])(\\d{1,4})(?![0-9])");
    private static final Pattern COMPOUND = Pattern.compile(
            "(?<![a-z])(ein|zwei|drei|vier|fuenf|sechs|sieben|acht|neun)und(zwanzig|dreissig|vierzig|fuenfzig|sechzig)");

    /** Erste Mengenangabe im Text, oder def. "alle" / "alles" liefert -1. */
    public static int number(String text, int def) {
        Matcher c = COMPOUND.matcher(text);
        if (c.find()) return NUMBERS.get(c.group(1)) + NUMBERS.get(c.group(2));
        if (text.matches(".*(?<![a-z])halbe[n]? (stack|stapel).*")) return 32;
        Matcher d = DIGITS.matcher(text);
        if (d.find()) {
            int n = Integer.parseInt(d.group(1));
            // "2 stacks"
            if (text.substring(d.end()).trim().startsWith("stack") || text.substring(d.end()).trim().startsWith("stapel")) n *= 64;
            return n;
        }
        for (String t : text.split(" ")) {
            if (t.equals("alle") || t.equals("alles") || t.equals("allen")) return -1;
            Integer n = NUMBERS.get(t);
            if (n != null && !(n == 1 && (t.equals("ein") || t.equals("eine") || t.equals("einen") || t.equals("einem")))) return n;
        }
        for (String t : text.split(" ")) {
            if (t.equals("ein") || t.equals("eine") || t.equals("einen") || t.equals("einem")) return 1;
        }
        return def;
    }

    private static final Pattern COORDS = Pattern.compile("(-?\\d{1,7}) (-?\\d{1,3}) (-?\\d{1,7})");

    /** Drei Zahlen hintereinander = Koordinaten. */
    public static int[] coords(String text) {
        Matcher m = COORDS.matcher(text);
        if (!m.find()) return null;
        return new int[] { Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)) };
    }
}
