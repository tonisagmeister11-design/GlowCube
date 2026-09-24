package de.glowcube.claudeai.task;

import java.util.Map;
import java.util.function.Predicate;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import de.glowcube.claudeai.brain.Lexicon;
import de.glowcube.claudeai.npc.Npc;
import de.glowcube.claudeai.world.Blocks;
import de.glowcube.claudeai.world.Fx;
import de.glowcube.claudeai.world.Mats;

/** Sachen in die naechste Kiste legen oder herausholen. */
public final class ContainerTask extends Task {

    private final boolean deposit;
    private final Predicate<Material> what;
    private final int count;
    private Block chest;

    public ContainerTask(boolean deposit, Predicate<Material> what, int count) {
        this.deposit = deposit;
        this.what = what;
        this.count = count;
    }

    @Override
    public String label() {
        return deposit ? "raeume in die Kiste" : "hole etwas aus der Kiste";
    }

    public static boolean isContainer(Material m) {
        String n = Mats.n(m);
        return n.equals("CHEST") || n.equals("TRAPPED_CHEST") || n.equals("BARREL") || n.endsWith("SHULKER_BOX");
    }

    @Override
    public Status tick(Npc npc) {
        if (chest == null || !isContainer(chest.getType())) {
            chest = nearest(npc);
            if (chest == null) return fail("Ich sehe hier keine Kiste.");
        }
        Location c = chest.getLocation().add(0.5, 0.5, 0.5);
        if (npc.location().distance(c) > 3.2) {
            npc.mover().go(c, 2.8, false, true);
            if (npc.mover().status() == de.glowcube.claudeai.move.Mover.Status.FAILED) return fail("Ich komme nicht an die Kiste.");
            return Status.RUNNING;
        }
        npc.mover().stop();
        npc.lookAtBlock(chest);
        if (!(chest.getState() instanceof Container container)) return fail("Die Kiste laesst sich nicht oeffnen.");
        Inventory box = container.getInventory();
        Fx.sound(c, () -> Sound.BLOCK_CHEST_OPEN, 0.6f, 1f);
        npc.swing();
        int moved = deposit ? move(npc.getInventory(), box, npc) : move(box, npc.getInventory(), npc);
        Fx.sound(c, () -> Sound.BLOCK_CHEST_CLOSE, 0.6f, 1f);
        if (moved == 0) return fail(deposit ? "Ich hab nichts Passendes zum Einlagern." : "In der Kiste ist nichts Passendes.");
        npc.say(deposit ? "Erledigt, " + moved + " Items liegen in der Kiste." : "Hab " + moved + " Items aus der Kiste genommen.");
        return Status.DONE;
    }

    private int move(Inventory from, Inventory to, Npc npc) {
        int left = count < 0 ? Integer.MAX_VALUE : count;
        int moved = 0;
        ItemStack[] contents = from.getContents();
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack s = contents[i];
            if (s == null || !what.test(s.getType())) continue;
            int n = Math.min(left, s.getAmount());
            ItemStack part = s.clone();
            part.setAmount(n);
            Map<Integer, ItemStack> rest = to.addItem(part);
            int notMoved = rest.values().stream().mapToInt(ItemStack::getAmount).sum();
            int really = n - notMoved;
            if (really <= 0) break;
            if (really == s.getAmount()) from.setItem(i, null);
            else {
                s.setAmount(s.getAmount() - really);
                from.setItem(i, s);
            }
            moved += really;
            left -= really;
        }
        return moved;
    }

    private static Block nearest(Npc npc) {
        Location me = npc.location();
        Block best = null;
        double bestD = 20 * 20;
        for (int dx = -20; dx <= 20; dx++) {
            for (int dz = -20; dz <= 20; dz++) {
                for (int dy = -5; dy <= 5; dy++) {
                    Block b = Blocks.at(me.getWorld(), me.getBlockX() + dx, me.getBlockY() + dy, me.getBlockZ() + dz);
                    if (b == null || !isContainer(b.getType())) continue;
                    double d = dx * dx + dz * dz + dy * dy;
                    if (d < bestD) {
                        bestD = d;
                        best = b;
                    }
                }
            }
        }
        return best;
    }

    @Override
    public void stop(Npc npc) {
        npc.mover().stop();
    }

    static String name(Material m) {
        return Lexicon.name(m);
    }
}
