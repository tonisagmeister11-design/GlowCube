package de.ownercontrol;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Falsche Spieler: /control spawn <Name> stellt eine Spielerfigur mit dem Skin des Kontos hin
 * (Mannequin-Entity), meldet im Chat "Name joined the game" und laesst sich wie ein echter
 * Spieler steuern - aus seiner Perspektive.
 */
final class Fakes {

    private static final class Fake {
        final String name;
        final Mannequin body;
        final boolean quiet; // Testfigur: keine Chatmeldungen an andere
        Fake(String name, Mannequin body, boolean quiet) { this.name = name; this.body = body; this.quiet = quiet; }
    }

    private static final class Ride {
        final Player owner;
        final Fake fake;
        final Session.Snapshot saved;
        int age;
        Ride(Player owner, Fake fake) { this.owner = owner; this.fake = fake; this.saved = Session.Snapshot.of(owner); }
    }

    private final OwnerControlPlugin plugin;
    private final ControlManager manager;
    private final Map<String, Fake> fakes = new HashMap<>();
    private final Map<UUID, Ride> rides = new HashMap<>();

    Fakes(OwnerControlPlugin plugin, ControlManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    boolean exists(String name) { return fakes.containsKey(name.toLowerCase(Locale.ROOT)); }
    boolean isRiding(Player p) { return rides.containsKey(p.getUniqueId()); }
    java.util.Set<String> names() {
        java.util.Set<String> out = new java.util.TreeSet<>();
        for (Fake f : fakes.values()) out.add(f.name);
        return out;
    }

    // ------------------------------------------------------------------ Spawn / Despawn

    String spawn(Player admin, String name, boolean quiet) {
        if (!name.matches("[A-Za-z0-9_]{3,16}")) return "Ungueltiger Name (3-16 Zeichen: Buchstaben, Zahlen, _).";
        if (exists(name)) return name + " gibt es schon.";
        if (Bukkit.getPlayerExact(name) != null) return name + " ist ein echter Spieler, der online ist.";
        Location at = admin.getLocation();
        Mannequin m = at.getWorld().spawn(at, Mannequin.class, e -> {
            try {
                e.setProfile(ResolvableProfile.resolvableProfile().name(name).build()); // Skin des Kontos
            } catch (Throwable t) {
                plugin.getLogger().warning("Skin konnte nicht gesetzt werden: " + t);
            }
            e.setCustomName(name);
            e.setCustomNameVisible(true);
            e.setGravity(false);
            e.setSilent(true);
            e.setInvulnerable(true);
            e.setPersistent(false);
            try { e.setAI(false); } catch (Throwable ignored) { }
            try { e.setImmovable(true); } catch (Throwable ignored) { }
            hideDescription(e);
        });
        fakes.put(name.toLowerCase(Locale.ROOT), new Fake(name, m, quiet));
        if (!quiet) announce("multiplayer.player.joined", name);
        return null;
    }

    boolean despawn(String name) {
        Fake f = fakes.remove(name.toLowerCase(Locale.ROOT));
        if (f == null) return false;
        for (Ride r : new ArrayList<>(rides.values())) if (r.fake == f) stop(r, true);
        f.body.remove();
        if (!f.quiet) announce("multiplayer.player.left", f.name);
        return true;
    }

    Location where(String name) {
        Fake f = fakes.get(name.toLowerCase(Locale.ROOT));
        return f == null ? null : f.body.getLocation();
    }

    /** Testfigur mit zufaelligem Namen - ohne Meldung an andere. Liefert den Namen oder null. */
    String spawnTest(Player admin) {
        String[] pool = {"Steve", "Alex", "Kai", "Mia", "Leo", "Nora", "Finn", "Lena"};
        java.util.concurrent.ThreadLocalRandom rnd = java.util.concurrent.ThreadLocalRandom.current();
        for (int i = 0; i < 20; i++) {
            String n = pool[rnd.nextInt(pool.length)] + "_Test" + rnd.nextInt(10, 100);
            if (spawn(admin, n, true) == null) return n;
        }
        return null;
    }

    /** Alle Testfiguren entfernen. */
    int despawnTests() {
        int n = 0;
        for (Fake f : new ArrayList<>(fakes.values())) if (f.quiet && despawn(f.name)) n++;
        return n;
    }

    boolean bring(String name, Location to) {
        Fake f = fakes.get(name.toLowerCase(Locale.ROOT));
        if (f == null) return false;
        f.body.teleport(to);
        return true;
    }

    /** Die "NPC"-Zeile unter dem Namen ausblenden (Methodenname je nach API-Stand, deshalb Reflection). */
    private void hideDescription(Mannequin m) {
        for (Method me : Mannequin.class.getMethods()) {
            try {
                if (me.getName().equals("setDescription") && me.getParameterCount() == 1
                        && !me.getParameterTypes()[0].isPrimitive()) me.invoke(m, new Object[]{null});
                else if (me.getName().equals("setHideDescription") && me.getParameterCount() == 1) me.invoke(m, true);
            } catch (Throwable ignored) { }
        }
    }

    // ------------------------------------------------------------------ Steuern

    String ride(Player owner, String name) {
        Fake f = fakes.get(name.toLowerCase(Locale.ROOT));
        if (f == null) return null;
        if (manager.isBusy(owner)) return "Beende zuerst deine laufende Steuerung (/control stop).";
        for (Ride r : rides.values()) if (r.fake == f) return f.name + " wird schon gesteuert.";
        Ride r = new Ride(owner, f);
        try {
            r.saved.write(manager.file(owner.getUniqueId()));
        } catch (Exception e) {
            return "Konnte deinen Zustand nicht sichern - abgebrochen: " + e.getMessage();
        }
        rides.put(owner.getUniqueId(), r);
        owner.teleport(f.body.getLocation());
        owner.setInvulnerable(true);
        for (Player p : Bukkit.getOnlinePlayers()) if (!p.equals(owner)) p.hidePlayer(plugin, owner);
        owner.hideEntity(plugin, f.body); // sonst sitzt sein Kopf in deiner Kamera
        return "";
    }

    Boolean stopFor(Player owner, boolean notify) {
        Ride r = rides.get(owner.getUniqueId());
        if (r == null) return false;
        stop(r, notify);
        return true;
    }

    private void stop(Ride r, boolean notify) {
        if (rides.get(r.owner.getUniqueId()) != r) return;
        rides.remove(r.owner.getUniqueId());
        Player o = r.owner;
        if (o.isOnline()) {
            for (Player p : Bukkit.getOnlinePlayers()) if (!p.equals(o)) p.showPlayer(plugin, o);
            if (r.fake.body.isValid()) o.showEntity(plugin, r.fake.body);
            manager.restore(o, r.saved);
            if (notify) o.sendMessage("\u00a7aSteuerung von " + r.fake.name + " beendet.");
        }
        manager.file(o.getUniqueId()).delete();
    }

    void onQuit(Player p) { stopFor(p, false); }

    void hideRidersFrom(Player joiner) {
        for (Ride r : rides.values()) joiner.hidePlayer(plugin, r.owner);
    }

    void tick() {
        for (Ride r : new ArrayList<>(rides.values())) {
            if (!r.owner.isOnline() || !r.fake.body.isValid()) { stop(r, true); continue; }
            Mannequin m = r.fake.body;
            m.teleport(r.owner.getLocation());
            if (r.age++ % 4 == 0) { // Hand und Ruestung des Admins zeigen
                EntityEquipment from = r.owner.getEquipment(), to = m.getEquipment();
                if (from != null && to != null) {
                    to.setItemInMainHand(from.getItemInMainHand());
                    to.setHelmet(from.getHelmet());
                    to.setChestplate(from.getChestplate());
                    to.setLeggings(from.getLeggings());
                    to.setBoots(from.getBoots());
                }
            }
        }
    }

    // ------------------------------------------------------------------ Chat / Meldungen

    /** Chatnachricht im Namen der Figur, genau wie eine echte Spielernachricht. */
    boolean say(String name, String text) {
        Fake f = fakes.get(name.toLowerCase(Locale.ROOT));
        if (f == null) return false;
        raw("chat.type.text", f.name, text);
        return true;
    }

    String nameOf(Player owner) {
        Ride r = rides.get(owner.getUniqueId());
        return r == null ? null : r.fake.name;
    }

    private void announce(String key, String name) { raw(key, name, null); }

    /** Vanilla-Systemtext per tellraw - erscheint in der Sprache jedes Spielers. */
    private void raw(String key, String name, String text) {
        StringBuilder with = new StringBuilder("\"" + name + "\"");
        if (text != null) with.append(",\"").append(esc(text)).append("\"");
        String color = text == null ? ",\"color\":\"yellow\"" : "";
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                "tellraw @a {\"translate\":\"" + key + "\",\"with\":[" + with + "]" + color + "}");
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ");
    }

    void stopAll() {
        for (Ride r : new ArrayList<>(rides.values())) stop(r, false);
        for (Fake f : fakes.values()) f.body.remove();
        fakes.clear();
    }
}
