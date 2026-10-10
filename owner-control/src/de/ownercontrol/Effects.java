package de.ownercontrol;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Server-seitige Streiche - brauchen keine Client-Mod. */
final class Effects {

    static final List<String> NAMES = List.of(
            "screamer", "creeper", "haunt", "fakeerror", "fakeop",
            "spin", "launch", "blind", "nausea", "shuffle", "stumble");

    private static final String[] HAUNT_SOUNDS = {
            "ambient.cave", "entity.ghast.scream", "entity.enderman.scream", "entity.warden.heartbeat",
            "entity.phantom.bite", "block.chest.open", "entity.zombie.ambient", "block.wooden_door.open",
            "entity.skeleton.ambient", "entity.witch.ambient"};

    private Effects() {}

    static boolean apply(Plugin plugin, Player p, String fx) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        switch (fx) {
            case "screamer" -> {
                p.showElderGuardian();
                sound(p, p.getLocation(), "entity.elder_guardian.curse", 2f, 0.6f);
                sound(p, p.getLocation(), "entity.warden.sonic_boom", 2f, 0.5f);
                p.sendTitle("§4§l☠", "", 0, 25, 10);
                potion(p, "darkness", 80, 0);
            }
            case "creeper" -> {
                Location behind = p.getLocation().add(p.getLocation().getDirection().multiply(-2.0));
                sound(p, behind, "entity.creeper.primed", 1.5f, 1f);
            }
            case "haunt" -> {
                int[] n = {0};
                BukkitTask[] t = new BukkitTask[1];
                t[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                    if (!p.isOnline() || ++n[0] > 12) { t[0].cancel(); return; }
                    Location l = p.getLocation().add(rnd.nextDouble(-6, 6), 0, rnd.nextDouble(-6, 6));
                    sound(p, l, HAUNT_SOUNDS[rnd.nextInt(HAUNT_SOUNDS.length)], 1.2f, 0.7f + rnd.nextFloat() * 0.6f);
                }, 0L, 25L);
            }
            case "fakeerror" -> {
                p.sendTitle("§4§lFATAL ERROR", "§cConnection to host lost", 0, 60, 10);
                p.sendMessage("§4[ERROR] §cjava.lang.OutOfMemoryError: Java heap space");
                p.sendMessage("§4[ERROR] §cat net.minecraft.server.MinecraftServer.tickServer(MinecraftServer.java:1337)");
                p.sendMessage("§4[ERROR] §cThe server is shutting down...");
            }
            case "fakeop" -> p.sendMessage("§7§o[Server: Made " + p.getName() + " a server operator]");
            case "spin" -> {
                int[] n = {0};
                BukkitTask[] t = new BukkitTask[1];
                t[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                    if (!p.isOnline() || ++n[0] > 60) { t[0].cancel(); return; }
                    Location l = p.getLocation();
                    l.setYaw(l.getYaw() + 18f);
                    p.teleport(l);
                }, 0L, 1L);
            }
            case "launch" -> p.setVelocity(new Vector(0, 1.6, 0));
            case "blind" -> { potion(p, "blindness", 100, 0); potion(p, "darkness", 100, 0); }
            case "nausea" -> potion(p, "nausea", 200, 1);
            case "shuffle" -> {
                ItemStack[] c = p.getInventory().getContents();
                List<ItemStack> l = new ArrayList<>(Arrays.asList(c).subList(0, Math.min(36, c.length)));
                Collections.shuffle(l);
                for (int i = 0; i < l.size(); i++) c[i] = l.get(i);
                p.getInventory().setContents(c);
            }
            case "stumble" -> p.dropItem(false);
            default -> { return false; }
        }
        return true;
    }

    private static void sound(Player p, Location l, String key, float vol, float pitch) {
        p.playSound(l, key, vol, pitch);
    }

    private static void potion(Player p, String key, int ticks, int amp) {
        PotionEffectType t = Registry.EFFECT.get(NamespacedKey.minecraft(key));
        if (t != null) p.addPotionEffect(new PotionEffect(t, ticks, amp));
    }
}
