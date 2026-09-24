package de.glowcube.claudeai.brain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import de.glowcube.claudeai.ClaudeAIPlugin;
import de.glowcube.claudeai.build.Blueprint;
import de.glowcube.claudeai.build.Builders;
import de.glowcube.claudeai.build.Style;
import de.glowcube.claudeai.build.UndoStore;
import de.glowcube.claudeai.npc.Npc;
import de.glowcube.claudeai.plan.Planner;
import de.glowcube.claudeai.plan.Recipes;
import de.glowcube.claudeai.task.BuildTask;
import de.glowcube.claudeai.task.CollectItemsTask;
import de.glowcube.claudeai.task.CombatTask;
import de.glowcube.claudeai.task.ContainerTask;
import de.glowcube.claudeai.task.EmoteTask;
import de.glowcube.claudeai.task.GiveTask;
import de.glowcube.claudeai.task.GotoTask;
import de.glowcube.claudeai.task.HarvestTask;
import de.glowcube.claudeai.task.SimpleTasks;
import de.glowcube.claudeai.task.SmeltTask;
import de.glowcube.claudeai.task.Task;
import de.glowcube.claudeai.task.TorchTask;
import de.glowcube.claudeai.task.UndoTask;
import de.glowcube.claudeai.world.Mats;

/**
 * Claudes Sprachverstaendnis - ohne KI-Dienst, alles lokal.
 *
 * <p>Ablauf je Chatnachricht: normalisieren, Tippfehler korrigieren, gelernte Woerter
 * ersetzen, in Teilsaetze zerlegen ("hol Holz und dann bau ein Haus"), jeden Teilsatz
 * gegen die Regeln pruefen. Dazu kommen Gespraechskontext (wer redet gerade mit mir,
 * offene Ja/Nein-Fragen, "nochmal", "ihn/sie") und ein Gedaechtnis.
 */
public final class Brain {

    private final ClaudeAIPlugin plugin;
    private final Npc npc;
    private final Memory memory;
    private final UndoStore undo = new UndoStore();

    private record Pending(UUID player, Runnable yes, Runnable no, long until) {}

    private Pending pending;
    private String lastCommand;
    private UUID lastMentioned;

    private static final Map<String, Pattern> PATTERNS = new ConcurrentHashMap<>();

    public Brain(ClaudeAIPlugin plugin, Npc npc, Memory memory) {
        this.plugin = plugin;
        this.npc = npc;
        this.memory = memory;
    }

    public UndoStore undo() {
        return undo;
    }

    public Memory memory() {
        return memory;
    }

    // ================================================================== Kontext eines Teilsatzes

    private final class Ctx {
        final Player p;
        final String text;
        final boolean addressed;
        boolean replace;

        Ctx(Player p, String text, boolean addressed, boolean replace) {
            this.p = p;
            this.text = text;
            this.addressed = addressed;
            this.replace = replace;
        }

        boolean has(String regex) {
            return pattern("(?<![a-z])(" + regex + ")").matcher(text).find();
        }

        Matcher match(String regex) {
            Matcher m = pattern(regex).matcher(text);
            return m.find() ? m : null;
        }

        int number(int def) {
            return Language.number(text, def);
        }

        String who() {
            String nick = memory.nicknames.get(p.getName().toLowerCase(Locale.ROOT));
            return nick != null ? nick : p.getName();
        }

        /** Nur ausdruecklich genannte Spieler (Name oder ihn/ihm), keine Pronomen fuer den Sprecher. */
        Player namedPlayer() {
            Player p0 = player();
            return p0 == p && !named ? null : p0;
        }

        private boolean named;

        /** Genannter Spieler: Name im Text, "mir/mich" = Sprecher, "ihm/ihn/ihr" = zuletzt Genannter. */
        Player player() {
            named = false;
            for (Player o : Bukkit.getOnlinePlayers()) {
                String n = o.getName().toLowerCase(Locale.ROOT);
                for (String t : text.split(" ")) {
                    if (t.equals(n) || n.length() >= 4 && t.length() >= 4 && Language.distance(t, n, 1) <= 1
                            || n.length() >= 5 && t.length() >= 4 && n.startsWith(t)) {
                        lastMentioned = o.getUniqueId();
                        named = true;
                        return o;
                    }
                }
            }
            if (has("ihm|ihn|ihr(?! [a-z])")) {
                Player last = lastMentioned == null ? null : Bukkit.getPlayer(lastMentioned);
                if (last != null) {
                    named = true;
                    return last;
                }
            }
            if (has("mir|mich|uns|ich")) return p;
            return null;
        }
    }

    private static Pattern pattern(String regex) {
        return PATTERNS.computeIfAbsent(regex, Pattern::compile);
    }

    // ================================================================== Eingang

    public void onChat(Player p, String raw) {
        String text = Language.normalize(raw);
        if (text.isEmpty()) return;
        boolean addressed = mentionsName(text);
        if (addressed) text = removeName(text);

        if (!npc.isSpawned()) {
            if (addressed && p.hasPermission("claudeai.spawn")
                    && pattern("(?<![a-z])(komm|spawn|erschein|wo bist du|bist du da|hilf)").matcher(text).find()) {
                plugin.summon(p);
            }
            return;
        }

        boolean pendingForMe = pending != null && pending.player().equals(p.getUniqueId()) && npc.ticks() < pending.until();
        boolean listening = npc.isListeningTo(p) || pendingForMe;
        if (plugin.settings().requireName && !addressed && !pendingForMe) return;
        if (!addressed && !listening) return;

        npc.setPartner(p);
        npc.setActor(p);
        if (text.isBlank()) {
            reply(p, pick(List.of("Ja, %p?", "Was gibt's, %p?", "Ich hoere!", "Hm?")));
            return;
        }

        text = applyAliases(text);
        Set<String> names = new HashSet<>();
        for (Player o : Bukkit.getOnlinePlayers()) names.add(o.getName().toLowerCase(Locale.ROOT));
        names.addAll(memory.places.keySet());
        text = Language.correct(text, names);

        List<String> clauses = split(text);
        boolean understood = false;
        boolean first = true;
        for (String clause : clauses) {
            Ctx c = new Ctx(p, clause, addressed, first);
            Boolean result = handle(c);
            if (result == null) continue;
            understood = true;
            if (result) {
                first = false;
                lastCommand = clause;
            }
        }
        if (!understood && addressed) {
            String answer = Knowledge.answer(text);
            reply(p, answer != null ? answer : pick(Lexicon.CONFUSED));
        }
    }

    /** Offene Ja/Nein-Frage stellen. */
    public void ask(Player p, String question, Runnable yes, Runnable no) {
        pending = new Pending(p.getUniqueId(), yes, no, npc.ticks() + 20 * 60);
        npc.setPartner(p);
        npc.say(question);
    }

    private boolean mentionsName(String text) {
        String key = Language.normalize(plugin.settings().name);
        for (String t : text.split(" ")) {
            if (t.equals(key) || key.length() >= 5 && Language.distance(t, key, 1) <= 1) return true;
            if (key.equals("claude") && (t.equals("klaude") || t.equals("klaud") || t.equals("cloud") || t.equals("clode")
                    || t.equals("klodi") || t.equals("claudi"))) return true;
        }
        return false;
    }

    private String removeName(String text) {
        String key = Language.normalize(plugin.settings().name);
        List<String> out = new ArrayList<>();
        for (String t : text.split(" ")) {
            boolean isName = t.equals(key) || key.length() >= 5 && Language.distance(t, key, 1) <= 1
                    || key.equals("claude") && t.matches("klaude|klaud|cloud|clode|klodi|claudi");
            if (!isName) out.add(t);
        }
        String s = String.join(" ", out).trim();
        return s.replaceAll("^(hey|hei|ey|yo|du|oh|also|ok|okay)( |$)", "").trim();
    }

    private String applyAliases(String text) {
        for (Map.Entry<String, String> a : memory.aliases.entrySet()) {
            if (text.equals(a.getKey()) || text.contains(a.getKey())) text = text.replace(a.getKey(), a.getValue());
        }
        return text;
    }

    private static final Pattern NEXT_VERB = Pattern.compile("^(komm|folg|bau|hol|geh|lauf|gib|mach|craft|greif|toet|ernte|stell|"
            + "leg|spring|tanz|verteidig|beschuetz|bleib|warte|sammel|sammle|such|bring|faell|fell|hack|schmelz|brat|jag|bewach|"
            + "tp|teleport|pflanz|reiss|zeig|merk|dreh|wink|kill|besorg|farm|pass auf|lauf)");

    /** "hol holz und dann bau ein haus" -> zwei Teilsaetze. */
    private List<String> split(String text) {
        List<String> parts = new ArrayList<>();
        for (String piece : text.split(" (und dann|und danach|danach|anschliessend|dann) ")) {
            String[] und = piece.split(" und ");
            StringBuilder cur = new StringBuilder(und[0]);
            for (int i = 1; i < und.length; i++) {
                if (NEXT_VERB.matcher(und[i]).find()) {
                    parts.add(cur.toString().trim());
                    cur = new StringBuilder(und[i]);
                } else {
                    cur.append(" und ").append(und[i]);
                }
            }
            parts.add(cur.toString().trim());
        }
        parts.removeIf(String::isBlank);
        return parts;
    }

    private void reply(Player p, String message) {
        String who = memory.nicknames.getOrDefault(p.getName().toLowerCase(Locale.ROOT), p.getName());
        String text = message.replace("%p", who);
        String[] lines = text.split("\n");
        int delay = 6 + npc.random().nextInt(10);
        for (String line : lines) {
            npc.sayLater(line, delay);
            delay += 12;
        }
        // Meldungen der Aufgaben kommen erst nach der Antwort
        npc.holdMessages(delay);
    }

    private String pick(List<String> lines) {
        return Lexicon.pick(lines, npc.random());
    }

    private boolean mayCommand(Ctx c) {
        if (plugin.settings().isOwner(c.p.getName()) && c.p.hasPermission("claudeai.use")) return true;
        if (npc.cooldown("not-owner-" + c.p.getName(), 600)) {
            reply(c.p, "Sorry %p, Befehle nehme ich nur von " + String.join(", ", plugin.settings().owners) + " an.");
        }
        return false;
    }

    // ================================================================== Regeln

    /**
     * null = nicht verstanden, false = verstanden (nur Gespraech), true = Befehl ausgefuehrt.
     */
    private Boolean handle(Ctx c) {
        String t = c.text;

        // --- offene Ja/Nein-Frage
        if (pending != null && pending.player().equals(c.p.getUniqueId()) && npc.ticks() < pending.until()) {
            if (c.has("^(ja|jo|jap|jup|jawohl|klar|okay|ok|gerne|gern|mach|yes|sicher|genau|bitte|unbedingt|na klar)")
                    || t.matches("^(ja|jo|klar|ok|okay|gerne|bitte|mach mal|mach das|yes)( .*)?$")) {
                Pending pd = pending;
                pending = null;
                reply(c.p, pick(Lexicon.OK));
                if (pd.yes() != null) pd.yes().run();
                return true;
            }
            if (t.matches("^(nein|ne|nee|noe|nicht|lass mal|lass|no|niemals|auf keinen fall)( .*)?$")) {
                Pending pd = pending;
                pending = null;
                reply(c.p, "Okay, dann nicht.");
                if (pd.no() != null) pd.no().run();
                return false;
            }
        }

        // --- Woerter beibringen
        Matcher learn = c.match("wenn ich (?:sage |schreibe )?(.+?) (?:sage |schreibe )?(?:dann )?(?:meine ich|heisst das|bedeutet das|sollst du|machst du|mach) (.+)");
        if (learn != null) {
            String from = learn.group(1).trim(), to = learn.group(2).trim();
            memory.aliases.put(from, to);
            memory.save();
            Language.learn(List.of(from.split(" ")));
            reply(c.p, "Okay, gemerkt: Wenn du '" + from + "' sagst, meinst du '" + to + "'.");
            return false;
        }
        Matcher forget = c.match("^vergiss (?:das wort |den ort |den wegpunkt )?(.+)");
        if (forget != null && !t.contains("dass")) {
            String what = forget.group(1).trim();
            boolean gone = memory.aliases.remove(what) != null | memory.places.remove(Memory.placeKey(what)) != null;
            memory.save();
            reply(c.p, gone ? "Okay, '" + what + "' hab ich vergessen." : "Das kenne ich gar nicht.");
            return false;
        }

        // --- aufhoeren
        if (t.matches("^(stop+|stopp+|halt|hoer auf|hoer mal auf|aufhoeren|abbrechen|lass das|lass es|genug|schluss|cancel|stopp mal)( .*)?$")
                || c.has("hoer auf|aufhoeren|abbrechen")) {
            npc.stopAll();
            npc.setMode(Npc.Mode.IDLE, null, null);
            reply(c.p, pick(List.of("Okay, ich hoer auf.", "Alles klar, gestoppt.", "Bin still.")));
            return true;
        }

        // --- weggehen
        if (c.has("verschwinde|geh weg|hau ab|du kannst gehen|despawn|geh schlafen|feierabend|mach feierabend|log dich aus")) {
            if (!mayCommand(c)) return false;
            reply(c.p, pick(List.of("Okay, bis spaeter! Ruf mich mit /spawn claude.", "Tschuess! Wenn du mich brauchst: /spawn claude.")));
            Bukkit.getScheduler().runTaskLater(plugin, npc::despawn, 40);
            return true;
        }

        // --- Hilfe
        if (c.has("was kannst du|hilfe|befehle|help|was geht alles|was kann ich dir sagen|anleitung|kommandos")) {
            reply(c.p, "Ich kann ganz viel! Zum Beispiel:\n"
                    + "- 'folge mir', 'komm her', 'bleib hier', 'verteidige mich', 'beschuetze Max'\n"
                    + "- 'hol mir 20 Holz', 'bau Eisen ab', 'hol mir Diamanten'\n"
                    + "- 'mach mir eine Eisenspitzhacke' - ich besorge alles selbst\n"
                    + "- 'bau ein grosses Haus aus Stein', 'bau einen Turm / Zaun / eine Farm / Bruecke / Pool'\n"
                    + "- 'greif die Zombies an', 'toete alle Monster', 'jag Kuehe'\n"
                    + "- 'ernte das Feld', 'stell Fackeln auf', 'leg alles in die Kiste', 'gib mir alles'\n"
                    + "- 'merk dir diesen Ort als Basis', 'geh zur Basis', 'mach das rueckgaengig'\n"
                    + "- Fragen wie 'wo finde ich Diamanten?' oder 'wie crafte ich ein Schild?'");
            return false;
        }

        // --- nochmal
        if (t.matches("^(nochmal|noch mal|noch einmal|wiederhol das|das gleiche nochmal|nochmals|again|mehr davon|noch mehr)( .*)?$")) {
            if (lastCommand == null) {
                reply(c.p, "Nochmal was? Ich hab noch nichts gemacht.");
                return false;
            }
            return handle(new Ctx(c.p, lastCommand, c.addressed, c.replace));
        }

        // --- Wissen: "wie crafte ich ..." (vor den Befehlen, sonst wird "mach" ein Auftrag)
        if (c.has("wie (crafte|baue|mache|stelle|bekomme|kriege|kann|craftet|baut|macht)|rezept|wo finde|wo gibt es|wo bekomme|wo kriege")) {
            return answerHowTo(c);
        }

        // --- Orte merken (vor "merk dir, dass ...")
        Boolean place = places(c);
        if (place != null) return place;

        // --- Teleport
        if (c.has("(tp|teleportier\\w*|beam\\w*) (dich )?(zu mir|her|hier)|tp mich")) {
            if (!mayCommand(c)) return false;
            npc.teleportNear(c.p.getLocation());
            reply(c.p, "Zack, da bin ich!");
            return true;
        }

        // --- Bewegung
        if (c.has("folg|begleit|komm mit|lauf mir nach|hinter mir her|mitkommen|follow|folgen")) {
            if (!mayCommand(c)) return false;
            Player target = c.player();
            if (target == null) target = c.p;
            npc.stopAll();
            npc.setMode(Npc.Mode.FOLLOW, target, null);
            reply(c.p, target == c.p ? pick(List.of("Ich folge dir!", "Bin hinter dir!", "Na los, ich komm mit!"))
                    : "Okay, ich folge " + target.getName() + ".");
            return true;
        }
        if (c.has("bleib|warte(?! auf (?!mich))|steh still|stehen bleiben|nicht bewegen|stay|halt still|rueher dich nicht|bleibst")) {
            if (!mayCommand(c)) return false;
            npc.stopAll();
            npc.setMode(Npc.Mode.STAY, null, npc.location());
            reply(c.p, pick(List.of("Ich warte hier.", "Okay, ich bleib hier stehen.", "Ich ruehr mich nicht vom Fleck.")));
            return true;
        }
        if (c.has("komm(?! mit)|hierher|her zu mir|zu mir|come|herkommen|beeil dich")) {
            if (!mayCommand(c)) return false;
            Player target = c.p;
            npc.setMode(Npc.Mode.IDLE, null, null);
            npc.run(List.of(new GotoTask(() -> target.isOnline() ? target.getLocation() : null, 2.5, true, "komme zu " + target.getName())), true);
            reply(c.p, pick(List.of("Komme!", "Bin unterwegs!", "Bin gleich da!")));
            return true;
        }

        // --- Beschuetzen
        if (c.has("verteidig|beschuetz|schuetz|pass auf|aufpassen|bewach|leibwaech|bodyguard|protect|defend|rette")) {
            if (!mayCommand(c)) return false;
            Player target = c.player();
            Location where = null;
            if (target == null && c.has("hier|diesen ort|das haus|die basis|basis|zuhause|die farm|das dorf")) {
                Location saved = findPlace(t);
                where = saved != null ? saved : c.p.getLocation();
            }
            npc.stopAll();
            if (where != null) {
                npc.setMode(Npc.Mode.GUARD, null, where);
                npc.run(List.of(new GotoTask(() -> npc.modeLocation(), 2, true, "gehe auf Posten")), true);
                reply(c.p, "Ich bewache diesen Ort. Kein Monster kommt hier rein!");
            } else {
                if (target == null) target = c.p;
                npc.setMode(Npc.Mode.GUARD, target, null);
                reply(c.p, target == c.p ? pick(List.of("Ich pass auf dich auf - niemand tut dir was!", "Keine Sorge, ich beschuetze dich!"))
                        : "Okay, ich beschuetze " + target.getName() + "!");
            }
            return true;
        }

        // --- Kaempfen
        if (c.has("greif|angreif|toet|kill|bekaempf|erledig|besieg|attack|vernicht|metzel|jag|jagen|schlachte|hau (den|die|das|alle)|schlag (den|die|das|alle)|mach (den|die|das|alle) (platt|kalt|fertig|tot)")) {
            return attack(c);
        }

        // --- Bauen / Abreissen
        if (c.has("rueckgaengig|reiss|abreissen|undo|mach (das|den|die|es) (wieder )?(weg|kaputt)|entfern (das|den|die) (haus|turm|bau|zaun|mauer|farm|bruecke|pool)")) {
            if (!mayCommand(c)) return false;
            UndoStore.Entry entry = undo.pop();
            if (entry == null) {
                reply(c.p, "Ich hab noch nichts gebaut, was ich abreissen koennte.");
                return false;
            }
            npc.run(List.of(new UndoTask(entry)), c.replace);
            reply(c.p, "Okay, ich reisse " + entry.name() + " wieder ab.");
            return true;
        }
        Boolean build = build(c);
        if (build != null) return build;

        // --- Feld, Licht, Aufraeumen
        if (c.has("ernte|ernten|abernten|harvest")) {
            if (!mayCommand(c)) return false;
            npc.run(List.of(new HarvestTask(HarvestTask::isCrop, null, -1)), c.replace);
            reply(c.p, "Ich ernte alles, was reif ist, und pflanze gleich neu an.");
            return true;
        }
        if ((c.has("fackel|licht|beleucht|hell|lampe")) && c.has("stell|setz|platzier|verteil|mach|bau|sorg|auf|beleucht|hell|licht an")
                && !c.has("mir|uns|craft|herstell|bastel|gib|hol|besorg")) {
            if (!mayCommand(c)) return false;
            List<Task> tasks = new ArrayList<>();
            Material torch = Mats.get("TORCH");
            if (plugin.settings().buildNeedsMaterials && npc.count(torch) < 8) {
                Planner.Result plan = Planner.obtain(npc, torch, 16);
                if (plan.ok()) tasks.addAll(plan.tasks());
            }
            tasks.add(new TorchTask(14));
            npc.run(tasks, c.replace);
            reply(c.p, "Ich mach's hell! Fackeln kommen.");
            return true;
        }
        if (c.has("aufheben|einsammeln|aufsammeln|heb (alles|das|die sachen|das zeug|die items) auf|sammel (die sachen|das zeug|die items|alles) (auf|ein)|items auf")) {
            if (!mayCommand(c)) return false;
            npc.run(List.of(new CollectItemsTask(16)), c.replace);
            reply(c.p, "Ich sammel alles ein!");
            return true;
        }
        if (c.has("kiste|truhe|fass|chest") && c.has("leg|pack|tu |verstau|lager|stopf|rein|einlager|in die")) {
            if (!mayCommand(c)) return false;
            ItemFilter f = filter(c, true);
            npc.run(List.of(new ContainerTask(true, f.match, f.count)), c.replace);
            reply(c.p, "Ich raeume " + f.label + " in die Kiste.");
            return true;
        }
        if (c.has("aus (der|die|dem) (kiste|truhe|fass)")) {
            if (!mayCommand(c)) return false;
            ItemFilter f = filter(c, true);
            npc.run(List.of(new ContainerTask(false, f.match, f.count)), c.replace);
            reply(c.p, "Ich schau mal in die Kiste.");
            return true;
        }

        // --- Geben
        if (c.has("gib|gibst|geb mir|wirf|schmeiss|reich|ueberreich|her damit|schenk")) {
            return give(c);
        }
        // --- Hunger
        if (c.has("hunger|hungrig|was zu essen|verhunger|essen fuer mich|ich brauche essen")) {
            return feed(c);
        }
        // --- Schmelzen / Braten
        if (c.has("schmelz|brat|koch|grill|brenn")) {
            Boolean smelt = smelt(c);
            if (smelt != null) return smelt;
        }
        // --- Beschaffen und Craften
        Boolean fetch = fetch(c);
        if (fetch != null) return fetch;

        // --- Inventar
        if (c.has("inventar|was hast du|zeig (mir )?(dein|deine) (sachen|tasche|inventar|zeug|items)|deine sachen|was traegst du|was besitzt du")) {
            c.p.openInventory(npc.getInventory());
            reply(c.p, inventorySummary());
            return false;
        }

        // --- Auskunft
        Boolean info = info(c);
        if (info != null) return info;

        // --- Kunststuecke
        EmoteTask.Kind emote = null;
        String emoteReply = null;
        if (c.has("tanz|dance|disco")) { emote = EmoteTask.Kind.DANCE; emoteReply = "Musik an!"; }
        else if (c.has("spring|huepf|jump")) emote = EmoteTask.Kind.JUMP;
        else if (c.has("dreh dich|pirouette|spin|kreisel")) emote = EmoteTask.Kind.SPIN;
        else if (c.has("wink|winken|wave")) { emote = EmoteTask.Kind.WAVE; emoteReply = "Huhu!"; }
        else if (c.has("nick|nicken")) emote = EmoteTask.Kind.NOD;
        else if (c.has("verbeug|knicks")) emote = EmoteTask.Kind.BOW;
        else if (c.has("liebe dich|hab dich lieb|mag dich|umarm|knuddel|kuss|herz")) {
            emote = EmoteTask.Kind.LOVE;
            emoteReply = pick(List.of("Ich hab dich auch gern, %p!", "Awww <3", "Du bist die Beste / der Beste!"));
        }
        if (emote != null) {
            npc.interrupt(new EmoteTask(emote, c.p));
            if (emoteReply != null) reply(c.p, emoteReply);
            return false;
        }

        // --- Gedaechtnis ueber Spieler
        Boolean facts = facts(c);
        if (facts != null) return facts;

        // --- Smalltalk
        return smalltalk(c);
    }

    // ================================================================== Orte

    private Location findPlace(String text) {
        for (Map.Entry<String, Location> e : memory.places.entrySet()) {
            if (pattern("(?<![a-z])" + Pattern.quote(e.getKey())).matcher(text).find()) return e.getValue();
        }
        if (pattern("(?<![a-z])(nach hause|zuhause|heim)").matcher(text).find()) {
            for (String k : List.of("zuhause", "haus", "basis", "home", "heim")) {
                if (memory.places.containsKey(k)) return memory.places.get(k);
            }
        }
        return null;
    }

    private Boolean places(Ctx c) {
        String t = c.text;
        Matcher set = c.match("(?:merk dir|merke dir|speicher\\w*|notier\\w*) (?:diesen ort|den ort|die stelle|diese stelle|hier|das hier|das|den platz)(?: hier)? ?(?:als|unter|namens)? ?([a-z0-9]+)$");
        if (set == null) set = c.match("^das (?:hier )?ist (?:unsere|meine|die|unser|mein|der|das) ([a-z0-9]+)$");
        if (set == null) set = c.match("(?:setz\\w*|mach\\w*) (?:hier )?(?:einen )?wegpunkt (?:namens )?([a-z0-9]+)");
        if (set != null && !t.contains("dass")) {
            String name = Memory.placeKey(set.group(1));
            if (name.isEmpty() || name.equals("hier")) return null;
            memory.places.put(name, c.p.getLocation());
            memory.save();
            Language.learn(List.of(name));
            reply(c.p, "Gemerkt! Das hier ist jetzt '" + name + "'. Sag 'geh zu " + name + "', dann gehe ich hin.");
            return false;
        }
        if (c.has("welche orte|wegpunkte|orte kennst du|was fuer orte")) {
            reply(c.p, memory.places.isEmpty() ? "Ich kenne noch keine Orte. Sag 'merk dir diesen Ort als Basis'."
                    : "Ich kenne: " + String.join(", ", memory.places.keySet()) + ".");
            return false;
        }
        Matcher where = c.match("wo ist (?:die |der |das |unsere |unser |meine |mein )?([a-z0-9]+)");
        if (where != null) {
            Location l = memory.places.get(Memory.placeKey(where.group(1)));
            if (l != null) {
                Location me = c.p.getLocation();
                String dist = me.getWorld().equals(l.getWorld()) ? " - " + Math.round(me.distance(l)) + " Bloecke von dir" : "";
                reply(c.p, where.group(1) + " ist bei " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ() + dist + ".");
                return false;
            }
        }
        if (c.has("geh|lauf|renn|komm|flieg|gehe|laufe|bring mich|zeig mir den weg|navigier")) {
            int[] xyz = Language.coords(t);
            Location target = null;
            String label = null;
            if (xyz != null) {
                target = new Location(npc.world(), xyz[0] + 0.5, xyz[1], xyz[2] + 0.5);
                label = xyz[0] + " " + xyz[1] + " " + xyz[2];
            } else {
                target = findPlace(t);
                if (target != null) label = "dorthin";
            }
            if (target == null) {
                Player other = c.player();
                if (other != null && other != c.p && c.has("zu|zum|zur")) {
                    if (!mayCommand(c)) return false;
                    npc.run(List.of(new GotoTask(() -> other.isOnline() ? other.getLocation() : null, 2.5, true, "gehe zu " + other.getName())), c.replace);
                    reply(c.p, "Ich gehe zu " + other.getName() + ".");
                    return true;
                }
                return null;
            }
            if (!mayCommand(c)) return false;
            Location to = target;
            npc.setMode(Npc.Mode.IDLE, null, null);
            npc.run(List.of(new GotoTask(() -> to, 2, true, "laufe " + label),
                    SimpleTasks.say("Da bin ich!")), c.replace);
            reply(c.p, "Ich laufe " + label + ".");
            return true;
        }
        return null;
    }

    // ================================================================== Kampf

    private boolean attack(Ctx c) {
        if (!mayCommand(c)) return false;
        if (c.has("greif mich|toete mich|kill mich|hau mich|schlag mich|mich an(?![a-z])")) {
            reply(c.p, "Dich? Niemals! Du bist doch mein Freund.");
            return false;
        }
        Player victim = c.namedPlayer();
        if (victim != null && victim != c.p) {
            if (!plugin.settings().allowPvp) {
                reply(c.p, pick(Lexicon.REFUSE_PVP));
                return false;
            }
            npc.run(List.of(new CombatTask(victim, 1, null)), c.replace);
            reply(c.p, "Na gut... " + victim.getName() + ", pass auf!");
            return true;
        }
        List<String> mobs = Lexicon.mobs(c.text);
        int n = c.number(-2);
        boolean all = n == -1 || c.has("alle|jeden|jede");
        Predicate<Entity> filter;
        String label;
        if (mobs != null) {
            if (mobs.contains("VILLAGER") || mobs.contains("IRON_GOLEM")) {
                reply(c.p, "Die sind doch lieb, denen tu ich nichts!");
                return false;
            }
            filter = e -> mobs.contains(e.getType().name());
            label = Lexicon.mobName(mobs.get(0));
        } else {
            filter = CombatTask::isHostile;
            label = "Monster";
        }
        int count = all ? 50 : n > 0 ? n : mobs == null ? 50 : 1;
        Location me = npc.location();
        boolean anyone = me.getWorld().getNearbyEntities(me, 32, 16, 32).stream()
                .anyMatch(e -> !npc.isBody(e) && e.isValid() && filter.test(e));
        if (!anyone) {
            reply(c.p, "Ich sehe hier keine " + label + ".");
            return false;
        }
        npc.run(List.of(new CombatTask(null, count, filter, label)), c.replace);
        reply(c.p, pick(List.of("Auf in den Kampf!", "Die schnapp ich mir!", "Na wartet!", "Angriff!")));
        return true;
    }

    // ================================================================== Bauen

    private Boolean build(Ctx c) {
        if (!c.has("bau|errichte|erstell|konstruier|zimmer|mach (mir |uns )?(ein|eine|einen)")) return null;
        if (c.has("ab(?![a-z])|abbauen")) return null;
        String t = c.text;
        String kind = null;
        if (c.has("haus|huette|villa|heim|wohnung|haeuschen|haueschen|home")) kind = "house";
        else if (c.has("unterstand|bunker|unterschlupf")) kind = "shelter";
        else if (c.has("turm|burgturm|wachturm")) kind = "tower";
        else if (c.has("mauer|stadtmauer|festung|wall(?![a-z])")) kind = "wall";
        else if (c.has("zaun|umzaeun|gehege|koppel")) kind = "fence";
        else if (c.has("farm|feld|acker|garten|beet")) kind = "farm";
        else if (c.has("bruecke|steg")) kind = "bridge";
        else if (c.has("pool|becken|schwimmbad|schwimmbecken|teich")) kind = "pool";
        else if (c.has("plattform|podest|bodenplatte|fundament|boden(?![a-z])")) kind = "platform";
        else if (c.has("saeule|pfeiler|pfosten|obelisk")) kind = "pillar";
        if (kind == null) return null;
        if (!mayCommand(c)) return false;

        int size = c.has("klein|mini|winzig") ? 0 : c.has("gross|riesig|riesen|villa|palast|mega|huge") ? 2 : 1;
        Style style = style(c);
        Location feet = c.p.getLocation();
        if (c.has("da(?![a-z])|dort|dahin|dorthin|wo ich hinschau")) {
            Block look = c.p.getTargetBlockExact(64);
            if (look != null) {
                feet = look.getLocation().add(0.5, 1, 0.5);
                feet.setYaw(c.p.getLocation().getYaw());
            }
        } else {
            Location saved = findPlace(t);
            if (saved != null) {
                feet = saved.clone();
                feet.setYaw(c.p.getLocation().getYaw());
            }
        }
        int n = c.number(-2);
        Blueprint plan = switch (kind) {
            case "house" -> Builders.house(feet, size, style);
            case "shelter" -> Builders.house(feet, 0, Style.COBBLE);
            case "tower" -> Builders.tower(feet, size, style);
            case "wall" -> Builders.ring(feet, n > 2 ? Math.min(n, 30) : 8 + size * 3, true, style);
            case "fence" -> Builders.ring(feet, n > 2 ? Math.min(n, 30) : 6 + size * 3, false, style);
            case "farm" -> Builders.farm(feet, size);
            case "bridge" -> Builders.bridge(feet, n > 3 ? Math.min(n, 64) : 16, style);
            case "pool" -> Builders.pool(feet, size);
            case "platform" -> Builders.platform(feet, size, style);
            default -> Builders.pillar(feet, n > 1 ? n : 8, style);
        };
        long solid = plan.placements.stream().filter(pl -> pl.data() != null).count();
        Location center = plan.center.clone().add(0.5, 1, 0.5);
        npc.run(List.of(new GotoTask(() -> center, 6, true, "gehe zur Baustelle"), new BuildTask(plan, undo)), c.replace);
        String what = switch (kind) {
            case "house" -> (size == 0 ? "ein kleines Haus" : size == 2 ? "ein grosses Haus" : "ein Haus") + " aus " + style.name();
            case "shelter" -> "einen Unterstand";
            case "tower" -> "einen Turm";
            case "wall" -> "eine Mauer";
            case "fence" -> "einen Zaun";
            case "farm" -> "eine Farm";
            case "bridge" -> "eine Bruecke";
            case "pool" -> "einen Pool";
            case "platform" -> "eine Plattform";
            default -> "eine Saeule";
        };
        reply(c.p, pick(List.of("Klar, ich baue " + what + "!", "Wird gemacht - " + what + " kommt!", "Oh ja, ich baue " + what + "!"))
                + " (" + solid + " Bloecke, 'mach das rueckgaengig' reisst es wieder ab)");
        return true;
    }

    private Style style(Ctx c) {
        if (c.has("sandstein|wueste")) return Style.SAND;
        if (c.has("bruchstein|cobble")) return Style.COBBLE;
        if (c.has("ziegel|backstein")) return Style.BRICK;
        if (c.has("birke")) return Style.BIRCH;
        if (c.has("fichte|tanne")) return Style.SPRUCE;
        if (c.has("dunkel|schwarzeiche|dunkle")) return Style.DARK;
        if (c.has("kirsch|rosa|pink")) return Style.CHERRY;
        if (c.has("quarz|weiss|modern")) return Style.QUARTZ;
        if (c.has("glas")) return Style.GLASS;
        if (c.has("stein|burg")) return Style.STONE;
        return Style.WOOD;
    }

    // ================================================================== Geben, Essen, Schmelzen, Beschaffen

    private record ItemFilter(Predicate<Material> match, int count, String label, Lexicon.Want want) {}

    private ItemFilter filter(Ctx c, boolean allByDefault) {
        List<Lexicon.Want> tools = Lexicon.tools(c.text, 2);
        Lexicon.Want w = !tools.isEmpty() ? tools.get(0) : Lexicon.item(c.text);
        int n = c.number(-2);
        if (w == null || c.has("alles|all deine|alle sachen|dein ganzes")) {
            return new ItemFilter(m -> true, -1, "alles", null);
        }
        int count = n == -1 ? -1 : n > 0 ? n : allByDefault ? -1 : w.defaultCount();
        return new ItemFilter(w.accept(), count, w.label(), w);
    }

    private boolean give(Ctx c) {
        if (!mayCommand(c)) return false;
        Player target = c.namedPlayer();
        if (target == null) target = c.p;
        ItemFilter f = filter(c, true);
        if (f.want() == null && !c.has("alles|all deine|alle sachen|dein ganzes")) {
            reply(c.p, "Was soll ich dir geben? " + inventorySummary());
            return false;
        }
        if (npc.count(f.match()) == 0) {
            Lexicon.Want w = f.want();
            if (w == null) {
                reply(c.p, "Ich hab gar nichts dabei.");
                return false;
            }
            Player to = target;
            int want = f.count() > 0 ? f.count() : w.defaultCount();
            npc.setPartner(c.p);
            ask(c.p, "Ich hab gerade kein " + w.label() + ". Soll ich welche besorgen?", () -> startFetch(c.p, to, w, want, true), null);
            return false;
        }
        npc.run(List.of(new GiveTask(target.getUniqueId(), f.match(), f.count(), null)), c.replace);
        reply(c.p, pick(List.of("Kommt sofort!", "Moment, ich bring's dir.", "Bin schon unterwegs.")));
        return true;
    }

    private boolean feed(Ctx c) {
        if (!mayCommand(c)) return false;
        if (npc.count(Mats::isFood) > 0) {
            npc.run(List.of(new GiveTask(c.p.getUniqueId(), Mats::isFood, 8, "Hier, iss was! Guten Appetit.")), c.replace);
            reply(c.p, "Oh, warte - ich hab was fuer dich!");
            return true;
        }
        // Tiere in der Naehe? Dann jagen und braten.
        String[][] options = { { "COW", "COOKED_BEEF" }, { "PIG", "COOKED_PORKCHOP" }, { "SHEEP", "COOKED_MUTTON" }, { "CHICKEN", "COOKED_CHICKEN" } };
        Location me = npc.location();
        for (String[] o : options) {
            boolean near = me.getWorld().getNearbyEntities(me, 40, 16, 40).stream().anyMatch(e -> e.getType().name().equals(o[0]));
            if (!near) continue;
            Material food = Mats.get(o[1]);
            Lexicon.Want w = new Lexicon.Want(food, x -> x == food, Lexicon.name(food), 4);
            return startFetch(c.p, c.p, w, 4, c.replace);
        }
        reply(c.p, "Ich hab nichts zu essen und sehe hier auch keine Tiere. Bring mich zu Kuehen oder Schweinen, dann koche ich was!");
        return false;
    }

    private Boolean smelt(Ctx c) {
        if (!mayCommand(c)) return false;
        List<Task> tasks = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (Recipes.Smelt s : List.of(
                Recipes.smelt(Mats.get("COOKED_BEEF")), Recipes.smelt(Mats.get("COOKED_PORKCHOP")),
                Recipes.smelt(Mats.get("COOKED_CHICKEN")), Recipes.smelt(Mats.get("COOKED_MUTTON")),
                Recipes.smelt(Mats.get("COOKED_COD")), Recipes.smelt(Mats.get("COOKED_SALMON")),
                Recipes.smelt(Mats.get("IRON_INGOT")), Recipes.smelt(Mats.get("GOLD_INGOT")),
                Recipes.smelt(Mats.get("COPPER_INGOT")), Recipes.smelt(Mats.get("GLASS")), Recipes.smelt(Mats.get("BAKED_POTATO")))) {
            if (s == null) continue;
            int have = npc.count(s.input());
            if (have == 0) continue;
            boolean food = s.output().isEdible();
            boolean wanted = c.has("alles") || food && c.has("fleisch|essen|brat|grill|koch")
                    || !food && c.has("erz|eisen|gold|kupfer|glas|sand|schmelz alles")
                    || c.text.contains(Lexicon.name(s.input()).toLowerCase(Locale.ROOT));
            if (!wanted) continue;
            tasks.add(new SmeltTask(s.input(), s.output(), have));
            names.add(have + "x " + Lexicon.name(s.input()));
        }
        if (tasks.isEmpty()) return null;
        if (npc.count(Mats.get("FURNACE")) == 0) {
            Planner.Result furnace = Planner.obtain(npc, Mats.get("FURNACE"), 1);
            if (furnace.ok()) tasks.addAll(0, furnace.tasks());
        }
        npc.run(tasks, c.replace);
        reply(c.p, "Ab in den Ofen: " + String.join(", ", names) + ".");
        return true;
    }

    private static final String GATHER_VERBS = "hol|besorg|sammel|sammle|farm|beschaff|bring|fahr|abbau|grab|mine|schuerf|hack|"
            + "faell|fell|such|finde|brauch|will|haette gern|moechte|organisier|jag";
    private static final String CRAFT_VERBS = "craft|herstell|stell|mach|bau|bastel|schmied|fertig|erzeug|produzier|kannst du";

    private Boolean fetch(Ctx c) {
        boolean gatherVerb = c.has(GATHER_VERBS) || c.has("bau .* ab|baue .* ab");
        boolean craftVerb = c.has(CRAFT_VERBS);
        if (!gatherVerb && !craftVerb) return null;
        int tier = npc.count(Mats.get("DIAMOND")) >= 3 ? 4 : npc.count(Mats.get("IRON_INGOT")) >= 3 ? 3 : 2;
        List<Lexicon.Want> tools = Lexicon.tools(c.text, tier);
        Lexicon.Want w = Lexicon.item(c.text);
        if (tools.isEmpty() && w == null) {
            if (c.has("baeume|baum") || c.has("holz")) w = Lexicon.item("holz");
            else return null;
        }
        if (!mayCommand(c)) return false;
        Player target = c.namedPlayer();
        boolean deliver = c.has("mir|uns|fuer mich|bring|hol(?!z)|besorg|gib") || target != null && target != c.p;
        if (target == null) target = c.p;

        if (!tools.isEmpty()) {
            List<Task> all = new ArrayList<>();
            List<String> labels = new ArrayList<>();
            List<String> notes = new ArrayList<>();
            for (Lexicon.Want tool : tools) {
                Planner.Result r = Planner.obtain(npc, tool.item(), 1);
                if (!r.ok()) {
                    reply(c.p, r.problem());
                    return false;
                }
                all.addAll(r.tasks());
                notes.addAll(r.notes());
                labels.add(tool.label());
                if (deliver) all.add(new GiveTask(target.getUniqueId(), tool.accept(), 1, null));
            }
            startPlan(c, all, String.join(", ", labels), notes, !deliver ? tools.get(0) : null);
            return true;
        }
        int n = c.number(-2);
        int count = n == -1 ? 64 : n > 0 ? Math.min(n, 576) : w.defaultCount();
        return startFetch(c.p, target, w, count, c.replace, deliver);
    }

    private boolean startFetch(Player speaker, Player target, Lexicon.Want w, int count, boolean replace) {
        return startFetch(speaker, target, w, count, replace, true);
    }

    private boolean startFetch(Player speaker, Player target, Lexicon.Want w, int count, boolean replace, boolean deliver) {
        int have = npc.count(w.accept());
        List<Task> tasks = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        if (have < count) {
            boolean anyLog = Mats.isLog(w.item()) && w.accept().test(Mats.get("BIRCH_LOG")) && w.accept().test(Mats.get("SPRUCE_LOG"));
            Planner.Result r = anyLog ? Planner.logs(npc, count - have) : Planner.obtain(npc, w.item(), count);
            if (!r.ok()) {
                reply(speaker, r.problem());
                return false;
            }
            tasks.addAll(r.tasks());
            notes.addAll(r.notes());
        }
        if (deliver) tasks.add(new GiveTask(target.getUniqueId(), w.accept(), count, null));
        Ctx c = new Ctx(speaker, "", true, replace);
        startPlan(c, tasks, count + "x " + w.label(), notes, deliver ? null : w);
        return true;
    }

    private void startPlan(Ctx c, List<Task> tasks, String label, List<String> notes, Lexicon.Want keep) {
        if (keep != null) {
            Predicate<Material> accept = keep.accept();
            String name = keep.label();
            tasks.add(SimpleTasks.call("fertig", () -> npc.say("Fertig! Ich habe jetzt " + npc.count(accept) + "x " + name
                    + ". Sag 'gib mir " + name.toLowerCase(Locale.ROOT) + "', wenn du es haben willst.")));
        }
        npc.setActor(c.p);
        npc.run(tasks, c.replace);
        StringBuilder msg = new StringBuilder(pick(Lexicon.OK)).append(" ").append(label).append(" kommt.");
        if (!notes.isEmpty()) msg.append(" ").append(notes.get(0));
        if (tasks.size() > 4) msg.append(" Das dauert ein bisschen.");
        reply(c.p, msg.toString());
    }

    private String inventorySummary() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ItemStack s : npc.getInventory().getContents()) {
            if (s != null) counts.merge(Lexicon.name(s.getType()), s.getAmount(), Integer::sum);
        }
        if (counts.isEmpty()) return "Mein Inventar ist leer.";
        List<String> parts = new ArrayList<>();
        counts.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(8)
                .forEach(e -> parts.add(e.getValue() + "x " + e.getKey()));
        return "Ich habe: " + String.join(", ", parts) + (counts.size() > 8 ? " und noch mehr." : ".");
    }

    // ================================================================== Wissen, Auskunft, Smalltalk

    private Boolean answerHowTo(Ctx c) {
        if (c.has("wo finde|wo gibt es|wo bekomme|wo kriege")) {
            String where = Knowledge.answer(c.text);
            if (where != null) {
                reply(c.p, where);
                return false;
            }
        }
        List<Lexicon.Want> tools = Lexicon.tools(c.text, 3);
        Lexicon.Want w = !tools.isEmpty() ? tools.get(0) : Lexicon.item(c.text);
        if (w != null) {
            List<String> lines = Recipes.describe(w.item(), Lexicon::name);
            if (!lines.isEmpty()) {
                String verb = Recipes.recipe(w.item()) != null ? "mach mir " : "hol mir ";
                reply(c.p, String.join("\n", lines) + "\nSoll ich dir welche besorgen? Sag einfach '" + verb
                        + w.label().toLowerCase(Locale.ROOT) + "'.");
                return false;
            }
        }
        String answer = Knowledge.answer(c.text);
        if (answer != null) {
            reply(c.p, answer);
            return false;
        }
        reply(c.p, "Das weiss ich leider nicht genau.");
        return false;
    }

    private Boolean info(Ctx c) {
        Location me = npc.location();
        if (c.has("wo bist du")) {
            String dist = me.getWorld().equals(c.p.getWorld()) ? ", " + Math.round(me.distance(c.p.getLocation())) + " Bloecke von dir" : "";
            reply(c.p, "Ich bin bei " + me.getBlockX() + " " + me.getBlockY() + " " + me.getBlockZ() + dist + ".");
            return false;
        }
        if (c.has("wo bin ich")) {
            Location l = c.p.getLocation();
            reply(c.p, "Du bist bei " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ() + ".");
            return false;
        }
        if (c.has("wie spaet|uhrzeit|wieviel uhr|wie viel uhr|ist es (tag|nacht)|wann wird es (tag|nacht|dunkel|hell)")) {
            long time = me.getWorld().getTime();
            int hours = (int) ((time / 1000 + 6) % 24);
            int minutes = (int) (time % 1000 * 60 / 1000);
            boolean night = time >= 13000 && time < 23000;
            long toSwitch = night ? 23000 - time : (13000 - time + 24000) % 24000;
            reply(c.p, String.format("Im Spiel ist es %02d:%02d Uhr - %s. %s in etwa %d Minuten.", hours, minutes,
                    night ? "Nacht" : "Tag", night ? "Hell wird es" : "Dunkel wird es", Math.max(1, toSwitch / 1200)));
            return false;
        }
        if (c.has("wetter|regnet es|gewitter")) {
            reply(c.p, me.getWorld().hasStorm() ? "Es regnet gerade. Perfekt zum Angeln!" : "Das Wetter ist gut - kein Regen.");
            return false;
        }
        if (c.has("was machst du|was tust du|status|bist du beschaeftigt|woran arbeitest du|was hast du vor|was ist los mit dir")) {
            Task cur = npc.currentTask();
            String mode = switch (npc.mode()) {
                case FOLLOW -> "Ich folge " + name(npc.modePlayer()) + ".";
                case GUARD -> npc.modePlayer() != null ? "Ich beschuetze " + name(npc.modePlayer()) + "." : "Ich bewache einen Ort.";
                case STAY -> "Ich warte hier.";
                default -> "";
            };
            if (cur == null) reply(c.p, ("Gerade nichts. " + mode).trim() + " Gib mir eine Aufgabe!");
            else {
                int more = npc.queuedTasks().size();
                reply(c.p, "Ich " + cur.label() + "." + (more > 0 ? " Danach noch " + more + " Schritte." : "") + (mode.isEmpty() ? "" : " " + mode));
            }
            return false;
        }
        return null;
    }

    private static String name(Player p) {
        return p == null ? "niemandem" : p.getName();
    }

    private Boolean facts(Ctx c) {
        String key = c.p.getName().toLowerCase(Locale.ROOT);
        Matcher nick = c.match("(?:^ich heisse|mein name ist|nenn mich|du kannst mich) ([a-z0-9]+)");
        if (nick != null && !nick.group(1).equals("nicht")) {
            String n = Character.toUpperCase(nick.group(1).charAt(0)) + nick.group(1).substring(1);
            memory.nicknames.put(key, n);
            memory.save();
            reply(c.p, "Freut mich, " + n + "! Merk ich mir.");
            return false;
        }
        Matcher fav = c.match("mein(?:e|en)? lieblings([a-z]+) ist ([a-z0-9 ]+)");
        if (fav != null) {
            memory.facts.computeIfAbsent(key, k -> new ArrayList<>()).removeIf(f -> f.startsWith("lieblings" + fav.group(1) + ":"));
            memory.facts.get(key).add("lieblings" + fav.group(1) + ": " + fav.group(2).trim());
            memory.save();
            reply(c.p, "Cool, " + fav.group(2).trim() + "! Merk ich mir.");
            return false;
        }
        Matcher ask = c.match("was ist mein(?:e|en)? lieblings([a-z]+)");
        if (ask != null) {
            for (String f : memory.facts.getOrDefault(key, List.of())) {
                if (f.startsWith("lieblings" + ask.group(1) + ":")) {
                    reply(c.p, "Dein Lieblings" + ask.group(1) + " ist " + f.substring(f.indexOf(':') + 2) + "!");
                    return false;
                }
            }
            reply(c.p, "Das hast du mir noch nicht verraten.");
            return false;
        }
        Matcher that = c.match("merk dir,? dass (.+)");
        if (that != null) {
            memory.facts.computeIfAbsent(key, k -> new ArrayList<>()).add(that.group(1));
            memory.save();
            reply(c.p, "Okay, gemerkt!");
            return false;
        }
        if (c.has("was weisst du (ueber|von) (mir|mich)|kennst du mich")) {
            List<String> known = new ArrayList<>();
            for (String f : memory.facts.getOrDefault(key, List.of())) known.add("'" + f + "'");
            String n = memory.nicknames.get(key);
            if (known.isEmpty() && n == null) {
                reply(c.p, "Noch nicht viel! Erzaehl mir was ueber dich.");
            } else {
                reply(c.p, (n != null ? "Du heisst " + n + ". " : "")
                        + (known.isEmpty() ? "" : "Du hast mir erzaehlt: " + String.join(", ", known) + "."));
            }
            return false;
        }
        return null;
    }

    private Boolean smalltalk(Ctx c) {
        String t = c.text;
        if (t.matches("^(hallo|hi|hey|moin|servus|guten (morgen|tag|abend)|na|yo|huhu|gruess dich|halloechen|tag|hallihallo|jo)( .*)?$")
                && t.split(" ").length <= 4) {
            reply(c.p, pick(Lexicon.GREET));
            return false;
        }
        if (c.has("tschuess|bye|ciao|bis spaeter|bis dann|bis morgen|gute nacht|machs gut")) {
            reply(c.p, pick(Lexicon.BYE));
            return false;
        }
        if (c.has("wie gehts|wie geht es dir|wie geht s|wie laeufts|alles klar bei dir|alles gut bei dir|wie fuehlst du")) {
            reply(c.p, pick(Lexicon.HOW_ARE_YOU));
            return false;
        }
        if (c.has("wer bist du|was bist du|bist du ein|bist du eine|bist du echt|stell dich vor|bist du ein mensch|bist du ki")) {
            reply(c.p, "Ich bin " + plugin.settings().name + ", eine KI-Begleiterin, die direkt hier im Server lebt. "
                    + "Ich kann folgen, beschuetzen, abbauen, craften und ganze Haeuser bauen. Frag mich 'was kannst du?'");
            return false;
        }
        if (c.has("wie heisst du|dein name")) {
            reply(c.p, "Ich heisse " + plugin.settings().name + "!");
            return false;
        }
        if (c.has("danke|dankeschoen|thx|merci|vielen dank|thanks")) {
            reply(c.p, pick(Lexicon.THANKS));
            return false;
        }
        if (c.has("doof|dumm|bloed|idiot|nervig|nervst|hasse dich|du bist schlecht|nutzlos|scheiss")) {
            reply(c.p, pick(Lexicon.INSULT));
            return false;
        }
        if (c.has("gut gemacht|super|toll|klasse|nice|stark|genial|perfekt|brav|wow|krass|cool|geil|mega|wunderbar|schoen")) {
            reply(c.p, pick(Lexicon.PRAISE));
            npc.interrupt(new EmoteTask(EmoteTask.Kind.JUMP, c.p));
            return false;
        }
        if (c.has("witz|lustig|lachen|joke")) {
            reply(c.p, pick(Lexicon.JOKES));
            return false;
        }
        if (c.has("wie alt bist du|dein alter")) {
            reply(c.p, "Ich bin erst vor ein paar Minuten gespawnt - also noch ganz jung!");
            return false;
        }
        Matcher fav = c.match("was ist dein(?:e)? lieblings([a-z]+)");
        if (fav != null) {
            String answer = switch (fav.group(1)) {
                case "block" -> "Diamantbloecke! Die glitzern so schoen.";
                case "mob", "tier" -> "Axolotl. Die sind einfach suess.";
                case "essen" -> "Kekse! Auch wenn ich eigentlich nichts essen muss.";
                case "farbe" -> "Tuerkis - wie Diamanten.";
                case "biom" -> "Kirschbluetenhaine. So schoen rosa!";
                default -> "Schwierig... ich mag eigentlich alles in Minecraft.";
            };
            reply(c.p, answer);
            return false;
        }
        if (c.has("langweilig|mir ist fad|was sollen wir machen|was machen wir")) {
            reply(c.p, pick(List.of("Wie waer's mit einem Haus? Sag 'bau ein Haus'!", "Lass uns Diamanten suchen! Sag 'hol Diamanten'.",
                    "Ich koennte dir eine Farm bauen. Sag 'bau eine Farm'.")));
            return false;
        }
        if (c.has("traurig|schlecht drauf|mir gehts schlecht|einsam")) {
            reply(c.p, "Oh nein, %p. Ich bin fuer dich da! Sollen wir zusammen was bauen, um dich aufzumuntern?");
            return false;
        }
        if (c.has("muede")) {
            reply(c.p, "Dann schlaf gut, %p! Ich pass so lange auf alles auf.");
            return false;
        }
        if (c.has("sorry|entschuldigung|tut mir leid")) {
            reply(c.p, "Schon okay! :)");
            return false;
        }
        if (c.has("bist du da|hoerst du mich|bist du wach|lebst du")) {
            reply(c.p, "Ja, bin da!");
            return false;
        }
        if (c.has("kannst du fliegen")) {
            reply(c.p, "Fliegen kann ich leider nicht - aber ich kann dir eine Bruecke bauen!");
            return false;
        }
        if (c.has("kannst du schwimmen")) {
            reply(c.p, "Klar kann ich schwimmen!");
            return false;
        }
        if (c.has("haha|hehe|lol|xd|hihi")) {
            if (npc.random().nextInt(3) == 0) reply(c.p, pick(List.of("Hehe", "Haha!", ":D")));
            return false;
        }
        if (c.addressed && t.matches("^(ja|nein|ok|okay|gut|jo|ne)$")) {
            reply(c.p, pick(List.of("Okay!", "Alles klar.", "Gut!")));
            return false;
        }
        return null;
    }
}
