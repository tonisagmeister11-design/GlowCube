/*
 * Decompiled with CFR 0.152.
 */
package de.adminfield;

import de.adminfield.ActivityLog;
import de.adminfield.AdminFieldPlugin;
import de.adminfield.Ui;
import de.adminfield.menu.PlayerActionMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public final class OwnerPowers {
    private final AdminFieldPlugin plugin;

    public OwnerPowers(AdminFieldPlugin adminFieldPlugin) {
        this.plugin = adminFieldPlugin;
    }

    public Location aim(Player player) {
        Block block = player.getTargetBlockExact(120);
        if (block == null) {
            return player.getLocation().clone();
        }
        return block.getLocation().add(0.5, 1.0, 0.5);
    }

    public void tntRain(Player player, final Location location) {
        final World world = location.getWorld();
        if (world == null) {
            return;
        }
        final int n = Math.max(1, Math.min(500, this.plugin.getConfig().getInt("ownerstuff.tnt-amount", 150)));
        final int n2 = Math.max(1, this.plugin.getConfig().getInt("ownerstuff.tnt-per-tick", 20));
        final double d = Math.max(1.0, this.plugin.getConfig().getDouble("ownerstuff.tnt-spread", 5.0));
        this.plugin.send((CommandSender)player, "<gold>" + n + " TNT<gray> gehen auf <white>" + Ui.pos(location) + "<gray> nieder.");
        this.plugin.log().add(ActivityLog.Level.ALERT, player.getName() + " ließ " + n + " TNT niedergehen", location, player.getUniqueId());
        world.playSound(location, Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.2f);
        new BukkitRunnable(this){
            private int spawned;
            {
                Objects.requireNonNull(ownerPowers);
            }

            public void run() {
                if (this.spawned >= n) {
                    this.cancel();
                    return;
                }
                int n3 = Math.min(n2, n - this.spawned);
                for (int i = 0; i < n3; ++i) {
                    Location location2 = location.clone().add(ThreadLocalRandom.current().nextDouble(-d, d), 8.0 + ThreadLocalRandom.current().nextDouble(0.0, 8.0), ThreadLocalRandom.current().nextDouble(-d, d));
                    TNTPrimed tNTPrimed = (TNTPrimed)world.spawn(location2, TNTPrimed.class);
                    tNTPrimed.setFuseTicks(40 + ThreadLocalRandom.current().nextInt(80));
                }
                this.spawned += n3;
            }
        }.runTaskTimer((Plugin)this.plugin, 0L, 1L);
    }

    public void lightning(Player player, Location location) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        world.strikeLightning(location);
        this.plugin.log().add(ActivityLog.Level.INFO, player.getName() + " rief einen Blitz", location, player.getUniqueId());
    }

    public void healNearby(Player player) {
        int n = 0;
        for (Player player2 : this.nearbyPlayers(player, 20.0)) {
            PlayerActionMenu.fullyHeal(player2);
            player2.getWorld().spawnParticle(Particle.HEART, player2.getLocation().add(0.0, 1.0, 0.0), 8, 0.4, 0.6, 0.4, 0.02);
            ++n;
        }
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.6f);
        this.plugin.send((CommandSender)player, "<green>" + n + " Spieler geheilt.");
    }

    public void freezeNearby(Player player) {
        int n = Math.max(1, this.plugin.getConfig().getInt("ownerstuff.freeze-seconds", 10));
        ArrayList<UUID> arrayList = new ArrayList<UUID>();
        for (Player player2 : this.nearbyPlayers(player, 20.0)) {
            if (player2.equals((Object)player) || this.plugin.access().hasRole(player2.getUniqueId())) continue;
            this.plugin.state().setFrozen(player2.getUniqueId(), true);
            arrayList.add(player2.getUniqueId());
            this.plugin.send((CommandSender)player2, "<aqua>Du steckst fest.");
            player2.getWorld().spawnParticle(Particle.SNOWFLAKE, player2.getLocation().add(0.0, 1.0, 0.0), 30, 0.4, 0.8, 0.4, 0.02);
        }
        player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0f, 0.8f);
        this.plugin.send((CommandSender)player, "<aqua>" + arrayList.size() + " Spieler für " + n + "s eingefroren.");
        Bukkit.getScheduler().runTaskLater((Plugin)this.plugin, () -> {
            for (UUID uUID : arrayList) {
                this.plugin.state().setFrozen(uUID, false);
                Player player = Bukkit.getPlayer((UUID)uUID);
                if (player == null) continue;
                this.plugin.send((CommandSender)player, "<green>Du kannst dich wieder bewegen.");
            }
        }, (long)n * 20L);
    }

    public void launchNearby(Player player) {
        int n = 0;
        for (Player player2 : this.nearbyPlayers(player, 15.0)) {
            if (player2.equals((Object)player)) continue;
            player2.setVelocity(new Vector(0.0, 1.8, 0.0));
            player2.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 300, 0));
            ++n;
        }
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 1.0f);
        this.plugin.send((CommandSender)player, "<gold>" + n + " Spieler in die Luft geschickt.");
    }

    public void toggleTime(Player player) {
        World world = player.getWorld();
        boolean bl = world.getTime() < 12000L;
        world.setTime(bl ? 13000L : 1000L);
        this.plugin.send((CommandSender)player, "<gray>Zeit in <white>" + world.getName() + "<gray> auf <white>" + (bl ? "Nacht" : "Tag") + "<gray> gestellt.");
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.2f);
    }

    public void cleanNearby(Player player) {
        int n = 0;
        int n2 = 0;
        for (Entity entity : player.getNearbyEntities(40.0, 40.0, 40.0)) {
            if (entity instanceof Monster) {
                entity.remove();
                ++n;
                continue;
            }
            if (!(entity instanceof Item)) continue;
            entity.remove();
            ++n2;
        }
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 0.8f);
        this.plugin.send((CommandSender)player, "<gray>" + n + " Monster und " + n2 + " Bodenitems entfernt.");
    }

    private List<Player> nearbyPlayers(Player player, double d) {
        ArrayList<Player> arrayList = new ArrayList<Player>();
        arrayList.add(player);
        for (Entity entity : player.getNearbyEntities(d, d, d)) {
            if (!(entity instanceof Player)) continue;
            Player player2 = (Player)entity;
            arrayList.add(player2);
        }
        return arrayList;
    }

    public void supplies(Player player) {
        this.plugin.giveItems(player, new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 64), new ItemStack(Material.TOTEM_OF_UNDYING, 16), new ItemStack(Material.FIREWORK_ROCKET, 64), new ItemStack(Material.ENDER_PEARL, 16));
        this.plugin.send((CommandSender)player, "<gray>Vorrat eingepackt.");
    }
}

