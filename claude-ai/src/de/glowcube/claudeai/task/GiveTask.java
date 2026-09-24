package de.glowcube.claudeai.task;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import de.glowcube.claudeai.brain.Lexicon;
import de.glowcube.claudeai.npc.Npc;
import de.glowcube.claudeai.world.Fx;

/** Zu einem Spieler laufen und ihm Items in die Hand druecken. count < 0 = alles davon. */
public final class GiveTask extends Task {

    private final UUID player;
    private final Predicate<Material> what;
    private final int count;
    private final String message;
    private int ticks;

    public GiveTask(UUID player, Predicate<Material> what, int count, String message) {
        this.player = player;
        this.what = what;
        this.count = count;
        this.message = message;
    }

    @Override
    public String label() {
        return "bringe Sachen vorbei";
    }

    @Override
    public void start(Npc npc) {
        ticks = 0;
    }

    @Override
    public Status tick(Npc npc) {
        Player p = Bukkit.getPlayer(player);
        if (p == null || !p.isOnline()) return fail("Die Person ist nicht mehr da - ich behalte die Sachen.");
        if (++ticks > 20 * 120) return fail("Ich komme nicht zu " + p.getName() + " durch.");
        Location me = npc.location();
        Location pl = p.getLocation();
        if (!me.getWorld().equals(pl.getWorld()) || me.distance(pl) > 3.0) {
            npc.mover().go(pl, 2.2, false, true);
            return Status.RUNNING;
        }
        npc.mover().stop();
        npc.lookAt(p.getEyeLocation());

        List<ItemStack> handover = new ArrayList<>();
        int left = count < 0 ? Integer.MAX_VALUE : count;
        ItemStack[] contents = npc.getInventory().getContents();
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack s = contents[i];
            if (s == null || !what.test(s.getType())) continue;
            int n = Math.min(left, s.getAmount());
            ItemStack part = s.clone();
            part.setAmount(n);
            handover.add(part);
            left -= n;
        }
        if (handover.isEmpty()) return fail("Davon habe ich leider nichts.");
        int total = 0;
        Map<String, Integer> summary = new java.util.LinkedHashMap<>();
        for (ItemStack s : handover) {
            npc.take(m -> m == s.getType(), s.getAmount());
            total += s.getAmount();
            summary.merge(Lexicon.name(s.getType()), s.getAmount(), Integer::sum);
            Map<Integer, ItemStack> rest = p.getInventory().addItem(s);
            for (ItemStack r : rest.values()) pl.getWorld().dropItemNaturally(pl, r);
        }
        npc.swing();
        Fx.sound(pl, () -> Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.0f);
        if (message != null) {
            npc.say(message);
        } else {
            List<String> parts = new ArrayList<>();
            summary.forEach((name, n) -> parts.add(n + "x " + name));
            npc.say(Lexicon.pick(Lexicon.GIVE_LINES, npc.random()) + " " + String.join(", ", parts) + (total > 0 ? "." : ""));
        }
        return Status.DONE;
    }

    @Override
    public void stop(Npc npc) {
        npc.mover().stop();
    }
}
