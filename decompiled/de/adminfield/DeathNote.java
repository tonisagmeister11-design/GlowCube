/*
 * Decompiled with CFR 0.152.
 */
package de.adminfield;

import de.adminfield.ActivityLog;
import de.adminfield.AdminFieldPlugin;
import de.adminfield.DeathFate;
import de.adminfield.Ui;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.security.Key;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Drowned;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Husk;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.entity.Wolf;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

public final class DeathNote {
    private final AdminFieldPlugin plugin;
    private final NamespacedKey markKey;
    private final Map<UUID, Entry> notes = new LinkedHashMap<UUID, Entry>();
    private final List<String> history = new ArrayList<String>();
    private final List<String> pendingReports = new ArrayList<String>();
    private File file;
    private static final String JOURNAL_FILE = "journal.dat";
    private static final String KEY_FILE = ".seed";
    private static final String LEGACY_FILE = "deathnote.yml";

    public DeathNote(AdminFieldPlugin adminFieldPlugin) {
        this.plugin = adminFieldPlugin;
        this.markKey = new NamespacedKey((Plugin)adminFieldPlugin, "deathnote_target");
    }

    public Collection<Entry> entries() {
        return new ArrayList<Entry>(this.notes.values());
    }

    public Entry entry(UUID uUID) {
        return this.notes.get(uUID);
    }

    public boolean isListed(UUID uUID) {
        return this.notes.containsKey(uUID);
    }

    public int count() {
        return this.notes.size();
    }

    public List<String> history() {
        return new ArrayList<String>(this.history);
    }

    public void write(Player player, Player player2, DeathFate deathFate) {
        this.notes.put(player2.getUniqueId(), new Entry(player2.getUniqueId(), player2.getName(), deathFate, player.getName(), System.currentTimeMillis()));
        this.save();
    }

    public boolean erase(UUID uUID) {
        boolean bl;
        boolean bl2 = bl = this.notes.remove(uUID) != null;
        if (bl) {
            this.save();
        }
        return bl;
    }

    public void clear() {
        this.notes.clear();
        this.save();
    }

    public void tick() {
        if (this.notes.isEmpty()) {
            return;
        }
        long l = Math.max(15L, this.plugin.getConfig().getLong("deathnote.retry-seconds", 90L)) * 1000L;
        long l2 = System.currentTimeMillis();
        for (Entry entry : this.entries()) {
            Player player = Bukkit.getPlayer((UUID)entry.target);
            if (player == null || !player.isOnline() || player.isDead() || player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR || l2 - entry.lastAttempt < l || !this.fits(player, entry.fate)) continue;
            ++entry.attempts;
            entry.lastAttempt = l2;
            this.execute(player, entry.fate);
        }
    }

    public boolean fits(Player player, DeathFate deathFate) {
        World world = player.getWorld();
        return switch (deathFate) {
            default -> throw new MatchException(null, null);
            case DeathFate.NACHTWACHE, DeathFate.PHANTOME -> {
                if (DeathNote.overworld(world) && DeathNote.night(world) && DeathNote.seesSky(player)) {
                    yield true;
                }
                yield false;
            }
            case DeathFate.SKELETT_PATROUILLE -> {
                if (DeathNote.overworld(world) && (DeathNote.night(world) || DeathNote.dark(player))) {
                    yield true;
                }
                yield false;
            }
            case DeathFate.LEISER_CREEPER -> DeathNote.dark(player);
            case DeathFate.SPINNENNEST, DeathFate.SILBERFISCHSCHWARM -> {
                if (player.getLocation().getBlockY() < 55 && !DeathNote.water(player)) {
                    yield true;
                }
                yield false;
            }
            case DeathFate.ENDERMANN -> {
                if (DeathNote.night(world) || DeathNote.dark(player)) {
                    yield true;
                }
                yield false;
            }
            case DeathFate.ERTRUNKENE -> DeathNote.water(player);
            case DeathFate.GEWITTERSCHLAG -> {
                if (DeathNote.overworld(world) && world.isThundering() && DeathNote.seesSky(player)) {
                    yield true;
                }
                yield false;
            }
            case DeathFate.KIESRUTSCH -> {
                if (player.getLocation().getBlockY() < 60 && DeathNote.ceilingAbove(player)) {
                    yield true;
                }
                yield false;
            }
            case DeathFate.ZEHRENDER_HUNGER -> {
                if (player.getFoodLevel() < 20) {
                    yield true;
                }
                yield false;
            }
            case DeathFate.LUFT_WIRD_KNAPP -> {
                if (player.isInWater() && player.getRemainingAir() < player.getMaximumAir()) {
                    yield true;
                }
                yield false;
            }
            case DeathFate.HEXENBESUCH -> {
                if (DeathNote.night(world) || DeathNote.biome(player, "swamp")) {
                    yield true;
                }
                yield false;
            }
            case DeathFate.WOLFSRUDEL -> DeathNote.biome(player, "forest", "taiga", "grove");
            case DeathFate.BIENENSCHWARM -> {
                if (!DeathNote.night(world) && DeathNote.biome(player, "flower", "forest", "plains", "meadow")) {
                    yield true;
                }
                yield false;
            }
            case DeathFate.WUESTENWACHE -> {
                if (DeathNote.night(world) && DeathNote.biome(player, "desert")) {
                    yield true;
                }
                yield false;
            }
            case DeathFate.NETHER_HINTERHALT, DeathFate.MAGMAWUERFEL -> DeathNote.nether(world);
            case DeathFate.FESTUNGSWACHE -> {
                if (DeathNote.nether(world) && DeathNote.dark(player)) {
                    yield true;
                }
                yield false;
            }
            case DeathFate.ILLAGER_PATROUILLE -> {
                if (DeathNote.overworld(world) && DeathNote.seesSky(player)) {
                    yield true;
                }
                yield false;
            }
            case DeathFate.WAECHTER_DER_TIEFE -> {
                if (DeathNote.water(player) && DeathNote.deepWater(player)) {
                    yield true;
                }
                yield false;
            }
            case DeathFate.FROSTNACHT -> DeathNote.biome(player, "snowy", "frozen", "ice", "peaks");
        };
    }

    private void execute(Player player, DeathFate deathFate) {
        World world = player.getWorld();
        switch (deathFate) {
            case NACHTWACHE: {
                this.spawnPack(player, EntityType.ZOMBIE, 3, 5, 0.9);
                break;
            }
            case SKELETT_PATROUILLE: {
                this.spawnPack(player, EntityType.SKELETON, 3, 4, 0.8);
                break;
            }
            case LEISER_CREEPER: {
                this.spawnPack(player, EntityType.CREEPER, 1, 2, 0.5);
                break;
            }
            case SPINNENNEST: {
                this.spawnPack(player, EntityType.CAVE_SPIDER, 4, 6, 0.4);
                break;
            }
            case ENDERMANN: {
                this.spawnPack(player, EntityType.ENDERMAN, 1, 1, 0.6);
                break;
            }
            case ERTRUNKENE: {
                this.spawnPack(player, EntityType.DROWNED, 3, 4, 0.9);
                break;
            }
            case PHANTOME: {
                this.spawnPack(player, EntityType.PHANTOM, 2, 4, 0.6);
                break;
            }
            case HEXENBESUCH: {
                this.spawnPack(player, EntityType.WITCH, 1, 2, 0.5);
                break;
            }
            case WOLFSRUDEL: {
                this.spawnPack(player, EntityType.WOLF, 4, 6, 0.6);
                break;
            }
            case BIENENSCHWARM: {
                this.spawnPack(player, EntityType.BEE, 6, 9, 0.3);
                break;
            }
            case WUESTENWACHE: {
                this.spawnPack(player, EntityType.HUSK, 4, 5, 0.9);
                break;
            }
            case NETHER_HINTERHALT: {
                this.spawnPack(player, EntityType.HOGLIN, 2, 3, 0.7);
                this.spawnPack(player, EntityType.PIGLIN_BRUTE, 1, 1, 0.6);
                break;
            }
            case FESTUNGSWACHE: {
                this.spawnPack(player, EntityType.WITHER_SKELETON, 2, 3, 0.8);
                this.spawnPack(player, EntityType.BLAZE, 1, 2, 0.5);
                break;
            }
            case ILLAGER_PATROUILLE: {
                this.spawnPack(player, EntityType.PILLAGER, 3, 4, 0.7);
                this.spawnPack(player, EntityType.VINDICATOR, 1, 2, 0.7);
                break;
            }
            case SILBERFISCHSCHWARM: {
                this.spawnPack(player, EntityType.SILVERFISH, 8, 11, 0.3);
                break;
            }
            case WAECHTER_DER_TIEFE: {
                this.spawnPack(player, EntityType.GUARDIAN, 3, 4, 0.7);
                break;
            }
            case MAGMAWUERFEL: {
                this.spawnPack(player, EntityType.MAGMA_CUBE, 3, 5, 0.6);
                break;
            }
            case GEWITTERSCHLAG: {
                Location location = player.getLocation().clone().add(ThreadLocalRandom.current().nextDouble(-1.5, 1.5), 0.0, ThreadLocalRandom.current().nextDouble(-1.5, 1.5));
                world.strikeLightning(location);
                break;
            }
            case KIESRUTSCH: {
                Location location = player.getEyeLocation();
                for (int i = 0; i < ThreadLocalRandom.current().nextInt(3, 6); ++i) {
                    Location location2 = location.clone().add(ThreadLocalRandom.current().nextDouble(-0.6, 0.6), 3.0 + (double)i * 0.4, ThreadLocalRandom.current().nextDouble(-0.6, 0.6));
                    if (!location2.getBlock().getType().isAir()) continue;
                    world.spawn(location2, FallingBlock.class, fallingBlock -> fallingBlock.setBlockData(Material.GRAVEL.createBlockData()));
                }
                break;
            }
            case ZEHRENDER_HUNGER: {
                player.setSaturation(0.0f);
                player.setExhaustion(Math.min(20.0f, player.getExhaustion() + 4.0f));
                player.setFoodLevel(Math.max(0, player.getFoodLevel() - ThreadLocalRandom.current().nextInt(1, 3)));
                break;
            }
            case LUFT_WIRD_KNAPP: {
                player.setRemainingAir(Math.max(-15, player.getRemainingAir() - ThreadLocalRandom.current().nextInt(60, 120)));
                break;
            }
            case FROSTNACHT: {
                player.setFreezeTicks(Math.min(player.getMaxFreezeTicks(), player.getFreezeTicks() + ThreadLocalRandom.current().nextInt(60, 130)));
            }
        }
    }

    private void spawnPack(Player player, EntityType entityType, int n, int n2, double d) {
        int n3 = ThreadLocalRandom.current().nextInt(n, n2 + 1);
        int n4 = 0;
        for (int i = 0; i < n3 * 3 && n4 < n3; ++i) {
            Location location = this.findSpot(player, entityType);
            if (location == null) continue;
            Entity entity2 = player.getWorld().spawn(location, entityType.getEntityClass(), entity -> {
                entity.getPersistentDataContainer().set(this.markKey, PersistentDataType.STRING, (Object)player.getUniqueId().toString());
                if (entity instanceof LivingEntity) {
                    LivingEntity livingEntity = (LivingEntity)entity;
                    this.dressUp(livingEntity, d);
                }
            });
            ++n4;
            if (!(entity2 instanceof Mob)) continue;
            Mob mob = (Mob)entity2;
            int n5 = ThreadLocalRandom.current().nextInt(20, 70);
            Bukkit.getScheduler().runTaskLater((Plugin)this.plugin, () -> {
                if (mob.isValid() && player.isOnline() && mob.getWorld().equals((Object)player.getWorld())) {
                    mob.setTarget((LivingEntity)player);
                    if (mob instanceof Wolf) {
                        Wolf wolf = (Wolf)mob;
                        wolf.setAngry(true);
                    }
                }
            }, (long)n5);
        }
    }

    private void dressUp(LivingEntity livingEntity, double d) {
        AttributeInstance attributeInstance;
        livingEntity.setRemoveWhenFarAway(true);
        livingEntity.setCanPickupItems(ThreadLocalRandom.current().nextBoolean());
        if (ThreadLocalRandom.current().nextDouble() < d * 0.5 && (attributeInstance = livingEntity.getAttribute(Attribute.MAX_HEALTH)) != null) {
            double d2 = 1.0 + ThreadLocalRandom.current().nextDouble(0.15, 0.55);
            attributeInstance.setBaseValue(attributeInstance.getBaseValue() * d2);
            livingEntity.setHealth(attributeInstance.getValue());
        }
        if (ThreadLocalRandom.current().nextDouble() < d * 0.35 && (attributeInstance = livingEntity.getAttribute(Attribute.ATTACK_DAMAGE)) != null) {
            attributeInstance.setBaseValue(attributeInstance.getBaseValue() * (1.0 + ThreadLocalRandom.current().nextDouble(0.1, 0.4)));
        }
        if ((attributeInstance = livingEntity.getEquipment()) == null) {
            return;
        }
        this.maybeArmor((EntityEquipment)attributeInstance, d);
        if ((livingEntity instanceof Zombie || livingEntity instanceof Husk || livingEntity instanceof Drowned || livingEntity instanceof WitherSkeleton) && ThreadLocalRandom.current().nextDouble() < d * 0.3) {
            Material[] materialArray = new Material[]{Material.WOODEN_SWORD, Material.STONE_SWORD, Material.GOLDEN_SWORD, Material.IRON_SWORD, Material.IRON_SHOVEL};
            attributeInstance.setItemInMainHand(new ItemStack(materialArray[ThreadLocalRandom.current().nextInt(materialArray.length)]));
            attributeInstance.setItemInMainHandDropChance(0.03f);
        }
    }

    private void maybeArmor(EntityEquipment entityEquipment, double d) {
        Material[][] materialArrayArray = new Material[][]{{Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS}, {Material.GOLDEN_HELMET, Material.GOLDEN_CHESTPLATE, Material.GOLDEN_LEGGINGS, Material.GOLDEN_BOOTS}, {Material.CHAINMAIL_HELMET, Material.CHAINMAIL_CHESTPLATE, Material.CHAINMAIL_LEGGINGS, Material.CHAINMAIL_BOOTS}, {Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS}};
        int n = ThreadLocalRandom.current().nextDouble() < 0.12 ? 3 : ThreadLocalRandom.current().nextInt(3);
        Material[] materialArray = materialArrayArray[n];
        double d2 = 0.16 + d * 0.2;
        if (DeathNote.roll(d2)) {
            entityEquipment.setHelmet(new ItemStack(materialArray[0]));
            entityEquipment.setHelmetDropChance(0.035f);
        }
        if (DeathNote.roll(d2 * 0.8)) {
            entityEquipment.setChestplate(new ItemStack(materialArray[1]));
            entityEquipment.setChestplateDropChance(0.035f);
        }
        if (DeathNote.roll(d2 * 0.7)) {
            entityEquipment.setLeggings(new ItemStack(materialArray[2]));
            entityEquipment.setLeggingsDropChance(0.035f);
        }
        if (DeathNote.roll(d2 * 0.9)) {
            entityEquipment.setBoots(new ItemStack(materialArray[3]));
            entityEquipment.setBootsDropChance(0.035f);
        }
    }

    private static boolean roll(double d) {
        return ThreadLocalRandom.current().nextDouble() < d;
    }

    private Location findSpot(Player player, EntityType entityType) {
        World world = player.getWorld();
        Location location = player.getEyeLocation();
        int n = this.plugin.getConfig().getInt("deathnote.spawn-min-distance", 14);
        int n2 = Math.max(n + 4, this.plugin.getConfig().getInt("deathnote.spawn-max-distance", 26));
        boolean bl = entityType == EntityType.PHANTOM || entityType == EntityType.BEE || entityType == EntityType.BLAZE || entityType == EntityType.GUARDIAN;
        boolean bl2 = entityType == EntityType.DROWNED || entityType == EntityType.GUARDIAN;
        for (int i = 0; i < 24; ++i) {
            Location location2;
            int n3;
            double d = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
            double d2 = ThreadLocalRandom.current().nextDouble(n, n2);
            int n4 = location.getBlockX() + (int)Math.round(Math.cos(d) * d2);
            if (!world.isChunkLoaded(n4 >> 4, (n3 = location.getBlockZ() + (int)Math.round(Math.sin(d) * d2)) >> 4)) continue;
            Location location3 = location2 = bl ? this.airSpot(world, n4, location.getBlockY(), n3) : this.groundSpot(world, n4, location.getBlockY(), n3, bl2);
            if (location2 == null || this.inView(player, location2)) continue;
            return location2;
        }
        return null;
    }

    private Location groundSpot(World world, int n, int n2, int n3, boolean bl) {
        for (int i = 4; i >= -8; --i) {
            int n4 = n2 + i;
            if (n4 <= world.getMinHeight() + 1 || n4 >= world.getMaxHeight() - 2) continue;
            Block block = world.getBlockAt(n, n4 - 1, n3);
            Block block2 = world.getBlockAt(n, n4, n3);
            Block block3 = world.getBlockAt(n, n4 + 1, n3);
            if (!(bl ? block2.getType() == Material.WATER && block3.getType() == Material.WATER : block.getType().isSolid() && block2.getType().isAir() && block3.getType().isAir())) continue;
            return new Location(world, (double)n + 0.5, (double)n4, (double)n3 + 0.5);
        }
        return null;
    }

    private Location airSpot(World world, int n, int n2, int n3) {
        int n4 = Math.min(world.getMaxHeight() - 4, n2 + ThreadLocalRandom.current().nextInt(6, 14));
        Location location = new Location(world, (double)n + 0.5, (double)n4, (double)n3 + 0.5);
        return location.getBlock().getType().isAir() ? location : null;
    }

    private boolean inView(Player player, Location location) {
        Location location2 = player.getEyeLocation();
        Vector vector = location.toVector().subtract(location2.toVector());
        if (vector.lengthSquared() < 1.0) {
            return true;
        }
        double d = vector.normalize().dot(location2.getDirection());
        return d > 0.35;
    }

    private static boolean overworld(World world) {
        return world.getEnvironment() == World.Environment.NORMAL;
    }

    private static boolean nether(World world) {
        return world.getEnvironment() == World.Environment.NETHER;
    }

    private static boolean night(World world) {
        long l = world.getTime();
        return l > 13000L && l < 23000L;
    }

    private static boolean seesSky(Player player) {
        Location location = player.getLocation();
        return location.getWorld().getHighestBlockYAt(location) <= location.getBlockY();
    }

    private static boolean dark(Player player) {
        return player.getLocation().getBlock().getLightLevel() < 8;
    }

    private static boolean water(Player player) {
        return player.isInWater() || player.getLocation().getBlock().getType() == Material.WATER;
    }

    private static boolean deepWater(Player player) {
        Location location = player.getLocation();
        for (int i = 1; i <= 4; ++i) {
            if (location.clone().subtract(0.0, (double)i, 0.0).getBlock().getType() == Material.WATER) continue;
            return false;
        }
        return true;
    }

    private static boolean ceilingAbove(Player player) {
        Location location = player.getEyeLocation();
        for (int i = 2; i <= 8; ++i) {
            if (!location.clone().add(0.0, (double)i, 0.0).getBlock().getType().isSolid()) continue;
            return true;
        }
        return false;
    }

    private static boolean biome(Player player, String ... stringArray) {
        String string = player.getLocation().getBlock().getBiome().getKey().getKey();
        for (String string2 : stringArray) {
            if (!string.contains(string2)) continue;
            return true;
        }
        return false;
    }

    public void onDeath(Player player, String string) {
        boolean bl;
        Entry entry = this.notes.get(player.getUniqueId());
        if (entry == null) {
            return;
        }
        long l = Math.max(30L, this.plugin.getConfig().getLong("deathnote.credit-seconds", 180L)) * 1000L;
        boolean bl2 = bl = entry.attempts > 0 && System.currentTimeMillis() - entry.lastAttempt <= l;
        if (!this.causedByFate(player, entry.fate, bl)) {
            if (bl) {
                this.report("<gray>Ziel <white>" + entry.name + "<gray> ist gestorben, aber nicht durch den Auftrag.\n<dark_gray>   Der Eintrag bleibt offen und wartet auf die nächste Gelegenheit.");
            }
            this.save();
            return;
        }
        this.notes.remove(player.getUniqueId());
        Object object = string == null || string.isBlank() ? player.getName() + " ist gestorben" : string;
        String string2 = "<gray>" + ActivityLog.clock(System.currentTimeMillis()) + " <white>" + entry.name + "<gray> · " + entry.fate.label() + " <dark_gray>· " + (String)object;
        this.history.add(0, string2);
        while (this.history.size() > 20) {
            this.history.remove(this.history.size() - 1);
        }
        this.save();
        this.report("<dark_red><bold>Auftrag ausgeführt</bold></dark_red>\n<gray>   Ziel: <white>" + entry.name + "\n<gray>   Schicksal: <white>" + entry.fate.label() + "\n<gray>   " + (String)object + "\n<dark_gray>   nach " + entry.attempts + " Gelegenheit" + (entry.attempts == 1 ? "" : "en"));
    }

    private boolean causedByFate(Player player, DeathFate deathFate, boolean bl) {
        EntityDamageEvent entityDamageEvent = player.getLastDamageCause();
        if (entityDamageEvent == null) {
            return false;
        }
        Entity entity = null;
        if (entityDamageEvent instanceof EntityDamageByEntityEvent) {
            Entity entity2;
            Projectile projectile;
            ProjectileSource projectileSource;
            EntityDamageByEntityEvent entityDamageByEntityEvent = (EntityDamageByEntityEvent)entityDamageEvent;
            Entity entity3 = entityDamageByEntityEvent.getDamager();
            entity = entity3 instanceof Projectile && (projectileSource = (projectile = (Projectile)entity3).getShooter()) instanceof Entity ? (entity2 = (Entity)projectileSource) : entity3;
        }
        if (entity != null && this.isMarkedFor(entity, player.getUniqueId())) {
            return true;
        }
        if (!bl) {
            return false;
        }
        if (entity != null && DeathNote.expectedTypes(deathFate).contains(entity.getType())) {
            return true;
        }
        return DeathNote.expectedCauses(deathFate).contains(entityDamageEvent.getCause());
    }

    private boolean isMarkedFor(Entity entity, UUID uUID) {
        String string = (String)entity.getPersistentDataContainer().get(this.markKey, PersistentDataType.STRING);
        return uUID.toString().equals(string);
    }

    private static Set<EntityType> expectedTypes(DeathFate deathFate) {
        return switch (deathFate) {
            case DeathFate.NACHTWACHE -> Set.of(EntityType.ZOMBIE);
            case DeathFate.SKELETT_PATROUILLE -> Set.of(EntityType.SKELETON);
            case DeathFate.LEISER_CREEPER -> Set.of(EntityType.CREEPER);
            case DeathFate.SPINNENNEST -> Set.of(EntityType.CAVE_SPIDER);
            case DeathFate.ENDERMANN -> Set.of(EntityType.ENDERMAN);
            case DeathFate.ERTRUNKENE -> Set.of(EntityType.DROWNED);
            case DeathFate.PHANTOME -> Set.of(EntityType.PHANTOM);
            case DeathFate.HEXENBESUCH -> Set.of(EntityType.WITCH);
            case DeathFate.WOLFSRUDEL -> Set.of(EntityType.WOLF);
            case DeathFate.BIENENSCHWARM -> Set.of(EntityType.BEE);
            case DeathFate.WUESTENWACHE -> Set.of(EntityType.HUSK);
            case DeathFate.NETHER_HINTERHALT -> Set.of(EntityType.HOGLIN, EntityType.PIGLIN_BRUTE);
            case DeathFate.FESTUNGSWACHE -> Set.of(EntityType.WITHER_SKELETON, EntityType.BLAZE);
            case DeathFate.ILLAGER_PATROUILLE -> Set.of(EntityType.PILLAGER, EntityType.VINDICATOR);
            case DeathFate.SILBERFISCHSCHWARM -> Set.of(EntityType.SILVERFISH);
            case DeathFate.WAECHTER_DER_TIEFE -> Set.of(EntityType.GUARDIAN);
            case DeathFate.MAGMAWUERFEL -> Set.of(EntityType.MAGMA_CUBE);
            default -> Set.of();
        };
    }

    private static Set<EntityDamageEvent.DamageCause> expectedCauses(DeathFate deathFate) {
        return switch (deathFate) {
            case DeathFate.ZEHRENDER_HUNGER -> Set.of(EntityDamageEvent.DamageCause.STARVATION);
            case DeathFate.LUFT_WIRD_KNAPP -> Set.of(EntityDamageEvent.DamageCause.DROWNING);
            case DeathFate.GEWITTERSCHLAG -> Set.of(EntityDamageEvent.DamageCause.LIGHTNING, EntityDamageEvent.DamageCause.FIRE, EntityDamageEvent.DamageCause.FIRE_TICK);
            case DeathFate.KIESRUTSCH -> Set.of(EntityDamageEvent.DamageCause.SUFFOCATION, EntityDamageEvent.DamageCause.FALLING_BLOCK);
            case DeathFate.FROSTNACHT -> Set.of(EntityDamageEvent.DamageCause.FREEZE);
            case DeathFate.LEISER_CREEPER -> Set.of(EntityDamageEvent.DamageCause.ENTITY_EXPLOSION);
            default -> Set.of();
        };
    }

    private void report(String string) {
        UUID uUID = this.plugin.access().ownerId();
        if (uUID == null) {
            return;
        }
        Player player = Bukkit.getPlayer((UUID)uUID);
        if (player != null) {
            player.sendMessage(this.plugin.prefix().append(Ui.mm(string)));
            return;
        }
        this.pendingReports.add(string);
        this.save();
    }

    public void deliverPending(Player player) {
        UUID uUID = this.plugin.access().ownerId();
        if (uUID == null || !uUID.equals(player.getUniqueId()) || this.pendingReports.isEmpty()) {
            return;
        }
        for (String string : new ArrayList<String>(this.pendingReports)) {
            player.sendMessage(this.plugin.prefix().append(Ui.mm(string)));
        }
        this.pendingReports.clear();
        this.save();
    }

    public void load() {
        this.file = new File(this.plugin.getDataFolder(), JOURNAL_FILE);
        File file = new File(this.plugin.getDataFolder(), LEGACY_FILE);
        FileConfiguration fileConfiguration = null;
        if (this.file.exists()) {
            fileConfiguration = this.readEncrypted(this.file);
        } else if (file.exists()) {
            fileConfiguration = YamlConfiguration.loadConfiguration((File)file);
        }
        if (fileConfiguration == null) {
            return;
        }
        ConfigurationSection configurationSection = fileConfiguration.getConfigurationSection("notes");
        if (configurationSection != null) {
            for (String string : configurationSection.getKeys(false)) {
                try {
                    UUID uUID = UUID.fromString(string);
                    DeathFate deathFate = DeathFate.byName(configurationSection.getString(string + ".fate"));
                    if (deathFate == null) continue;
                    Entry entry = new Entry(uUID, configurationSection.getString(string + ".name", "Unbekannt"), deathFate, configurationSection.getString(string + ".written-by", "Unbekannt"), configurationSection.getLong(string + ".written-at"));
                    entry.attempts = configurationSection.getInt(string + ".attempts");
                    entry.lastAttempt = configurationSection.getLong(string + ".last-attempt");
                    this.notes.put(uUID, entry);
                }
                catch (IllegalArgumentException illegalArgumentException) {
                    this.plugin.getLogger().warning("Ein gespeicherter Eintrag ist beschädigt und wurde übersprungen.");
                }
            }
        }
        this.history.addAll(fileConfiguration.getStringList("history"));
        this.pendingReports.addAll(fileConfiguration.getStringList("pending"));
        if (file.exists()) {
            this.save();
            if (!file.delete()) {
                file.deleteOnExit();
            }
        }
    }

    public void save() {
        if (this.file == null) {
            this.file = new File(this.plugin.getDataFolder(), JOURNAL_FILE);
        }
        YamlConfiguration yamlConfiguration = new YamlConfiguration();
        for (Entry entry : this.notes.values()) {
            String string = "notes." + String.valueOf(entry.target);
            yamlConfiguration.set(string + ".name", (Object)entry.name);
            yamlConfiguration.set(string + ".fate", (Object)entry.fate.name());
            yamlConfiguration.set(string + ".written-by", (Object)entry.writtenBy);
            yamlConfiguration.set(string + ".written-at", (Object)entry.writtenAt);
            yamlConfiguration.set(string + ".attempts", (Object)entry.attempts);
            yamlConfiguration.set(string + ".last-attempt", (Object)entry.lastAttempt);
        }
        yamlConfiguration.set("history", this.history);
        yamlConfiguration.set("pending", this.pendingReports);
        this.writeEncrypted(this.file, (FileConfiguration)yamlConfiguration);
    }

    private FileConfiguration readEncrypted(File file) {
        try {
            byte[] byArray = Files.readAllBytes(file.toPath());
            if (byArray.length < 13) {
                return null;
            }
            byte[] byArray2 = Arrays.copyOfRange(byArray, 0, 12);
            byte[] byArray3 = Arrays.copyOfRange(byArray, 12, byArray.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(2, (Key)this.key(), new GCMParameterSpec(128, byArray2));
            String string = new String(cipher.doFinal(byArray3), StandardCharsets.UTF_8);
            YamlConfiguration yamlConfiguration = new YamlConfiguration();
            yamlConfiguration.loadFromString(string);
            return yamlConfiguration;
        }
        catch (Exception exception) {
            this.plugin.getLogger().warning("Gespeicherte Daten konnten nicht gelesen werden: " + exception.getClass().getSimpleName());
            return null;
        }
    }

    private void writeEncrypted(File file, FileConfiguration fileConfiguration) {
        try {
            this.plugin.getDataFolder().mkdirs();
            byte[] byArray = new byte[12];
            new SecureRandom().nextBytes(byArray);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(1, (Key)this.key(), new GCMParameterSpec(128, byArray));
            byte[] byArray2 = cipher.doFinal(fileConfiguration.saveToString().getBytes(StandardCharsets.UTF_8));
            byte[] byArray3 = new byte[byArray.length + byArray2.length];
            System.arraycopy(byArray, 0, byArray3, 0, byArray.length);
            System.arraycopy(byArray2, 0, byArray3, byArray.length, byArray2.length);
            Files.write(file.toPath(), byArray3, new OpenOption[0]);
        }
        catch (Exception exception) {
            this.plugin.getLogger().warning("Daten konnten nicht gespeichert werden: " + exception.getClass().getSimpleName());
        }
    }

    private SecretKey key() throws IOException {
        byte[] byArray;
        File file = new File(this.plugin.getDataFolder(), KEY_FILE);
        if (file.exists()) {
            byArray = Files.readAllBytes(file.toPath());
        } else {
            byArray = new byte[32];
            new SecureRandom().nextBytes(byArray);
            this.plugin.getDataFolder().mkdirs();
            Files.write(file.toPath(), byArray, new OpenOption[0]);
            try {
                Files.setAttribute(file.toPath(), "dos:hidden", true, new LinkOption[0]);
            }
            catch (Exception exception) {
                // empty catch block
            }
        }
        if (byArray.length != 32) {
            throw new IOException("Schluesseldatei hat die falsche Laenge");
        }
        return new SecretKeySpec(byArray, "AES");
    }

    public static final class Entry {
        private final UUID target;
        private final String name;
        private final DeathFate fate;
        private final String writtenBy;
        private final long writtenAt;
        private int attempts;
        private long lastAttempt;

        Entry(UUID uUID, String string, DeathFate deathFate, String string2, long l) {
            this.target = uUID;
            this.name = string;
            this.fate = deathFate;
            this.writtenBy = string2;
            this.writtenAt = l;
        }

        public UUID target() {
            return this.target;
        }

        public String name() {
            return this.name;
        }

        public DeathFate fate() {
            return this.fate;
        }

        public String writtenBy() {
            return this.writtenBy;
        }

        public long writtenAt() {
            return this.writtenAt;
        }

        public int attempts() {
            return this.attempts;
        }

        public long lastAttempt() {
            return this.lastAttempt;
        }
    }
}

