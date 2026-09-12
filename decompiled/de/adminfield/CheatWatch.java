/*
 * Decompiled with CFR 0.152.
 */
package de.adminfield;

import de.adminfield.ActivityLog;
import de.adminfield.AdminFieldPlugin;
import de.adminfield.Ui;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

public final class CheatWatch {
    private static final Set<Material> HIGH_VALUE = Set.of(Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE, Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE, Material.ANCIENT_DEBRIS);
    private static final Set<Material> SLIPPERY = Set.of(Material.ICE, Material.PACKED_ICE, Material.BLUE_ICE, Material.FROSTED_ICE);
    private static final Set<Material> SOUL_GROUND = Set.of(Material.SOUL_SAND, Material.SOUL_SOIL);
    private static final Set<Material> SLOWS_FALLING = Set.of(Material.COBWEB, Material.POWDER_SNOW, Material.BUBBLE_COLUMN, Material.HONEY_BLOCK, Material.SCAFFOLDING, Material.WATER, Material.LAVA, Material.KELP, Material.KELP_PLANT, Material.SEAGRASS, Material.TALL_SEAGRASS);
    private static final Set<Material> SOFT_LANDING = Set.of(Material.WATER, Material.HAY_BLOCK, Material.SLIME_BLOCK, Material.COBWEB, Material.POWDER_SNOW, Material.SWEET_BERRY_BUSH, Material.HONEY_BLOCK, Material.BUBBLE_COLUMN, Material.SCAFFOLDING, Material.LADDER, Material.VINE);
    private final AdminFieldPlugin plugin;
    private final Map<UUID, Report> watched = new HashMap<UUID, Report>();

    public CheatWatch(AdminFieldPlugin adminFieldPlugin) {
        this.plugin = adminFieldPlugin;
    }

    public boolean isWatched(UUID uUID) {
        return this.watched.containsKey(uUID);
    }

    public Report report(UUID uUID) {
        return this.watched.get(uUID);
    }

    public Collection<Report> reports() {
        return new ArrayList<Report>(this.watched.values());
    }

    public int count() {
        return this.watched.size();
    }

    public void start(Player player, Player player2) {
        Report report = new Report(player2.getUniqueId(), player2.getName(), player.getName());
        report.resetMovement(player2.getLocation());
        this.watched.put(player2.getUniqueId(), report);
        this.plugin.log().add(ActivityLog.Level.INFO, player.getName() + " überwacht " + player2.getName() + " auf Cheats", player2.getLocation(), player2.getUniqueId());
    }

    public void stop(UUID uUID) {
        this.watched.remove(uUID);
    }

    public void forget(UUID uUID) {
        this.watched.remove(uUID);
    }

    public String confidence(Report report, Kind kind) {
        int n = report.hits(kind);
        int n2 = this.alertAfter();
        if (n == 0) {
            return "<dark_gray>nichts";
        }
        if (n < n2) {
            return "<gray>Hinweis <dark_gray>(" + n + "/" + n2 + ")";
        }
        if (n < n2 * 2) {
            return "<yellow>wahrscheinlich";
        }
        return "<red>sehr wahrscheinlich";
    }

    private int alertAfter() {
        return Math.max(1, this.plugin.getConfig().getInt("cheatwatch.alert-after-hits", 3));
    }

    public void tick(int n) {
        if (this.watched.isEmpty()) {
            return;
        }
        for (Report report : this.reports()) {
            Player player = this.plugin.getServer().getPlayer(report.uuid);
            if (player == null || player.isDead()) continue;
            Location location = player.getLocation();
            Location location2 = report.lastSample;
            long l = System.nanoTime();
            double d = (double)(l - report.lastSampleNanos) / 1.0E9;
            report.lastSample = location.clone();
            report.lastSampleNanos = l;
            if (location2 == null || location2.getWorld() == null || !location2.getWorld().equals((Object)location.getWorld())) {
                report.resetMovement(location);
                continue;
            }
            double d2 = (double)n / 20.0;
            if (d > d2 * 2.5 || d <= 0.0) {
                report.hoverTicks = 0;
                report.travelled = 0.0;
                report.travelSeconds = 0.0;
                continue;
            }
            this.checkFly(player, report, location2, location, n);
            this.checkSpeed(player, report, location2, location, d);
            this.trackFall(player, report);
        }
    }

    private void checkFly(Player player, Report report, Location location, Location location2, int n) {
        if (!this.plugin.getConfig().getBoolean("cheatwatch.fly.enabled", true)) {
            return;
        }
        if (this.flightExcused(player, report) || this.onGround(player)) {
            report.hoverTicks = 0;
            return;
        }
        double d = location2.getY() - location.getY();
        report.hoverTicks = d > -0.1 ? (report.hoverTicks += n) : 0;
        int n2 = Math.max(20, this.plugin.getConfig().getInt("cheatwatch.fly.hover-ticks", 30));
        if (report.hoverTicks >= n2) {
            report.hoverTicks = 0;
            this.flag(player, report, Kind.FLY, "hängt seit " + String.format("%.1f", (double)n2 / 20.0) + "s in der Luft, ohne zu fallen");
        }
    }

    private boolean flightExcused(Player player, Report report) {
        long l = System.currentTimeMillis();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return true;
        }
        if (player.getAllowFlight() || player.isFlying()) {
            return true;
        }
        if (player.isGliding() || player.isRiptiding() || player.isSwimming() || player.isClimbing()) {
            return true;
        }
        if (player.isInsideVehicle()) {
            return true;
        }
        if (player.isInWater() || player.isInLava()) {
            return true;
        }
        if (player.hasPotionEffect(PotionEffectType.LEVITATION) || player.hasPotionEffect(PotionEffectType.SLOW_FALLING)) {
            return true;
        }
        Location location = player.getLocation();
        if (SLOWS_FALLING.contains(location.getBlock().getType()) || SLOWS_FALLING.contains(location.clone().add(0.0, 1.0, 0.0).getBlock().getType())) {
            return true;
        }
        for (Entity entity : player.getNearbyEntities(0.8, 1.2, 0.8)) {
            if (entity instanceof Item || entity instanceof Player || !(entity.getLocation().getY() <= location.getY())) continue;
            return true;
        }
        return l - report.lastDamage < 2500L || l - report.lastTeleport < 3000L;
    }

    private boolean onGround(Player player) {
        Location location = player.getLocation();
        double[] dArray = new double[]{-0.1, -0.6};
        double[] dArray2 = new double[]{-0.35, 0.35};
        for (double d : dArray) {
            for (double d2 : dArray2) {
                for (double d3 : dArray2) {
                    if (location.clone().add(d2, d, d3).getBlock().getType().isAir()) continue;
                    return true;
                }
            }
            if (location.clone().add(0.0, d, 0.0).getBlock().getType().isAir()) continue;
            return true;
        }
        return false;
    }

    private void checkSpeed(Player player, Report report, Location location, Location location2, double d) {
        if (!this.plugin.getConfig().getBoolean("cheatwatch.speed.enabled", true)) {
            return;
        }
        if (this.speedExcused(player, report)) {
            report.travelled = 0.0;
            report.travelSeconds = 0.0;
            report.speedStrikes = 0;
            return;
        }
        double d2 = location2.getX() - location.getX();
        double d3 = location2.getZ() - location.getZ();
        report.travelled += Math.sqrt(d2 * d2 + d3 * d3);
        report.travelSeconds += d;
        if (report.travelSeconds < 1.0) {
            return;
        }
        double d4 = report.travelled / report.travelSeconds;
        report.travelled = 0.0;
        report.travelSeconds = 0.0;
        double d5 = this.allowedSpeed(player);
        if (d4 <= d5) {
            report.speedStrikes = 0;
            return;
        }
        ++report.speedStrikes;
        int n = Math.max(2, this.plugin.getConfig().getInt("cheatwatch.speed.strikes", 4));
        if (report.speedStrikes >= n) {
            report.speedStrikes = 0;
            this.flag(player, report, Kind.SPEED, String.format("%.1f Blöcke/s über %d Sekunden am Stück – erlaubt wären etwa %.1f", d4, n, d5));
        }
    }

    private double allowedSpeed(Player player) {
        ItemStack itemStack;
        Block block;
        double d = this.plugin.getConfig().getDouble("cheatwatch.speed.max-blocks-per-second", 10.5);
        d *= Math.max(1.0, (double)player.getWalkSpeed() / 0.2);
        PotionEffect potionEffect = player.getPotionEffect(PotionEffectType.SPEED);
        if (potionEffect != null) {
            d *= 1.0 + 0.2 * (double)(potionEffect.getAmplifier() + 1);
        }
        if (player.hasPotionEffect(PotionEffectType.DOLPHINS_GRACE)) {
            d *= 2.0;
        }
        if (SLIPPERY.contains((block = player.getLocation().clone().add(0.0, -0.2, 0.0).getBlock()).getType())) {
            d *= 3.0;
        }
        if (SOUL_GROUND.contains(block.getType()) && (itemStack = player.getInventory().getBoots()) != null && itemStack.containsEnchantment(Enchantment.SOUL_SPEED)) {
            d *= 1.7;
        }
        return d;
    }

    private boolean speedExcused(Player player, Report report) {
        long l = System.currentTimeMillis();
        if (player.getGameMode() == GameMode.SPECTATOR || player.getGameMode() == GameMode.CREATIVE) {
            return true;
        }
        if (player.isInsideVehicle() || player.isGliding() || player.isRiptiding()) {
            return true;
        }
        if (player.getAllowFlight() || player.isFlying()) {
            return true;
        }
        return l - report.lastTeleport < 3000L || l - report.lastDamage < 1500L;
    }

    private void trackFall(Player player, Report report) {
        boolean bl = !this.onGround(player);
        double d = player.getLocation().getY();
        if (bl) {
            report.highestY = report.wasAirborne ? Math.max(report.highestY, d) : d;
            report.wasAirborne = true;
            return;
        }
        if (!report.wasAirborne) {
            return;
        }
        report.wasAirborne = false;
        if (!this.plugin.getConfig().getBoolean("cheatwatch.nofall.enabled", true)) {
            return;
        }
        double d2 = report.highestY - d;
        double d3 = this.plugin.getConfig().getDouble("cheatwatch.nofall.min-fall", 10.0);
        ItemStack itemStack = player.getInventory().getBoots();
        if (itemStack != null && itemStack.containsEnchantment(Enchantment.FEATHER_FALLING)) {
            d3 += 3.0 * (double)itemStack.getEnchantmentLevel(Enchantment.FEATHER_FALLING);
        }
        if (d2 < d3 || this.fallExcused(player, report)) {
            return;
        }
        this.flag(player, report, Kind.NOFALL, String.format("%.0f Blöcke auf harten Boden gefallen, ohne Schaden zu nehmen", d2));
    }

    private boolean fallExcused(Player player, Report report) {
        long l = System.currentTimeMillis();
        if (l - report.lastFallDamage < 2000L || l - report.lastDamage < 2000L) {
            return true;
        }
        if (l - report.lastTeleport < 5000L) {
            return true;
        }
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return true;
        }
        if (this.plugin.state().isGod(player.getUniqueId())) {
            return true;
        }
        if (player.isGliding() || player.isInWater() || player.isInsideVehicle() || player.isClimbing() || player.hasPotionEffect(PotionEffectType.SLOW_FALLING) || player.hasPotionEffect(PotionEffectType.LEVITATION)) {
            return true;
        }
        Location location = player.getLocation();
        for (double d : new double[]{0.0, -0.2, -1.0}) {
            if (!SOFT_LANDING.contains(location.clone().add(0.0, d, 0.0).getBlock().getType())) continue;
            return true;
        }
        return false;
    }

    public void noteDamage(UUID uUID, boolean bl) {
        long l;
        Report report = this.watched.get(uUID);
        if (report == null) {
            return;
        }
        report.lastDamage = l = System.currentTimeMillis();
        if (bl) {
            report.lastFallDamage = l;
        }
    }

    public void noteTeleport(Player player) {
        Report report = this.watched.get(player.getUniqueId());
        if (report != null) {
            report.lastTeleport = System.currentTimeMillis();
            report.resetMovement(player.getLocation());
        }
    }

    public void noteRespawn(Player player) {
        Report report = this.watched.get(player.getUniqueId());
        if (report != null) {
            report.lastTeleport = System.currentTimeMillis();
            report.resetMovement(player.getLocation());
        }
    }

    public void onAttack(Player player, Entity entity) {
        Report report = this.watched.get(player.getUniqueId());
        if (report == null || !this.plugin.getConfig().getBoolean("cheatwatch.reach.enabled", true)) {
            return;
        }
        if (player.getGameMode() == GameMode.CREATIVE || !(entity instanceof LivingEntity)) {
            return;
        }
        Vector vector = player.getEyeLocation().toVector();
        BoundingBox boundingBox = entity.getBoundingBox();
        Vector vector2 = new Vector(CheatWatch.clamp(vector.getX(), boundingBox.getMinX(), boundingBox.getMaxX()), CheatWatch.clamp(vector.getY(), boundingBox.getMinY(), boundingBox.getMaxY()), CheatWatch.clamp(vector.getZ(), boundingBox.getMinZ(), boundingBox.getMaxZ()));
        double d = vector.distance(vector2);
        double d2 = Math.min(0.8, (double)player.getPing() / 100.0 * 0.15);
        double d3 = this.plugin.getConfig().getDouble("cheatwatch.reach.max-distance", 3.6) + d2;
        if (d <= d3) {
            return;
        }
        long l = System.currentTimeMillis();
        report.reachStrikes.addLast(l);
        while (!report.reachStrikes.isEmpty() && l - report.reachStrikes.peekFirst() > 30000L) {
            report.reachStrikes.pollFirst();
        }
        int n = Math.max(2, this.plugin.getConfig().getInt("cheatwatch.reach.strikes", 3));
        if (report.reachStrikes.size() >= n) {
            report.reachStrikes.clear();
            this.flag(player, report, Kind.REACH, String.format("%d Treffer aus über %.1f Blöcken innerhalb von 30s (zuletzt %.2f)", n, d3, d));
        }
    }

    private static double clamp(double d, double d2, double d3) {
        return Math.max(d2, Math.min(d3, d));
    }

    public void onBreak(Player player, Block block) {
        Report report = this.watched.get(player.getUniqueId());
        if (report == null || !this.plugin.getConfig().getBoolean("cheatwatch.xray.enabled", true)) {
            return;
        }
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        Location location = block.getLocation();
        ++report.minedTotal;
        ++report.blocksSinceVein;
        boolean bl = HIGH_VALUE.contains(block.getType());
        boolean bl2 = bl && this.naturallyExposed(block, report.recentBreaks);
        report.recentBreaks.addLast(location.clone());
        while (report.recentBreaks.size() > 40) {
            report.recentBreaks.pollFirst();
        }
        if (!bl) {
            return;
        }
        if (bl2) {
            ++report.exposedFinds;
            report.lastOre = location.clone();
            return;
        }
        boolean bl3 = report.lastOre != null && report.lastOre.getWorld().equals((Object)location.getWorld()) && report.lastOre.distanceSquared(location) <= 9.0;
        report.lastOre = location.clone();
        if (bl3) {
            return;
        }
        ++report.hiddenVeins;
        int n = report.blocksSinceVein;
        report.blocksSinceVein = 0;
        report.veinGaps.addLast(n);
        while (report.veinGaps.size() > 8) {
            report.veinGaps.pollFirst();
        }
        if (this.beelinedTo(report, location)) {
            ++report.beelines;
        }
        this.checkXray(player, report);
    }

    private boolean naturallyExposed(Block block, Deque<Location> deque) {
        for (BlockFace blockFace : new BlockFace[]{BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            Block block2 = block.getRelative(blockFace);
            if (block2.getType().isOccluding()) continue;
            Location location = block2.getLocation();
            boolean bl = false;
            for (Location location2 : deque) {
                if (!location2.getWorld().equals((Object)location.getWorld()) || location2.getBlockX() != location.getBlockX() || location2.getBlockY() != location.getBlockY() || location2.getBlockZ() != location.getBlockZ()) continue;
                bl = true;
                break;
            }
            if (bl) continue;
            return true;
        }
        return false;
    }

    private void checkXray(Player player, Report report) {
        int n = Math.max(2, this.plugin.getConfig().getInt("cheatwatch.xray.beelines", 4));
        if (report.beelines >= n) {
            report.beelines = 0;
            this.flag(player, report, Kind.XRAY, n + "-mal abgebogen und schnurgerade auf ein verstecktes Erz zugegraben");
            return;
        }
        int n2 = Math.max(3, this.plugin.getConfig().getInt("cheatwatch.xray.min-hidden-veins", 6));
        int n3 = this.plugin.getConfig().getInt("cheatwatch.xray.max-average-gap", 25);
        if (report.veinGaps.size() >= n2) {
            int n4 = 0;
            for (int n5 : report.veinGaps) {
                n4 += n5;
            }
            int n6 = n4 / report.veinGaps.size();
            if (n6 < n3) {
                this.flag(player, report, Kind.XRAY, report.veinGaps.size() + " versteckte Adern mit nur " + n6 + " Blöcken Umweg dazwischen");
            }
        }
    }

    private boolean beelinedTo(Report report, Location location) {
        Location location22;
        ArrayList<Location> arrayList = new ArrayList<Location>(report.recentBreaks);
        if (arrayList.size() < 12) {
            return false;
        }
        int n = arrayList.size() - 1;
        List list = arrayList.subList(n - 5, n);
        Vector vector = location.toVector().subtract(((Location)list.get(0)).toVector());
        if (vector.lengthSquared() < 9.0) {
            return false;
        }
        vector.normalize();
        for (Location location22 : list) {
            Vector vector2 = location22.toVector().subtract(((Location)list.get(0)).toVector());
            double d = vector2.dot(vector);
            double d2 = vector2.clone().subtract(vector.clone().multiply(d)).length();
            if (!(d2 > 1.2)) continue;
            return false;
        }
        List list2 = arrayList.subList(n - 11, n - 5);
        location22 = ((Location)list2.get(list2.size() - 1)).toVector().subtract(((Location)list2.get(0)).toVector());
        if (location22.lengthSquared() < 4.0) {
            return false;
        }
        location22.normalize();
        return location22.dot(vector) < 0.6;
    }

    private void flag(Player player, Report report, Kind kind, String string) {
        int n = report.hits.merge(kind, 1, Integer::sum);
        report.lastDetail.put(kind, string);
        int n2 = this.alertAfter();
        if (n < n2) {
            return;
        }
        long l = System.currentTimeMillis();
        long l2 = Math.max(3L, this.plugin.getConfig().getLong("cheatwatch.alert-cooldown-seconds", 30L)) * 1000L;
        Long l3 = report.lastAlert.get((Object)kind);
        if (l3 != null && l - l3 < l2) {
            return;
        }
        report.lastAlert.put(kind, l);
        String string2 = n >= n2 * 2 ? "sehr wahrscheinlich" : "wahrscheinlich";
        Location location = player.getLocation();
        this.plugin.log().add(ActivityLog.Level.ALERT, player.getName() + " – " + kind.label() + " " + string2 + " (" + n + "x): " + string, location, player.getUniqueId());
        this.plugin.notifyAdmins("<red><bold>CHEAT</bold></red> <dark_gray>·</dark_gray> <white>" + player.getName() + "</white> <dark_gray>·</dark_gray> " + kind.color() + "<bold>" + kind.label() + "</bold> <dark_gray>·</dark_gray> " + (n >= n2 * 2 ? "<red>" : "<yellow>") + string2 + " <dark_gray>(" + n + "x)</dark_gray>\n<gray>   " + string + "\n<gray>   bei <white>" + Ui.pos(location) + "</white> <click:run_command:'/admin tp " + location.getWorld().getName() + " " + location.getBlockX() + " " + location.getBlockY() + " " + location.getBlockZ() + "'><hover:show_text:'<gray>Hinteleportieren'><aqua>[TP]</aqua></hover></click>");
    }

    public static final class Report {
        private final UUID uuid;
        private final String name;
        private final String watchedBy;
        private final long since = System.currentTimeMillis();
        private final Map<Kind, Integer> hits = new EnumMap<Kind, Integer>(Kind.class);
        private final Map<Kind, Long> lastAlert = new EnumMap<Kind, Long>(Kind.class);
        private final Map<Kind, String> lastDetail = new EnumMap<Kind, String>(Kind.class);
        private Location lastSample;
        private long lastSampleNanos;
        private int hoverTicks;
        private double travelled;
        private double travelSeconds;
        private int speedStrikes;
        private long lastDamage;
        private long lastTeleport;
        private double highestY;
        private boolean wasAirborne;
        private long lastFallDamage;
        private final Deque<Long> reachStrikes = new ArrayDeque<Long>();
        private int minedTotal;
        private int hiddenVeins;
        private int exposedFinds;
        private int blocksSinceVein;
        private Location lastOre;
        private final Deque<Integer> veinGaps = new ArrayDeque<Integer>();
        private final Deque<Location> recentBreaks = new ArrayDeque<Location>();
        private int beelines;

        Report(UUID uUID, String string, String string2) {
            this.uuid = uUID;
            this.name = string;
            this.watchedBy = string2;
        }

        public UUID uuid() {
            return this.uuid;
        }

        public String name() {
            return this.name;
        }

        public String watchedBy() {
            return this.watchedBy;
        }

        public long since() {
            return this.since;
        }

        public int hits(Kind kind) {
            return this.hits.getOrDefault((Object)kind, 0);
        }

        public int totalHits() {
            int n = 0;
            for (int n2 : this.hits.values()) {
                n += n2;
            }
            return n;
        }

        public String detail(Kind kind) {
            return this.lastDetail.get((Object)kind);
        }

        public int minedTotal() {
            return this.minedTotal;
        }

        public int minedValuable() {
            return this.hiddenVeins;
        }

        public int exposedFinds() {
            return this.exposedFinds;
        }

        public double orePercent() {
            if (this.minedTotal == 0) {
                return 0.0;
            }
            return 100.0 * (double)this.hiddenVeins / (double)this.minedTotal;
        }

        void resetMovement(Location location) {
            this.lastSample = location == null ? null : location.clone();
            this.lastSampleNanos = System.nanoTime();
            this.hoverTicks = 0;
            this.travelled = 0.0;
            this.travelSeconds = 0.0;
            this.speedStrikes = 0;
            this.wasAirborne = false;
            this.highestY = location == null ? 0.0 : location.getY();
        }
    }

    public static enum Kind {
        FLY("Fliegen", Material.FEATHER, "<aqua>"),
        SPEED("Speed", Material.SUGAR, "<yellow>"),
        XRAY("X-Ray", Material.DIAMOND_ORE, "<light_purple>"),
        REACH("Reach", Material.NETHERITE_SWORD, "<red>"),
        NOFALL("NoFall", Material.SLIME_BLOCK, "<green>");

        private final String label;
        private final Material icon;
        private final String color;

        private Kind(String string2, Material material, String string3) {
            this.label = string2;
            this.icon = material;
            this.color = string3;
        }

        public String label() {
            return this.label;
        }

        public Material icon() {
            return this.icon;
        }

        public String color() {
            return this.color;
        }
    }
}

