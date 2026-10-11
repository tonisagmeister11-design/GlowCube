package de.glowcube.bedwars;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Eine komplette Bedwars-Runde: 5 Teams a 4 Spieler, du plus 19 Bots. */
public final class Game {
    private static final Random RND = new Random();
    private static final String[] NAMES = {
            "Finn", "Luca", "Mila", "Nico", "Emma", "Jonas", "Lena", "Ben", "Noah", "Anna", "Leon", "Mia",
            "Paul", "Hanna", "Felix", "Lara", "Tim", "Sophie", "Max", "Ida", "Elias", "Clara", "Moritz", "Nele"};

    public static final class TeamState {
        public final BwTeam team;
        public final BlockPos island;
        public final BlockPos generator;
        public final Vec3 spawn;
        public boolean bedAlive;
        public boolean bedPending;
        public boolean hasPlayer;
        public boolean playerOut;
        public int playerArmorTier;
        /** Gemeinsame Kasse aller Bots des Teams. */
        public int iron, gold, diamond, emerald;
        public final List<BotProfile> bots = new ArrayList<>();
        public BwTeam targetTeam;
        public int pendingSince;
        public Map<BlockPos, BlockState> bedSnapshot = new HashMap<>();

        TeamState(BwTeam team, Arena arena) {
            this.team = team;
            this.island = arena.islandCenter(team.ordinal());
            this.generator = Arena.generator(island);
            this.spawn = Arena.spawn(island);
        }

        boolean alive() {
            if (hasPlayer && !playerOut) return true;
            for (BotProfile b : bots) {
                if (!b.eliminated) return true;
            }
            return false;
        }
    }

    private static final class Backup {
        final List<ItemStack> items = new ArrayList<>();
        final Vec3 pos;
        final float yaw, pitch;
        final GameType mode;
        final float health;
        final int food;

        Backup(ServerPlayer p) {
            for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
                items.add(p.getInventory().getItem(i).copy());
            }
            pos = p.position();
            yaw = p.getYRot();
            pitch = p.getXRot();
            mode = p.gameMode();
            health = p.getHealth();
            food = p.getFoodData().getFoodLevel();
        }

        private Backup(List<ItemStack> items, Vec3 pos, float yaw, float pitch, GameType mode, float health, int food) {
            this.items.addAll(items);
            this.pos = pos;
            this.yaw = yaw;
            this.pitch = pitch;
            this.mode = mode;
            this.health = health;
            this.food = food;
        }

        void restore(ServerPlayer p, ServerLevel level) {
            p.getInventory().clearContent();
            for (int i = 0; i < items.size(); i++) {
                p.getInventory().setItem(i, items.get(i));
            }
            p.removeAllEffects();
            p.teleportTo(level, pos.x, pos.y, pos.z, Set.<Relative>of(), yaw, pitch, true);
            p.setGameMode(mode);
            p.setHealth(Math.max(1.0f, health));
            p.getFoodData().setFoodLevel(food);
        }

        // --- Sicherung auf der Platte, damit ein Absturz mitten in der Runde nichts kostet ---

        static java.nio.file.Path file(MinecraftServer srv) {
            return srv.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("glowcube_bedwars_backup.json");
        }

        void save(MinecraftServer srv) {
            try {
                var ops = net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, srv.registryAccess());
                com.google.gson.JsonObject o = new com.google.gson.JsonObject();
                o.addProperty("x", pos.x);
                o.addProperty("y", pos.y);
                o.addProperty("z", pos.z);
                o.addProperty("yaw", yaw);
                o.addProperty("pitch", pitch);
                o.addProperty("mode", mode.getId());
                o.addProperty("health", health);
                o.addProperty("food", food);
                com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
                for (ItemStack s : items) {
                    arr.add(ItemStack.OPTIONAL_CODEC.encodeStart(ops, s).getOrThrow());
                }
                o.add("items", arr);
                java.nio.file.Files.writeString(file(srv), o.toString());
            } catch (Exception e) {
                System.err.println("[Bedwars] Inventar-Sicherung fehlgeschlagen: " + e);
            }
        }

        static Backup load(MinecraftServer srv) {
            try {
                java.nio.file.Path f = file(srv);
                if (!java.nio.file.Files.exists(f)) return null;
                var ops = net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, srv.registryAccess());
                com.google.gson.JsonObject o = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(f)).getAsJsonObject();
                List<ItemStack> items = new ArrayList<>();
                for (com.google.gson.JsonElement e : o.getAsJsonArray("items")) {
                    items.add(ItemStack.OPTIONAL_CODEC.parse(ops, e).getOrThrow());
                }
                return new Backup(items, new Vec3(o.get("x").getAsDouble(), o.get("y").getAsDouble(), o.get("z").getAsDouble()),
                        o.get("yaw").getAsFloat(), o.get("pitch").getAsFloat(), GameType.byId(o.get("mode").getAsInt()),
                        o.get("health").getAsFloat(), o.get("food").getAsInt());
            } catch (Exception e) {
                System.err.println("[Bedwars] Inventar-Sicherung unlesbar: " + e);
                return null;
            }
        }

        static void delete(MinecraftServer srv) {
            try {
                java.nio.file.Files.deleteIfExists(file(srv));
            } catch (Exception ignored) {
            }
        }
    }

    private static MinecraftServer server;
    private static ServerLevel level;
    private static Arena arena;
    private static TeamState[] teams;
    private static ServerPlayer player;
    private static Backup backup;
    private static boolean running;
    private static int tick;
    private static int playerRespawnAt = -1;
    private static int endAt = -1;
    private static int pendingStartAt = -1;
    private static ServerPlayer pendingStartPlayer;
    private static int playerSwordTier;
    private static final Map<java.util.UUID, Integer> protectedUntil = new HashMap<>();
    private static final List<Villager> shops = new ArrayList<>();
    public static boolean autoStart = true;
    public static boolean debug;

    private Game() {}

    // --- Zugriff fuer Bots und Shop ---------------------------------------------------------

    public static boolean running() { return running; }
    public static Arena arena() { return arena; }
    public static ServerPlayer player() { return player; }

    public static TeamState playerTeam() {
        if (teams == null) return null;
        for (TeamState t : teams) {
            if (t.hasPlayer) return t;
        }
        return null;
    }

    public static BwTeam teamOf(Entity e) {
        if (!running) return null;
        if (e instanceof BedwarsBot b && b.profile != null) return b.profile.team.team;
        if (e == player) {
            TeamState t = playerTeam();
            return t == null ? null : t.team;
        }
        return null;
    }

    public static int playerSwordTier(ServerPlayer p) {
        int best = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.is(Items.STONE_SWORD)) best = Math.max(best, 1);
            else if (s.is(Items.IRON_SWORD)) best = Math.max(best, 2);
            else if (s.is(Items.DIAMOND_SWORD)) best = Math.max(best, 3);
        }
        return best;
    }

    public static boolean isProtected(Entity e) {
        Integer until = protectedUntil.get(e.getUUID());
        return until != null && until > tick;
    }

    // --- Start und Ende ---------------------------------------------------------------------

    public static void onJoin(ServerPlayer p, MinecraftServer srv) {
        if (!running) {
            // Rest einer abgestuerzten Runde: altes Inventar und alte Position zurueckgeben
            Backup left = Backup.load(srv);
            if (left != null) {
                left.restore(p, (ServerLevel) p.level());
                Backup.delete(srv);
                p.sendSystemMessage(Component.literal("Deine Sachen aus der letzten Bedwars-Runde wurden zurueckgegeben.").withStyle(ChatFormatting.GREEN));
            }
        }
        if (!autoStart || !srv.isSingleplayer()) return;
        pendingStartPlayer = p;
        pendingStartAt = srv.getTickCount() + 40;
    }

    public static void start(ServerPlayer p) {
        if (running) {
            stop(true);
        }
        server = p.level().getServer();
        level = (ServerLevel) p.level();
        player = p;
        backup = new Backup(p);
        backup.save(server);
        tick = 0;
        endAt = -1;
        playerRespawnAt = -1;
        playerSwordTier = 0;
        protectedUntil.clear();

        arena = new Arena(level, p.blockPosition());
        if (debug) arena.forceLoad(true);
        arena.build();
        running = true;

        teams = new TeamState[BwTeam.values().length];
        for (BwTeam t : BwTeam.values()) {
            teams[t.ordinal()] = new TeamState(t, arena);
        }
        TeamState mine = teams[RND.nextInt(teams.length)];
        mine.hasPlayer = true;

        List<String> names = new ArrayList<>(List.of(NAMES));
        java.util.Collections.shuffle(names, RND);
        int nameIdx = 0;
        for (TeamState t : teams) {
            int bots = t.hasPlayer ? 3 : 4;
            for (int i = 0; i < bots; i++) {
                BotProfile prof = new BotProfile(names.get(nameIdx++ % names.size()), t, RND);
                t.bots.add(prof);
            }
        }

        // Beim Spielerteam ist es Zufall, ob ein Bot das Bett setzt - sonst musst du es selbst tun.
        for (TeamState t : teams) {
            if (t == mine) continue;
            arena.placeBed(t.team);
            t.bedAlive = true;
            snapshot(t);
        }
        boolean botPlacesBed = RND.nextInt(100) < 60;
        if (botPlacesBed) {
            arena.placeBed(mine.team);
            mine.bedAlive = true;
            snapshot(mine);
        } else {
            mine.bedPending = true;
            mine.pendingSince = 0;
        }

        for (TeamState t : teams) {
            spawnShop(t);
            for (BotProfile b : t.bots) {
                spawnBot(b);
            }
        }

        preparePlayer(mine);
        tell(Component.literal("=== BEDWARS === 5 Teams, du bist im Team ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(mine.team.label).withStyle(mine.team.format, ChatFormatting.BOLD)));
        tell(Component.literal("Zerstoere die Betten der anderen Teams und ueberlebe. Shop: Dorfbewohner auf deiner Insel oder /bedwars shop.").withStyle(ChatFormatting.YELLOW));
        if (mine.bedPending) {
            tell(Component.literal("Niemand in deinem Team hat das Bett gesetzt! Setze es selbst auf deine Insel (du hast es im Inventar).").withStyle(ChatFormatting.RED));
        } else {
            BotProfile who = mine.bots.get(RND.nextInt(mine.bots.size()));
            tell(Component.literal(who.name + " hat das Bett deines Teams platziert.").withStyle(ChatFormatting.GREEN));
        }
    }

    public static void stop(boolean restorePlayer) {
        if (!running) return;
        running = false;
        for (TeamState t : teams) {
            for (BotProfile b : t.bots) {
                if (b.body != null) b.body.discard();
            }
        }
        for (Villager v : shops) v.discard();
        shops.clear();
        AABB box = new AABB(arena.center.getX() - 60, Arena.Y - 70, arena.center.getZ() - 60, arena.center.getX() + 60, Arena.Y + 50, arena.center.getZ() + 60);
        for (Entity e : level.getEntities((Entity) null, box, x -> x instanceof ItemEntity || x instanceof BedwarsBot)) {
            e.discard();
        }
        if (restorePlayer && player != null && backup != null) {
            backup.restore(player, level);
            Backup.delete(server);
        }
        arena.clear();
        if (debug) arena.forceLoad(false);
        player = null;
        backup = null;
    }

    private static void preparePlayer(TeamState mine) {
        player.getInventory().clearContent();
        player.removeAllEffects();
        player.setGameMode(GameType.SURVIVAL);
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        mine.playerArmorTier = 0;
        equipPlayerArmor(player, mine);
        player.getInventory().add(new ItemStack(Items.WOODEN_SWORD));
        if (mine.bedPending) {
            player.getInventory().add(new ItemStack(mine.team.bed.asItem()));
        }
        teleportTo(player, mine.spawn);
        protectedUntil.put(player.getUUID(), tick + 60);
    }

    private static void teleportTo(ServerPlayer p, Vec3 pos) {
        p.teleportTo(level, pos.x, pos.y, pos.z, Set.<Relative>of(), 0.0f, 0.0f, true);
    }

    // --- Ausruestung des Spielers -----------------------------------------------------------

    public static void equipPlayerArmor(ServerPlayer p, TeamState t) {
        p.setItemSlot(EquipmentSlot.HEAD, dyed(Items.LEATHER_HELMET, t.team));
        p.setItemSlot(EquipmentSlot.CHEST, dyed(Items.LEATHER_CHESTPLATE, t.team));
        Item legs = switch (t.playerArmorTier) {
            case 0 -> Items.LEATHER_LEGGINGS;
            case 1 -> Items.CHAINMAIL_LEGGINGS;
            case 2 -> Items.IRON_LEGGINGS;
            default -> Items.DIAMOND_LEGGINGS;
        };
        Item boots = switch (t.playerArmorTier) {
            case 0 -> Items.LEATHER_BOOTS;
            case 1 -> Items.CHAINMAIL_BOOTS;
            case 2 -> Items.IRON_BOOTS;
            default -> Items.DIAMOND_BOOTS;
        };
        p.setItemSlot(EquipmentSlot.LEGS, t.playerArmorTier == 0 ? dyed(legs, t.team) : new ItemStack(legs));
        p.setItemSlot(EquipmentSlot.FEET, t.playerArmorTier == 0 ? dyed(boots, t.team) : new ItemStack(boots));
    }

    private static ItemStack dyed(Item item, BwTeam team) {
        ItemStack s = new ItemStack(item);
        s.set(DataComponents.DYED_COLOR, new DyedItemColor(team.rgb));
        return s;
    }

    // --- Bots -------------------------------------------------------------------------------

    private static void spawnBot(BotProfile prof) {
        BedwarsBot bot = new BedwarsBot(EntityTypes.HUSK, level);
        bot.profile = prof;
        prof.body = bot;
        TeamState t = prof.team;
        double ox = (RND.nextDouble() - 0.5) * 3.0;
        double oz = (RND.nextDouble() - 0.5) * 3.0;
        bot.setPos(t.spawn.x + ox, t.spawn.y, t.spawn.z + oz);
        bot.setCustomName(bot.displayName());
        bot.setCustomNameVisible(true);
        bot.setPersistenceRequired();
        bot.setCanPickUpLoot(false);
        prof.equip(bot);
        level.addFreshEntity(bot);
        protectedUntil.put(bot.getUUID(), tick + 60);
    }

    private static void spawnShop(TeamState t) {
        Villager v = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
        if (v == null) return;
        Vec3 pos = Arena.shop(t.island);
        v.setPos(pos.x, pos.y, pos.z);
        v.setNoAi(true);
        v.setPermanentlyInvulnerable(true);
        v.setSilent(true);
        v.setPersistenceRequired();
        v.setCustomName(Component.literal("SHOP").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        v.setCustomNameVisible(true);
        v.addTag("bw_shop");
        level.addFreshEntity(v);
        shops.add(v);
    }

    public static LivingEntity nearestEnemy(BedwarsBot self, double range) {
        BwTeam mine = self.profile.team.team;
        LivingEntity best = null;
        double bestD = range * range;
        if (player != null && player.isAlive() && player.gameMode() == GameType.SURVIVAL && teamOf(player) != mine && !isProtected(player)) {
            double d = self.distanceToSqr(player);
            if (d < bestD) {
                bestD = d;
                best = player;
            }
        }
        for (TeamState t : teams) {
            if (t.team == mine) continue;
            for (BotProfile b : t.bots) {
                BedwarsBot body = b.body;
                if (body == null || !body.isAlive() || isProtected(body)) continue;
                double d = self.distanceToSqr(body);
                if (d < bestD) {
                    bestD = d;
                    best = body;
                }
            }
        }
        return best;
    }

    public static TeamState raidTarget(TeamState from) {
        TeamState cur = from.targetTeam == null ? null : teams[from.targetTeam.ordinal()];
        if (cur != null && cur != from && cur.alive()) {
            return cur;
        }
        List<TeamState> options = new ArrayList<>();
        for (TeamState t : teams) {
            if (t != from && t.alive()) options.add(t);
        }
        if (options.isEmpty()) return null;
        // Benachbarte Inseln sind etwas wahrscheinlicher, aber jedes Team kann Ziel sein
        List<TeamState> weighted = new ArrayList<>();
        for (TeamState t : options) {
            int dist = Math.min(Math.abs(t.team.ordinal() - from.team.ordinal()), teams.length - Math.abs(t.team.ordinal() - from.team.ordinal()));
            for (int i = 0; i < (dist == 1 ? 3 : 2); i++) weighted.add(t);
        }
        TeamState pick = weighted.get(RND.nextInt(weighted.size()));
        from.targetTeam = pick.team;
        return pick;
    }

    public static Vec3 anyEnemyPosition(TeamState from) {
        Vec3 best = null;
        double bestD = Double.MAX_VALUE;
        Vec3 origin = Vec3.atCenterOf(from.island);
        if (player != null && player.isAlive() && teamOf(player) != from.team) {
            best = player.position();
            bestD = origin.distanceToSqr(best);
        }
        for (TeamState t : teams) {
            if (t == from) continue;
            for (BotProfile b : t.bots) {
                if (b.body != null && b.body.isAlive()) {
                    double d = origin.distanceToSqr(b.body.position());
                    if (d < bestD) {
                        bestD = d;
                        best = b.body.position();
                    }
                }
            }
        }
        return best;
    }

    // --- Betten -----------------------------------------------------------------------------

    private static void snapshot(TeamState t) {
        t.bedSnapshot.clear();
        for (BlockPos p : arena.bedBlocks(t.team)) {
            t.bedSnapshot.put(p, level.getBlockState(p));
        }
    }

    public static void destroyBed(TeamState target, String by, TeamState byTeam) {
        if (!target.bedAlive) return;
        target.bedAlive = false;
        target.bedPending = false;
        target.bedSnapshot.clear();
        arena.removeBed(target.team);
        level.playSound(null, BlockPos.containing(target.spawn), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.MASTER, 2.0f, 1.0f);
        tell(Component.literal("BETT ZERSTOERT > ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal("Das Bett von Team ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(target.team.label).withStyle(target.team.format))
                .append(Component.literal(" wurde von " + by + " zerstoert!").withStyle(ChatFormatting.GRAY)));
        if (target.hasPlayer) {
            player.sendSystemMessage(Component.literal("Dein Bett ist weg - du hast nur noch ein Leben!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        }
    }

    /** Spieler schlaegt auf einen Block. true = erlaubt, false = abbrechen. */
    public static boolean onPlayerBreak(ServerPlayer p, BlockPos pos, BlockState state) {
        if (!running || p != player) return true;
        if (state.getBlock() instanceof net.minecraft.world.level.block.BedBlock) {
            BwTeam owner = arena.bedOwner(pos);
            if (owner == null) return false;
            TeamState t = teams[owner.ordinal()];
            if (t.hasPlayer) {
                p.sendSystemMessage(Component.literal("Du kannst dein eigenes Bett nicht abbauen.").withStyle(ChatFormatting.RED));
                return false;
            }
            destroyBed(t, p.getName().getString() + " (" + playerTeam().team.label + ")", playerTeam());
            return false;
        }
        return !arena.map.contains(pos.asLong());
    }

    // --- Tod und Respawn --------------------------------------------------------------------

    /** Vor dem Tod. Gibt false zurueck, wenn der Tod selbst abgefangen wurde. */
    public static boolean onDeath(LivingEntity victim, DamageSource source) {
        if (!running) return true;
        if (victim == player) {
            handlePlayerDeath(source);
            return false;
        }
        if (victim instanceof BedwarsBot bot && bot.profile != null) {
            handleBotDeath(bot, source);
            return false;
        }
        return true;
    }

    private static Component nameOf(Entity e) {
        if (e instanceof BedwarsBot b && b.profile != null) return b.displayName();
        if (e == player) {
            TeamState t = playerTeam();
            return Component.literal(player.getName().getString()).withStyle(t == null ? ChatFormatting.WHITE : t.team.format);
        }
        return e.getName();
    }

    private static Entity killerOf(LivingEntity victim, DamageSource source) {
        Entity k = source.getEntity();
        if (k == null && victim.getLastHurtByMob() != null && victim.tickCount - victim.getLastHurtByMobTimestamp() < 200) {
            k = victim.getLastHurtByMob();
        }
        return k == null || k == victim ? null : k;
    }

    private static void announceKill(LivingEntity victim, Entity killer, boolean finalKill) {
        Component msg = nameOf(victim).copy().append(Component.literal(killer == null ? " ist in die Leere gefallen." : " wurde von ").withStyle(ChatFormatting.GRAY));
        if (killer != null) {
            msg = msg.copy().append(nameOf(killer)).append(Component.literal(" getoetet.").withStyle(ChatFormatting.GRAY));
        }
        if (finalKill) {
            msg = msg.copy().append(Component.literal(" ENDGUELTIG!").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        }
        tell(msg);
    }

    private static void handlePlayerDeath(DamageSource source) {
        TeamState t = playerTeam();
        Entity killer = killerOf(player, source);
        boolean canRespawn = t.bedAlive || t.bedPending;
        announceKill(player, killer, !canRespawn);

        // Beute an den Moerder
        int[] loot = takeLoot(player);
        if (killer instanceof BedwarsBot b && b.profile != null) {
            b.profile.team.iron += loot[0];
            b.profile.team.gold += loot[1];
            b.profile.team.diamond += loot[2];
            b.profile.team.emerald += loot[3];
        }
        player.getInventory().clearContent();
        player.setHealth(player.getMaxHealth());
        player.clearFire();
        player.removeAllEffects();
        player.fallDistance = 0;
        player.setDeltaMovement(Vec3.ZERO);
        player.setGameMode(GameType.SPECTATOR);
        teleportTo(player, t.spawn.add(0, 8, 0));
        if (canRespawn) {
            playerRespawnAt = tick + 100;
            player.sendSystemMessage(Component.literal("Du erscheinst in 5 Sekunden wieder.").withStyle(ChatFormatting.YELLOW));
        } else {
            t.playerOut = true;
            player.sendSystemMessage(Component.literal("Du bist ausgeschieden! Du schaust nur noch zu.").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            checkWinner();
        }
    }

    private static int[] takeLoot(ServerPlayer p) {
        int[] r = new int[4];
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.is(Items.IRON_INGOT)) r[0] += s.getCount();
            else if (s.is(Items.GOLD_INGOT)) r[1] += s.getCount();
            else if (s.is(Items.DIAMOND)) r[2] += s.getCount();
            else if (s.is(Items.EMERALD)) r[3] += s.getCount();
        }
        return r;
    }

    private static void handleBotDeath(BedwarsBot bot, DamageSource source) {
        BotProfile prof = bot.profile;
        TeamState t = prof.team;
        Entity killer = killerOf(bot, source);
        boolean canRespawn = t.bedAlive || t.bedPending;
        announceKill(bot, killer, !canRespawn);

        if (killer == player) {
            // Beute fuer den Spieler: ein kleiner Anteil der Team-Kasse
            int fe = Math.min(prof.team.iron, 8);
            int au = Math.min(prof.team.gold, 2);
            prof.team.iron -= fe;
            prof.team.gold -= au;
            if (fe > 0) player.getInventory().add(new ItemStack(Items.IRON_INGOT, fe));
            if (au > 0) player.getInventory().add(new ItemStack(Items.GOLD_INGOT, au));
        }
        prof.onDeath();
        bot.discard();
        prof.body = null;
        if (canRespawn) {
            prof.respawnAt = tick + 100;
        } else {
            prof.eliminated = true;
            checkWinner();
        }
    }

    private static void respawnPlayer() {
        TeamState t = playerTeam();
        playerRespawnAt = -1;
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        equipPlayerArmor(player, t);
        player.getInventory().add(new ItemStack(Items.WOODEN_SWORD));
        player.setHealth(player.getMaxHealth());
        teleportTo(player, t.spawn);
        protectedUntil.put(player.getUUID(), tick + 60);
        player.sendSystemMessage(Component.literal("Du bist wieder da!").withStyle(ChatFormatting.GREEN));
    }

    // --- Hauptschleife ----------------------------------------------------------------------

    public static void tick(MinecraftServer srv) {
        if (pendingStartAt >= 0 && srv.getTickCount() >= pendingStartAt) {
            ServerPlayer p = pendingStartPlayer;
            pendingStartAt = -1;
            pendingStartPlayer = null;
            if (p != null && !p.hasDisconnected() && p.isAlive()) {
                start(p);
            }
        }
        if (!running) return;
        tick++;

        if (player == null || player.hasDisconnected()) {
            stop(false);
            return;
        }

        generators();

        if (playerRespawnAt >= 0 && tick >= playerRespawnAt) {
            respawnPlayer();
        }
        if (tick % 5 == 0) {
            for (TeamState t : teams) {
                for (BotProfile b : t.bots) {
                    if (b.eliminated) continue;
                    if (b.body == null && b.respawnAt >= 0 && tick >= b.respawnAt) {
                        b.respawnAt = -1;
                        spawnBot(b);
                    }
                }
            }
        }
        if (tick % 10 == 0) {
            voidCheck();
        }
        if (tick % 20 == 0) {
            oncePerSecond();
        }
    }

    private static void voidCheck() {
        if (player.isAlive() && player.gameMode() == GameType.SURVIVAL && player.getY() < Arena.VOID_Y) {
            player.kill(level);
        }
        for (TeamState t : teams) {
            for (BotProfile b : t.bots) {
                if (b.body != null && b.body.isAlive() && b.body.getY() < Arena.VOID_Y) {
                    b.body.kill(level);
                }
            }
        }
    }

    private static void oncePerSecond() {
        player.getFoodData().setFoodLevel(20);

        AABB box = new AABB(arena.center.getX() - 60, Arena.Y - 70, arena.center.getZ() - 60, arena.center.getX() + 60, Arena.Y + 50, arena.center.getZ() + 60);
        for (Entity e : level.getEntities((Entity) null, box, x -> x instanceof Monster && !(x instanceof BedwarsBot))) {
            e.discard();
        }

        for (TeamState t : teams) {
            if (t.bedPending) {
                if (arena.hasBed(t.team)) {
                    t.bedPending = false;
                    t.bedAlive = true;
                    snapshot(t);
                    tell(Component.literal("Das Bett von Team " + t.team.label + " steht.").withStyle(t.team.format));
                } else if (tick - t.pendingSince > 900) {
                    t.bedPending = false;
                    t.bedAlive = true;
                    arena.placeBed(t.team);
                    snapshot(t);
                    BotProfile who = t.bots.get(RND.nextInt(t.bots.size()));
                    tell(Component.literal(who.name + " hat das Bett von Team " + t.team.label + " platziert.").withStyle(t.team.format));
                }
            } else if (t.bedAlive) {
                // Explosionen reissen Betten nicht weg
                for (Map.Entry<BlockPos, BlockState> e : t.bedSnapshot.entrySet()) {
                    if (level.getBlockState(e.getKey()) != e.getValue()) {
                        level.setBlock(e.getKey(), e.getValue(), 3);
                    }
                }
            }
        }

        checkWinner();
        if (running) {
            statusBar();
            if (debug && tick % 200 == 0) {
                debugDump();
            }
        }
    }

    private static void debugDump() {
        for (TeamState t : teams) {
            StringBuilder sb = new StringBuilder("[BW dump t=" + tick / 20 + "s] " + t.team.label + " bed=" + t.bedAlive + " ");
            for (BotProfile b : t.bots) {
                sb.append(b.name).append('(').append(b.role.name().charAt(0)).append(b.eliminated ? " OUT" : b.body == null ? " dead" : String.format(" hp%.0f @%.0f,%.0f,%.0f", b.body.getHealth(), b.body.getX() - arena.center.getX(), b.body.getY() - Arena.Y, b.body.getZ() - arena.center.getZ()))
                        .append(b.body == null ? "" : " ->" + (t.targetTeam == null ? "?" : t.targetTeam.label) + " d" + (int) Math.sqrt(b.body.distanceToSqr(Vec3.atCenterOf(teams[(t.targetTeam == null ? t.team : t.targetTeam).ordinal()].island))))
                        .append(" fe").append(t.iron).append("/au").append(t.gold).append("/bl").append(b.blocks).append("/sw").append(b.swordTier).append("/ar").append(b.armorTier).append(") ");
            }
            System.out.println(sb);
        }
    }

    private static void statusBar() {
        Component bar = Component.empty();
        for (TeamState t : teams) {
            ChatFormatting f = t.alive() ? t.team.format : ChatFormatting.DARK_GRAY;
            String mark = t.bedAlive || t.bedPending ? "✔" : (t.alive() ? "✖" : "☠");
            int alive = 0;
            for (BotProfile b : t.bots) if (!b.eliminated) alive++;
            if (t.hasPlayer && !t.playerOut) alive++;
            bar = bar.copy().append(Component.literal(t.team.label + " " + mark + alive + "  ").withStyle(f));
        }
        player.sendOverlayMessage(bar);
    }

    private static void generators() {
        for (TeamState t : teams) {
            if (!t.alive()) continue;
            if (tick % 30 == 0) drop(t.generator, Items.IRON_INGOT);
            if (tick % 120 == 0) drop(t.generator, Items.GOLD_INGOT);
        }
        BlockPos c = arena.center;
        if (tick % 600 == 0) {
            drop(c.offset(4, 2, 0), Items.DIAMOND);
            drop(c.offset(-4, 2, 0), Items.DIAMOND);
        }
        if (tick % 1200 == 0) {
            drop(c.offset(0, 2, 4), Items.EMERALD);
            drop(c.offset(0, 2, -4), Items.EMERALD);
        }
    }

    private static void drop(BlockPos pos, Item item) {
        AABB box = new AABB(pos).inflate(2.5);
        int existing = 0;
        for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, box)) {
            if (e.getItem().is(item)) existing += e.getItem().getCount();
        }
        if (existing >= 40) return;
        ItemEntity e = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, new ItemStack(item));
        e.setDeltaMovement(0, 0.1, 0);
        level.addFreshEntity(e);
    }

    // --- Sieg -------------------------------------------------------------------------------

    private static void checkWinner() {
        if (!running || endAt >= 0) return;
        TeamState last = null;
        int alive = 0;
        for (TeamState t : teams) {
            if (t.alive()) {
                alive++;
                last = t;
            }
        }
        if (alive <= 1 && last != null) {
            endAt = tick + 200;
            tell(Component.literal("TEAM " + last.team.label.toUpperCase() + " HAT GEWONNEN!").withStyle(last.team.format, ChatFormatting.BOLD));
            player.sendSystemMessage(Component.literal(last.hasPlayer ? "Sieg! Du hast Bedwars gewonnen." : "Verloren. Naechste Runde: /bedwars start").withStyle(last.hasPlayer ? ChatFormatting.GOLD : ChatFormatting.RED));
        } else if (alive == 0) {
            endAt = tick + 40;
        }
    }

    public static void afterTick() {
        if (running && endAt >= 0 && tick >= endAt) {
            endAt = -1;
            ServerPlayer p = player;
            stop(true);
            if (p != null) {
                p.sendSystemMessage(Component.literal("Runde vorbei. Mit /bedwars start geht es von vorne los.").withStyle(ChatFormatting.GREEN));
            }
        }
    }

    public static void onDisconnect(ServerPlayer p) {
        if (running && p == player) {
            stop(true);
        }
    }

    public static void onServerStopping() {
        if (running) {
            stop(true);
        }
    }

    private static void tell(Component c) {
        if (debug) {
            System.out.println("[BW t=" + tick / 20 + "s] " + c.getString());
        }
        if (player != null) {
            player.sendSystemMessage(c);
        }
    }

    public static String status() {
        if (!running) return "Keine Runde aktiv. /bedwars start";
        StringBuilder sb = new StringBuilder("Runde laeuft seit " + tick / 20 + "s");
        for (TeamState t : teams) {
            sb.append("\n").append(t.team.label).append(": Bett ").append(t.bedAlive ? "ja" : (t.bedPending ? "fehlt noch" : "nein"))
                    .append(", ").append(t.alive() ? "im Spiel" : "ausgeschieden");
        }
        return sb.toString();
    }
}
