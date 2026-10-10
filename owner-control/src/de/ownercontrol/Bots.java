package de.ownercontrol;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** /control letkill: ein Spieler wird zum Bot, der einen anderen mit Kill Aura jagt. */
final class Bots {

    private static final class Fight {
        final Player bot, victim;
        final GameMode oldMode;
        int age;
        Fight(Player bot, Player victim) { this.bot = bot; this.victim = victim; this.oldMode = bot.getGameMode(); }
    }

    private static final String[] TIERS = {"WOODEN", "GOLDEN", "STONE", "IRON", "DIAMOND", "NETHERITE"};

    private final OwnerControlPlugin plugin;
    private final Map<UUID, Fight> fights = new HashMap<>();

    Bots(OwnerControlPlugin plugin) { this.plugin = plugin; }

    boolean isBot(Player p) { return fights.containsKey(p.getUniqueId()); }

    String start(Player bot, Player victim) {
        if (bot.getUniqueId().equals(victim.getUniqueId())) return "Bot und Ziel sind derselbe Spieler.";
        if (isBot(bot)) return bot.getName() + " ist schon als Bot unterwegs.";
        if (victim.isDead() || bot.isDead()) return "Einer der beiden ist tot.";
        Fight f = new Fight(bot, victim);
        fights.put(bot.getUniqueId(), f);
        if (bot.getGameMode() != GameMode.SURVIVAL) bot.setGameMode(GameMode.SURVIVAL);
        equip(bot);
        bot.teleport(spot(victim));
        return null;
    }

    boolean stop(Player bot) {
        Fight f = fights.get(bot.getUniqueId());
        if (f == null) return false;
        end(f, "abgebrochen");
        return true;
    }

    void stopAll() {
        for (Fight f : new ArrayList<>(fights.values())) end(f, "Plugin beendet");
    }

    private void end(Fight f, String why) {
        fights.remove(f.bot.getUniqueId());
        if (f.bot.isOnline() && !f.bot.isDead()) {
            f.bot.setSprinting(false);
            f.bot.setGameMode(f.oldMode);
        }
        plugin.tell("Bot " + f.bot.getName() + " gegen " + f.victim.getName() + " beendet: " + why);
    }

    void tick() {
        int limit = plugin.getConfig().getInt("letkill-timeout-seconds", 300) * 20;
        for (Fight f : new ArrayList<>(fights.values())) {
            Player b = f.bot, v = f.victim;
            if (!b.isOnline() || b.isDead()) { end(f, "Bot ist gestorben"); continue; }
            if (!v.isOnline() || v.isDead()) { end(f, "Ziel ist tot"); continue; }
            if (++f.age > limit) { end(f, "Zeitlimit"); continue; }
            if (!b.getWorld().equals(v.getWorld())) { b.teleport(spot(v)); continue; }

            Location be = b.getEyeLocation();
            Location vl = v.getLocation();
            double dx = vl.getX() - be.getX();
            double dy = vl.getY() + 0.9 - be.getY();
            double dz = vl.getZ() - be.getZ();
            double hd = Math.hypot(dx, dz);
            b.setRotation((float) Math.toDegrees(Math.atan2(-dx, dz)),
                    (float) Math.toDegrees(-Math.atan2(dy, Math.max(hd, 0.001))));

            Vector now = b.getVelocity();
            if (hd > 1.8) {
                double ux = dx / hd, uz = dz / hd;
                double vy = now.getY();
                Location bl = b.getLocation();
                World w = b.getWorld();
                boolean blocked = !w.getBlockAt((int) Math.floor(bl.getX() + ux * 0.8), bl.getBlockY(),
                        (int) Math.floor(bl.getZ() + uz * 0.8)).isPassable();
                if (blocked && b.isOnGround()) vy = 0.42;
                b.setSprinting(true);
                b.setVelocity(new Vector(ux * 0.28, vy, uz * 0.28));
            }

            // Kill Aura: genau dann zuschlagen, wenn der Angriff voll aufgeladen ist (volle Schadenskraft)
            if (b.getLocation().distance(vl) <= 3.0 && b.getAttackCooldown() >= 1.0f) {
                b.attack(v);
                b.swingMainHand();
            }
        }
    }

    /** Ein freier Bodenplatz etwa 10 Bloecke vom Ziel entfernt. */
    private Location spot(Player v) {
        Location c = v.getLocation();
        World w = c.getWorld();
        double dist = plugin.getConfig().getDouble("letkill-distance");
        if (dist < 3) dist = 10;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < 16; i++) {
            double a = rnd.nextDouble(0, Math.PI * 2);
            int x = (int) Math.floor(c.getX() + Math.cos(a) * dist);
            int z = (int) Math.floor(c.getZ() + Math.sin(a) * dist);
            for (int dy = 6; dy >= -10; dy--) {
                int y = c.getBlockY() + dy;
                var feet = w.getBlockAt(x, y, z);
                var below = w.getBlockAt(x, y - 1, z);
                if (feet.isPassable() && !feet.isLiquid() && w.getBlockAt(x, y + 1, z).isPassable()
                        && !below.isPassable() && !below.isLiquid()) {
                    return new Location(w, x + 0.5, y, z + 0.5);
                }
            }
        }
        return c; // kein freier Platz gefunden - direkt beim Ziel
    }

    /** Beste Waffe (Schwert vor Axt, hoeherer Stoff vor niedrigerem) in die Hand nehmen. */
    private void equip(Player b) {
        PlayerInventory inv = b.getInventory();
        int best = -1, bestScore = -1;
        for (int i = 0; i < 36; i++) {
            ItemStack it = inv.getItem(i);
            if (it == null) continue;
            String n = it.getType().name();
            boolean sword = n.endsWith("_SWORD");
            if (!sword && !n.endsWith("_AXE")) continue;
            int tier = 0;
            for (int t = 0; t < TIERS.length; t++) if (n.startsWith(TIERS[t])) tier = t;
            int score = tier * 2 + (sword ? 1 : 0);
            if (score > bestScore) { bestScore = score; best = i; }
        }
        int held = inv.getHeldItemSlot();
        if (best < 0) { // keine Waffe dabei: ein Schwert in einen freien Platz legen
            for (int i = 0; i < 36; i++) {
                if (inv.getItem(i) == null) { inv.setItem(i, new ItemStack(Material.NETHERITE_SWORD)); best = i; break; }
            }
            if (best < 0) return;
        }
        if (best < 9) { inv.setHeldItemSlot(best); return; }
        ItemStack a = inv.getItem(best), c = inv.getItem(held);
        inv.setItem(held, a);
        inv.setItem(best, c);
    }
}
