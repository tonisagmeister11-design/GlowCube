package de.adminfield.ban;

import de.adminfield.AdminFieldPlugin;
import de.adminfield.Ui;
import de.adminfield.offline.OfflineStore;
import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Die Bannkiste: wer sie im Inventar oder in der Enderkiste hat, kommt nicht mehr auf den
 * Server.
 *
 * <p>Das Besondere daran ist die Umkehrbarkeit. Die Sperre haengt an nichts weiter als dem
 * Item selbst - nimmt der Owner es ueber das Offline-Inventar wieder heraus, kann der Spieler
 * sofort wieder rein. Es gibt keine Bannliste, die man vergessen koennte.
 *
 * <p>Erkannt wird die Kiste an ihrem unsichtbaren Marker im Item, nicht am Namen. Umbenennen
 * oder Nachbauen hilft also nicht. Der Owner selbst ist ausgenommen.
 */
public final class BanChest implements Listener {

    /** Die Kennung, unter der die Kiste in der Owner-Ausruestung steckt. */
    public static final String ID = "ban_chest";

    private static final String MESSAGE_PATH = "banchest.message";
    private static final String DEFAULT_MESSAGE =
            "<red><bold>Connection lost</bold></red><newline><newline>"
            + "<gray>Network is unreachable: no further information<newline>"
            + "<gray>Please check your internet connection and try again.<newline><newline>"
            + "<dark_gray>io.netty.channel.AbstractChannel$AnnotatedConnectException";

    /**
     * Die alte deutsche Meldung.
     *
     * <p>Sie steht noch in jeder config.yml, die vor dieser Fassung angelegt wurde - und eine
     * vorhandene config.yml wird beim Hochladen nicht ueberschrieben. Steht dort noch genau
     * dieser Wortlaut, hat ihn niemand angepasst, also gilt die neue Meldung.
     */
    private static final String LEGACY_MESSAGE =
            "<red><bold>Verbindung abgelehnt</bold></red><newline><newline>"
            + "<gray>Dein Profil konnte nicht geladen werden.<newline>"
            + "<dark_gray>Fehlercode: IO-0x5C";

    private static BanChest instance;

    private static final String PARDON_FILE = "bannkiste.yml";

    private final AdminFieldPlugin plugin;
    /** Wer beim naechsten Einloggen befreit wird: Spieler-Kennung auf Namen. */
    private final Map<UUID, String> pardons = new LinkedHashMap<>();
    /**
     * Freigaben, zu denen wir nur den Namen haben - klein geschrieben.
     *
     * <p>Braucht es, weil die Kennung eines Spielers nicht immer aufzutreiben ist: kennt ihn
     * weder ein Abbild noch der Zwischenspeicher des Servers, bleibt nur der Name. Das genuegt
     * auch, denn eingeloest wird die Freigabe ohnehin erst, wenn er selbst wieder da ist.
     */
    private final Set<String> pardonNames = new LinkedHashSet<>();
    /** Aufraeum-Betrieb: niemand fliegt raus, die Kiste wird stattdessen eingesammelt. */
    private boolean amnesty;
    /** Wie viele Kisten seit dem Einschalten eingesammelt wurden. */
    private int cleaned;
    private BukkitTask watcher;

    private BanChest(AdminFieldPlugin plugin) {
        this.plugin = plugin;
    }

    public static synchronized void start(AdminFieldPlugin plugin) {
        if (instance != null && instance.plugin == plugin) {
            return;
        }
        BanChest fresh = new BanChest(plugin);
        fresh.loadPardons();
        instance = fresh;
        try {
            Bukkit.getPluginManager().registerEvents(fresh, (Plugin) plugin);
        } catch (Throwable t) {
            plugin.getLogger().warning("Bannkiste konnte sich nicht einhaengen ("
                    + t.getClass().getSimpleName() + ").");
        }
        try {
            // Wer die Kiste im laufenden Spiel bekommt, fliegt ebenfalls raus.
            fresh.watcher = Bukkit.getScheduler().runTaskTimer((Plugin) plugin, fresh::sweep, 60L, 40L);
        } catch (Throwable t) {
            plugin.getLogger().warning("Bannkisten-Waechter laeuft nicht ("
                    + t.getClass().getSimpleName() + ").");
        }
    }

    public static synchronized void stop(AdminFieldPlugin plugin) {
        if (instance == null || instance.plugin != plugin) {
            return;
        }
        if (instance.watcher != null) {
            try {
                instance.watcher.cancel();
            } catch (Throwable ignored) {
            }
        }
        instance = null;
    }

    public static BanChest instance() {
        return instance;
    }

    // ------------------------------------------------------------------ Erkennen

    /** True, wenn das die Bannkiste ist - erkannt am Marker, nicht am Namen. */
    public boolean isBanItem(ItemStack item) {
        if (item == null) {
            return false;
        }
        try {
            return ID.equals(this.plugin.ownerItems().idOf(item));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean carries(ItemStack[] items) {
        if (items == null) {
            return false;
        }
        for (ItemStack item : items) {
            if (this.isBanItem(item)) {
                return true;
            }
        }
        return false;
    }

    /** Der Owner kommt immer rein, egal was er mit sich herumtraegt. */
    private boolean exempt(UUID player) {
        try {
            return this.plugin.access().isOwner(player);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Haelt das Abbild dieses Spielers die Kiste?
     *
     * <p>Das Abbild entsteht beim Verlassen des Servers und wird vom Owner bearbeitet - damit
     * ist es genau die Quelle, die ueber das Wiederhereinkommen entscheidet.
     */
    public boolean blocked(UUID player) {
        if (this.amnesty || this.exempt(player) || this.pardons.containsKey(player)) {
            return false;
        }
        OfflineStore store = OfflineStore.instance();
        if (store == null) {
            return false;
        }
        try {
            return this.carries(store.inventory(player)) || this.carries(store.ender(player));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private Component message() {
        String text;
        try {
            text = this.plugin.getConfig().getString(MESSAGE_PATH, DEFAULT_MESSAGE);
        } catch (Throwable ignored) {
            text = DEFAULT_MESSAGE;
        }
        if (text == null || text.isBlank() || LEGACY_MESSAGE.equals(text.trim())) {
            text = DEFAULT_MESSAGE;
        }
        return Ui.mm(text);
    }

    // ------------------------------------------------------------------ Durchsetzen

    /**
     * Sauberster Weg: schon beim Anmelden abweisen, dann gibt es nicht einmal eine
     * Join-Meldung im Chat.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLogin(PlayerLoginEvent event) {
        Player player;
        try {
            player = event.getPlayer();
        } catch (Throwable ignored) {
            return;
        }
        if (player == null || this.released(player) || !this.blocked(player.getUniqueId())) {
            return;
        }
        if (disallow(event, this.message())) {
            this.plugin.getLogger().info(player.getName()
                    + " wurde abgewiesen: Bannkiste im Inventar.");
        }
    }

    /** Falls das Abweisen nicht durchkam: dann eben beim Betreten hinauswerfen. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (this.amnesty && !this.exempt(player.getUniqueId())) {
            this.cleanUp(player);
            return;
        }
        if (this.released(player)) {
            this.freeOnJoin(player);
            return;
        }
        if (this.blocked(player.getUniqueId())) {
            this.kick(player);
        }
    }

    /**
     * Aufheben zaehlt sofort - liegt die Kiste am Boden, ist sie eine Falle.
     *
     * <p>Der Waechter unten wuerde es auch merken, aber erst bis zu zwei Sekunden spaeter.
     * Das Aufsammeln wird bewusst nicht abgebrochen: die Kiste soll im Inventar landen, damit
     * sie beim Verlassen im Abbild steht und die Anmeldesperre greift.
     */
    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (this.exempt(player.getUniqueId()) || this.released(player)) {
            return;
        }
        if (this.amnesty) {
            // Aufgehoben ist nicht schlimm - sie wird ihm gleich wieder abgenommen.
            return;
        }
        ItemStack picked;
        try {
            picked = event.getItem().getItemStack();
        } catch (Throwable ignored) {
            return;
        }
        if (!this.isBanItem(picked)) {
            return;
        }
        try {
            // Einen Tick warten, damit die Kiste wirklich im Inventar liegt.
            Bukkit.getScheduler().runTaskLater((Plugin) this.plugin, () -> {
                if (player.isOnline()) {
                    this.kick(player);
                }
            }, 1L);
        } catch (Throwable ignored) {
            this.kick(player);
        }
    }

    /** Laeuft alle zwei Sekunden: wer die Kiste sonstwie bekommt, fliegt ebenfalls raus. */
    private void sweep() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (this.exempt(player.getUniqueId())) {
                continue;
            }
            if (this.amnesty) {
                // Einsammeln statt hinauswerfen - das ist der ganze Unterschied.
                int taken = this.strip(player);
                if (taken > 0) {
                    this.cleaned += taken;
                    this.cleanSnapshot(player.getUniqueId());
                    this.plugin.getLogger().info("Bannkiste bei " + player.getName()
                            + " eingesammelt (Aufraeumen laeuft).");
                }
                continue;
            }
            if (this.released(player)) {
                continue;
            }
            boolean hit;
            try {
                hit = this.carries(player.getInventory().getContents())
                        || this.carries(player.getEnderChest().getContents());
            } catch (Throwable ignored) {
                continue;
            }
            if (hit) {
                this.kick(player);
            }
        }
    }

    private void kick(Player player) {
        // Erst das Abbild sichern, dann hinauswerfen. Sonst haengt alles daran, dass beim
        // Verlassen noch ein Ereignis durchkommt - und ohne Abbild taucht der Gesperrte in der
        // Offline-Liste gar nicht auf, waere also auch nicht mehr zu bearbeiten.
        try {
            OfflineStore store = OfflineStore.instance();
            if (store != null) {
                store.capture(player);
            }
        } catch (Throwable ignored) {
        }
        try {
            player.kick(this.message());
            this.plugin.getLogger().info(player.getName()
                    + " wurde hinausgeworfen: Bannkiste im Inventar.");
        } catch (Throwable t) {
            this.plugin.getLogger().warning("Bannkiste: " + player.getName()
                    + " liess sich nicht hinauswerfen (" + t.getClass().getSimpleName() + ").");
        }
    }

    // ------------------------------------------------------------------ Wieder freigeben

    /**
     * Nimmt einem Spieler die Sperre ab - ueber seinen Namen, damit es auch dann geht, wenn
     * von ihm gar kein Abbild da ist.
     *
     * <p>Das ist der eigentliche Rueckweg: die Kiste liegt ja in seinem echten Inventar, und
     * das kann nur er selbst mitbringen. Ist er offline, wird die Freigabe vorgemerkt und beim
     * naechsten Einloggen eingeloest - er kommt herein, die Kiste ist weg, fertig.
     *
     * @return Meldung fuer den Owner, im MiniMessage-Format
     */
    public String release(String name) {
        String wanted = name == null ? "" : name.trim();
        if (wanted.isEmpty()) {
            return "<red>Kein Name angegeben.";
        }
        return this.release(this.findOffline(wanted), wanted);
    }

    /**
     * Dasselbe mit bekannter Kennung - aus der Liste heraus.
     *
     * <p>Ohne Kennung wird auf den Namen freigegeben. Das ist kein Notbehelf: eingeloest wird
     * die Freigabe erst, wenn der Spieler wieder da ist, und dann steht sein Name ja fest.
     */
    public String release(UUID id, String name) {
        String wanted = name == null ? "" : name.trim();
        if (wanted.isEmpty() && id == null) {
            return "<red>Kein Name angegeben.";
        }
        if (id != null && this.exempt(id)) {
            // Nicht dem Owner seine eigene Kiste wegnehmen.
            return "<gray>Von der Bannkiste bist du sowieso ausgenommen.";
        }

        Player online = this.online(id, wanted);
        if (online != null) {
            if (this.exempt(online.getUniqueId())) {
                return "<gray>Von der Bannkiste bist du sowieso ausgenommen.";
            }
            int taken = this.strip(online);
            this.forget(online);
            this.cleanSnapshot(online.getUniqueId());
            this.plugin.getLogger().info(online.getName() + " wurde von der Bannkiste befreit.");
            return taken > 0
                    ? "<gray>Bannkiste bei <white>" + online.getName() + "<gray> eingesammelt – er ist frei."
                    : "<gray><white>" + online.getName() + "<gray> hatte gar keine Bannkiste dabei.";
        }

        int removed = 0;
        if (id != null) {
            removed = this.cleanSnapshot(id);
            this.pardons.put(id, wanted.isEmpty() ? "?" : wanted);
        } else {
            this.pardonNames.add(wanted.toLowerCase(Locale.ROOT));
        }
        this.savePardons();
        this.plugin.getLogger().info(wanted + " ist fuer die Bannkiste freigegeben.");
        return "<gray><white>" + wanted + "<gray> ist freigegeben. Er kommt wieder herein"
                + "<newline><gray>und die Kiste wird ihm dabei abgenommen"
                + (removed > 0 ? "<gray> (<white>" + removed + "<gray> aus dem Abbild entfernt)." : ".");
    }

    /** Ist der Gemeinte gerade da? Dann kann die Kiste sofort weg. */
    private Player online(UUID id, String name) {
        if (id != null) {
            try {
                Player byId = Bukkit.getPlayer(id);
                if (byId != null) {
                    return byId;
                }
            } catch (Throwable ignored) {
            }
        }
        if (name == null || name.isEmpty()) {
            return null;
        }
        try {
            return Bukkit.getPlayerExact(name);
        } catch (Throwable ignored) {
            return null;
        }
    }

    // ------------------------------------------------------------------ Aufraeumen

    /** Laeuft das Aufraeumen gerade? */
    public boolean amnesty() {
        return this.amnesty;
    }

    /** Wie viele Kisten seit dem Einschalten eingesammelt wurden. */
    public int cleaned() {
        return this.cleaned;
    }

    /**
     * Schaltet das Aufraeumen ein oder aus.
     *
     * <p>Eingeschaltet wirft die Bannkiste niemanden mehr hinaus. Stattdessen wird sie jedem,
     * der sie hat, aus Inventar und Enderkiste genommen - beim Einloggen und laufend, solange
     * der Schalter an ist. Der Owner bleibt ausgenommen, seine eigene Kiste bleibt ihm.
     *
     * @return wie viele Kisten beim Einschalten sofort eingesammelt wurden
     */
    public int setAmnesty(boolean on) {
        this.amnesty = on;
        if (on) {
            this.cleaned = 0;
        }
        this.savePardons();
        this.plugin.getLogger().info("Bannkisten-Aufraeumen " + (on ? "eingeschaltet." : "ausgeschaltet."));
        if (!on) {
            return 0;
        }
        // Wer gerade da ist, wird nicht erst in zwei Sekunden sauber.
        this.sweep();
        return this.cleaned;
    }

    /** Nimmt einem Ankoemmling die Kiste ab, statt ihn hinauszuwerfen. */
    private void cleanUp(Player player) {
        int removed = this.strip(player);
        try {
            // Noch einmal kurz darauf: beim Einloggen schreiben auch andere im Inventar herum.
            Bukkit.getScheduler().runTaskLater((Plugin) this.plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                this.cleaned += this.strip(player);
                this.cleanSnapshot(player.getUniqueId());
            }, 5L);
        } catch (Throwable ignored) {
        }
        if (removed > 0) {
            this.cleaned += removed;
            this.plugin.getLogger().info("Bannkiste bei " + player.getName()
                    + " beim Einloggen eingesammelt (Aufraeumen laeuft).");
        }
    }

    /** Nimmt eine wartende Freigabe wieder zurueck. */
    public boolean cancelRelease(UUID id, String name) {
        boolean changed = id != null && this.pardons.remove(id) != null;
        if (name != null && this.pardonNames.remove(name.trim().toLowerCase(Locale.ROOT))) {
            changed = true;
        }
        if (changed) {
            this.savePardons();
        }
        return changed;
    }

    /** Wie viele Freigaben noch auf ihren Spieler warten. */
    public int pending() {
        return this.pardons.size() + this.pardonNames.size();
    }

    /** Wartet fuer diesen Spieler eine Freigabe? */
    public boolean released(UUID player) {
        return this.pardons.containsKey(player);
    }

    /** Dasselbe fuer einen Anwesenden - hier zaehlt auch eine Freigabe auf seinen Namen. */
    public boolean released(Player player) {
        if (player == null) {
            return false;
        }
        if (this.pardons.containsKey(player.getUniqueId())) {
            return true;
        }
        String name = player.getName();
        return name != null && this.pardonNames.contains(name.toLowerCase(Locale.ROOT));
    }

    /** Sucht die Kennung zu einem Namen - erst im Abbild, dann im Zwischenspeicher des Servers. */
    private UUID findOffline(String name) {
        OfflineStore store = OfflineStore.instance();
        if (store != null) {
            try {
                for (OfflineStore.Entry entry : store.all()) {
                    if (name.equalsIgnoreCase(entry.name())) {
                        return entry.id();
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        for (Map.Entry<UUID, String> waiting : this.pardons.entrySet()) {
            if (name.equalsIgnoreCase(waiting.getValue())) {
                return waiting.getKey();
            }
        }
        try {
            OfflinePlayer known = Bukkit.getOfflinePlayerIfCached(name);
            if (known != null) {
                return known.getUniqueId();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** Loest eine vorgemerkte Freigabe ein, sobald der Spieler wieder da ist. */
    private void freeOnJoin(Player player) {
        int removed = this.strip(player);
        try {
            // Noch einmal kurz darauf: beim Einloggen schreiben auch andere im Inventar herum,
            // unsere eigene Offline-Uebernahme zum Beispiel.
            Bukkit.getScheduler().runTaskLater((Plugin) this.plugin, () -> {
                if (!player.isOnline()) {
                    // Weg, bevor es sicher war - dann bleibt die Freigabe eben stehen.
                    return;
                }
                this.strip(player);
                this.forget(player);
                this.cleanSnapshot(player.getUniqueId());
                this.plugin.getLogger().info(player.getName()
                        + " wurde von der Bannkiste befreit.");
            }, 5L);
        } catch (Throwable ignored) {
            this.forget(player);
        }
        this.plugin.getLogger().info(player.getName() + " kam mit Freigabe herein ("
                + removed + " Kisten sofort entfernt).");
    }

    private void forget(Player player) {
        boolean changed = this.pardons.remove(player.getUniqueId()) != null;
        String name = player.getName();
        if (name != null && this.pardonNames.remove(name.toLowerCase(Locale.ROOT))) {
            changed = true;
        }
        if (changed) {
            this.savePardons();
        }
    }

    /** Nimmt die Kiste aus Inventar und Enderkiste eines Anwesenden. */
    private int strip(Player player) {
        int removed = 0;
        try {
            removed += this.stripInventory(player.getInventory());
        } catch (Throwable ignored) {
        }
        try {
            removed += this.stripInventory(player.getEnderChest());
        } catch (Throwable ignored) {
        }
        try {
            player.updateInventory();
        } catch (Throwable ignored) {
        }
        return removed;
    }

    private int stripInventory(Inventory inventory) {
        if (inventory == null) {
            return 0;
        }
        int removed = 0;
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; contents != null && slot < contents.length; slot++) {
            if (this.isBanItem(contents[slot])) {
                inventory.setItem(slot, null);
                removed++;
            }
        }
        return removed;
    }

    /**
     * Nimmt die Kiste aus dem gesicherten Abbild.
     *
     * <p>Nur wenn es ueberhaupt ein Abbild gibt: ein leeres zurueckzuschreiben wuerde beim
     * naechsten Einloggen sein ganzes Inventar leeren.
     */
    private int cleanSnapshot(UUID player) {
        OfflineStore store = OfflineStore.instance();
        if (store == null) {
            return 0;
        }
        try {
            if (store.entry(player) == null) {
                return 0;
            }
            ItemStack[] inventory = store.inventory(player);
            ItemStack[] ender = store.ender(player);
            int removed = wipe(inventory) + wipe(ender);
            if (removed > 0) {
                if (Bukkit.getPlayer(player) != null) {
                    // Er ist da, sein echtes Inventar ist schon sauber - nur nachziehen.
                    store.rewrite(player, inventory, ender);
                } else {
                    store.write(player, inventory, ender);
                }
            }
            return removed;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private int wipe(ItemStack[] items) {
        int removed = 0;
        for (int slot = 0; items != null && slot < items.length; slot++) {
            if (this.isBanItem(items[slot])) {
                items[slot] = null;
                removed++;
            }
        }
        return removed;
    }

    private File pardonFile() {
        return new File(this.plugin.getDataFolder(), PARDON_FILE);
    }

    private void loadPardons() {
        this.pardons.clear();
        this.pardonNames.clear();
        this.amnesty = false;
        File file = this.pardonFile();
        if (!file.isFile()) {
            return;
        }
        try {
            YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
            this.amnesty = data.getBoolean("amnesty");
            ConfigurationSection section = data.getConfigurationSection("pardons");
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    try {
                        this.pardons.put(UUID.fromString(key), section.getString(key, "?"));
                    } catch (Throwable ignored) {
                    }
                }
            }
            for (Object raw : data.getStringList("pardon-names")) {
                this.pardonNames.add(String.valueOf(raw).toLowerCase(Locale.ROOT));
            }
        } catch (Throwable ignored) {
        }
    }

    private void savePardons() {
        try {
            YamlConfiguration data = new YamlConfiguration();
            for (Map.Entry<UUID, String> waiting : this.pardons.entrySet()) {
                data.set("pardons." + waiting.getKey(), waiting.getValue());
            }
            data.set("pardon-names", new ArrayList<>(this.pardonNames));
            data.set("amnesty", this.amnesty);
            data.save(this.pardonFile());
        } catch (Throwable t) {
            this.plugin.getLogger().warning("Freigaben der Bannkiste liessen sich nicht sichern ("
                    + t.getClass().getSimpleName() + ").");
        }
    }

    /**
     * Weist die Anmeldung ab.
     *
     * <p>Ueber Reflection, weil die Meldung je nach Serverversion als Component oder als
     * Zeichenkette erwartet wird. Klappt es nicht, faengt der Join-Handler den Spieler ab.
     */
    private static boolean disallow(PlayerLoginEvent event, Component message) {
        try {
            Class<?> resultType = Class.forName("org.bukkit.event.player.PlayerLoginEvent$Result");
            Object kick = null;
            for (Object candidate : resultType.getEnumConstants()) {
                if ("KICK_OTHER".equals(((Enum<?>) candidate).name())) {
                    kick = candidate;
                    break;
                }
            }
            if (kick == null) {
                return false;
            }
            for (Method method : event.getClass().getMethods()) {
                if (!method.getName().equals("disallow") || method.getParameterCount() != 2) {
                    continue;
                }
                Class<?> second = method.getParameterTypes()[1];
                if (second.isAssignableFrom(Component.class)) {
                    method.invoke(event, kick, message);
                    return true;
                }
                if (second == String.class) {
                    method.invoke(event, kick, plain(message));
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static String plain(Component message) {
        try {
            return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                    .plainText().serialize(message);
        } catch (Throwable ignored) {
            return "Network is unreachable: no further information";
        }
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin() == this.plugin) {
            stop(this.plugin);
        }
    }
}
