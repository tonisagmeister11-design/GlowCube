package de.adminfield.offline;

import de.adminfield.AdminFieldPlugin;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;

/**
 * Macht Inventare von Spielern bearbeitbar, die gerade nicht auf dem Server sind.
 *
 * <p>Wie das geht: beim Verlassen wird das Inventar und die Enderkiste als Abbild in
 * {@code plugins/AdminField/offline/<uuid>.yml} abgelegt. Der Admin bearbeitet dieses Abbild,
 * und beim naechsten Einloggen wird es in das echte Inventar uebertragen.
 *
 * <p>Warum nicht direkt in die Spielerdatei schreiben: die liegt in einem Format, das eng an
 * der Serverversion haengt: ein Fehler dabei waere unwiederbringlicher Itemverlust. Noetig ist
 * es auch nicht - solange jemand offline ist, kann sich sein Inventar nicht aendern. Das
 * Abbild bleibt also die ganze Zeit exakt richtig, und beim Einloggen kommt genau das heraus,
 * was der Admin gesehen hat. Die echte Spielerdatei wird nie angefasst.
 */
public final class OfflineStore implements Listener {

    /** So viele Felder hat ein Spielerinventar: 36 Taschen, 4 Ruestung, 1 zweite Hand. */
    public static final int INVENTORY_SLOTS = 41;
    /** So viele Felder hat eine Enderkiste. */
    public static final int ENDER_SLOTS = 27;

    private static final String FOLDER = "offline";

    /** Ein bekanntes Abbild. */
    public static final class Entry {
        private final UUID id;
        private final String name;
        private final long saved;
        private final boolean pending;
        private final int items;

        private Entry(UUID id, String name, long saved, boolean pending, int items) {
            this.id = id;
            this.name = name;
            this.saved = saved;
            this.pending = pending;
            this.items = items;
        }

        public UUID id() {
            return this.id;
        }

        public String name() {
            return this.name;
        }

        /** Wann der Spieler zuletzt offline gegangen ist. */
        public long saved() {
            return this.saved;
        }

        /** True, wenn ein Admin etwas geaendert hat, das noch auf den naechsten Join wartet. */
        public boolean pending() {
            return this.pending;
        }

        public int items() {
            return this.items;
        }
    }

    private static OfflineStore instance;

    private final AdminFieldPlugin plugin;

    private OfflineStore(AdminFieldPlugin plugin) {
        this.plugin = plugin;
    }

    public static synchronized void start(AdminFieldPlugin plugin) {
        if (instance != null && instance.plugin == plugin) {
            return;
        }
        OfflineStore fresh = new OfflineStore(plugin);
        instance = fresh;
        try {
            Bukkit.getPluginManager().registerEvents(fresh, (Plugin) plugin);
        } catch (Throwable t) {
            plugin.getLogger().warning("Offline-Inventare konnten sich nicht einhaengen ("
                    + t.getClass().getSimpleName() + ").");
        }
    }

    public static synchronized void stop(AdminFieldPlugin plugin) {
        if (instance != null && instance.plugin == plugin) {
            // Wer beim Herunterfahren noch da ist, bekommt jetzt sein Abbild.
            for (Player online : Bukkit.getOnlinePlayers()) {
                instance.snapshot(online);
            }
            instance = null;
        }
    }

    public static OfflineStore instance() {
        return instance;
    }

    // ------------------------------------------------------------------ Lesen

    /** Alle bekannten Abbilder, zuletzt offline gegangene zuerst. */
    public List<Entry> all() {
        List<Entry> out = new ArrayList<>();
        File folder = this.folder();
        File[] files = folder.listFiles();
        if (files == null) {
            return out;
        }
        for (File file : files) {
            String fileName = file.getName();
            if (!fileName.toLowerCase(Locale.ROOT).endsWith(".yml")) {
                continue;
            }
            UUID id;
            try {
                id = UUID.fromString(fileName.substring(0, fileName.length() - 4));
            } catch (Throwable ignored) {
                continue;
            }
            try {
                YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
                out.add(new Entry(id, data.getString("name", "Unbekannt"), data.getLong("saved"),
                        data.getBoolean("pending"), count(read(data, "inventory", INVENTORY_SLOTS))
                                + count(read(data, "ender", ENDER_SLOTS))));
            } catch (Throwable ignored) {
                // Kaputte Datei einfach auslassen statt das ganze Menue zu verlieren.
            }
        }
        out.sort(Comparator.comparingLong(Entry::saved).reversed());
        return out;
    }

    public Entry entry(UUID id) {
        for (Entry candidate : this.all()) {
            if (candidate.id.equals(id)) {
                return candidate;
            }
        }
        return null;
    }

    public ItemStack[] inventory(UUID id) {
        return read(this.load(id), "inventory", INVENTORY_SLOTS);
    }

    public ItemStack[] ender(UUID id) {
        return read(this.load(id), "ender", ENDER_SLOTS);
    }

    // ------------------------------------------------------------------ Schreiben

    /**
     * Legt das geaenderte Abbild ab und merkt es als offen vor.
     *
     * @param inventory 41 Felder oder {@code null}, wenn unveraendert
     * @param ender     27 Felder oder {@code null}, wenn unveraendert
     */
    public boolean write(UUID id, ItemStack[] inventory, ItemStack[] ender) {
        YamlConfiguration data = this.load(id);
        if (inventory != null) {
            put(data, "inventory", inventory, INVENTORY_SLOTS);
        }
        if (ender != null) {
            put(data, "ender", ender, ENDER_SLOTS);
        }
        data.set("pending", true);
        return this.save(id, data);
    }

    /**
     * Sichert sofort ein Abbild, ohne auf das Verlassen zu warten.
     *
     * <p>Gedacht fuer den Moment, bevor jemand hinausgeworfen wird: danach ist er in der
     * Offline-Liste, und zwar mit genau dem Inventar, das ihn hinausgeworfen hat.
     */
    public void capture(Player player) {
        if (player != null) {
            this.snapshot(player);
        }
    }

    // ------------------------------------------------------------------ Events

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.snapshot(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        YamlConfiguration data = this.load(player.getUniqueId());
        if (!data.getBoolean("pending")) {
            return;
        }
        try {
            ItemStack[] inventory = read(data, "inventory", INVENTORY_SLOTS);
            ItemStack[] ender = read(data, "ender", ENDER_SLOTS);
            apply(player.getInventory(), inventory);
            apply(player.getEnderChest(), ender);
            try {
                player.updateInventory();
            } catch (Throwable ignored) {
            }
            this.plugin.getLogger().info("Offline vorgemerkte Aenderungen an "
                    + player.getName() + " uebernommen.");
        } catch (Throwable t) {
            this.plugin.getLogger().warning("Offline-Aenderungen an " + player.getName()
                    + " liessen sich nicht uebernehmen (" + t.getClass().getSimpleName() + ").");
            return;
        }
        // Erledigt - das Abbild spiegelt jetzt den echten Stand.
        data.set("pending", false);
        this.save(player.getUniqueId(), data);
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin() == this.plugin) {
            stop(this.plugin);
        }
    }

    // ------------------------------------------------------------------ Innereien

    /** Sichert den aktuellen Stand eines Spielers als Abbild. */
    private void snapshot(Player player) {
        try {
            YamlConfiguration data = this.load(player.getUniqueId());
            if (data.getBoolean("pending")) {
                // Ein Admin hat etwas vorgemerkt, das noch nicht angekommen ist.
                // Das darf der Ausstieg nicht ueberschreiben.
                return;
            }
            data.set("name", player.getName());
            data.set("saved", System.currentTimeMillis());
            data.set("pending", false);
            put(data, "inventory", player.getInventory().getContents(), INVENTORY_SLOTS);
            put(data, "ender", player.getEnderChest().getContents(), ENDER_SLOTS);
            this.save(player.getUniqueId(), data);
        } catch (Throwable t) {
            this.plugin.getLogger().warning("Abbild von " + player.getName()
                    + " konnte nicht gesichert werden (" + t.getClass().getSimpleName() + ").");
        }
    }

    private static void apply(Inventory target, ItemStack[] items) {
        if (target == null) {
            return;
        }
        int size = Math.min(items.length, target.getSize());
        for (int slot = 0; slot < size; slot++) {
            target.setItem(slot, items[slot]);
        }
    }

    private static ItemStack[] read(YamlConfiguration data, String path, int slots) {
        ItemStack[] out = new ItemStack[slots];
        ConfigurationSection section = data.getConfigurationSection(path);
        if (section == null) {
            return out;
        }
        for (String key : section.getKeys(false)) {
            int slot;
            try {
                slot = Integer.parseInt(key);
            } catch (NumberFormatException ignored) {
                continue;
            }
            if (slot < 0 || slot >= slots) {
                continue;
            }
            try {
                out[slot] = section.getItemStack(key);
            } catch (Throwable ignored) {
                // Ein Item, das diese Serverversion nicht mehr kennt - Feld bleibt leer.
            }
        }
        return out;
    }

    private static void put(YamlConfiguration data, String path, ItemStack[] items, int slots) {
        data.set(path, null);
        if (items == null) {
            return;
        }
        for (int slot = 0; slot < slots && slot < items.length; slot++) {
            ItemStack item = items[slot];
            if (item == null || item.getType().isAir()) {
                continue;
            }
            data.set(path + "." + slot, item);
        }
    }

    private static int count(ItemStack[] items) {
        int found = 0;
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir()) {
                found++;
            }
        }
        return found;
    }

    private File folder() {
        File folder = new File(this.plugin.getDataFolder(), FOLDER);
        if (!folder.isDirectory()) {
            folder.mkdirs();
        }
        return folder;
    }

    private File file(UUID id) {
        return new File(this.folder(), id + ".yml");
    }

    private YamlConfiguration load(UUID id) {
        File file = this.file(id);
        if (!file.isFile()) {
            return new YamlConfiguration();
        }
        try {
            return YamlConfiguration.loadConfiguration(file);
        } catch (Throwable ignored) {
            return new YamlConfiguration();
        }
    }

    private boolean save(UUID id, YamlConfiguration data) {
        try {
            data.save(this.file(id));
            return true;
        } catch (Throwable t) {
            this.plugin.getLogger().severe("Offline-Abbild konnte nicht gespeichert werden: "
                    + t.getClass().getSimpleName());
            return false;
        }
    }
}
