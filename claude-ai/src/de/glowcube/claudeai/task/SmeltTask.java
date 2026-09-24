package de.glowcube.claudeai.task;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.inventory.ItemStack;

import de.glowcube.claudeai.brain.Lexicon;
import de.glowcube.claudeai.npc.Npc;
import de.glowcube.claudeai.plan.Recipes;
import de.glowcube.claudeai.world.Fx;
import de.glowcube.claudeai.world.Mats;

/** Schmilzt/brat Items im Ofen. Brennstoff wird automatisch gewaehlt. */
public final class SmeltTask extends StationTask {

    private final Material input;
    private final Material output;
    private final int count;
    private int done;
    private int timer;
    private double fuel;

    public SmeltTask(Material input, Material output, int count) {
        super("FURNACE", true);
        this.input = input;
        this.output = output;
        this.count = Math.max(1, count);
    }

    @Override
    public String label() {
        return "schmelze " + Lexicon.name(input);
    }

    @Override
    public Status tick(Npc npc) {
        Status prep = prepare(npc);
        if (prep != Status.DONE) return prep;
        if (++timer < npc.settings().smeltTicksPerItem) {
            if (timer % 10 == 0 && station != null) {
                Fx.particle(station.getLocation().add(0.5, 1.1, 0.5), () -> Particle.CLOUD, 3, 0.15);
            }
            return Status.RUNNING;
        }
        timer = 0;
        boolean inputMatchesAnyLog = Mats.isLog(input);
        if (npc.count(m -> inputMatchesAnyLog ? Mats.isLog(m) : m == input) == 0) {
            packUp(npc);
            if (done > 0) return Status.DONE;
            return fail("Ich habe kein " + Lexicon.name(input) + " zum Schmelzen.");
        }
        while (fuel < 1) {
            if (!refuel(npc)) {
                packUp(npc);
                return fail("Mir ist der Brennstoff ausgegangen (Kohle oder Holz).");
            }
        }
        fuel -= 1;
        npc.take(m -> inputMatchesAnyLog ? Mats.isLog(m) : m == input, 1);
        npc.give(output, 1);
        Fx.sound(npc.location(), () -> Sound.BLOCK_FURNACE_FIRE_CRACKLE, 0.6f, 1f);
        if (++done >= count) {
            packUp(npc);
            return Status.DONE;
        }
        return Status.RUNNING;
    }

    private boolean refuel(Npc npc) {
        // Kohle zuerst, dann Bretter, Staemme, Stoecke - aber nie das, was geschmolzen werden soll
        String[] order = { "COAL", "CHARCOAL", "COAL_BLOCK", "BLAZE_ROD", "DRIED_KELP_BLOCK" };
        for (String n : order) {
            Material m = Mats.get(n);
            if (m != null && m != input && npc.take(m, 1) == 1) {
                fuel += Recipes.fuelValue(m);
                return true;
            }
        }
        for (ItemStack s : npc.getInventory().getContents()) {
            if (s == null || s.getType() == input || Mats.isLog(input) && Mats.isLog(s.getType())) continue;
            double v = Recipes.fuelValue(s.getType());
            if (v > 0) {
                Material m = s.getType();
                npc.take(m, 1);
                fuel += v;
                return true;
            }
        }
        return false;
    }
}
