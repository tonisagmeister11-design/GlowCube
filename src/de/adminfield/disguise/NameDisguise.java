package de.adminfield.disguise;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import de.adminfield.AdminFieldPlugin;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;

/**
 * Gibt dich als ein beliebiges Minecraft-Konto aus: fremder Name ueber dem Kopf, fremder Name
 * in der Tabliste, fremder Skin.
 *
 * <p>Ablauf: Name eintippen, Skin wird im Hintergrund bei Mojang geholt, dann wird das Profil
 * des Spielers ausgetauscht und er fuer alle anderen einmal neu uebertragen.
 *
 * <p>Das Setzen des Profils laeuft bewusst ueber Reflection. Diese Stelle ist der
 * versionsabhaengigste Teil des ganzen Plugins - so gibt es im schlimmsten Fall eine
 * verstaendliche Meldung in der Konsole statt eines Absturzes.
 */
public final class NameDisguise implements Listener {

    /** Eine laufende Namens-Verkleidung inklusive allem, was zum Zuruecksetzen noetig ist. */
    public static final class Active {
        private final UUID player;
        private final String realName;
        private final String realTextures;
        private final String realSignature;
        private String fakeName;

        private Active(UUID player, String realName, String realTextures, String realSignature) {
            this.player = player;
            this.realName = realName;
            this.realTextures = realTextures;
            this.realSignature = realSignature;
        }

        public UUID player() {
            return this.player;
        }

        public String realName() {
            return this.realName;
        }

        public String fakeName() {
            return this.fakeName;
        }
    }

    private static NameDisguise instance;

    private final AdminFieldPlugin plugin;
    private final Map<UUID, Active> active = new LinkedHashMap<>();

    private NameDisguise(AdminFieldPlugin plugin) {
        this.plugin = plugin;
    }

    public static synchronized NameDisguise get(AdminFieldPlugin plugin) {
        if (instance != null && instance.plugin == plugin) {
            return instance;
        }
        if (instance != null) {
            instance.restoreAll();
        }
        NameDisguise fresh = new NameDisguise(plugin);
        instance = fresh;
        try {
            Bukkit.getPluginManager().registerEvents(fresh, (Plugin) plugin);
        } catch (Throwable t) {
            plugin.getLogger().warning("Namens-Verkleidung konnte sich nicht einhaengen ("
                    + t.getClass().getSimpleName() + ").");
        }
        return fresh;
    }

    public static synchronized void stop(AdminFieldPlugin plugin) {
        if (instance != null && instance.plugin == plugin) {
            instance.restoreAll();
            instance = null;
        }
    }

    // ------------------------------------------------------------------ Abfragen

    public boolean isDisguised(UUID player) {
        return this.active.containsKey(player);
    }

    public String nameOf(UUID player) {
        Active entry = this.active.get(player);
        return entry == null ? null : entry.fakeName;
    }

    public int count() {
        return this.active.size();
    }

    public Collection<Active> all() {
        return new ArrayList<>(this.active.values());
    }

    // ------------------------------------------------------------------ Anwenden

    /**
     * Holt den Skin im Hintergrund und verwandelt den Spieler dann.
     *
     * @param feedback bekommt eine fertige Meldung fuer den Owner (laeuft im Haupt-Thread)
     */
    public void disguiseAsync(Player target, String wantedName, Consumer<String> feedback) {
        if (!SkinFetch.isValidName(wantedName)) {
            feedback.accept("<red>Das ist kein gültiger Minecraft-Name: <white>" + wantedName);
            return;
        }
        UUID id = target.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously((Plugin) this.plugin, () -> {
            String message;
            SkinFetch.Skin skin = null;
            try {
                skin = SkinFetch.lookup(wantedName);
                message = skin == null
                        ? "<red>Den Spieler <white>" + wantedName + "<red> gibt es nicht."
                        : null;
            } catch (Throwable t) {
                message = "<red>Mojang war nicht erreichbar (" + t.getClass().getSimpleName() + ").";
            }
            SkinFetch.Skin found = skin;
            String error = message;
            Bukkit.getScheduler().runTask((Plugin) this.plugin, () -> {
                if (error != null) {
                    feedback.accept(error);
                    return;
                }
                Player online = Bukkit.getPlayer(id);
                if (online == null || !online.isOnline()) {
                    feedback.accept("<red>Der Spieler ist nicht mehr online.");
                    return;
                }
                if (this.apply(online, found)) {
                    feedback.accept("<gray>Du bist jetzt <white>" + found.name()
                            + "<gray> – Name, Nametag und Skin.");
                } else {
                    feedback.accept("<red>Das Profil ließ sich nicht setzen. "
                            + "Näheres steht in der Server-Konsole.");
                }
            });
        });
    }

    private boolean apply(Player target, SkinFetch.Skin skin) {
        UUID id = target.getUniqueId();
        Active entry = this.active.get(id);
        if (entry == null) {
            // Erstes Mal: den echten Namen und Skin merken, damit es ein Zurueck gibt.
            String[] own = ownTextures(target);
            entry = new Active(id, target.getName(), own[0], own[1]);
            this.active.put(id, entry);
        }
        entry.fakeName = skin.name();

        boolean ok = writeProfile(target, skin.name(), id, skin.value(), skin.signature());
        if (!ok) {
            if (entry.fakeName == null) {
                this.active.remove(id);
            }
            return false;
        }
        this.setListName(target, skin.name());
        this.refresh(target);
        return true;
    }

    /** Setzt Name und Skin wieder auf das echte Konto zurueck. */
    public void restore(UUID player) {
        Active entry = this.active.remove(player);
        if (entry == null) {
            return;
        }
        Player target = Bukkit.getPlayer(player);
        if (target == null) {
            return;
        }
        writeProfile(target, entry.realName, player, entry.realTextures, entry.realSignature);
        this.setListName(target, null);
        this.refresh(target);
    }

    public void restoreAll() {
        for (UUID id : new ArrayList<>(this.active.keySet())) {
            this.restore(id);
        }
    }

    // ------------------------------------------------------------------ Sichtbar machen

    /**
     * Blendet den Spieler bei allen anderen kurz aus und wieder ein.
     *
     * <p>Erst dadurch schickt der Server das neue Profil an die Clients - sonst behalten alle
     * den alten Namen und Skin, bis sie sich neu verbinden.
     */
    private void refresh(Player target) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.getUniqueId().equals(target.getUniqueId())) {
                continue;
            }
            try {
                viewer.hideEntity((Plugin) this.plugin, target);
            } catch (Throwable ignored) {
            }
        }
        Bukkit.getScheduler().runTaskLater((Plugin) this.plugin, () -> {
            boolean vanished = false;
            try {
                vanished = this.plugin.state().isVanished(target);
            } catch (Throwable ignored) {
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
            if (vanished) {
                // Vanish wieder herstellen, das Einblenden haette es sonst aufgehoben.
                try {
                    this.plugin.state().setVanished(target, true);
                } catch (Throwable ignored) {
                }
            }
        }, 3L);
    }

    // ------------------------------------------------------------------ Profil schreiben

    /** Liest den echten Skin des Spielers aus, damit er spaeter zurueckgesetzt werden kann. */
    private static String[] ownTextures(Player target) {
        try {
            PlayerProfile profile = target.getPlayerProfile();
            if (profile != null) {
                for (Object raw : profile.getProperties()) {
                    ProfileProperty property = (ProfileProperty) raw;
                    if ("textures".equals(property.getName())) {
                        return new String[]{property.getValue(), property.getSignature()};
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return new String[]{null, null};
    }

    /**
     * Baut ein Profil mit dem gewuenschten Namen und Skin und haengt es an den Spieler.
     *
     * <p>Komplett ueber Reflection, weil genau diese drei Aufrufe sich zwischen Serverversionen
     * am ehesten unterscheiden. Schlaegt etwas fehl, steht der Grund in der Konsole.
     */
    private boolean writeProfile(Player target, String name, UUID id, String textures, String signature) {
        try {
            Object profile = createProfile(id, name);
            if (profile == null) {
                this.fail("Bukkit.createProfile(UUID, String) gibt es auf diesem Server nicht");
                return false;
            }
            if (textures != null) {
                Class<?> propertyType = Class.forName("com.destroystokyo.paper.profile.ProfileProperty");
                Constructor<?> ctor = propertyType.getConstructor(String.class, String.class, String.class);
                Object property = ctor.newInstance("textures", textures, signature);
                Method setProperty = find(profile.getClass(), "setProperty", 1);
                if (setProperty == null) {
                    this.fail("PlayerProfile.setProperty fehlt");
                    return false;
                }
                setProperty.invoke(profile, property);
            }
            Method setProfile = find(target.getClass(), "setPlayerProfile", 1);
            if (setProfile == null) {
                this.fail("Player.setPlayerProfile fehlt - diese Paper-Version kann das nicht");
                return false;
            }
            setProfile.invoke(target, profile);
            return true;
        } catch (Throwable t) {
            Throwable cause = t.getCause() != null ? t.getCause() : t;
            this.fail(cause.getClass().getSimpleName()
                    + (cause.getMessage() == null ? "" : ": " + cause.getMessage()));
            return false;
        }
    }

    private static Object createProfile(UUID id, String name) throws Exception {
        for (Method m : Bukkit.class.getMethods()) {
            if (!m.getName().equals("createProfile") || m.getParameterCount() != 2) {
                continue;
            }
            Class<?>[] types = m.getParameterTypes();
            if (types[0] == UUID.class && types[1] == String.class) {
                return m.invoke(null, id, name);
            }
        }
        return null;
    }

    /** Sucht eine oeffentliche Methode ueber Name und Parameterzahl, inklusive geerbter. */
    private static Method find(Class<?> owner, String name, int parameters) {
        for (Method m : owner.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == parameters) {
                return m;
            }
        }
        return null;
    }

    private void setListName(Player target, String name) {
        // Die Tabliste zieht normalerweise das Profil nach. Sicherheitshalber trotzdem setzen.
        try {
            Method m = find(target.getClass(), "setPlayerListName", 1);
            if (m != null && m.getParameterTypes()[0] == String.class) {
                m.invoke(target, name);
            }
        } catch (Throwable ignored) {
        }
    }

    private void fail(String reason) {
        this.plugin.getLogger().warning("Namens-Verkleidung fehlgeschlagen: " + reason);
    }

    // ------------------------------------------------------------------ Events

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Active entry = this.active.get(event.getPlayer().getUniqueId());
        if (entry == null || entry.fakeName == null) {
            return;
        }
        // Nach dem Neu-Verbinden ist das echte Profil wieder aktiv - also noch einmal aufsetzen.
        Player joined = event.getPlayer();
        String wanted = entry.fakeName;
        Bukkit.getScheduler().runTaskLater((Plugin) this.plugin,
                () -> this.disguiseAsync(joined, wanted, message -> {
                }), 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Eintrag bleibt gemerkt, damit die Verkleidung beim naechsten Join wieder da ist.
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin() == this.plugin) {
            this.restoreAll();
            synchronized (NameDisguise.class) {
                if (instance == this) {
                    instance = null;
                }
            }
        }
    }

    /** Kleine Hilfe fuer das Menue: schickt dem Owner eine Meldung. */
    public void tell(Player owner, String message) {
        this.plugin.send((CommandSender) owner, message);
    }
}
