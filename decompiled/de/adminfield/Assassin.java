/*
 * Decompiled with CFR 0.152.
 */
package de.adminfield;

import com.destroystokyo.paper.entity.ai.GoalType;
import de.adminfield.AdminFieldPlugin;
import de.adminfield.Ui;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

public final class Assassin {
    public static final List<String> NAMES = List.of("Der Attentäter", "Kopfgeldjäger", "Der Schatten", "Schwarzer Ritter", "Nachtklinge", "Der Wanderer", "Grabwächter", "Die Klinge");
    private final AdminFieldPlugin plugin;
    private final NamespacedKey markKey;
    private final Map<UUID, Contract> contracts = new HashMap<UUID, Contract>();

    public Assassin(AdminFieldPlugin adminFieldPlugin) {
        this.plugin = adminFieldPlugin;
        this.markKey = new NamespacedKey((Plugin)adminFieldPlugin, "assassin_target");
    }

    public boolean isHunted(UUID uUID) {
        return this.contracts.containsKey(uUID);
    }

    public String hunterName(UUID uUID) {
        Contract contract = this.contracts.get(uUID);
        return contract == null ? null : contract.name;
    }

    public int count() {
        return this.contracts.size();
    }

    public String validateName(Player player, String string) {
        String string2;
        String string3 = string2 = string == null ? "" : string.trim();
        if (string2.length() < 2 || string2.length() > 24) {
            return "Der Name muss zwischen 2 und 24 Zeichen lang sein.";
        }
        if (string2.chars().anyMatch(Character::isISOControl)) {
            return "Der Name enthält ungültige Zeichen.";
        }
        if (string2.equalsIgnoreCase(player.getName())) {
            return null;
        }
        if (Bukkit.getPlayerExact((String)string2) != null) {
            return "So heißt ein Spieler, der gerade online ist. Bitte einen Kampfnamen wählen.";
        }
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayerIfCached((String)string2);
        if (offlinePlayer != null && offlinePlayer.hasPlayedBefore()) {
            return "So heißt ein Spieler, der schon auf diesem Server war. Bitte einen Kampfnamen wählen.";
        }
        return null;
    }

    public boolean send(Player player, Player player2, String string, boolean bl) {
        return this.send(player, player2, string, bl, false);
    }

    public boolean send(Player player, Player player2, String string, boolean bl, boolean bl2) {
        Location location;
        if (!this.plugin.access().isOwner(player.getUniqueId())) {
            return false;
        }
        if (this.isHunted(player2.getUniqueId())) {
            this.dismiss(player2.getUniqueId(), false);
        }
        if ((location = this.findSpot(player2)) == null) {
            this.plugin.send((CommandSender)player, "<red>Kein Platz für den Attentäter in der Nähe von " + player2.getName() + ".");
            return false;
        }
        double d = this.plugin.getConfig().getDouble(bl ? "assassin.health-strong" : "assassin.health", bl ? 120.0 : 60.0);
        double d2 = this.plugin.getConfig().getDouble(bl ? "assassin.damage-strong" : "assassin.damage", bl ? 14.0 : 9.0);
        Zombie zombie2 = (Zombie)player2.getWorld().spawn(location, Zombie.class, zombie -> {
            ItemStack itemStack;
            zombie.setAdult();
            zombie.setShouldBurnInDay(false);
            zombie.setCanPickupItems(false);
            zombie.setRemoveWhenFarAway(false);
            zombie.setPersistent(true);
            zombie.setSilent(true);
            Bukkit.getMobGoals().removeAllGoals((Mob)zombie, GoalType.TARGET);
            Bukkit.getMobGoals().removeAllGoals((Mob)zombie, GoalType.MOVE);
            Bukkit.getMobGoals().removeAllGoals((Mob)zombie, GoalType.LOOK);
            zombie.setAggressive(false);
            zombie.customName(Ui.item("<white>" + string));
            zombie.setCustomNameVisible(this.plugin.disguise() == null);
            zombie.getPersistentDataContainer().set(this.markKey, PersistentDataType.STRING, (Object)player2.getUniqueId().toString());
            EntityEquipment entityEquipment = zombie.getEquipment();
            if (this.plugin.disguise() != null) {
                itemStack = new ItemStack(bl ? Material.NETHERITE_HELMET : Material.DIAMOND_HELMET);
            } else {
                itemStack = new ItemStack(Material.PLAYER_HEAD);
                if (bl2) {
                    SkullMeta skullMeta = (SkullMeta)itemStack.getItemMeta();
                    skullMeta.setOwningPlayer((OfflinePlayer)player);
                    itemStack.setItemMeta((ItemMeta)skullMeta);
                }
            }
            entityEquipment.setHelmet(itemStack);
            entityEquipment.setChestplate(new ItemStack(bl ? Material.NETHERITE_CHESTPLATE : Material.DIAMOND_CHESTPLATE));
            entityEquipment.setLeggings(new ItemStack(bl ? Material.NETHERITE_LEGGINGS : Material.DIAMOND_LEGGINGS));
            entityEquipment.setBoots(new ItemStack(bl ? Material.NETHERITE_BOOTS : Material.DIAMOND_BOOTS));
            entityEquipment.setItemInMainHand(new ItemStack(bl ? Material.NETHERITE_SWORD : Material.DIAMOND_SWORD));
            entityEquipment.setHelmetDropChance(0.0f);
            entityEquipment.setChestplateDropChance(0.0f);
            entityEquipment.setLeggingsDropChance(0.0f);
            entityEquipment.setBootsDropChance(0.0f);
            entityEquipment.setItemInMainHandDropChance(0.0f);
            Assassin.set(zombie, Attribute.MAX_HEALTH, d);
            Assassin.set(zombie, Attribute.ATTACK_DAMAGE, d2);
            Assassin.set(zombie, Attribute.MOVEMENT_SPEED, 0.36);
            Assassin.set(zombie, Attribute.FOLLOW_RANGE, 80.0);
            Assassin.set(zombie, Attribute.KNOCKBACK_RESISTANCE, 0.7);
            zombie.setHealth(d);
        });
        if (this.plugin.disguise() != null) {
            this.plugin.disguise().apply((LivingEntity)zombie2, string, bl2 ? player.getPlayerProfile() : null);
        }
        zombie2.getPathfinder().moveTo((LivingEntity)player2, 1.25);
        this.contracts.put(player2.getUniqueId(), new Contract(player2.getUniqueId(), zombie2.getUniqueId(), string, bl));
        World world = location.getWorld();
        world.spawnParticle(Particle.SMOKE, location.clone().add(0.0, 1.0, 0.0), 20, 0.3, 0.5, 0.3, 0.02);
        world.playSound(location, Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 0.6f);
        this.plugin.send((CommandSender)player, "<gray><white>" + string + "<gray> ist unterwegs zu <white>" + player2.getName() + "<gray>.");
        return true;
    }

    private static void set(Zombie zombie, Attribute attribute, double d) {
        AttributeInstance attributeInstance = zombie.getAttribute(attribute);
        if (attributeInstance != null) {
            attributeInstance.setBaseValue(d);
        }
    }

    private Location findSpot(Player player) {
        World world = player.getWorld();
        Location location = player.getEyeLocation();
        int n = Math.max(4, this.plugin.getConfig().getInt("assassin.spawn-distance", 10));
        Location location2 = null;
        for (int i = 0; i < 30; ++i) {
            Vector vector;
            int n2;
            double d = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
            double d2 = (double)n + ThreadLocalRandom.current().nextDouble(-2.0, 2.0);
            int n3 = location.getBlockX() + (int)Math.round(Math.cos(d) * d2);
            if (!world.isChunkLoaded(n3 >> 4, (n2 = location.getBlockZ() + (int)Math.round(Math.sin(d) * d2)) >> 4)) continue;
            Location location3 = null;
            for (int j = 4; j >= -6 && location3 == null; --j) {
                int n4 = location.getBlockY() + j;
                if (n4 <= world.getMinHeight() + 1 || n4 >= world.getMaxHeight() - 2) continue;
                Block block = world.getBlockAt(n3, n4 - 1, n2);
                Block block2 = world.getBlockAt(n3, n4, n2);
                Block block3 = world.getBlockAt(n3, n4 + 1, n2);
                if (!block.getType().isSolid() || !block2.getType().isAir() || !block3.getType().isAir()) continue;
                location3 = new Location(world, (double)n3 + 0.5, (double)n4, (double)n2 + 0.5);
            }
            if (location3 == null) continue;
            if (location2 == null) {
                location2 = location3;
            }
            if (!((vector = location3.toVector().subtract(location.toVector()).normalize()).dot(location.getDirection()) < 0.3)) continue;
            return location3;
        }
        return location2;
    }

    public void tick() {
        if (this.contracts.isEmpty()) {
            return;
        }
        long l = Math.max(20L, this.plugin.getConfig().getLong("assassin.timeout-seconds", 180L)) * 1000L;
        long l2 = System.currentTimeMillis();
        for (Contract contract : new ArrayList<Contract>(this.contracts.values())) {
            Location location;
            Zombie zombie;
            Entity entity = Bukkit.getEntity((UUID)contract.assassin);
            Player player = Bukkit.getPlayer((UUID)contract.target);
            if (!(entity instanceof Zombie) || (zombie = (Zombie)entity).isDead()) {
                this.contracts.remove(contract.target);
                continue;
            }
            if (player == null || !player.isOnline() || player.isDead() || player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR || l2 - contract.since > l) {
                this.vanish(zombie);
                this.contracts.remove(contract.target);
                continue;
            }
            if ((!zombie.getWorld().equals((Object)player.getWorld()) || zombie.getLocation().distanceSquared(player.getLocation()) > 2025.0) && (location = this.findSpot(player)) != null) {
                zombie.teleportAsync(location);
            }
            if (zombie.isInWater() && (location = this.findSpot(player)) != null) {
                zombie.teleportAsync(location);
            }
            zombie.getPathfinder().moveTo((LivingEntity)player, 1.25);
            zombie.lookAt((Entity)player);
            zombie.setAggressive(false);
            double d = 11.559999999999999;
            if (!zombie.getWorld().equals((Object)player.getWorld()) || !(zombie.getLocation().distanceSquared(player.getLocation()) <= d) || l2 - contract.lastStrike < 600L) continue;
            contract.lastStrike = l2;
            double d2 = this.plugin.getConfig().getDouble(contract.strong ? "assassin.damage-strong" : "assassin.damage", contract.strong ? 14.0 : 9.0);
            zombie.swingMainHand();
            player.damage(d2, (Entity)zombie);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.0f);
        }
    }

    public void onDeath(PlayerDeathEvent playerDeathEvent) {
        Zombie zombie;
        EntityDamageByEntityEvent entityDamageByEntityEvent;
        boolean bl;
        Player player = playerDeathEvent.getPlayer();
        Contract contract = this.contracts.get(player.getUniqueId());
        if (contract == null) {
            return;
        }
        EntityDamageEvent entityDamageEvent = player.getLastDamageCause();
        boolean bl2 = bl = entityDamageEvent instanceof EntityDamageByEntityEvent && (entityDamageByEntityEvent = (EntityDamageByEntityEvent)entityDamageEvent).getDamager().getUniqueId().equals(contract.assassin);
        if (!bl) {
            return;
        }
        playerDeathEvent.deathMessage((Component)Component.translatable((String)"death.attack.mob", (ComponentLike[])new ComponentLike[]{Component.text((String)player.getName()), Component.text((String)contract.name)}));
        entityDamageByEntityEvent = Bukkit.getEntity((UUID)contract.assassin);
        if (entityDamageByEntityEvent instanceof Zombie) {
            zombie = (Zombie)entityDamageByEntityEvent;
            this.vanish(zombie);
        }
        this.contracts.remove(player.getUniqueId());
        Player player2 = zombie = this.plugin.access().ownerId() == null ? null : Bukkit.getPlayer((UUID)this.plugin.access().ownerId());
        if (zombie != null) {
            this.plugin.send((CommandSender)zombie, "<gold>Auftrag ausgeführt: <white>" + contract.name + "<gray> hat <white>" + player.getName() + "<gray> erledigt.");
        }
    }

    public void onQuit(UUID uUID) {
        this.dismiss(uUID, false);
    }

    public boolean isAssassin(Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(this.markKey, PersistentDataType.STRING);
    }

    public boolean dismiss(UUID uUID, boolean bl) {
        Contract contract = this.contracts.remove(uUID);
        if (contract == null) {
            return false;
        }
        Entity entity = Bukkit.getEntity((UUID)contract.assassin);
        if (entity instanceof Zombie) {
            Zombie zombie = (Zombie)entity;
            this.vanish(zombie);
        }
        return true;
    }

    public void dismissAll() {
        for (UUID uUID : new ArrayList<UUID>(this.contracts.keySet())) {
            this.dismiss(uUID, false);
        }
    }

    private void vanish(Zombie zombie) {
        if (this.plugin.disguise() != null) {
            this.plugin.disguise().remove((Entity)zombie);
        }
        Location location = zombie.getLocation();
        location.getWorld().spawnParticle(Particle.LARGE_SMOKE, location.clone().add(0.0, 1.0, 0.0), 25, 0.3, 0.6, 0.3, 0.02);
        location.getWorld().playSound(location, Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 0.5f);
        zombie.remove();
    }

    private static final class Contract {
        final UUID target;
        final UUID assassin;
        final String name;
        final boolean strong;
        final long since = System.currentTimeMillis();
        long lastStrike;

        Contract(UUID uUID, UUID uUID2, String string, boolean bl) {
            this.target = uUID;
            this.assassin = uUID2;
            this.name = string;
            this.strong = bl;
        }
    }
}

