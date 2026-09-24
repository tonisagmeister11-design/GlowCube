package de.glowcube.claudeai;

import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

import de.glowcube.claudeai.npc.Npc;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** Alles, was Claude von der Welt mitbekommt. */
final class Events implements Listener {

    private final ClaudeAIPlugin plugin;

    Events(ClaudeAIPlugin plugin) {
        this.plugin = plugin;
    }

    /** Chat laeuft asynchron - die Antwort passiert auf dem Hauptthread. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChat(AsyncChatEvent event) {
        Player p = event.getPlayer();
        String text = PlainTextComponentSerializer.plainText().serialize(event.message());
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline()) plugin.brain().onChat(p, text);
        });
    }

    /** "/spawn claude" abfangen, bevor Essentials & Co. es bekommen. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String msg = event.getMessage().trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        String name = plugin.settings().name.toLowerCase(Locale.ROOT);
        if (msg.equals("/spawn " + name) || msg.equals("/spawn claude")) {
            event.setCancelled(true);
            plugin.summon(event.getPlayer());
        } else if (msg.equals("/despawn " + name) || msg.equals("/despawn claude")) {
            event.setCancelled(true);
            if (event.getPlayer().hasPermission("claudeai.spawn")) plugin.dismiss(event.getPlayer());
        }
    }

    /** Rechtsklick auf Claude oeffnet ihr Inventar. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEntityEvent event) {
        Npc npc = plugin.npc();
        if (!npc.isBody(event.getRightClicked())) return;
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player p = event.getPlayer();
        npc.setPartner(p);
        if (p.hasPermission("claudeai.use")) p.openInventory(npc.getInventory());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent event) {
        Npc npc = plugin.npc();
        if (npc.isBody(event.getEntity()) && plugin.settings().invulnerable) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamageBy(EntityDamageByEntityEvent event) {
        Npc npc = plugin.npc();
        if (!npc.isSpawned()) return;
        Entity damager = event.getDamager();
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Entity shooter) damager = shooter;
        if (npc.isBody(damager)) return;
        if (npc.isBody(event.getEntity())) {
            npc.onHurt(damager);
            return;
        }
        if (event.getEntity() instanceof Player victim && damager instanceof LivingEntity attacker) {
            npc.onAttack(victim, attacker);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Npc npc = plugin.npc();
        Player dead = event.getEntity();
        Player partner = npc.partner();
        if (npc.isSpawned() && partner != null && partner.getUniqueId().equals(dead.getUniqueId())) {
            npc.sayLater("Oh nein, " + dead.getName() + "! Ich warte hier auf dich.", 20);
        }
    }
}
