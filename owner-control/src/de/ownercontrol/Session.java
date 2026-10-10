package de.ownercontrol;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Ein laufendes /control: wer steuert wen, plus der gesicherte Zustand des Owners. */
final class Session {

    final Player owner;
    final Player target;
    final Snapshot saved;
    /** Letzter gemeinsamer Inventarstand - Grundlage fuer den Abgleich in beide Richtungen. */
    ItemStack[] lastInv;

    Session(Player owner, Player target) {
        this.owner = owner;
        this.target = target;
        this.saved = Snapshot.of(owner);
    }

    /** Zustand des Owners vor der Steuerung. Wird auch auf Platte gelegt (Absturzschutz). */
    static final class Snapshot {
        Location loc;
        GameMode mode;
        ItemStack[] inv;
        int held;
        double health;
        int food;
        float saturation;
        float exp;
        int level;
        boolean allowFlight;
        boolean flying;
        boolean invulnerable;

        static Snapshot of(Player p) {
            Snapshot s = new Snapshot();
            s.loc = p.getLocation();
            s.mode = p.getGameMode();
            s.inv = Session.clone(p.getInventory().getContents());
            s.held = p.getInventory().getHeldItemSlot();
            s.health = p.getHealth();
            s.food = p.getFoodLevel();
            s.saturation = p.getSaturation();
            s.exp = p.getExp();
            s.level = p.getLevel();
            s.allowFlight = p.getAllowFlight();
            s.flying = p.isFlying();
            s.invulnerable = p.isInvulnerable();
            return s;
        }

        void write(File f) throws IOException {
            YamlConfiguration y = new YamlConfiguration();
            y.set("loc", loc);
            y.set("mode", mode.name());
            y.set("inv", Arrays.asList(inv));
            y.set("held", held);
            y.set("health", health);
            y.set("food", food);
            y.set("saturation", (double) saturation);
            y.set("exp", (double) exp);
            y.set("level", level);
            y.set("allowFlight", allowFlight);
            y.set("flying", flying);
            y.set("invulnerable", invulnerable);
            y.save(f);
        }

        static Snapshot read(File f) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            Snapshot s = new Snapshot();
            s.loc = (Location) y.get("loc");
            s.mode = GameMode.valueOf(y.getString("mode"));
            List<?> l = y.getList("inv");
            List<ItemStack> items = new ArrayList<>();
            if (l != null) for (Object o : l) items.add(o instanceof ItemStack i ? i : null);
            s.inv = items.toArray(new ItemStack[0]);
            s.held = y.getInt("held");
            s.health = y.getDouble("health");
            s.food = y.getInt("food");
            s.saturation = (float) y.getDouble("saturation");
            s.exp = (float) y.getDouble("exp");
            s.level = y.getInt("level");
            s.allowFlight = y.getBoolean("allowFlight", false);
            s.flying = y.getBoolean("flying", false);
            s.invulnerable = y.getBoolean("invulnerable", false);
            return s;
        }
    }

    static ItemStack[] clone(ItemStack[] in) {
        ItemStack[] out = new ItemStack[in.length];
        for (int i = 0; i < in.length; i++) out[i] = in[i] == null ? null : in[i].clone();
        return out;
    }
}
