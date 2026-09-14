/*
 * Decompiled with CFR 0.152.
 */
package com.glowcube.utils;

import com.glowcube.utils.Home;
import com.glowcube.utils.Msg;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

public class TeleportManager
implements Listener {
    private static final Title.Times COUNTDOWN_TIMES = Title.Times.times((Duration)Duration.ZERO, (Duration)Duration.ofSeconds(50L), (Duration)Duration.ZERO);
    private final JavaPlugin plugin;
    private final Map<UUID, BukkitTask> pending = new HashMap<UUID, BukkitTask>();

    public TeleportManager(JavaPlugin javaPlugin) {
        this.plugin = javaPlugin;
    }

    public boolean isTeleporting(Player player) {
        return this.pending.containsKey(player.getUniqueId());
    }

    public void teleport(Player player, Home home) {
        if (this.isTeleporting(player)) {
            Msg.error((CommandSender)player, "Du wirst bereits teleportiert.", new TagResolver[0]);
            return;
        }
        Location location = home.toLocation();
        if (location == null) {
            Msg.error((CommandSender)player, "Die Welt von Home <yellow><home></yellow> ist gerade nicht geladen.", Msg.ph("home", home.name()));
            return;
        }
        int n = this.plugin.getConfig().getInt("teleport-delay", 3);
        if (n <= 0 || player.hasPermission("glowcube.home.bypass-delay")) {
            this.finish(player, home, location);
            return;
        }
        location.getWorld().getChunkAtAsync(location);
        int[] nArray = new int[]{n};
        BukkitTask bukkitTask = this.plugin.getServer().getScheduler().runTaskTimer((Plugin)this.plugin, () -> {
            if (!player.isOnline()) {
                this.cancel(player, false);
                return;
            }
            if (nArray[0] <= 0) {
                this.cleanup(player);
                this.finish(player, home, location);
                return;
            }
            player.showTitle(Title.title((Component)Msg.mm("<aqua><bold>Bitte stehen bleiben", new TagResolver[0]), (Component)Msg.mm("<gray>Teleport in <yellow>" + nArray[0] + "<gray>...", new TagResolver[0]), (Title.Times)COUNTDOWN_TIMES));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f + (float)(n - nArray[0]) * 0.15f);
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, false, false, false));
            nArray[0] = nArray[0] - 1;
        }, 0L, 20L);
        this.pending.put(player.getUniqueId(), bukkitTask);
    }

    private void finish(Player player, Home home, Location location) {
        boolean bl2 = this.plugin.getConfig().getBoolean("teleport-safety", true);
        Location location2 = location;
        if (bl2 && !TeleportManager.isSafe(location)) {
            location2 = TeleportManager.findSafeLocation(location);
            if (location2 == null) {
                Msg.error((CommandSender)player, "Teleport abgebrochen: Home <yellow><home></yellow> ist nicht sicher und in der Naehe wurde kein sicherer Platz gefunden.", Msg.ph("home", home.name()));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }
            Msg.info((CommandSender)player, "Dein Home war nicht sicher, du wurdest zum naechsten sicheren Platz gebracht.", new TagResolver[0]);
        }
        Location location3 = location2;
        player.teleportAsync(location3).thenAccept(bl -> {
            if (!bl.booleanValue()) {
                Msg.error((CommandSender)player, "Teleport fehlgeschlagen.", new TagResolver[0]);
                return;
            }
            player.removePotionEffect(PotionEffectType.BLINDNESS);
            player.playSound(location3, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
            player.playSound(location3, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);
            location3.getWorld().spawnParticle(Particle.PORTAL, location3.clone().add(0.0, 1.0, 0.0), 100, 0.5, 1.0, 0.5, 0.1);
            Msg.success((CommandSender)player, "Teleportiert zu <yellow><home></yellow>.", Msg.ph("home", home.name()));
        });
    }

    private void cleanup(Player player) {
        BukkitTask bukkitTask = this.pending.remove(player.getUniqueId());
        if (bukkitTask != null) {
            bukkitTask.cancel();
        }
    }

    public void cancel(Player player, boolean bl) {
        if (!this.isTeleporting(player)) {
            return;
        }
        this.cleanup(player);
        player.clearTitle();
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        if (bl) {
            Msg.error((CommandSender)player, "Teleport abgebrochen, weil du dich bewegt hast.", new TagResolver[0]);
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
        }
    }

    public void cancelAll() {
        for (BukkitTask bukkitTask : this.pending.values()) {
            bukkitTask.cancel();
        }
        this.pending.clear();
    }

    @EventHandler(ignoreCancelled=true)
    public void onMove(PlayerMoveEvent playerMoveEvent) {
        if (!playerMoveEvent.hasChangedBlock()) {
            return;
        }
        if (!this.plugin.getConfig().getBoolean("cancel-on-move", true)) {
            return;
        }
        this.cancel(playerMoveEvent.getPlayer(), true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent playerQuitEvent) {
        this.cancel(playerQuitEvent.getPlayer(), false);
    }

    private static boolean isSafe(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return false;
        }
        Block block = world.getBlockAt(location);
        Block block2 = block.getRelative(0, 1, 0);
        Block block3 = block.getRelative(0, -1, 0);
        return TeleportManager.isPassable(block) && TeleportManager.isPassable(block2) && TeleportManager.isSolidGround(block3);
    }

    private static boolean isPassable(Block block) {
        Material material = block.getType();
        if (material == Material.LAVA || material == Material.FIRE || material == Material.SOUL_FIRE || material == Material.MAGMA_BLOCK || material == Material.CACTUS || material == Material.SWEET_BERRY_BUSH || material == Material.POWDER_SNOW || material == Material.WITHER_ROSE) {
            return false;
        }
        return block.isPassable() && !block.isLiquid();
    }

    private static boolean isSolidGround(Block block) {
        Material material = block.getType();
        if (material == Material.LAVA || material == Material.MAGMA_BLOCK || material == Material.CACTUS || material == Material.CAMPFIRE || material == Material.SOUL_CAMPFIRE || material == Material.POWDER_SNOW) {
            return false;
        }
        return material.isSolid() || material == Material.WATER;
    }

    private static Location findSafeLocation(Location location) {
        World world = location.getWorld();
        int n = location.getBlockX();
        int n2 = location.getBlockY();
        int n3 = location.getBlockZ();
        for (int i = 0; i <= 2; ++i) {
            for (int j = -i; j <= i; ++j) {
                for (int k = -i; k <= i; ++k) {
                    if (Math.abs(j) != i && Math.abs(k) != i) continue;
                    block3: for (int i2 = 0; i2 <= 6; ++i2) {
                        for (int n4 : new int[]{1, -1}) {
                            int n5 = n2 + i2 * n4;
                            if (n5 <= world.getMinHeight() || n5 >= world.getMaxHeight() - 1) continue;
                            Location location2 = new Location(world, (double)(n + j) + 0.5, (double)n5, (double)(n3 + k) + 0.5, location.getYaw(), location.getPitch());
                            if (TeleportManager.isSafe(location2)) {
                                return location2;
                            }
                            if (i2 == 0) continue block3;
                        }
                    }
                }
            }
        }
        return null;
    }
}

