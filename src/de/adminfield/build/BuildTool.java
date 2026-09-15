package de.adminfield.build;

import de.adminfield.AdminFieldPlugin;
import de.adminfield.Ui;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Bauen mit grossen Zahlen: zwei Ecken setzen, Block waehlen, fuellen.
 *
 * <p>Die Ecken werden mit dem Bau-Stab gesetzt - Linksklick die erste, Rechtsklick die zweite.
 * Alles Weitere geschieht im Menue.
 *
 * <p>Gesetzt wird nicht auf einen Schlag, sondern haeppchenweise ueber mehrere Ticks. Ein
 * Auftrag ueber hunderttausend Bloecke wuerde den Server sonst fuer Sekunden anhalten, und jeder
 * Spieler haette einen Ruckler. So bleibt er die ganze Zeit bedienbar.
 *
 * <p>Jeder Auftrag merkt sich, was vorher dastand. Vertippt man sich in der Ecke, holt
 * "Rueckgaengig" den alten Zustand zurueck - genau so, wie er war, mit Drehrichtung und allem.
 */
public final class BuildTool implements Listener {

    /** So viele Bloecke pro Tick - genug, dass es zuegig geht, wenig genug fuer ruhige Ticks. */
    private static final int PER_TICK = 4000;
    private static final String LIMIT_PATH = "build.max-blocks";
    private static final int DEFAULT_LIMIT = 50000;
    /**
     * So viele Auftraege lassen sich zuruecknehmen.
     *
     * <p>Klein gehalten mit Absicht: fuer jeden veraenderten Block wird sein alter Zustand
     * aufgehoben, und bei fuenfzigtausend Bloecken sind das einige Megabyte. Zwei Schritte
     * reichen fuer den Griff daneben, ohne dass der Arbeitsspeicher darunter leidet.
     */
    private static final int UNDO_STEPS = 2;

    /** Was man mit der Auswahl machen kann. */
    public enum Job {
        FILL("Füllen"),
        WALLS("Wände"),
        FLOOR("Boden"),
        CEILING("Decke"),
        SHELL("Hülle"),
        CLEAR("Leeren");

        private final String label;

        Job(String label) {
            this.label = label;
        }

        public String label() {
            return this.label;
        }
    }

    /** Die beiden Ecken, die jemand gesetzt hat. */
    public static final class Selection {
        private String world;
        private int[] first;
        private int[] second;

        public boolean complete() {
            return this.world != null && this.first != null && this.second != null;
        }

        public int[] first() {
            return this.first;
        }

        public int[] second() {
            return this.second;
        }

        public String world() {
            return this.world;
        }

        private int min(int axis) {
            return Math.min(this.first[axis], this.second[axis]);
        }

        private int max(int axis) {
            return Math.max(this.first[axis], this.second[axis]);
        }

        /** Kantenlaengen in Bloecken. */
        public int[] size() {
            if (!this.complete()) {
                return new int[]{0, 0, 0};
            }
            return new int[]{this.max(0) - this.min(0) + 1,
                    this.max(1) - this.min(1) + 1,
                    this.max(2) - this.min(2) + 1};
        }

        public long volume() {
            int[] size = this.size();
            return (long) size[0] * (long) size[1] * (long) size[2];
        }

        /** Verschiebt eine Seite nach aussen. */
        private void grow(int axis, int amount) {
            if (!this.complete()) {
                return;
            }
            if (amount >= 0) {
                if (this.first[axis] >= this.second[axis]) {
                    this.first[axis] += amount;
                } else {
                    this.second[axis] += amount;
                }
            } else if (this.first[axis] <= this.second[axis]) {
                this.first[axis] += amount;
            } else {
                this.second[axis] += amount;
            }
        }
    }

    /** Ein Block, wie er vor dem Auftrag aussah. */
    private static final class Change {
        private final int x;
        private final int y;
        private final int z;
        private final BlockData was;

        private Change(int x, int y, int z, BlockData was) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.was = was;
        }
    }

    /** Ein ganzer Auftrag, rueckwaerts. */
    private static final class Undo {
        private final String world;
        private final List<Change> changes;
        private final String what;

        private Undo(String world, List<Change> changes, String what) {
            this.world = world;
            this.changes = changes;
            this.what = what;
        }
    }

    private static BuildTool instance;

    private final AdminFieldPlugin plugin;
    private final NamespacedKey wandKey;
    private final Map<UUID, Selection> selections = new HashMap<>();
    private final Map<UUID, Material> palette = new HashMap<>();
    private final Map<UUID, Deque<Undo>> undos = new HashMap<>();
    private final Map<UUID, Boolean> busy = new HashMap<>();

    private BuildTool(AdminFieldPlugin plugin) {
        this.plugin = plugin;
        this.wandKey = new NamespacedKey((Plugin) plugin, "build_wand");
    }

    public static synchronized void start(AdminFieldPlugin plugin) {
        if (instance != null && instance.plugin == plugin) {
            return;
        }
        BuildTool fresh = new BuildTool(plugin);
        instance = fresh;
        try {
            Bukkit.getPluginManager().registerEvents(fresh, (Plugin) plugin);
        } catch (Throwable t) {
            plugin.getLogger().warning("Bau-Werkzeug konnte sich nicht einhaengen ("
                    + t.getClass().getSimpleName() + ").");
        }
    }

    public static synchronized void stop(AdminFieldPlugin plugin) {
        if (instance != null && instance.plugin == plugin) {
            instance = null;
        }
    }

    public static BuildTool instance() {
        return instance;
    }

    // ------------------------------------------------------------------ Der Stab

    /** Der Bau-Stab, erkennbar an seinem unsichtbaren Marker. */
    public ItemStack wand() {
        Material material = material("GOLDEN_AXE", Material.IRON_PICKAXE);
        ItemStack item = Ui.glowing(material, "<gradient:#7bdcff:#3a7bff><bold>Bau-Stab</bold></gradient>",
                List.of("<gray>Linksklick auf einen Block: <white>Ecke 1",
                        "<gray>Rechtsklick auf einen Block: <white>Ecke 2",
                        "",
                        "<gray>Der Rest geht im Menü:",
                        "<gray>Block wählen, Auswahl vergrößern, füllen.",
                        "",
                        "<dark_gray>Er baut nichts ab – die Klicks setzen nur",
                        "<dark_gray>die Ecken."));
        try {
            ItemMeta meta = item.getItemMeta();
            meta.getPersistentDataContainer().set(this.wandKey, PersistentDataType.STRING, "1");
            item.setItemMeta(meta);
        } catch (Throwable ignored) {
            // Ohne Marker bliebe er wirkungslos - dann lieber gar keinen ausgeben.
            return null;
        }
        return item;
    }

    private boolean isWand(ItemStack item) {
        if (item == null) {
            return false;
        }
        try {
            return item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer()
                    .has(this.wandKey, PersistentDataType.STRING);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Ein Material ueber seinen Namen - so bleibt es unabhaengig von der Serverversion. */
    public static Material material(String name, Material fallback) {
        try {
            Material found = Material.valueOf(name);
            return found == null ? fallback : found;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    // ------------------------------------------------------------------ Auswahl

    public Selection selection(Player player) {
        return this.selections.computeIfAbsent(player.getUniqueId(), id -> new Selection());
    }

    public Material chosen(Player player) {
        return this.palette.get(player.getUniqueId());
    }

    public void choose(Player player, Material material) {
        this.palette.put(player.getUniqueId(), material);
    }

    public void clearSelection(Player player) {
        this.selections.remove(player.getUniqueId());
    }

    /** Vergroessert die Auswahl auf einer Seite. */
    public void grow(Player player, int axis, int amount) {
        Selection selection = this.selection(player);
        if (!selection.complete()) {
            this.plugin.send(player, "<red>Setze zuerst beide Ecken mit dem Bau-Stab.");
            return;
        }
        selection.grow(axis, amount);
        int[] size = selection.size();
        this.plugin.send(player, "<gray>Auswahl: <white>" + size[0] + " × " + size[1] + " × "
                + size[2] + "<gray> (" + selection.volume() + " Blöcke)");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        ItemStack item;
        Block block;
        try {
            item = event.getItem();
            block = event.getClickedBlock();
        } catch (Throwable ignored) {
            return;
        }
        if (block == null || !this.isWand(item)) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission("adminfield.build")) {
            return;
        }
        Action action = event.getAction();
        boolean second = action == Action.RIGHT_CLICK_BLOCK;
        if (!second && action != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        event.setCancelled(true);

        Selection selection = this.selection(player);
        String world = block.getWorld().getName();
        if (!world.equals(selection.world)) {
            // Andere Welt: die alte Ecke passt nicht mehr dazu.
            selection.world = world;
            selection.first = null;
            selection.second = null;
        }
        int[] corner = new int[]{block.getX(), block.getY(), block.getZ()};
        if (second) {
            selection.second = corner;
        } else {
            selection.first = corner;
        }
        String text = "<gray>Ecke <white>" + (second ? "2" : "1") + "<gray> gesetzt: <white>"
                + corner[0] + " / " + corner[1] + " / " + corner[2];
        if (selection.complete()) {
            int[] size = selection.size();
            text += "<newline><gray>Auswahl: <white>" + size[0] + " × " + size[1] + " × " + size[2]
                    + "<gray> (" + selection.volume() + " Blöcke)";
        }
        this.plugin.send(player, text);
    }

    // ------------------------------------------------------------------ Bauen

    public int limit() {
        try {
            return Math.max(1, this.plugin.getConfig().getInt(LIMIT_PATH, DEFAULT_LIMIT));
        } catch (Throwable ignored) {
            return DEFAULT_LIMIT;
        }
    }

    public boolean working(Player player) {
        return Boolean.TRUE.equals(this.busy.get(player.getUniqueId()));
    }

    /**
     * Fuehrt einen Auftrag aus.
     *
     * <p>Laeuft ueber mehrere Ticks, deshalb gibt es hier nichts zurueck - die Rueckmeldung
     * kommt als Nachricht, wenn es fertig ist.
     */
    public void run(Player player, Job job) {
        Selection selection = this.selection(player);
        if (!selection.complete()) {
            this.plugin.send(player, "<red>Setze zuerst beide Ecken mit dem Bau-Stab.");
            return;
        }
        if (this.working(player)) {
            this.plugin.send(player, "<red>Dein letzter Auftrag läuft noch.");
            return;
        }
        World world = Bukkit.getWorld(selection.world);
        if (world == null) {
            this.plugin.send(player, "<red>Die Welt der Auswahl gibt es nicht mehr.");
            return;
        }
        Material material = job == Job.CLEAR ? Material.AIR : this.chosen(player);
        if (material == null) {
            this.plugin.send(player, "<red>Wähle zuerst einen Block aus.");
            return;
        }

        int minX = selection.min(0);
        int maxX = selection.max(0);
        int minZ = selection.min(2);
        int maxZ = selection.max(2);
        int minY = Math.max(selection.min(1), floorOf(world));
        int maxY = Math.min(selection.max(1), ceilingOf(world));
        if (minY > maxY) {
            this.plugin.send(player, "<red>Die Auswahl liegt ausserhalb der Welt.");
            return;
        }
        long volume = (long) (maxX - minX + 1) * (long) (maxY - minY + 1) * (long) (maxZ - minZ + 1);
        int limit = this.limit();
        if (volume > limit) {
            this.plugin.send(player, "<red>Zu groß: <white>" + volume + "<red> Blöcke, erlaubt sind <white>"
                    + limit + "<red>.<newline><gray>Grenze ändern: <white>build.max-blocks<gray> in der config.yml.");
            return;
        }

        List<Change> changes = new ArrayList<>();
        this.busy.put(player.getUniqueId(), Boolean.TRUE);
        this.plugin.send(player, "<gray>" + job.label() + " läuft … <dark_gray>(" + volume + " Blöcke)");
        this.walk(player, world, job, material, minX, minY, minZ, maxX, maxY, maxZ, changes,
                placed -> {
                    this.busy.remove(player.getUniqueId());
                    this.remember(player, new Undo(world.getName(), changes, job.label()));
                    this.plugin.send(player, "<gray>" + job.label() + " fertig: <white>" + placed
                            + "<gray> Blöcke gesetzt.");
                });
    }

    /** Geht die Auswahl haeppchenweise durch und setzt, was zum Auftrag gehoert. */
    private void walk(Player player, World world, Job job, Material material,
            int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
            List<Change> changes, Consumer<Integer> done) {
        int[] cursor = new int[]{minX, minY, minZ};
        int[] placed = new int[]{0};
        Runnable step = new Runnable() {
            @Override
            public void run() {
                int budget = PER_TICK;
                while (budget-- > 0) {
                    if (cursor[0] > maxX) {
                        done.accept(placed[0]);
                        return;
                    }
                    int x = cursor[0];
                    int y = cursor[1];
                    int z = cursor[2];
                    // Cursor gleich weiterschieben, damit jeder Ausstieg unten sauber ist.
                    if (++cursor[2] > maxZ) {
                        cursor[2] = minZ;
                        if (++cursor[1] > maxY) {
                            cursor[1] = minY;
                            cursor[0]++;
                        }
                    }
                    if (!belongs(job, x, y, z, minX, minY, minZ, maxX, maxY, maxZ)) {
                        continue;
                    }
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() == material) {
                        continue;
                    }
                    try {
                        changes.add(new Change(x, y, z, block.getBlockData()));
                    } catch (Throwable ignored) {
                        // Ohne alten Zustand gibt es fuer diesen Block eben kein Zurueck.
                    }
                    block.setType(material, false);
                    placed[0]++;
                }
                schedule(this);
            }
        };
        this.schedule(step);
    }

    private void schedule(Runnable step) {
        try {
            Bukkit.getScheduler().runTask((Plugin) this.plugin, step);
        } catch (Throwable ignored) {
            step.run();
        }
    }

    /** Gehoert dieser Block zum Auftrag? */
    private static boolean belongs(Job job, int x, int y, int z,
            int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        boolean side = x == minX || x == maxX || z == minZ || z == maxZ;
        boolean floor = y == minY;
        boolean ceiling = y == maxY;
        return switch (job) {
            case FILL, CLEAR -> true;
            case WALLS -> side;
            case FLOOR -> floor;
            case CEILING -> ceiling;
            case SHELL -> side || floor || ceiling;
        };
    }

    private static int floorOf(World world) {
        try {
            return world.getMinHeight();
        } catch (Throwable ignored) {
            return -64;
        }
    }

    private static int ceilingOf(World world) {
        try {
            return world.getMaxHeight() - 1;
        } catch (Throwable ignored) {
            return 319;
        }
    }

    // ------------------------------------------------------------------ Rueckgaengig

    private void remember(Player player, Undo undo) {
        if (undo.changes.isEmpty()) {
            return;
        }
        Deque<Undo> stack = this.undos.computeIfAbsent(player.getUniqueId(), id -> new ArrayDeque<>());
        stack.push(undo);
        while (stack.size() > UNDO_STEPS) {
            stack.removeLast();
        }
    }

    /** Wie viele Schritte sich zurueckholen lassen. */
    public int undoSteps(Player player) {
        Deque<Undo> stack = this.undos.get(player.getUniqueId());
        return stack == null ? 0 : stack.size();
    }

    /** Der Name des letzten Auftrags, oder null. */
    public String lastJob(Player player) {
        Deque<Undo> stack = this.undos.get(player.getUniqueId());
        return stack == null || stack.isEmpty() ? null : stack.peek().what;
    }

    public void undo(Player player) {
        Deque<Undo> stack = this.undos.get(player.getUniqueId());
        if (stack == null || stack.isEmpty()) {
            this.plugin.send(player, "<red>Es gibt nichts zurückzunehmen.");
            return;
        }
        if (this.working(player)) {
            this.plugin.send(player, "<red>Dein letzter Auftrag läuft noch.");
            return;
        }
        Undo undo = stack.pop();
        World world = Bukkit.getWorld(undo.world);
        if (world == null) {
            this.plugin.send(player, "<red>Die Welt dazu gibt es nicht mehr.");
            return;
        }
        this.busy.put(player.getUniqueId(), Boolean.TRUE);
        this.plugin.send(player, "<gray>Nehme zurück: <white>" + undo.what + " <dark_gray>("
                + undo.changes.size() + " Blöcke)");
        int[] cursor = new int[]{undo.changes.size() - 1};
        Runnable step = new Runnable() {
            @Override
            public void run() {
                int budget = PER_TICK;
                while (budget-- > 0) {
                    if (cursor[0] < 0) {
                        BuildTool.this.busy.remove(player.getUniqueId());
                        BuildTool.this.plugin.send(player, "<gray>Zurückgenommen: <white>"
                                + undo.changes.size() + "<gray> Blöcke.");
                        return;
                    }
                    Change change = undo.changes.get(cursor[0]--);
                    try {
                        world.getBlockAt(change.x, change.y, change.z).setBlockData(change.was, false);
                    } catch (Throwable ignored) {
                    }
                }
                BuildTool.this.schedule(this);
            }
        };
        this.schedule(step);
    }

    // ------------------------------------------------------------------ Aufraeumen

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        this.selections.remove(id);
        this.palette.remove(id);
        this.undos.remove(id);
        this.busy.remove(id);
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin() == this.plugin) {
            stop(this.plugin);
        }
    }
}
