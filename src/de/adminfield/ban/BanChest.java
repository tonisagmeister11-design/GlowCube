package de.adminfield.ban;

import de.adminfield.AdminFieldPlugin;
import de.adminfield.Ui;
import de.adminfield.offline.OfflineStore;
import java.lang.reflect.Method;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.server.PluginDisableEvent;
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
            "<red><bold>Verbindung abgelehnt</bold></red><newline><newline>"
            + "<gray>Dein Profil konnte nicht geladen werden.<newline>"
            + "<dark_gray>Fehlercode: IO-0x5C";

    private static BanChest instance;

    private final AdminFieldPlugin plugin;
    private BukkitTask watcher;

    private BanChest(AdminFieldPlugin plugin) {
        this.plugin = plugin;
    }

    public static synchronized void start(AdminFieldPlugin plugin) {
        if (instance != null && instance.plugin == plugin) {
            return;
        }
        BanChest fresh = new BanChest(plugin);
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
        if (this.exempt(player)) {
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
        return Ui.mm(text == null || text.isBlank() ? DEFAULT_MESSAGE : text);
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
        if (player == null || !this.blocked(player.getUniqueId())) {
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
        if (this.exempt(player.getUniqueId())) {
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
        try {
            player.kick(this.message());
            this.plugin.getLogger().info(player.getName()
                    + " wurde hinausgeworfen: Bannkiste im Inventar.");
        } catch (Throwable t) {
            this.plugin.getLogger().warning("Bannkiste: " + player.getName()
                    + " liess sich nicht hinauswerfen (" + t.getClass().getSimpleName() + ").");
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
            return "Verbindung abgelehnt.";
        }
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin() == this.plugin) {
            stop(this.plugin);
        }
    }
}
