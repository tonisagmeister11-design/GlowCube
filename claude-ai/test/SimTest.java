import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.FakeWorld;
import org.bukkit.Fakes;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import de.glowcube.claudeai.ClaudeAIPlugin;
import de.glowcube.claudeai.npc.Npc;

/**
 * Spielt eine ganze Runde mit Claude in einer simulierten Welt durch - mit echten
 * Chat-Saetzen - und prueft, ob am Ende das Richtige passiert ist.
 */
public final class SimTest {

    static FakeWorld w;
    static Fakes.FPlayer toni;
    static ClaudeAIPlugin plugin;
    static int printed;
    static int failures;
    static final List<String> results = new ArrayList<>();

    public static void main(String[] args) {
        w = new FakeWorld();
        Bukkit.world = w;
        for (int[] t : new int[][] { { 8, 3 }, { 12, -6 }, { -10, 9 }, { 15, 12 }, { -14, -12 }, { 20, -2 }, { -3, 16 } }) w.tree(t[0], t[1]);
        for (int x = 5; x <= 6; x++) for (int y = 58; y <= 59; y++) for (int z = 5; z <= 6; z++) w.put(x, y, z, "COAL_ORE");
        for (int x = -6; x <= -5; x++) for (int z = -4; z <= -3; z++) w.put(x, 56, z, "IRON_ORE");
        w.put(3, 65, -3, "CHEST");

        toni = new Fakes.FPlayer(w, new Location(w, 0.5, 65, 0.5, 0, 0), "Toni");
        w.entities.add(toni);
        Bukkit.players.add(toni);

        plugin = new ClaudeAIPlugin();
        plugin.onEnable();
        Bukkit.advance(50);

        step("/spawn claude");
        plugin.summon(toni);
        Bukkit.advance(40);
        check("Claude ist da", plugin.npc().isSpawned() && near(npcLoc(), toni.getLocation(), 4));
        check("Skin per /data gesetzt", Bukkit.commands.stream().anyMatch(c -> c.contains("entity/player/wide/steve")));

        chat("Claude, folge mir");
        check("folgt", plugin.npc().mode() == Npc.Mode.FOLLOW);
        for (int i = 0; i < 20; i++) {
            toni.teleport(toni.getLocation().add(0.8, 0, 0));
            Bukkit.advance(5);
        }
        Bukkit.advance(80);
        check("ist hinterhergelaufen", near(npcLoc(), toni.getLocation(), 5));

        chat("hol mir 5 holz");
        runUntilIdle(4000);
        check("Toni hat 5 Holz bekommen", logs(toni) >= 5);

        chat("claude mach mir eine steinspitzhacke");
        runUntilIdle(12000);
        check("Toni hat eine Steinspitzhacke", toni.inv.count("STONE_PICKAXE") == 1);

        toni.teleport(new Location(w, 30.5, 65, 30.5, 0, 0));
        chat("bau ein kleines haus");
        runUntilIdle(6000);
        int doors = count("OAK_DOOR"), beds = count("WHITE_BED"), tables = count("CRAFTING_TABLE"), stairs = count("SPRUCE_STAIRS");
        check("Haus steht (Tuer " + doors + ", Bett " + beds + ", Werkbank " + tables + ", Dach " + stairs + ")",
                doors == 2 && beds == 2 && tables >= 1 && stairs > 20);

        chat("mach das rueckgaengig");
        runUntilIdle(3000);
        check("Haus ist wieder weg", count("OAK_DOOR") == 0 && count("SPRUCE_STAIRS") == 0);

        Fakes.FLiving zombie = (Fakes.FLiving) w.spawnEntity(toni.getLocation().add(6, 0, 3), EntityType.valueOf("ZOMBIE"));
        chat("greif den zombie an");
        runUntilIdle(1500);
        check("Zombie besiegt", !zombie.isValid());

        chat("verteidige mich");
        check("Beschuetzer-Modus", plugin.npc().mode() == Npc.Mode.GUARD);
        Fakes.FLiving z2 = (Fakes.FLiving) w.spawnEntity(toni.getLocation().add(-5, 0, 2), EntityType.valueOf("ZOMBIE"));
        Bukkit.advance(600);
        check("Angreifer von selbst erledigt", !z2.isValid());

        toni.teleport(new Location(w, 4.5, 65, 1.5, 0, 0));
        chat("hol mir 3 kohle");
        runUntilIdle(12000);
        check("Toni hat Kohle (aus dem Boden gegraben)", toni.inv.count("COAL") >= 3);

        chat("mach mir fackeln");
        runUntilIdle(4000);
        check("Fackeln gecraftet und uebergeben", toni.inv.count("TORCH") >= 4);

        toni.teleport(new Location(w, 2.5, 65, -1.5, 0, 0));
        chat("hol 4 holz und dann leg alles in die kiste");
        runUntilIdle(8000);
        var chest = ((Fakes.FBlock) w.getBlockAt(3, 65, -3)).getInventory();
        check("Kiste befuellt: " + chest, chest.getContents().length > 0 && chest.firstEmpty() != 0);

        // Gespraech und Gedaechtnis
        chat("wie crafte ich eine fackel");
        check("Rezept erklaert", lastSaid().contains("Kohle") || saidSince("Kohle"));
        chat("merk dir diesen ort als basis");
        check("Ort gemerkt", plugin.brain().memory().places.containsKey("basis"));
        toni.teleport(new Location(w, -20.5, 65, 10.5, 0, 0));
        chat("geh zur basis");
        runUntilIdle(3000);
        check("zur Basis gelaufen", near(npcLoc(), new Location(w, 2.5, 65, -1.5), 4));
        chat("wenn ich hopp sage meine ich spring");
        plugin.brain().onChat(toni, "hopp");
        Bukkit.advance(2);
        check("gelerntes Wort verstanden", plugin.npc().currentTask() != null && plugin.npc().currentTask().label().contains("Quatsch"));
        Bukkit.advance(60);
        chat("folg mier");
        check("Tippfehler 'folg mier' verstanden", plugin.npc().mode() == Npc.Mode.FOLLOW);
        chat("klaude wie gehts dir");
        chat("was kannst du");
        chat("ich heisse Toniboy");
        chat("wie spaet ist es");
        chat("danke claude du bist toll");
        chat("claude erzaehl einen witz");
        chat("claude bla blub fnord");
        Bukkit.advance(100);
        flush();

        // ------------------------------------------------------------ Sprachtest: viele Formulierungen
        System.out.println();
        System.out.println("==================== Sprachtest ====================");
        Fakes.FPlayer max = new Fakes.FPlayer(w, toni.getLocation().add(3, 0, 0), "Max");
        w.entities.add(max);
        Bukkit.players.add(max);
        understand("claude komm her", () -> taskIs("komme zu"));
        understand("claude kannst du mir bitte 10 steine holen", () -> saidAny("Steine kommt"));
        understand("claude fael ein paar baeume", () -> saidAny("Holz kommt"));
        understand("claude bau mir einen turm aus stein", () -> saidAny("Turm"));
        understand("claude bau ein kleines haus", () -> saidAny("kleines Haus"));
        understand("claude bau eine farm", () -> saidAny("Farm"));
        understand("claude bau einen zaun um uns", () -> saidAny("Zaun"));
        understand("claude toete alle monster", () -> taskIs("kaempfe") || saidAny("keine Monster"));
        understand("claude beschuetze max", () -> plugin.npc().mode() == Npc.Mode.GUARD && plugin.npc().modePlayer() == max);
        understand("claude verteidige meinen freund max", () -> plugin.npc().mode() == Npc.Mode.GUARD && plugin.npc().modePlayer() == max);
        understand("claude folge max", () -> plugin.npc().mode() == Npc.Mode.FOLLOW && plugin.npc().modePlayer() == max);
        understand("claude was hast du dabei", () -> saidAny("Ich habe", "leer"));
        understand("claude wo bist du", () -> saidAny("Ich bin bei"));
        understand("claude ernte das feld", () -> saidAny("ernte"));
        understand("claude stell fackeln auf", () -> saidAny("hell"));
        understand("claude mach mir ein eisenschwert", () -> saidAny("Eisenschwert kommt"));
        understand("claude ich hab hunger", () -> saidAny("iss", "essen", "Tiere", "Rindfleisch", "Steaks", "Koteletts", "Hammel", "Haehnchen"));
        understand("claude bleib hier", () -> plugin.npc().mode() == Npc.Mode.STAY);
        understand("claude hoer auf", () -> plugin.npc().mode() == Npc.Mode.IDLE && !plugin.npc().busy());
        understand("claude hallo", () -> saidAny("Hey", "Hallo", "Hi", "Servus"));
        understand("claude wer bist du", () -> saidAny("KI-Begleiterin"));
        understand("claude wo finde ich diamanten", () -> saidAny("-58"));
        understand("claude wie baue ich ein netherportal", () -> saidAny("Obsidian"));
        understand("claude tanz mal", () -> taskIs("Quatsch"));
        understand("claude hol mir 3 brot", () -> saidAny("Brot kommt", "Weizen"));
        understand("claude greif max an", () -> saidAny("keine Spieler", "mache ich nicht"));
        understand("claude greif mich an", () -> saidAny("Niemals"));
        understand("claude gib mir alles", () -> saidAny("Kommt sofort", "bring", "unterwegs", "nichts dabei"));
        understand("claude wie spaet ist es", () -> saidAny("Uhr"));
        understand("claude was ist dein lieblingsblock", () -> saidAny("Diamant"));
        understand("claude hol 5 holz und dann bau ein haus", () -> saidAny("Holz kommt") && saidAny("Haus"));
        understand("claude bau eisen ab", () -> saidAny("Eisen kommt"));
        understand("claude jag kuehe", () -> taskIs("kaempfe", "jage") || saidAny("keine Kuh"));
        understand("claude mach das rueckgaengig", () -> saidAny("reisse", "nichts gebaut"));
        understand("claude teleportier dich zu mir", () -> saidAny("Zack"));
        understand("claude merk dir dass ich gerne baue", () -> saidAny("gemerkt"));
        understand("claude was weisst du ueber mich", () -> saidAny("gerne baue"));
        understand("claude verschwinde", () -> saidAny("bis spaeter", "Tschuess"));

        System.out.println();
        System.out.println("==================== Ergebnis ====================");
        results.forEach(System.out::println);
        System.out.println(failures == 0 ? "ALLES BESTANDEN" : failures + " FEHLGESCHLAGEN");
        System.exit(failures == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------ Hilfen

    static int mark;

    static void understand(String msg, java.util.function.BooleanSupplier ok) {
        flush();
        mark = toni.chat.size();
        System.out.println("<Toni> " + msg);
        plugin.brain().onChat(toni, msg);
        Bukkit.advance(3);
        boolean early = ok.getAsBoolean();
        Bukkit.advance(60);
        flush();
        check("versteht: '" + msg + "'", early || ok.getAsBoolean());
        plugin.npc().stopAll();
    }

    static boolean saidAny(String... words) {
        for (int i = mark; i < toni.chat.size(); i++) {
            for (String w0 : words) if (toni.chat.get(i).contains(w0)) return true;
        }
        return false;
    }

    static boolean taskIs(String... parts) {
        var t = plugin.npc().currentTask();
        if (t == null) return false;
        for (String p : parts) if (t.label().contains(p)) return true;
        return false;
    }

    static void chat(String msg) {
        flush();
        System.out.println("<Toni> " + msg);
        plugin.brain().onChat(toni, msg);
        Bukkit.advance(40);
        flush();
    }

    static void step(String s) {
        System.out.println("<Toni> " + s);
    }

    static void runUntilIdle(int maxTicks) {
        long start = System.currentTimeMillis();
        for (int t = 0; t < maxTicks; t += 10) {
            Bukkit.advance(10);
            flush();
            if (!plugin.npc().busy() && t > 40) break;
        }
        Bukkit.advance(40);
        flush();
        System.out.println("   (" + (System.currentTimeMillis() - start) + " ms, Inventar Claude: "
                + plugin.npc().getInventory() + ")");
    }

    static void flush() {
        while (printed < toni.chat.size()) System.out.println("   " + toni.chat.get(printed++));
    }

    static String lastSaid() {
        return toni.chat.isEmpty() ? "" : toni.chat.get(toni.chat.size() - 1);
    }

    static boolean saidSince(String word) {
        for (int i = Math.max(0, toni.chat.size() - 6); i < toni.chat.size(); i++) if (toni.chat.get(i).contains(word)) return true;
        return false;
    }

    static Location npcLoc() {
        return plugin.npc().location();
    }

    static boolean near(Location a, Location b, double d) {
        return a != null && b != null && a.distance(b) <= d;
    }

    static int logs(Fakes.FPlayer p) {
        int n = 0;
        for (var s : p.inv.getContents()) if (s != null && s.getType().name().endsWith("_LOG")) n += s.getAmount();
        return n;
    }

    static int count(String name) {
        Material m = Material.getMaterial(name);
        int n = 0;
        for (var d : w.blocks.values()) if (d.getMaterial() == m) n++;
        return n;
    }

    static void check(String what, boolean ok) {
        String line = (ok ? "  OK    " : "  FEHLT ") + what;
        results.add(line);
        System.out.println(">> " + line.trim());
        if (!ok) failures++;
    }

    @SuppressWarnings("unused")
    static long entities(String type) {
        return w.entities.stream().filter(e -> ((Entity) e).getType().name().equals(type)).count();
    }
}
