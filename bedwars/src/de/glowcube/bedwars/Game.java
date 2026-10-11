package de.glowcube.bedwars;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class Game {
   private static final Random RND = new Random();
   private static final String[] BOYS = new String[]{
      "Finn", "Luca", "Nico", "Jonas", "Ben", "Noah", "Leon", "Paul", "Felix", "Tim", "Max", "Elias", "Moritz", "Lukas", "Jan", "David", "Emil", "Tom", "Jakob", "Theo"
   };
   private static final String[] GIRLS = new String[]{
      "Mila", "Emma", "Lena", "Anna", "Mia", "Hanna", "Lara", "Sophie", "Ida", "Clara", "Nele", "Lea", "Marie", "Lina", "Zoe", "Julia", "Paula", "Greta"
   };
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
   private static int planBudget;
   private static int diamondTier = 1;
   private static int emeraldTier = 1;
   private static Difficulty oldDifficulty;
   private static long savedClockTicks = -1;
   private static boolean savedClockPaused;
   private static boolean firstBlood;
   private static boolean suddenDeath;
   private static final Map<UUID, Integer> protectedUntil = new HashMap<>();
   private static final List<Villager> shops = new ArrayList<>();
   public static boolean autoStart = true;
   public static boolean debug;

   private Game() {
   }

   public static boolean running() {
      return running;
   }

   public static Arena arena() {
      return arena;
   }

   public static ServerPlayer player() {
      return player;
   }

   public static int tick() {
      return tick;
   }

   public static boolean suddenDeath() {
      return suddenDeath;
   }

   public static List<TeamState> teams() {
      return teams == null ? List.of() : List.of(teams);
   }

   public static TeamState playerTeam() {
      if (teams == null) {
         return null;
      } else {
         for (TeamState t : teams) {
            if (t.hasPlayer) {
               return t;
            }
         }

         return null;
      }
   }

   public static BwTeam teamOf(Entity e) {
      if (!running) {
         return null;
      } else if (e instanceof BedwarsBot bot && bot.profile != null) {
         return bot.profile.team.team;
      } else if (e == player) {
         TeamState t = playerTeam();
         return t == null ? null : t.team;
      } else {
         return null;
      }
   }

   public static boolean isEnemy(TeamState t, Entity e) {
      if (e == null || !e.isAlive()) {
         return false;
      } else if (e == player) {
         return player.gameMode() == GameType.SURVIVAL && !t.hasPlayer;
      } else {
         return e instanceof BedwarsBot bot && bot.profile != null && bot.profile.team != t;
      }
   }

   /** Alle lebenden Gegner eines Teams: der Spieler und die Bots der anderen Teams. */
   public static List<LivingEntity> enemiesOf(TeamState t) {
      List<LivingEntity> list = new ArrayList<>();
      if (player != null && player.isAlive() && player.gameMode() == GameType.SURVIVAL && !t.hasPlayer) {
         list.add(player);
      }

      for (TeamState o : teams) {
         if (o != t) {
            for (BotProfile p : o.bots) {
               if (p.body != null && p.body.isAlive()) {
                  list.add(p.body);
               }
            }
         }
      }

      return list;
   }

   public static LivingEntity nearestMember(TeamState t, Vec3 from) {
      LivingEntity best = null;
      double bd = Double.MAX_VALUE;
      if (t.hasPlayer && player != null && player.isAlive() && player.gameMode() == GameType.SURVIVAL) {
         best = player;
         bd = player.position().distanceToSqr(from);
      }

      for (BotProfile p : t.bots) {
         if (p.body != null && p.body.isAlive()) {
            double d = p.body.position().distanceToSqr(from);
            if (d < bd) {
               bd = d;
               best = p.body;
            }
         }
      }

      return best;
   }

   public static boolean takePlanBudget() {
      if (planBudget > 0) {
         planBudget--;
         return true;
      } else {
         return false;
      }
   }

   public static Holder<Enchantment> enchantment(ResourceKey<Enchantment> key) {
      return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
   }

   public static void enchant(ItemStack stack, ResourceKey<Enchantment> key, int lvl) {
      if (lvl > 0 && level != null && !stack.isEmpty()) {
         stack.enchant(enchantment(key), lvl);
      }
   }

   /** Ein Team-Upgrade wurde gekauft: Ausruestung aller Teammitglieder auffrischen und Bescheid geben. */
   public static void upgradeBought(TeamState t, String name, BotProfile buyer, ServerPlayer playerBuyer) {
      for (BotProfile b : t.bots) {
         if (b.body != null && b.body.isAlive()) {
            b.equipArmor(b.body);
            b.body.refreshHeldItem();
         }
      }

      if (t.hasPlayer && player != null) {
         applyPlayerUpgrades(player, t);
         Component who = playerBuyer != null ? Component.literal(playerBuyer.getName().getString()) : Component.literal(buyer == null ? "Dein Team" : buyer.name);
         player.sendSystemMessage(Component.literal("[TEAM-UPGRADE] ").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD).append(who.copy().withStyle(t.team.format)).append(Component.literal(" hat " + name + " gekauft.").withStyle(ChatFormatting.GRAY)));
      }

      if (buyer != null && t.hasPlayer) {
         BotChat.say(buyer, "hab " + name.toLowerCase() + " fuers team geholt", 20, true);
      }
   }

   /** Schaerfe auf alle Schwerter, Schutz auf die Ruestung des Spielers. */
   public static void applyPlayerUpgrades(ServerPlayer p, TeamState t) {
      if (t.sharpness) {
         Holder<Enchantment> sharp = enchantment(Enchantments.SHARPNESS);

         for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if ((s.is(Items.WOODEN_SWORD) || s.is(Items.STONE_SWORD) || s.is(Items.IRON_SWORD) || s.is(Items.DIAMOND_SWORD)) && EnchantmentHelper.getItemEnchantmentLevel(sharp, s) < 1) {
               s.enchant(sharp, 1);
            }
         }
      }

      if (t.protection > 0 && EnchantmentHelper.getItemEnchantmentLevel(enchantment(Enchantments.PROTECTION), p.getItemBySlot(EquipmentSlot.LEGS)) < t.protection) {
         equipPlayerArmor(p, t);
      }
   }

   public static int playerSwordTier(ServerPlayer p) {
      int tier = 0;

      for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
         ItemStack s = p.getInventory().getItem(i);
         if (s.is(Items.STONE_SWORD)) {
            tier = Math.max(tier, 1);
         } else if (s.is(Items.IRON_SWORD)) {
            tier = Math.max(tier, 2);
         } else if (s.is(Items.DIAMOND_SWORD)) {
            tier = Math.max(tier, 3);
         }
      }

      return tier;
   }

   public static int playerGearScore(ServerPlayer p) {
      TeamState t = playerTeam();
      return playerSwordTier(p) * 2 + (t == null ? 0 : t.playerArmorTier * 2);
   }

   public static boolean isProtected(Entity e) {
      Integer until = protectedUntil.get(e.getUUID());
      return until != null && until > tick;
   }

   public static void onJoin(ServerPlayer p, MinecraftServer srv) {
      if (!running) {
         Backup b = Backup.load(srv);
         if (b != null) {
            b.restore(p, p.level());
            Backup.delete(srv);
            p.sendSystemMessage(Component.literal("Deine Sachen aus der letzten Bedwars-Runde wurden zurueckgegeben.").withStyle(ChatFormatting.GREEN));
         }
      }

      if (autoStart && srv.isSingleplayer()) {
         pendingStartPlayer = p;
         pendingStartAt = srv.getTickCount() + 40;
      }
   }

   // ====================================================================== Start und Ende

   public static void start(ServerPlayer p) {
      if (running) {
         stop(true);
      }

      server = p.level().getServer();
      level = p.level();
      player = p;
      backup = new Backup(p);
      backup.save(server);
      tick = 0;
      endAt = -1;
      playerRespawnAt = -1;
      diamondTier = 1;
      emeraldTier = 1;
      firstBlood = false;
      suddenDeath = false;
      protectedUntil.clear();
      BotChat.reset();
      arena = new Arena(level, p.blockPosition());
      arena.forceLoad(true);
      removeLeftovers();
      arena.build();
      oldDifficulty = level.getDifficulty();
      if (oldDifficulty != Difficulty.NORMAL) {
         server.setDifficulty(Difficulty.NORMAL, true);
      }

      freezeDaylight();
      running = true;
      teams = new TeamState[BwTeam.values().length];

      for (BwTeam t : BwTeam.values()) {
         teams[t.ordinal()] = new TeamState(t, arena);
      }

      TeamState mine = teams[RND.nextInt(teams.length)];
      mine.hasPlayer = true;
      List<String> boys = new ArrayList<>(List.of(BOYS));
      List<String> girls = new ArrayList<>(List.of(GIRLS));
      Collections.shuffle(boys, RND);
      Collections.shuffle(girls, RND);

      for (TeamState t : teams) {
         int count = t.hasPlayer ? 3 : 4;

         for (int i = 0; i < count; i++) {
            boolean alex = RND.nextBoolean() && !girls.isEmpty() || boys.isEmpty();
            String name = alex ? girls.remove(0) : boys.remove(0);
            t.bots.add(new BotProfile(name, t, RND, alex));
         }

         t.thinkAt = 20 + t.team.ordinal() * 8;
      }

      // Jedes Team hat von Anfang an ein Bett
      for (TeamState t : teams) {
         arena.placeBed(t.team);
         t.bedAlive = true;
         snapshot(t);
      }

      for (TeamState t : teams) {
         spawnShop(t);

         for (BotProfile b : t.bots) {
            b.wool = 16;
            spawnBot(b);
         }
      }

      preparePlayer(mine);
      tell(
         Component.literal("=== BEDWARS === 5 Teams, du bist im Team ")
            .withStyle(ChatFormatting.GOLD)
            .append(Component.literal(mine.team.label).withStyle(mine.team.format, ChatFormatting.BOLD))
      );
      tell(Component.literal("Zerstoere die Betten der anderen Teams und ueberlebe. Shop: Haendler unter dem Pavillon auf deiner Insel oder /bedwars shop.").withStyle(ChatFormatting.YELLOW));
      tell(
         Component.literal("Deine Teamkollegen hoeren auf den Chat: \"verteidigt\", \"greift blau an\", \"folgt mir\", \"holt dias\", \"baut das bett zu\", \"wo seid ihr\"...")
            .withStyle(ChatFormatting.AQUA)
      );
      BotChat.gameStart();
   }

   private static Holder<net.minecraft.world.clock.WorldClock> overworldClock() {
      return level.registryAccess().lookupOrThrow(Registries.WORLD_CLOCK).getOrThrow(net.minecraft.world.clock.WorldClocks.OVERWORLD);
   }

   /** Waehrend der Runde ist immer Mittag; danach laeuft die Weltuhr dort weiter, wo sie war. */
   private static void freezeDaylight() {
      savedClockTicks = -1;

      try {
         if (level.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
            net.minecraft.world.clock.ServerClockManager cm = level.clockManager();
            Holder<net.minecraft.world.clock.WorldClock> clock = overworldClock();
            savedClockTicks = cm.getInstance(clock).totalTicks();
            savedClockPaused = cm.getInstance(clock).isPaused();
            cm.moveToTimeMarker(clock, net.minecraft.world.clock.ClockTimeMarkers.NOON);
            cm.setPaused(clock, true);
            server.getPlayerList().broadcastAll(cm.createFullSyncPacket());
         }
      } catch (Throwable e) {
         savedClockTicks = -1;
         System.err.println("[Bedwars] Konnte die Tageszeit nicht anhalten: " + e);
      }
   }

   private static void restoreDaylight() {
      if (savedClockTicks >= 0) {
         try {
            net.minecraft.world.clock.ServerClockManager cm = level.clockManager();
            Holder<net.minecraft.world.clock.WorldClock> clock = overworldClock();
            cm.setTotalTicks(clock, savedClockTicks + (savedClockPaused ? 0 : tick));
            cm.setPaused(clock, savedClockPaused);
            server.getPlayerList().broadcastAll(cm.createFullSyncPacket());
         } catch (Throwable e) {
            System.err.println("[Bedwars] Konnte die Tageszeit nicht zuruecksetzen: " + e);
         }

         savedClockTicks = -1;
      }
   }

   /** Testmodus: der Platzhalter-Spieler schaut nur zu, die Bots spielen unter sich. */
   public static void benchPlayer() {
      TeamState t = playerTeam();
      if (running && t != null && player != null) {
         t.playerOut = true;
         player.setGameMode(GameType.SPECTATOR);
      }
   }

   /** Mannequins einer abgestuerzten Runde wegraeumen. */
   private static void removeLeftovers() {
      for (Entity e : level.getEntities((Entity)null, arena.bounds(), en -> en instanceof Mannequin && en.entityTags().contains("bw_bot") || en.entityTags().contains("bw_shop"))) {
         e.discard();
      }
   }

   public static void stop(boolean restore) {
      if (running) {
         running = false;

         for (TeamState t : teams) {
            for (BotProfile b : t.bots) {
               if (b.body != null) {
                  b.body.discard();
                  b.body = null;
               }
            }
         }

         for (Villager v : shops) {
            v.discard();
         }

         shops.clear();

         for (Entity e : level.getEntities((Entity)null, arena.bounds(), en -> en instanceof ItemEntity || en instanceof BedwarsBot || en.entityTags().contains("bw_bot"))) {
            e.discard();
         }

         if (restore && player != null && backup != null) {
            backup.restore(player, level);
            Backup.delete(server);
         }

         arena.clear();
         arena.forceLoad(false);
         if (oldDifficulty != null && oldDifficulty != level.getDifficulty()) {
            server.setDifficulty(oldDifficulty, true);
         }

         restoreDaylight();
         BotChat.reset();
         player = null;
         backup = null;
      }
   }

   private static void preparePlayer(TeamState t) {
      player.getInventory().clearContent();
      player.removeAllEffects();
      player.setGameMode(GameType.SURVIVAL);
      player.setHealth(player.getMaxHealth());
      player.getFoodData().setFoodLevel(20);
      t.playerArmorTier = 0;
      equipPlayerArmor(player, t);
      player.getInventory().add(new ItemStack(Items.WOODEN_SWORD));
      player.getInventory().add(new ItemStack(t.team.wool.asItem(), 16));
      teleportTo(player, t.island().spawn());
      protectedUntil.put(player.getUUID(), tick + 60);
   }

   private static void teleportTo(ServerPlayer p, Vec3 pos) {
      p.teleportTo(level, pos.x, pos.y, pos.z, Set.of(), 0.0F, 0.0F, true);
   }

   public static void equipPlayerArmor(ServerPlayer p, TeamState t) {
      ItemStack helmet = dyed(Items.LEATHER_HELMET, t.team);
      ItemStack chest = dyed(Items.LEATHER_CHESTPLATE, t.team);
      enchant(helmet, Enchantments.PROTECTION, t.protection);
      enchant(chest, Enchantments.PROTECTION, t.protection);
      p.setItemSlot(EquipmentSlot.HEAD, helmet);
      p.setItemSlot(EquipmentSlot.CHEST, chest);
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
      ItemStack legStack = t.playerArmorTier == 0 ? dyed(legs, t.team) : new ItemStack(legs);
      ItemStack bootStack = t.playerArmorTier == 0 ? dyed(boots, t.team) : new ItemStack(boots);
      enchant(legStack, Enchantments.PROTECTION, t.protection);
      enchant(bootStack, Enchantments.PROTECTION, t.protection);
      p.setItemSlot(EquipmentSlot.LEGS, legStack);
      p.setItemSlot(EquipmentSlot.FEET, bootStack);
   }

   private static ItemStack dyed(Item item, BwTeam team) {
      ItemStack stack = new ItemStack(item);
      stack.set(DataComponents.DYED_COLOR, new DyedItemColor(team.rgb));
      return stack;
   }

   private static void spawnBot(BotProfile p) {
      BedwarsBot bot = new BedwarsBot(level);
      p.body = bot;
      p.task = BotProfile.Task.IDLE;
      p.taskLockUntil = 0;
      Vec3 spawn = p.team.island().spawn();
      double ox = (RND.nextDouble() - 0.5) * 3.0;
      double oz = (RND.nextDouble() - 0.5) * 3.0;
      bot.setPos(spawn.x + ox, spawn.y, spawn.z + oz);
      bot.setYRot(RND.nextFloat() * 360.0F);
      bot.setup(p);
      level.addFreshEntity(bot);
      protectedUntil.put(bot.getUUID(), tick + 60);
      TeamBrain.requestThink(p.team);
   }

   private static void spawnShop(TeamState t) {
      Villager v = (Villager)EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
      if (v != null) {
         Vec3 pos = t.island().shop();
         v.setPos(pos.x, pos.y, pos.z);
         v.setNoAi(true);
         v.setPermanentlyInvulnerable(true);
         v.setSilent(true);
         v.setPersistenceRequired();
         v.setCustomName(Component.literal("SHOP").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
         v.setCustomNameVisible(true);
         v.addTag("bw_shop");
         Vec3 look = t.island().spawn();
         float yaw = (float)(Math.atan2(-(look.x - pos.x), look.z - pos.z) * 180.0 / Math.PI);
         v.setYRot(yaw);
         v.setYHeadRot(yaw);
         v.setYBodyRot(yaw);
         level.addFreshEntity(v);
         shops.add(v);
      }
   }

   private static void snapshot(TeamState t) {
      t.bedSnapshot.clear();

      for (BlockPos pos : arena.bedBlocks(t.team)) {
         t.bedSnapshot.put(pos, level.getBlockState(pos));
      }
   }

   // ====================================================================== Betten und Kills

   public static void destroyBed(TeamState victim, TeamState byTeam, BotProfile byBot, ServerPlayer byPlayer) {
      if (victim.bedAlive) {
         victim.bedAlive = false;
         victim.bedSnapshot.clear();
         arena.removeBed(victim.team);
         level.playSound(null, BlockPos.containing(victim.island().spawn()), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.MASTER, 2.0F, 1.0F);
         String by = byBot != null ? byBot.name : (byPlayer != null ? byPlayer.getName().getString() : "jemandem");
         tell(
            Component.literal("BETT ZERSTOERT > ")
               .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
               .append(Component.literal("Das Bett von Team ").withStyle(ChatFormatting.GRAY))
               .append(Component.literal(victim.team.label).withStyle(victim.team.format))
               .append(Component.literal(" wurde von ").withStyle(ChatFormatting.GRAY))
               .append(Component.literal(by).withStyle(byTeam == null ? ChatFormatting.WHITE : byTeam.team.format))
               .append(Component.literal(" zerstoert!").withStyle(ChatFormatting.GRAY))
         );
         if (byBot != null) {
            byBot.beds++;
         }

         if (byTeam != null) {
            victim.grudgeAgainst[byTeam.team.ordinal()] += 3.0;
            if (byTeam.hasPlayer && byPlayer != null) {
               victim.grudgePlayer += 1.5;
            }
         }

         if (victim.hasPlayer && player != null) {
            player.sendSystemMessage(Component.literal("Dein Bett ist weg - du hast nur noch ein Leben!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
         }

         BotChat.bedDestroyed(victim, byTeam, byBot, byPlayer);
         TeamBrain.requestThink(victim);
      }
   }

   public static boolean onPlayerBreak(ServerPlayer p, BlockPos pos, BlockState state) {
      if (!running || p != player) {
         return true;
      } else if (state.getBlock() instanceof BedBlock) {
         BwTeam owner = arena.bedOwner(pos);
         if (owner == null) {
            return false;
         } else {
            TeamState t = teams[owner.ordinal()];
            if (t.hasPlayer) {
               p.sendSystemMessage(Component.literal("Du kannst dein eigenes Bett nicht abbauen.").withStyle(ChatFormatting.RED));
               return false;
            } else {
               destroyBed(t, playerTeam(), null, p);
               return false;
            }
         }
      } else {
         return !arena.isMap(pos);
      }
   }

   public static boolean onDeath(LivingEntity e, DamageSource src) {
      if (!running) {
         return true;
      } else if (e == player) {
         handlePlayerDeath(src);
         return false;
      } else if (e instanceof BedwarsBot bot && bot.profile != null) {
         handleBotDeath(bot, src);
         return false;
      } else {
         return true;
      }
   }

   private static Component nameOf(Entity e) {
      if (e instanceof BedwarsBot bot && bot.profile != null) {
         return bot.displayName();
      } else if (e == player) {
         TeamState t = playerTeam();
         return Component.literal(player.getName().getString()).withStyle(t == null ? ChatFormatting.WHITE : t.team.format);
      } else {
         return e.getName();
      }
   }

   private static Entity killerOf(LivingEntity victim, DamageSource src) {
      Entity killer = src.getEntity();
      if (killer == null && victim.getLastHurtByMob() != null && victim.tickCount - victim.getLastHurtByMobTimestamp() < 200) {
         killer = victim.getLastHurtByMob();
      }

      return killer != null && killer != victim ? killer : null;
   }

   private static void announceKill(LivingEntity victim, Entity killer, boolean finalKill) {
      MutableComponent msg = nameOf(victim).copy()
         .append(Component.literal(killer == null ? " ist in die Leere gefallen." : (victim.getY() < Arena.VOID_Y + 5 ? " wurde von " : " wurde von ")).withStyle(ChatFormatting.GRAY));
      if (killer != null) {
         msg.append(nameOf(killer)).append(Component.literal(victim.getY() < Arena.VOID_Y + 5 ? " in die Leere gestossen." : " getoetet.").withStyle(ChatFormatting.GRAY));
      }

      if (finalKill) {
         msg.append(Component.literal(" ENDGUELTIG!").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
      }

      tell(msg);
      if (!firstBlood && killer != null) {
         firstBlood = true;
         tell(Component.literal("ERSTES BLUT! ").withStyle(ChatFormatting.RED, ChatFormatting.BOLD).append(nameOf(killer)));
      }

      BotChat.kill(victim, killer, finalKill);
      if (killer instanceof BedwarsBot kb && kb.profile != null) {
         kb.profile.kills++;
         if (finalKill) {
            kb.profile.finals++;
         }
      }

      TeamState killerTeam = killer instanceof BedwarsBot kb2 && kb2.profile != null ? kb2.profile.team : (killer == player ? playerTeam() : null);
      TeamState victimTeam = victim instanceof BedwarsBot vb && vb.profile != null ? vb.profile.team : (victim == player ? playerTeam() : null);
      if (killerTeam != null && victimTeam != null && killerTeam != victimTeam) {
         victimTeam.grudgeAgainst[killerTeam.team.ordinal()] += 0.4;
         if (killer == player) {
            victimTeam.grudgePlayer += 0.25;
         }
      }
   }

   private static void handlePlayerDeath(DamageSource src) {
      TeamState t = playerTeam();
      Entity killer = killerOf(player, src);
      boolean canRespawn = t.bedAlive;
      announceKill(player, killer, !canRespawn);
      int[] loot = takeLoot(player);
      if (killer instanceof BedwarsBot kb && kb.profile != null) {
         kb.profile.team.iron += loot[0];
         kb.profile.team.gold += loot[1];
         kb.profile.team.diamond += loot[2];
         kb.profile.team.emerald += loot[3];
      }

      player.getInventory().clearContent();
      player.setHealth(player.getMaxHealth());
      player.clearFire();
      player.removeAllEffects();
      player.fallDistance = 0.0;
      player.setDeltaMovement(Vec3.ZERO);
      player.setGameMode(GameType.SPECTATOR);
      teleportTo(player, t.island().spawn().add(0.0, 8.0, 0.0));
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
      int[] loot = new int[4];

      for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
         ItemStack s = p.getInventory().getItem(i);
         if (s.is(Items.IRON_INGOT)) {
            loot[0] += s.getCount();
         } else if (s.is(Items.GOLD_INGOT)) {
            loot[1] += s.getCount();
         } else if (s.is(Items.DIAMOND)) {
            loot[2] += s.getCount();
         } else if (s.is(Items.EMERALD)) {
            loot[3] += s.getCount();
         }
      }

      return loot;
   }

   private static void handleBotDeath(BedwarsBot bot, DamageSource src) {
      BotProfile p = bot.profile;
      TeamState t = p.team;
      Entity killer = killerOf(bot, src);
      boolean canRespawn = t.bedAlive;
      announceKill(bot, killer, !canRespawn);
      if (killer == player) {
         int iron = Math.min(t.iron, 8);
         int gold = Math.min(t.gold, 2);
         t.iron -= iron;
         t.gold -= gold;
         if (iron > 0) {
            player.getInventory().add(new ItemStack(Items.IRON_INGOT, iron));
         }

         if (gold > 0) {
            player.getInventory().add(new ItemStack(Items.GOLD_INGOT, gold));
         }
      } else if (killer instanceof BedwarsBot kb && kb.profile != null) {
         int iron = Math.min(t.iron, 6);
         t.iron -= iron;
         kb.profile.team.iron += iron;
      }

      p.onDeath();
      bot.discard();
      p.body = null;
      if (canRespawn) {
         p.respawnAt = tick + 100;
      } else {
         p.eliminated = true;
         if (!t.alive()) {
            tell(Component.literal("TEAM AUSGESCHIEDEN > ").withStyle(ChatFormatting.RED, ChatFormatting.BOLD).append(Component.literal("Team " + t.team.label).withStyle(t.team.format)));
            BotChat.teamEliminated(t);
         }

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
      applyPlayerUpgrades(player, t);
      player.setHealth(player.getMaxHealth());
      teleportTo(player, t.island().spawn());
      protectedUntil.put(player.getUUID(), tick + 60);
      player.sendSystemMessage(Component.literal("Du bist wieder da!").withStyle(ChatFormatting.GREEN));
   }

   // ====================================================================== Tick

   public static void tick(MinecraftServer srv) {
      if (pendingStartAt >= 0 && srv.getTickCount() >= pendingStartAt) {
         ServerPlayer p = pendingStartPlayer;
         pendingStartAt = -1;
         pendingStartPlayer = null;
         if (p != null && !p.hasDisconnected() && p.isAlive()) {
            start(p);
         }
      }

      if (running) {
         tick++;
         planBudget = 3;
         if (player != null && !player.hasDisconnected()) {
            generators();
            if (playerRespawnAt >= 0 && tick >= playerRespawnAt) {
               respawnPlayer();
            }

            if (tick % 5 == 0) {
               for (TeamState t : teams) {
                  for (BotProfile b : t.bots) {
                     if (!b.eliminated && b.body == null && b.respawnAt >= 0 && tick >= b.respawnAt) {
                        b.respawnAt = -1;
                        spawnBot(b);
                     }
                  }
               }
            }

            for (TeamState t : teams) {
               TeamBrain.tick(t);
            }

            BotChat.tick();
            arena.repairStep(250);
            if (tick % 10 == 0) {
               voidCheck();
            }

            if (tick % 20 == 0) {
               oncePerSecond();
            }
         } else {
            stop(false);
         }
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
      TeamState pt = playerTeam();
      if (pt != null && player.gameMode() == GameType.SURVIVAL) {
         applyPlayerUpgrades(player, pt);
      }

      for (TeamState t : teams) {
         if (t.healPool) {
            Vec3 home = t.island().centerVec();
            List<LivingEntity> members = new ArrayList<>();
            if (t.hasPlayer && player.gameMode() == GameType.SURVIVAL) {
               members.add(player);
            }

            for (BotProfile b : t.bots) {
               if (b.body != null && b.body.isAlive()) {
                  members.add(b.body);
               }
            }

            for (LivingEntity m : members) {
               if (m.position().distanceTo(home) < Arena.ISLAND_RADIUS + 3 && !m.hasEffect(MobEffects.REGENERATION)) {
                  m.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 50, 0, true, true));
               }
            }
         }
      }

      for (Entity e : level.getEntities((Entity)null, arena.bounds(), en -> en instanceof Monster)) {
         e.discard();
      }

      for (TeamState t : teams) {
         if (t.bedAlive) {
            for (Entry<BlockPos, BlockState> en : t.bedSnapshot.entrySet()) {
               if (level.getBlockState(en.getKey()) != en.getValue()) {
                  level.setBlock(en.getKey(), en.getValue(), 3);
               }
            }
         }
      }

      if (tick == 20 * 660) {
         tell(Component.literal("In einer Minute werden alle Betten zerstoert!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
      }

      if (tick == 20 * 720) {
         tell(Component.literal("BETTZERSTOERUNG > ").withStyle(ChatFormatting.RED, ChatFormatting.BOLD).append(Component.literal("Alle Betten wurden zerstoert!").withStyle(ChatFormatting.GRAY)));
         level.playSound(null, BlockPos.containing(arena.center.getX(), Arena.Y, arena.center.getZ()), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.MASTER, 3.0F, 0.8F);

         for (TeamState t : teams) {
            if (t.bedAlive) {
               t.bedAlive = false;
               t.bedSnapshot.clear();
               arena.removeBed(t.team);
               TeamBrain.requestThink(t);
            }
         }

         if (player != null) {
            player.sendSystemMessage(Component.literal("Ab jetzt hast du nur noch ein Leben!").withStyle(ChatFormatting.RED));
         }
      }

      if (tick == 20 * 900) {
         suddenDeath = true;
         tell(Component.literal("SUDDEN DEATH > ").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD).append(Component.literal("Jetzt wird nicht mehr gewartet - alle gegen alle!").withStyle(ChatFormatting.GRAY)));
         level.playSound(null, BlockPos.containing(arena.center.getX(), Arena.Y, arena.center.getZ()), SoundEvents.WITHER_SPAWN, SoundSource.MASTER, 2.0F, 1.0F);
      }

      if (suddenDeath) {
         for (TeamState t : teams) {
            for (BotProfile b : t.bots) {
               if (b.body != null && b.body.isAlive() && !b.body.hasEffect(MobEffects.GLOWING)) {
                  b.body.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100000, 0, false, false));
               }
            }
         }
      }

      if (tick == 20 * 360 || tick == 20 * 720) {
         diamondTier++;
         tell(Component.literal("Die Diamant-Spawner sind jetzt Stufe " + (diamondTier == 2 ? "II" : "III") + "!").withStyle(ChatFormatting.AQUA));
      }

      if (tick == 20 * 540 || tick == 20 * 900) {
         emeraldTier++;
         tell(Component.literal("Die Smaragd-Spawner sind jetzt Stufe " + (emeraldTier == 2 ? "II" : "III") + "!").withStyle(ChatFormatting.DARK_GREEN));
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
         StringBuilder sb = new StringBuilder("[BW dump t=" + tick / 20 + "s] " + t.team.label + " bed=" + t.bedAlive + " fort=" + t.fortificationStrength() + " fe" + t.iron + "/au" + t.gold + "/di" + t.diamond + "/em" + t.emerald + " ");

         for (BotProfile b : t.bots) {
            sb.append(b.name).append('(').append(b.role.name().charAt(0));
            if (b.eliminated) {
               sb.append(" OUT");
            } else if (b.body == null) {
               sb.append(" dead");
            } else {
               sb.append(String.format(" hp%.0f @%.0f,%.0f,%.0f ", b.body.getHealth(), b.body.getX() - arena.center.getX(), b.body.getY() - Arena.Y, b.body.getZ() - arena.center.getZ()))
                  .append(b.body.debugState());
            }

            sb.append(" bl").append(b.blocks()).append(" sw").append(b.swordTier).append(" ar").append(b.armorTier).append(" k").append(b.kills).append(") ");
         }

         System.out.println(sb);
      }
   }

   private static void statusBar() {
      MutableComponent bar = Component.empty();

      for (TeamState t : teams) {
         ChatFormatting color = t.alive() ? t.team.format : ChatFormatting.DARK_GRAY;
         String mark = t.bedAlive ? "✔" : (t.alive() ? "✖" : "☠");
         bar.append(Component.literal(t.team.label + " " + mark + t.aliveCount() + "  ").withStyle(color));
      }

      player.sendOverlayMessage(bar);
   }

   private static void generators() {
      for (TeamState t : teams) {
         if (t.alive()) {
            BlockPos gen = t.island().generator();
            int ironEvery = t.forge == 0 ? 20 : (t.forge == 1 ? 14 : 10);
            int goldEvery = t.forge == 0 ? 100 : (t.forge == 1 ? 70 : 50);
            if (tick % ironEvery == 0) {
               drop(gen, Items.IRON_INGOT, 48);
            }

            if (tick % goldEvery == 0) {
               drop(gen, Items.GOLD_INGOT, 16);
            }
         }
      }

      int diamondEvery = diamondTier == 1 ? 600 : (diamondTier == 2 ? 460 : 320);
      if (tick % diamondEvery == 0) {
         for (BlockPos d : arena.diamondGens) {
            drop(d, Items.DIAMOND, 4);
         }
      }

      int emeraldEvery = emeraldTier == 1 ? 1200 : (emeraldTier == 2 ? 860 : 600);
      if (tick % emeraldEvery == 0) {
         for (BlockPos e : arena.emeraldGens) {
            drop(e, Items.EMERALD, 3);
         }
      }
   }

   private static void drop(BlockPos pos, Item item, int cap) {
      int have = 0;

      for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2.5))) {
         if (e.getItem().is(item)) {
            have += e.getItem().getCount();
         }
      }

      if (have < cap) {
         ItemEntity e = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, new ItemStack(item));
         e.setDeltaMovement(0.0, 0.1, 0.0);
         level.addFreshEntity(e);
      }
   }

   private static void checkWinner() {
      if (running && endAt < 0) {
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
            player.sendSystemMessage(
               Component.literal(last.hasPlayer ? "Sieg! Du hast Bedwars gewonnen." : "Verloren. Naechste Runde: /bedwars start")
                  .withStyle(last.hasPlayer ? ChatFormatting.GOLD : ChatFormatting.RED)
            );
            BotProfile mvp = null;

            for (TeamState t : teams) {
               for (BotProfile b : t.bots) {
                  if (mvp == null || b.kills + b.beds * 3 + b.finals > mvp.kills + mvp.beds * 3 + mvp.finals) {
                     mvp = b;
                  }
               }
            }

            if (mvp != null) {
               tell(Component.literal("Bester Bot: ").withStyle(ChatFormatting.GOLD)
                  .append(Component.literal(mvp.name).withStyle(mvp.team.team.format))
                  .append(Component.literal(" - " + mvp.kills + " Kills, " + mvp.finals + " Finals, " + mvp.beds + " Betten").withStyle(ChatFormatting.GRAY)));
            }

            BotChat.gameOver(last);
         } else if (alive == 0) {
            endAt = tick + 40;
         }
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

   public static void onChat(ServerPlayer p, String text) {
      if (running && p == player) {
         BotChat.onPlayerChat(p, text);
      }
   }

   public static void broadcast(Component msg) {
      if (debug) {
         System.out.println("[BW chat t=" + tick / 20 + "s] " + msg.getString());
      }

      if (server != null) {
         for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(msg);
         }
      }
   }

   private static void tell(Component msg) {
      if (debug) {
         System.out.println("[BW t=" + tick / 20 + "s] " + msg.getString());
      }

      if (player != null) {
         player.sendSystemMessage(msg);
      }
   }

   public static String status() {
      if (!running) {
         return "Keine Runde aktiv. /bedwars start";
      } else {
         StringBuilder sb = new StringBuilder("Runde laeuft seit " + tick / 20 + "s");

         for (TeamState t : teams) {
            sb.append("\n").append(t.team.label).append(": Bett ").append(t.bedAlive ? "ja" : "nein").append(", ").append(t.alive() ? t.aliveCount() + " im Spiel" : "ausgeschieden");

            for (BotProfile b : t.bots) {
               if (!b.eliminated) {
                  sb.append("\n  ").append(b.name).append(" - ").append(b.body == null ? "tot" : b.taskLabel());
               }
            }
         }

         return sb.toString();
      }
   }

   // ====================================================================== Sicherung

   private static final class Backup {
      final List<ItemStack> items = new ArrayList<>();
      final Vec3 pos;
      final float yaw;
      final float pitch;
      final GameType mode;
      final float health;
      final int food;

      Backup(ServerPlayer p) {
         for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            this.items.add(p.getInventory().getItem(i).copy());
         }

         this.pos = p.position();
         this.yaw = p.getYRot();
         this.pitch = p.getXRot();
         this.mode = p.gameMode();
         this.health = p.getHealth();
         this.food = p.getFoodData().getFoodLevel();
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

      void restore(ServerPlayer p, ServerLevel lvl) {
         p.getInventory().clearContent();

         for (int i = 0; i < this.items.size(); i++) {
            p.getInventory().setItem(i, this.items.get(i));
         }

         p.removeAllEffects();
         p.teleportTo(lvl, this.pos.x, this.pos.y, this.pos.z, Set.of(), this.yaw, this.pitch, true);
         p.setGameMode(this.mode);
         p.setHealth(Math.max(1.0F, this.health));
         p.getFoodData().setFoodLevel(this.food);
      }

      static Path file(MinecraftServer srv) {
         return srv.getWorldPath(LevelResource.ROOT).resolve("glowcube_bedwars_backup.json");
      }

      void save(MinecraftServer srv) {
         try {
            RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, srv.registryAccess());
            JsonObject json = new JsonObject();
            json.addProperty("x", this.pos.x);
            json.addProperty("y", this.pos.y);
            json.addProperty("z", this.pos.z);
            json.addProperty("yaw", this.yaw);
            json.addProperty("pitch", this.pitch);
            json.addProperty("mode", this.mode.getId());
            json.addProperty("health", this.health);
            json.addProperty("food", this.food);
            JsonArray arr = new JsonArray();

            for (ItemStack s : this.items) {
               arr.add((JsonElement)ItemStack.OPTIONAL_CODEC.encodeStart(ops, s).getOrThrow());
            }

            json.add("items", arr);
            Files.writeString(file(srv), json.toString());
         } catch (Exception e) {
            System.err.println("[Bedwars] Inventar-Sicherung fehlgeschlagen: " + e);
         }
      }

      static Backup load(MinecraftServer srv) {
         try {
            Path f = file(srv);
            if (!Files.exists(f)) {
               return null;
            } else {
               RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, srv.registryAccess());
               JsonObject json = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
               List<ItemStack> items = new ArrayList<>();

               for (JsonElement el : json.getAsJsonArray("items")) {
                  items.add((ItemStack)ItemStack.OPTIONAL_CODEC.parse(ops, el).getOrThrow());
               }

               return new Backup(
                  items,
                  new Vec3(json.get("x").getAsDouble(), json.get("y").getAsDouble(), json.get("z").getAsDouble()),
                  json.get("yaw").getAsFloat(),
                  json.get("pitch").getAsFloat(),
                  GameType.byId(json.get("mode").getAsInt()),
                  json.get("health").getAsFloat(),
                  json.get("food").getAsInt()
               );
            }
         } catch (Exception e) {
            System.err.println("[Bedwars] Inventar-Sicherung unlesbar: " + e);
            return null;
         }
      }

      static void delete(MinecraftServer srv) {
         try {
            Files.deleteIfExists(file(srv));
         } catch (Exception ignored) {
         }
      }
   }

   // ====================================================================== Teamzustand

   public record DefenseCell(BlockPos pos, int layer) {
   }

   public static final class TeamState {
      public final BwTeam team;
      public final Arena.Island island;
      public boolean bedAlive;
      public boolean hasPlayer;
      public boolean playerOut;
      public int playerArmorTier;
      public int iron;
      public int gold;
      public int diamond;
      public int emerald;
      public final List<BotProfile> bots = new ArrayList<>();
      public Map<BlockPos, BlockState> bedSnapshot = new HashMap<>();

      // Team-Upgrades (mit Diamanten)
      public boolean sharpness;
      public int protection;
      public int forge;
      public boolean healPool;

      // Gehirn
      public int thinkAt;
      public boolean fortified;
      public boolean cautiousAnnounced;
      public TeamState raidTarget;
      public int raidTargetSince;
      public LivingEntity lastAttacker;
      public int lastAttackedAt;
      public int lastAlarm = -1000;
      public int lastCut = -1000;
      public int lastSortie = -1000;
      public double grudgePlayer;
      public final double[] grudgeAgainst = new double[BwTeam.values().length];
      public TeamBrain.Command command = TeamBrain.Command.NONE;
      public TeamState commandTarget;
      public int commandUntil;
      private List<DefenseCell> defense;
      private List<BlockPos> defenseSpots;

      TeamState(BwTeam team, Arena arena) {
         this.team = team;
         this.island = arena.island(team);
      }

      public Arena.Island island() {
         return this.island;
      }

      public boolean alive() {
         if (this.hasPlayer && !this.playerOut) {
            return true;
         } else {
            for (BotProfile b : this.bots) {
               if (!b.eliminated) {
                  return true;
               }
            }

            return false;
         }
      }

      public int aliveCount() {
         int n = this.hasPlayer && !this.playerOut ? 1 : 0;

         for (BotProfile b : this.bots) {
            if (!b.eliminated) {
               n++;
            }
         }

         return n;
      }

      public double averageGear() {
         int n = 0;
         int sum = 0;

         for (BotProfile b : this.bots) {
            if (!b.eliminated) {
               n++;
               sum += b.gearScore();
            }
         }

         return n == 0 ? 0.0 : (double)sum / n;
      }

      /** Die Verteidigung ums Bett: Schicht 1 Wolle, Schicht 2 Endstein, Schicht 3 Holz. */
      public List<DefenseCell> defensePlan() {
         if (this.defense == null) {
            List<BlockPos> bed = List.of(this.island.bedFoot(), this.island.bedHead());
            List<DefenseCell> cells = new ArrayList<>();
            BlockPos foot = this.island.bedFoot();

            for (int dx = -4; dx <= 4; dx++) {
               for (int dz = -4; dz <= 4; dz++) {
                  for (int dy = 0; dy <= 3; dy++) {
                     BlockPos c = foot.offset(dx, dy, dz);
                     if (!bed.contains(c) && !arena.isMap(c)) {
                        int d = Integer.MAX_VALUE;

                        for (BlockPos b : bed) {
                           d = Math.min(d, Math.abs(c.getX() - b.getX()) + Math.abs(c.getZ() - b.getZ()) + (c.getY() - b.getY()));
                        }

                        if (d >= 1 && d <= 3) {
                           cells.add(new DefenseCell(c, d));
                        }
                     }
                  }
               }
            }

            Vec3 center = this.island.bedCenter();
            cells.sort(Comparator.comparingInt(DefenseCell::layer).thenComparingInt(c -> c.pos().getY()).thenComparingDouble(c -> Vec3.atCenterOf(c.pos()).distanceTo(center)));
            this.defense = cells;
            List<BlockPos> spots = new ArrayList<>();

            for (int dx = -6; dx <= 6; dx++) {
               for (int dz = -6; dz <= 6; dz++) {
                  BlockPos c = foot.offset(dx, 0, dz);
                  int d = Integer.MAX_VALUE;

                  for (BlockPos b : bed) {
                     d = Math.min(d, Math.abs(c.getX() - b.getX()) + Math.abs(c.getZ() - b.getZ()));
                  }

                  if (d == 4 && level.getBlockState(c).isAir() && level.getBlockState(c.above()).isAir() && !level.getBlockState(c.below()).isAir()) {
                     spots.add(c);
                  }
               }
            }

            this.defenseSpots = spots;
         }

         return this.defense;
      }

      public int defenseLayers() {
         return this.gold >= 8 || tick > 2400 ? 3 : 2;
      }

      public BlockPos nearestDefenseSpot(BlockPos cell) {
         this.defensePlan();
         BlockPos best = BlockPos.containing(this.island.spawn());
         double bd = Double.MAX_VALUE;

         for (BlockPos s : this.defenseSpots) {
            double d = s.distSqr(cell);
            if (d < bd) {
               bd = d;
               best = s;
            }
         }

         return best;
      }

      public int missingDefense(int maxLayer) {
         int n = 0;

         for (DefenseCell c : this.defensePlan()) {
            if (c.layer() <= maxLayer && level.getBlockState(c.pos()).canBeReplaced()) {
               n++;
            }
         }

         return n;
      }

      public int fortificationStrength() {
         int s = 0;

         for (DefenseCell c : this.defensePlan()) {
            BlockState st = level.getBlockState(c.pos());
            if (st.is(Blocks.END_STONE)) {
               s += 3;
            } else if (st.is(Blocks.OAK_PLANKS)) {
               s += 2;
            } else if (!st.canBeReplaced()) {
               s++;
            }
         }

         return s;
      }
   }
}
