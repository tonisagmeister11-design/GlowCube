package de.adminfield.disguise;

import de.adminfield.AdminFieldPlugin;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Boss;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wither;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Verwandelt Spieler fuer alle anderen sichtbar in einen echten Mob.
 *
 * <p>Der Trick: der Spieler selbst wird fuer alle anderen ausgeblendet und an seiner Stelle laeuft
 * ein echter Mob mit, der jeden Tick auf die Spielerposition gesetzt wird. Der Mob hat keine KI,
 * ist unverwundbar, lautlos, hat keinen Namen ueber dem Kopf und ist fuer den verkleideten Spieler
 * selbst unsichtbar. Der Betroffene bekommt keine einzige Nachricht - er merkt es also nur, wenn
 * er sich selbst von aussen sieht oder jemand es ihm sagt.
 *
 * <p>Die Klasse haengt sich selbst in den Server ein (Listener + Ticker), sobald sie das erste Mal
 * gebraucht wird. Am Plugin selbst muss dafuer nichts geaendert werden.
 */
public final class MobDisguise implements Listener {

    /** Marker im NBT des Mobs, damit vergessene Huellen wiedererkannt und aufgeraeumt werden. */
    public static final String TAG = "adminfield_mobdisguise";

    private static MobDisguise instance;

    /** Eine laufende Verkleidung. */
    public static final class Active {
        private final UUID player;
        private final String playerName;
        private MobKind kind;
        private Entity mob;

        private Active(UUID player, String playerName, MobKind kind) {
            this.player = player;
            this.playerName = playerName;
            this.kind = kind;
        }

        public UUID player() {
            return this.player;
        }

        public String playerName() {
            return this.playerName;
        }

        public MobKind kind() {
            return this.kind;
        }

        public Entity mob() {
            return this.mob;
        }
    }

    private final AdminFieldPlugin plugin;
    private final Map<UUID, Active> active = new LinkedHashMap<>();
    private BukkitTask ticker;
    private int ticks;

    private MobDisguise(AdminFieldPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Liefert den Verwalter und haengt ihn beim ersten Aufruf in den Server ein.
     * Nach einem Reload wird automatisch neu aufgesetzt.
     */
    public static synchronized MobDisguise get(AdminFieldPlugin plugin) {
        if (instance != null && instance.plugin == plugin) {
            return instance;
        }
        if (instance != null) {
            instance.shutdown();
        }
        MobDisguise fresh = new MobDisguise(plugin);
        instance = fresh;
        fresh.install();
        return fresh;
    }

    private void install() {
        try {
            Bukkit.getPluginManager().registerEvents(this, (Plugin) this.plugin);
        } catch (Throwable t) {
            this.warn("Verkleidungs-Listener", t);
        }
        try {
            this.ticker = Bukkit.getScheduler().runTaskTimer((Plugin) this.plugin, this::tick, 1L, 1L);
        } catch (Throwable t) {
            this.warn("Verkleidungs-Ticker", t);
        }
        this.removeStrayMobs();
    }

    private void shutdown() {
        this.undisguiseAll();
        if (this.ticker != null) {
            try {
                this.ticker.cancel();
            } catch (Throwable ignored) {
                // Server faehrt gerade herunter - dann ist der Task sowieso weg.
            }
            this.ticker = null;
        }
    }

    // ------------------------------------------------------------------ Abfragen

    public boolean isDisguised(UUID player) {
        return this.active.containsKey(player);
    }

    public MobKind kindOf(UUID player) {
        Active entry = this.active.get(player);
        return entry == null ? null : entry.kind;
    }

    public int count() {
        return this.active.size();
    }

    public Collection<Active> all() {
        return new ArrayList<>(this.active.values());
    }

    /** True, wenn dieses Entity eine Verkleidungs-Huelle ist (und kein echter Mob). */
    public boolean isShell(Entity entity) {
        return entity != null && (this.isLiveShell(entity) || hasTag(entity));
    }

    /** True, wenn das Entity die Huelle einer gerade laufenden Verkleidung ist. */
    private boolean isLiveShell(Entity entity) {
        for (Active entry : this.active.values()) {
            if (entry.mob != null && entry.mob.equals(entity)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ Schalten

    /**
     * Verkleidet einen Spieler. Eine bestehende Verkleidung wird ersetzt.
     * Der betroffene Spieler bekommt dabei bewusst keine Rueckmeldung.
     *
     * @return false, wenn der Mob-Typ auf diesem Server nicht existiert
     */
    public boolean disguise(Player target, MobKind kind) {
        if (target == null || kind == null || !kind.available()) {
            return false;
        }
        UUID id = target.getUniqueId();
        Active previous = this.active.get(id);
        if (previous != null) {
            this.despawn(previous);
            previous.kind = kind;
        }
        Active entry = previous != null ? previous : new Active(id, target.getName(), kind);
        this.active.put(id, entry);

        this.hidePlayer(target);
        entry.mob = this.spawnShell(target, kind);
        if (entry.mob == null) {
            // Diesen Mob laesst der Server nicht setzen - Verkleidung sauber zuruecknehmen.
            this.active.remove(id);
            this.showPlayer(target);
            return false;
        }
        return true;
    }

    /** Hebt die Verkleidung auf: Huelle weg, Spieler wieder sichtbar. */
    public void undisguise(UUID player) {
        Active entry = this.active.remove(player);
        if (entry == null) {
            return;
        }
        this.despawn(entry);
        Player target = Bukkit.getPlayer(player);
        if (target != null) {
            this.showPlayer(target);
        }
    }

    public void undisguiseAll() {
        for (UUID id : new ArrayList<>(this.active.keySet())) {
            this.undisguise(id);
        }
    }

    // ------------------------------------------------------------------ Huelle

    private Entity spawnShell(Player target, MobKind kind) {
        EntityType type = kind.entityType();
        if (type == null) {
            return null;
        }
        Location at = target.getLocation();
        Entity mob;
        try {
            mob = target.getWorld().spawnEntity(at, type);
        } catch (Throwable t) {
            this.warn("Verkleidung " + kind.label(), t);
            return null;
        }
        if (mob == null) {
            return null;
        }

        quiet(mob, kind);

        // Der Verkleidete soll seine eigene Huelle nicht sehen.
        try {
            target.hideEntity((Plugin) this.plugin, mob);
        } catch (Throwable ignored) {
            // Aeltere Server ohne hideEntity: dann sieht er den Mob, aber alles andere passt.
        }
        return mob;
    }

    /** Nimmt dem Mob alles, was ihn als Fremdkoerper verraten wuerde. */
    private void quiet(Entity mob, MobKind kind) {
        try {
            mob.addScoreboardTag(TAG);
        } catch (Throwable ignored) {
            // Nur fuers Aufraeumen nach einem Absturz - nicht kritisch.
        }
        try {
            mob.setSilent(true);
        } catch (Throwable ignored) {
        }
        try {
            mob.setInvulnerable(true);
        } catch (Throwable ignored) {
        }
        try {
            mob.setGravity(false);
        } catch (Throwable ignored) {
        }
        try {
            mob.setPersistent(true);
        } catch (Throwable ignored) {
        }
        try {
            mob.setCustomNameVisible(false);
        } catch (Throwable ignored) {
        }
        if (mob instanceof LivingEntity living) {
            try {
                living.setAI(false);
            } catch (Throwable ignored) {
            }
            try {
                living.setCollidable(false);
            } catch (Throwable ignored) {
            }
            try {
                living.setRemoveWhenFarAway(false);
            } catch (Throwable ignored) {
            }
            try {
                living.setCanPickupItems(false);
            } catch (Throwable ignored) {
            }
        }
        if (kind.baby()) {
            boolean done = false;
            try {
                if (mob instanceof Ageable ageable) {
                    ageable.setBaby();
                    done = true;
                }
            } catch (Throwable ignored) {
            }
            if (!done) {
                try {
                    if (mob instanceof Zombie zombie) {
                        zombie.setBaby();
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        if (mob instanceof Wither wither) {
            try {
                // Ohne das haengt der Wither in der Spawn-Animation fest.
                wither.setInvulnerableTicks(0);
            } catch (Throwable ignored) {
            }
        }
        hideBossBar(mob);
    }

    /** Wither und Enderdrache wuerden sonst jedem in der Naehe eine Bossleiste einblenden. */
    private static void hideBossBar(Entity mob) {
        try {
            if (mob instanceof Boss boss) {
                boss.getBossBar().removeAll();
            }
        } catch (Throwable ignored) {
        }
    }

    private void despawn(Active entry) {
        if (entry.mob == null) {
            return;
        }
        try {
            entry.mob.remove();
        } catch (Throwable ignored) {
        }
        entry.mob = null;
    }

    // ------------------------------------------------------------------ Sichtbarkeit

    private void hidePlayer(Player target) {
        try {
            // Deckt auch alle ab, die erst spaeter dazukommen.
            target.setVisibleByDefault(false);
        } catch (Throwable ignored) {
        }
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.getUniqueId().equals(target.getUniqueId())) {
                continue;
            }
            try {
                viewer.hideEntity((Plugin) this.plugin, target);
            } catch (Throwable ignored) {
            }
        }
    }

    private void showPlayer(Player target) {
        try {
            target.setVisibleByDefault(true);
        } catch (Throwable ignored) {
        }
        if (this.isVanished(target)) {
            // Vanish hat Vorrang: sichtbar machen wuerde das Verstecken aufheben.
            try {
                this.plugin.state().setVanished(target, true);
            } catch (Throwable ignored) {
            }
            return;
        }
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.getUniqueId().equals(target.getUniqueId())) {
                continue;
            }
            try {
                viewer.showEntity((Plugin) this.plugin, target);
            } catch (Throwable ignored) {
            }
        }
    }

    // ------------------------------------------------------------------ Ticker

    private boolean isVanished(Player target) {
        try {
            return this.plugin.state().isVanished(target);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void tick() {
        if (this.active.isEmpty()) {
            return;
        }
        // Andere Teile des Plugins (z.B. Vanish) rufen showPlayer auf und wuerden den
        // Verkleideten damit wieder sichtbar machen. Einmal pro Sekunde nachziehen.
        boolean reassert = (++this.ticks % 20) == 0;
        for (Active entry : new ArrayList<>(this.active.values())) {
            Player target = Bukkit.getPlayer(entry.player);
            if (target == null || !target.isOnline()) {
                // Offline: Huelle weg, Verkleidung bleibt gemerkt und kommt beim Rejoin zurueck.
                this.despawn(entry);
                continue;
            }
            Entity mob = entry.mob;
            if (mob == null || !mob.isValid()) {
                // Nur einmal pro Sekunde nachsetzen, damit ein Mob, den der Server
                // partout nicht spawnen will, nicht jeden Tick neu probiert wird.
                if (reassert) {
                    entry.mob = this.spawnShell(target, entry.kind);
                }
                continue;
            }
            World world = target.getWorld();
            if (!world.equals(mob.getWorld())) {
                this.despawn(entry);
                entry.mob = this.spawnShell(target, entry.kind);
                continue;
            }
            try {
                mob.teleport(target.getLocation());
            } catch (Throwable ignored) {
            }
            try {
                // Zombies und Skelette wuerden in der Sonne anfangen zu brennen.
                mob.setFireTicks(0);
            } catch (Throwable ignored) {
            }
            hideBossBar(mob);
            if (reassert) {
                this.hidePlayer(target);
                try {
                    target.hideEntity((Plugin) this.plugin, mob);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    // ------------------------------------------------------------------ Events

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player joined = event.getPlayer();

        // Wer neu dazukommt, darf die Verkleideten nicht als Spieler sehen.
        for (Active entry : this.active.values()) {
            if (entry.player.equals(joined.getUniqueId())) {
                continue;
            }
            Player hidden = Bukkit.getPlayer(entry.player);
            if (hidden == null) {
                continue;
            }
            try {
                joined.hideEntity((Plugin) this.plugin, hidden);
            } catch (Throwable ignored) {
            }
        }

        // War er selbst verkleidet, wird die Huelle wieder aufgebaut.
        Active own = this.active.get(joined.getUniqueId());
        if (own != null) {
            this.hidePlayer(joined);
            this.despawn(own);
            own.mob = this.spawnShell(joined, own.kind);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Active entry = this.active.get(event.getPlayer().getUniqueId());
        if (entry != null) {
            this.despawn(entry);
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Active entry = this.active.get(event.getPlayer().getUniqueId());
        if (entry == null) {
            return;
        }
        this.despawn(entry);
        entry.mob = this.spawnShell(event.getPlayer(), entry.kind);
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        // Keine Trefferanimation und kein Schadenslaut an der Huelle.
        if (this.active.isEmpty()) {
            return;
        }
        if (this.isLiveShell(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    /**
     * Niemand darf mit der Huelle hantieren.
     *
     * <p>Sonst koennte man das verkleidete "Schaf" scheren, die "Kuh" melken, das "Schwein"
     * satteln und reiten, Tiere mit Weizen fuettern, ein Namensschild draufkleben oder beim
     * Ruestungsstaender Sachen anziehen. Alles davon wuerde Items kosten, Items aus dem Nichts
     * erzeugen oder der Huelle doch wieder einen Namen ueber den Kopf setzen.
     */
    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (this.active.isEmpty()) {
            return;
        }
        if (this.isLiveShell(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin() == this.plugin) {
            this.shutdown();
            synchronized (MobDisguise.class) {
                if (instance == this) {
                    instance = null;
                }
            }
        }
    }

    // ------------------------------------------------------------------ Aufraeumen

    /** Entfernt Huellen, die ein Absturz oder ein harter Stop in der Welt zurueckgelassen hat. */
    public int removeStrayMobs() {
        int removed = 0;
        try {
            for (World world : Bukkit.getWorlds()) {
                for (Entity entity : world.getEntities()) {
                    if (!hasTag(entity) || this.isLiveShell(entity)) {
                        continue;
                    }
                    try {
                        entity.remove();
                        removed++;
                    } catch (Throwable ignored) {
                    }
                }
            }
        } catch (Throwable t) {
            this.warn("Aufraeumen alter Verkleidungen", t);
        }
        return removed;
    }

    private static boolean hasTag(Entity entity) {
        try {
            java.util.Set<String> tags = entity.getScoreboardTags();
            return tags != null && tags.contains(TAG);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void warn(String what, Throwable t) {
        this.plugin.getLogger().warning(what + " nicht verfuegbar (" + t.getClass().getSimpleName() + ").");
    }

    /** Nur fuer das Menue: die Namen aller gerade verkleideten Spieler. */
    public List<String> names() {
        List<String> out = new ArrayList<>();
        for (Active entry : this.active.values()) {
            out.add(entry.playerName);
        }
        return out;
    }
}
