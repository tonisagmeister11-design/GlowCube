/*
 * Decompiled with CFR 0.152.
 */
package de.adminfield;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import de.adminfield.ActivityLog;
import de.adminfield.AdminFieldPlugin;
import de.adminfield.AdminState;
import de.adminfield.OwnerPowers;
import de.adminfield.Ui;
import de.adminfield.menu.Menu;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;

public final class AdminListeners
implements Listener {
    private final AdminFieldPlugin plugin;

    public AdminListeners(AdminFieldPlugin adminFieldPlugin) {
        this.plugin = adminFieldPlugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent inventoryClickEvent) {
        InventoryHolder inventoryHolder = inventoryClickEvent.getInventory().getHolder();
        if (!(inventoryHolder instanceof Menu)) {
            return;
        }
        Menu menu = (Menu)inventoryHolder;
        if (!menu.allowClick(inventoryClickEvent)) {
            inventoryClickEvent.setCancelled(true);
        }
        if (inventoryClickEvent.getClickedInventory() == null || !(inventoryClickEvent.getClickedInventory().getHolder() instanceof Menu)) {
            return;
        }
        menu.click(inventoryClickEvent);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent inventoryDragEvent) {
        Menu menu;
        InventoryHolder inventoryHolder = inventoryDragEvent.getInventory().getHolder();
        if (inventoryHolder instanceof Menu && !(menu = (Menu)inventoryHolder).allowDrag(inventoryDragEvent)) {
            inventoryDragEvent.setCancelled(true);
        }
    }

    @EventHandler
    public void onMenuClose(InventoryCloseEvent inventoryCloseEvent) {
        InventoryHolder inventoryHolder = inventoryCloseEvent.getInventory().getHolder();
        if (inventoryHolder instanceof Menu) {
            Menu menu = (Menu)inventoryHolder;
            menu.closed();
        }
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onPlace(BlockPlaceEvent blockPlaceEvent) {
        this.plugin.tracker().onPlace(blockPlaceEvent.getPlayer(), blockPlaceEvent.getBlockPlaced());
        if (blockPlaceEvent.getBlockPlaced().getType() == Material.TNT) {
            this.plugin.tracker().dangerous(blockPlaceEvent.getPlayer(), blockPlaceEvent.getBlockPlaced(), "TNT platziert");
        }
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onBreak(BlockBreakEvent blockBreakEvent) {
        this.plugin.tracker().onBreak(blockBreakEvent.getPlayer(), blockBreakEvent.getBlock());
        this.plugin.cheats().onBreak(blockBreakEvent.getPlayer(), blockBreakEvent.getBlock());
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onDamageWatch(EntityDamageEvent entityDamageEvent) {
        Entity entity = entityDamageEvent.getEntity();
        if (entity instanceof Player) {
            Player player = (Player)entity;
            this.plugin.cheats().noteDamage(player.getUniqueId(), entityDamageEvent.getCause() == EntityDamageEvent.DamageCause.FALL);
        }
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onAttackWatch(EntityDamageByEntityEvent entityDamageByEntityEvent) {
        Entity entity = entityDamageByEntityEvent.getDamager();
        if (entity instanceof Player) {
            Player player = (Player)entity;
            this.plugin.cheats().onAttack(player, entityDamageByEntityEvent.getEntity());
        }
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onTeleportWatch(PlayerTeleportEvent playerTeleportEvent) {
        this.plugin.cheats().noteTeleport(playerTeleportEvent.getPlayer());
    }

    @EventHandler(ignoreCancelled=true)
    public void onCurseJump(PlayerJumpEvent playerJumpEvent) {
        this.plugin.curses().onJump(playerJumpEvent.getPlayer());
    }

    @EventHandler(ignoreCancelled=true)
    public void onCurseDrop(BlockDropItemEvent blockDropItemEvent) {
        this.plugin.curses().onDrop(blockDropItemEvent.getPlayer(), blockDropItemEvent.getItems());
    }

    @EventHandler(ignoreCancelled=true)
    public void onCurseSprint(PlayerToggleSprintEvent playerToggleSprintEvent) {
        if (playerToggleSprintEvent.isSprinting()) {
            this.plugin.curses().onSprint(playerToggleSprintEvent.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled=true)
    public void onCurseContainer(InventoryOpenEvent inventoryOpenEvent) {
        if (inventoryOpenEvent.getInventory().getHolder() instanceof Menu) {
            return;
        }
        HumanEntity humanEntity = inventoryOpenEvent.getPlayer();
        if (humanEntity instanceof Player) {
            Player player = (Player)humanEntity;
            this.plugin.curses().onOpenContainer(player);
        }
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onRespawnWatch(PlayerRespawnEvent playerRespawnEvent) {
        Player player = playerRespawnEvent.getPlayer();
        Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> this.plugin.cheats().noteRespawn(player));
    }

    @EventHandler(ignoreCancelled=true)
    public void onBucket(PlayerBucketEmptyEvent playerBucketEmptyEvent) {
        if (playerBucketEmptyEvent.getBucket() == Material.LAVA_BUCKET) {
            this.plugin.tracker().dangerous(playerBucketEmptyEvent.getPlayer(), playerBucketEmptyEvent.getBlock(), "Lava ausgegossen");
        }
    }

    @EventHandler(ignoreCancelled=true)
    public void onIgnite(BlockIgniteEvent blockIgniteEvent) {
        Player player = blockIgniteEvent.getPlayer();
        if (player != null && blockIgniteEvent.getCause() == BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL) {
            this.plugin.tracker().dangerous(player, blockIgniteEvent.getBlock(), "Feuer gelegt");
        }
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onExplode(EntityExplodeEvent entityExplodeEvent) {
        if (entityExplodeEvent.blockList().size() < 8) {
            return;
        }
        Location location = entityExplodeEvent.getLocation();
        this.plugin.log().add(ActivityLog.Level.WARN, "Explosion (" + entityExplodeEvent.blockList().size() + " Blöcke) durch " + Ui.pretty(entityExplodeEvent.getEntityType().name()), location, null);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent playerJoinEvent) {
        Player player = playerJoinEvent.getPlayer();
        this.plugin.log().add(ActivityLog.Level.INFO, player.getName() + " hat den Server betreten", player.getLocation(), player.getUniqueId());
        this.plugin.state().applyVanishFor(player);
        if (this.plugin.state().isVanished(player)) {
            this.plugin.state().setVanished(player, true);
        }
        this.plugin.jail().onJoin(player);
        this.plugin.deathNote().deliverPending(player);
    }

    @EventHandler(ignoreCancelled=true)
    public void onWand(PlayerInteractEvent playerInteractEvent) {
        if (playerInteractEvent.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (playerInteractEvent.getAction() != Action.RIGHT_CLICK_AIR && playerInteractEvent.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        String string = this.plugin.ownerItems().idOf(playerInteractEvent.getItem());
        if (string == null || !string.startsWith("wand_")) {
            return;
        }
        Player player = playerInteractEvent.getPlayer();
        if (!this.plugin.access().isOwner(player.getUniqueId())) {
            this.plugin.send((CommandSender)player, "<red>Dieser Stab gehorcht nur dem Owner.");
            playerInteractEvent.setCancelled(true);
            return;
        }
        playerInteractEvent.setCancelled(true);
        OwnerPowers ownerPowers = this.plugin.powers();
        switch (string) {
            case "wand_tnt": {
                ownerPowers.tntRain(player, ownerPowers.aim(player));
                break;
            }
            case "wand_lightning": {
                ownerPowers.lightning(player, ownerPowers.aim(player));
                break;
            }
            case "wand_heal": {
                ownerPowers.healNearby(player);
                break;
            }
            case "wand_freeze": {
                ownerPowers.freezeNearby(player);
                break;
            }
            case "wand_launch": {
                ownerPowers.launchNearby(player);
                break;
            }
            case "wand_time": {
                ownerPowers.toggleTime(player);
                break;
            }
            case "wand_clean": {
                ownerPowers.cleanNearby(player);
                break;
            }
        }
    }

    @EventHandler
    public void onJailSetupClick(PlayerInteractEvent playerInteractEvent) {
        Player player = playerInteractEvent.getPlayer();
        if (!this.plugin.jail().isSettingUp(player.getUniqueId())) {
            return;
        }
        if (playerInteractEvent.getAction() != Action.LEFT_CLICK_BLOCK && playerInteractEvent.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block block = playerInteractEvent.getClickedBlock();
        if (block == null) {
            return;
        }
        playerInteractEvent.setCancelled(true);
        this.plugin.jail().completeSetup(player, block);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent playerQuitEvent) {
        Player player = playerQuitEvent.getPlayer();
        this.plugin.log().add(ActivityLog.Level.INFO, player.getName() + " hat den Server verlassen", player.getLocation(), player.getUniqueId());
        this.plugin.tracker().forgetPlayer(player.getUniqueId());
        this.plugin.state().takePrompt(player.getUniqueId());
        this.plugin.assassin().onQuit(player.getUniqueId());
    }

    @EventHandler
    public void onAssassinDeath(EntityDeathEvent entityDeathEvent) {
        if (this.plugin.assassin().isAssassin((Entity)entityDeathEvent.getEntity())) {
            entityDeathEvent.getDrops().clear();
            entityDeathEvent.setDroppedExp(0);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent playerDeathEvent) {
        Player player = playerDeathEvent.getPlayer();
        this.plugin.assassin().onDeath(playerDeathEvent);
        Object object = playerDeathEvent.deathMessage() == null ? player.getName() + " ist gestorben" : PlainTextComponentSerializer.plainText().serialize(playerDeathEvent.deathMessage());
        this.plugin.log().add(ActivityLog.Level.WARN, (String)object, player.getLocation(), player.getUniqueId());
        this.plugin.deathNote().onDeath(player, (String)object);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent playerChangedWorldEvent) {
        Player player = playerChangedWorldEvent.getPlayer();
        if (!this.plugin.state().isWatched(player.getUniqueId())) {
            return;
        }
        this.plugin.log().add(ActivityLog.Level.INFO, player.getName() + " wechselte nach " + player.getWorld().getName(), player.getLocation(), player.getUniqueId());
    }

    @EventHandler(ignoreCancelled=true)
    public void onCommand(PlayerCommandPreprocessEvent playerCommandPreprocessEvent) {
        Player player = playerCommandPreprocessEvent.getPlayer();
        if (!this.plugin.state().isWatched(player.getUniqueId())) {
            return;
        }
        this.plugin.log().add(ActivityLog.Level.INFO, player.getName() + " führte aus: " + playerCommandPreprocessEvent.getMessage(), player.getLocation(), player.getUniqueId());
    }

    @EventHandler(ignoreCancelled=true)
    public void onMove(PlayerMoveEvent playerMoveEvent) {
        if (!this.plugin.state().isFrozen(playerMoveEvent.getPlayer().getUniqueId())) {
            return;
        }
        Location location = playerMoveEvent.getFrom();
        Location location2 = playerMoveEvent.getTo();
        if (location2 == null) {
            return;
        }
        if (location.getBlockX() == location2.getBlockX() && location.getBlockY() == location2.getBlockY() && location.getBlockZ() == location2.getBlockZ()) {
            return;
        }
        playerMoveEvent.setTo(new Location(location.getWorld(), location.getX(), location.getY(), location.getZ(), location2.getYaw(), location2.getPitch()));
    }

    @EventHandler(ignoreCancelled=true)
    public void onDamage(EntityDamageEvent entityDamageEvent) {
        Entity entity = entityDamageEvent.getEntity();
        if (entity instanceof Player) {
            Player player = (Player)entity;
            if (this.plugin.state().isGod(player.getUniqueId())) {
                entityDamageEvent.setCancelled(true);
            }
        }
    }

    @EventHandler(priority=EventPriority.LOWEST)
    public void onChat(AsyncChatEvent asyncChatEvent) {
        Player player = asyncChatEvent.getPlayer();
        AdminState.Prompt prompt = this.plugin.state().prompt(player.getUniqueId());
        if (prompt == null) {
            return;
        }
        asyncChatEvent.setCancelled(true);
        this.plugin.state().takePrompt(player.getUniqueId());
        String string = PlainTextComponentSerializer.plainText().serialize(asyncChatEvent.message()).trim();
        Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> {
            if (string.isEmpty() || string.equalsIgnoreCase("abbrechen") || string.equalsIgnoreCase("cancel")) {
                this.plugin.send((CommandSender)player, "<gray>Abgebrochen.");
                return;
            }
            prompt.handler().accept(string);
        });
    }
}

