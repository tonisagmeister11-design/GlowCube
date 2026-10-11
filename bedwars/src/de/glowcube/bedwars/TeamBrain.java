package de.glowcube.bedwars;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Der Stratege eines Teams. Alle zwei Sekunden (und sofort bei Alarm) schaut er sich die Lage an
 * und verteilt Auftraege: Bett zubauen, verteidigen, sammeln, Mitte halten, zu zweit angreifen,
 * jagen, Bruecken abreissen. Ohne Bett spielt das Team vorsichtig: erst ausruesten, dann nur
 * gemeinsam und nur gegen schwaechere Gegner.
 */
public final class TeamBrain {
   private static final Random RND = new Random();

   public enum Command {
      NONE,
      DEFEND,
      ATTACK,
      FOLLOW,
      MID,
      FORTIFY,
      HELP
   }

   private TeamBrain() {
   }

   public static void requestThink(Game.TeamState t) {
      t.thinkAt = Math.min(t.thinkAt, Game.tick() + 2);
   }

   public static boolean canAfford(Game.TeamState t, int iron) {
      return t.iron >= iron;
   }

   /** Lohnt sich der Weg zum Shop gerade? */
   public static boolean wantsToShop(BotProfile p) {
      Game.TeamState t = p.team;
      return p.swordTier < 1 && t.iron >= 10
         || p.armorTier < 1 && t.iron >= 40 && p.swordTier >= 1
         || p.swordTier < 2 && t.gold >= 7
         || p.armorTier < 2 && t.gold >= 12
         || p.swordTier < 3 && t.emerald >= 4
         || p.armorTier < 3 && t.emerald >= 6
         || p.blocks() < 8 && t.iron >= 4
         || p.gapples == 0 && t.gold >= 5 && p.armorTier >= 1;
   }

   public static BlockPos pickMidSpot(BotProfile p) {
      Arena a = Game.arena();
      Game.TeamState t = p.team;
      if (Game.tick() > 1800 && (t.emerald < 4 || RND.nextInt(3) == 0)) {
         return a.emeraldGens.get(RND.nextInt(a.emeraldGens.size())).below();
      } else {
         BlockPos home = t.island().origin;
         return a.diamondGens.stream().min(Comparator.comparingDouble(g -> g.distSqr(home) + RND.nextInt(200))).orElse(a.center.above());
      }
   }

   public static void onMemberHurt(BotProfile p, LivingEntity attacker) {
      Game.TeamState t = p.team;
      if (Game.isEnemy(t, attacker)) {
         t.lastAttacker = attacker;
         t.lastAttackedAt = Game.tick();
         if (attacker instanceof ServerPlayer) {
            t.grudgePlayer += 0.15;
         }

         // Teamkollegen in der Naehe helfen mit
         if (p.body != null) {
            for (BotProfile mate : t.bots) {
               if (mate != p && mate.body != null && mate.body.isAlive() && mate.body.distanceTo(p.body) < 14.0) {
                  BotProfile.Task task = mate.task;
                  if (task == BotProfile.Task.GATHER || task == BotProfile.Task.IDLE || task == BotProfile.Task.FORTIFY || task == BotProfile.Task.DEFEND) {
                     assign(mate, BotProfile.Task.HUNT, null, attacker, null, 160);
                  }
               }
            }
         }
      }
   }

   private static void assign(BotProfile p, BotProfile.Task task, Game.TeamState team, LivingEntity entity, BlockPos pos, int lockTicks) {
      boolean changed = p.task != task || p.taskTeam != team || p.taskEntity != entity;
      p.task = task;
      p.taskTeam = team;
      p.taskEntity = entity;
      p.taskPos = pos;
      p.taskLockUntil = Game.tick() + lockTicks;
      if (changed) {
         p.taskSince = Game.tick();
      }
   }

   private static boolean locked(BotProfile p) {
      return p.task != BotProfile.Task.IDLE && Game.tick() < p.taskLockUntil;
   }

   // ====================================================================== Denken

   public static void tick(Game.TeamState t) {
      if (t.alive() && Game.tick() >= t.thinkAt) {
         t.thinkAt = Game.tick() + 40;
         think(t);
      }
   }

   /** Diamanten in Team-Upgrades stecken - in einer sinnvollen Reihenfolge. */
   private static void buyUpgrades(Game.TeamState t, List<BotProfile> live) {
      if (live.isEmpty()) {
         return;
      }

      BotProfile buyer = live.get(RND.nextInt(live.size()));
      if (t.forge == 0 && t.diamond >= 4) {
         t.diamond -= 4;
         t.forge = 1;
         Game.upgradeBought(t, "Eisenschmiede I", buyer, null);
      } else if (!t.sharpness && t.diamond >= 8) {
         t.diamond -= 8;
         t.sharpness = true;
         Game.upgradeBought(t, "Geschaerfte Klingen", buyer, null);
      } else if (t.protection < 1 && t.diamond >= 5) {
         t.diamond -= 5;
         t.protection = 1;
         Game.upgradeBought(t, "Schutz I", buyer, null);
      } else if (!t.healPool && t.diamond >= 3) {
         t.diamond -= 3;
         t.healPool = true;
         Game.upgradeBought(t, "Heilfeld", buyer, null);
      } else if (t.protection < 2 && t.diamond >= 10) {
         t.diamond -= 10;
         t.protection = 2;
         Game.upgradeBought(t, "Schutz II", buyer, null);
      } else if (t.forge < 2 && t.diamond >= 8) {
         t.diamond -= 8;
         t.forge = 2;
         Game.upgradeBought(t, "Eisenschmiede II", buyer, null);
      } else if (t.protection < 3 && t.diamond >= 20) {
         t.diamond -= 20;
         t.protection = 3;
         Game.upgradeBought(t, "Schutz III", buyer, null);
      }
   }

   private static void think(Game.TeamState t) {
      List<BotProfile> live = new ArrayList<>();

      for (BotProfile p : t.bots) {
         if (p.body != null && p.body.isAlive()) {
            live.add(p);
         }
      }

      if (live.isEmpty()) {
         return;
      }

      buyUpgrades(t, live);
      Arena arena = Game.arena();
      Arena.Island is = t.island();
      Vec3 home = is.centerVec();
      Vec3 bed = is.bedCenter();
      List<LivingEntity> intruders = new ArrayList<>();
      List<LivingEntity> approaching = new ArrayList<>();
      LivingEntity bedThreat = null;

      for (LivingEntity e : Game.enemiesOf(t)) {
         double dHome = horizontal(e.position(), home);
         if (dHome < Arena.ISLAND_RADIUS + 7 && e.getY() > Arena.Y - 8) {
            intruders.add(e);
            if (t.bedAlive && e.position().distanceTo(bed) < 7.0) {
               bedThreat = e;
            }
         } else if (dHome < Arena.ISLAND_RADIUS + 24 && arena.islandAt(e.position(), 2.0) == null && e.getY() > Arena.Y - 6) {
            approaching.add(e);
         }
      }

      // 1) Alarm: Gegner auf der Insel
      if (!intruders.isEmpty()) {
         LivingEntity main = bedThreat != null ? bedThreat : intruders.stream().min(Comparator.comparingDouble(e -> e.position().distanceTo(bed))).get();
         boolean critical = bedThreat != null;

         for (BotProfile p : live) {
            double d = horizontal(p.body.position(), home);
            boolean raidingFar = p.task == BotProfile.Task.RAID && p.taskTeam != null && horizontal(p.body.position(), p.taskTeam.island().centerVec()) < 14.0;
            if (d < 30.0 || critical && d < 60.0 && !raidingFar) {
               if (p.task != BotProfile.Task.HUNT || p.taskEntity == null || !p.taskEntity.isAlive()) {
                  assign(p, BotProfile.Task.DEFEND, null, main, null, 60);
               }
            }
         }

         if (Game.tick() - t.lastAlarm > 300) {
            t.lastAlarm = Game.tick();
            BotProfile caller = nearest(live, main.position());
            if (caller != null) {
               BotChat.alarm(caller, main, critical);
            }
         }

         return;
      }

      // 2) Gegner bauen eine Bruecke zu uns: abreissen
      if (!approaching.isEmpty() && Game.tick() - t.lastCut > 60) {
         LivingEntity e = approaching.get(0);
         BlockPos cut = findBridgeBlock(t, e);
         if (cut != null) {
            BotProfile cutter = null;
            double best = Double.MAX_VALUE;

            for (BotProfile p : live) {
               double d = horizontal(p.body.position(), home);
               if (d < Arena.ISLAND_RADIUS + 2 && (p.task == BotProfile.Task.DEFEND || p.task == BotProfile.Task.GATHER || p.task == BotProfile.Task.IDLE || !t.bedAlive)) {
                  double dd = p.body.position().distanceTo(Vec3.atCenterOf(cut));
                  if (dd < best) {
                     best = dd;
                     cutter = p;
                  }
               }
            }

            if (cutter != null) {
               t.lastCut = Game.tick();
               assign(cutter, BotProfile.Task.CUT_BRIDGE, null, null, cut, 80);
               if (RND.nextInt(3) == 0) {
                  BotChat.event(cutter, BotChat.Event.CUT_BRIDGE, e);
               }
            }
         }
      }

      if (t.bedAlive) {
         planWithBed(t, live);
      } else {
         planWithoutBed(t, live);
      }
   }

   private static double horizontal(Vec3 a, Vec3 b) {
      double dx = a.x - b.x;
      double dz = a.z - b.z;
      return Math.sqrt(dx * dx + dz * dz);
   }

   private static BotProfile nearest(List<BotProfile> list, Vec3 pos) {
      BotProfile best = null;
      double bd = Double.MAX_VALUE;

      for (BotProfile p : list) {
         double d = p.body.position().distanceTo(pos);
         if (d < bd) {
            bd = d;
            best = p;
         }
      }

      return best;
   }

   /** Sucht den Block der gegnerischen Bruecke, der am naechsten an unserer Insel haengt. */
   private static BlockPos findBridgeBlock(Game.TeamState t, LivingEntity enemy) {
      Arena arena = Game.arena();
      ServerLevel level = arena.level;
      Vec3 home = t.island().centerVec();
      Vec3 dir = enemy.position().subtract(home).multiply(1.0, 0.0, 1.0);
      double len = dir.length();
      if (len < 1.0) {
         return null;
      } else {
         dir = dir.scale(1.0 / len);
         BlockPos best = null;
         double bestD = Double.MAX_VALUE;

         for (double s = Arena.ISLAND_RADIUS - 1; s < Math.min(len, Arena.ISLAND_RADIUS + 7); s += 0.5) {
            Vec3 p = home.add(dir.scale(s));

            for (int dy = -2; dy <= 1; dy++) {
               for (int ox = -1; ox <= 1; ox++) {
                  for (int oz = -1; oz <= 1; oz++) {
                     BlockPos b = BlockPos.containing(p.x + ox, Arena.Y + dy, p.z + oz);
                     BlockState st = level.getBlockState(b);
                     if (!st.isAir() && !arena.isMap(b) && st.getFluidState().isEmpty() && level.getBlockState(b.below()).isAir()) {
                        double d = horizontal(Vec3.atCenterOf(b), home);
                        if (d > Arena.ISLAND_RADIUS - 0.5 && d < bestD) {
                           bestD = d;
                           best = b;
                        }
                     }
                  }
               }
            }
         }

         return best;
      }
   }

   private static double raidScore(Game.TeamState us, Game.TeamState them, BotProfile leader) {
      double score = 0.0;
      int dist = Math.abs(us.team.ordinal() - them.team.ordinal());
      dist = Math.min(dist, BwTeam.values().length - dist);
      score += dist == 1 ? 3.0 : 1.0;
      score -= them.fortificationStrength() * 0.08;
      score += (4 - them.aliveCount()) * 1.2;
      score += us.grudgeAgainst[them.team.ordinal()];
      if (them.hasPlayer) {
         score += 0.8 + us.grudgePlayer * 2.0 + (leader != null ? leader.aggression : 0.0);
      }

      return score + RND.nextDouble() * 1.5;
   }

   private static Game.TeamState chooseRaidTarget(Game.TeamState t, BotProfile leader) {
      if (t.command == Command.ATTACK && t.commandTarget != null && t.commandTarget.alive() && t.commandTarget.bedAlive) {
         return t.commandTarget;
      } else if (t.raidTarget != null && t.raidTarget.bedAlive && t.raidTarget.alive() && Game.tick() - t.raidTargetSince < 1200) {
         return t.raidTarget;
      } else {
         Game.TeamState best = null;
         double bestScore = -1.0E9;

         for (Game.TeamState o : Game.teams()) {
            if (o != t && o.alive() && o.bedAlive) {
               double s = raidScore(t, o, leader);
               if (s > bestScore) {
                  bestScore = s;
                  best = o;
               }
            }
         }

         if (best != t.raidTarget) {
            t.raidTarget = best;
            t.raidTargetSince = Game.tick();
         }

         return best;
      }
   }

   private static ServerPlayer nearbyPlayerTarget(Game.TeamState t, BotProfile p, double range) {
      ServerPlayer pl = Game.player();
      if (pl != null && pl.isAlive() && !pl.isSpectator() && Game.isEnemy(t, pl) && !Game.isProtected(pl)) {
         return p.body.distanceTo(pl) < range ? pl : null;
      } else {
         return null;
      }
   }

   private static void planWithBed(Game.TeamState t, List<BotProfile> live) {
      int tick = Game.tick();
      int members = live.size() + (t.hasPlayer && !t.playerOut ? 1 : 0);
      boolean commanded = t.command != Command.NONE && tick < t.commandUntil;
      List<BotProfile> free = new ArrayList<>();

      for (BotProfile p : live) {
         if (!locked(p) || p.task == BotProfile.Task.IDLE) {
            free.add(p);
         }
      }

      if (commanded) {
         applyCommand(t, live);
         return;
      }

      // Bett zubauen: genau einer, am liebsten ein Bauer
      boolean fortifying = live.stream().anyMatch(p -> p.task == BotProfile.Task.FORTIFY || p.task == BotProfile.Task.SHOP && p.afterShop == BotProfile.Task.FORTIFY);
      if (!t.fortified && !fortifying && !free.isEmpty()) {
         BotProfile b = free.stream().max(Comparator.comparingDouble(p -> (p.role == BotProfile.Role.BUILDER ? 2.0 : 0.0) + p.caution)).get();
         assign(b, b.blocks() >= 8 ? BotProfile.Task.FORTIFY : BotProfile.Task.SHOP, null, null, null, 400);
         if (b.task == BotProfile.Task.SHOP) {
            b.afterShop = BotProfile.Task.FORTIFY;
         }

         free.remove(b);
      }

      // ein Verteidiger, sobald genug Leute da sind
      boolean hasDefender = live.stream().anyMatch(p -> p.task == BotProfile.Task.DEFEND);
      if (!hasDefender && members >= 3 && tick > 600 && !free.isEmpty()) {
         BotProfile d = free.stream().max(Comparator.comparingDouble(p -> p.caution + (p.role == BotProfile.Role.BUILDER ? 0.5 : 0.0))).get();
         assign(d, BotProfile.Task.DEFEND, null, null, null, 900);
         free.remove(d);
      }

      // Mitte: Diamanten und Smaragde
      boolean hasMid = live.stream().anyMatch(p -> p.task == BotProfile.Task.MID);
      if (!hasMid && tick > 700 && !free.isEmpty() && (t.diamond < 6 || t.emerald < 4)) {
         BotProfile m = free.stream()
            .max(Comparator.comparingDouble(p -> (p.role == BotProfile.Role.COLLECTOR ? 2.0 : 0.0) + p.gearScore() * 0.2 + RND.nextDouble()))
            .get();
         if (m.blocks() >= 16 || t.iron >= 4) {
            assign(m, m.blocks() >= 16 ? BotProfile.Task.MID : BotProfile.Task.SHOP, null, null, null, 700);
            if (m.task == BotProfile.Task.SHOP) {
               m.afterShop = BotProfile.Task.MID;
            }

            free.remove(m);
         }
      }

      // Jagd auf den Spieler, wenn er in der Naehe ist (oder wir eine Rechnung offen haben)
      for (BotProfile p : new ArrayList<>(free)) {
         ServerPlayer pl = nearbyPlayerTarget(t, p, 18.0 + t.grudgePlayer * 15.0);
         if (pl != null && RND.nextDouble() < 0.25 + p.aggression * 0.4 + t.grudgePlayer * 0.3) {
            if (live.stream().noneMatch(o -> o.task == BotProfile.Task.HUNT && o.taskEntity == pl)) {
               assign(p, BotProfile.Task.HUNT, null, pl, null, 300);
               free.remove(p);
               if (RND.nextInt(3) == 0) {
                  BotChat.event(p, BotChat.Event.HUNT_PLAYER, pl);
               }
               break;
            }
         }
      }

      // Angriff: Stuermer zuerst, dann zu zweit
      Game.TeamState target = chooseRaidTarget(t, free.isEmpty() ? null : free.get(0));
      long raiders = live.stream().filter(p -> p.task == BotProfile.Task.RAID).count();
      for (BotProfile p : free) {
         boolean early = tick < 1200;
         boolean ready = p.swordTier >= 1 || tick > 2400 || p.role == BotProfile.Role.RUSHER || p.aggression > 0.75;
         if (target != null && (raiders < 2 || members <= 2) && ready && (!early || p.role == BotProfile.Role.RUSHER || p.aggression > 0.7)) {
            if (p.blocks() >= 28) {
               assign(p, BotProfile.Task.RAID, target, null, null, 1400);
               raiders++;
               if (RND.nextInt(2) == 0) {
                  BotChat.event(p, BotChat.Event.GOING_RAID, target);
               }

               // Begleitschutz
               for (BotProfile q : free) {
                  if (q != p && q.task == BotProfile.Task.IDLE || q != p && q.task == BotProfile.Task.GATHER && !locked(q)) {
                     if (live.size() >= 3 && RND.nextDouble() < 0.6) {
                        assign(q, BotProfile.Task.ESCORT, null, p.body, null, 900);
                     }
                     break;
                  }
               }
            } else if (t.iron >= 4) {
               assign(p, BotProfile.Task.SHOP, null, null, null, 300);
               p.afterShop = BotProfile.Task.RAID;
               p.taskTeam = target;
            } else {
               assign(p, BotProfile.Task.GATHER, null, null, null, 300);
            }
         } else if (p.task == BotProfile.Task.IDLE || !locked(p)) {
            if (wantsToShop(p)) {
               assign(p, BotProfile.Task.SHOP, null, null, null, 300);
               p.afterShop = BotProfile.Task.GATHER;
            } else {
               assign(p, BotProfile.Task.GATHER, null, null, null, 200 + RND.nextInt(200));
            }
         }
      }

      fixShopFollowUps(live);
   }

   /** Nach dem Einkaufen fuer einen Angriff muss das Ziel wieder gesetzt sein. */
   private static void fixShopFollowUps(List<BotProfile> live) {
      for (BotProfile p : live) {
         if (p.task == BotProfile.Task.RAID && p.taskTeam == null) {
            p.taskTeam = chooseRaidTarget(p.team, p);
            if (p.taskTeam == null) {
               p.task = BotProfile.Task.IDLE;
            }
         }
      }
   }

   private static void planWithoutBed(Game.TeamState t, List<BotProfile> live) {
      int tick = Game.tick();
      if (!t.cautiousAnnounced) {
         t.cautiousAnnounced = true;

         for (BotProfile p : live) {
            // Wer weit weg und schlecht ausgeruestet ist, kommt erst einmal heim
            if (p.task == BotProfile.Task.RAID || p.task == BotProfile.Task.MID || p.task == BotProfile.Task.FORTIFY) {
               if (p.gearScore() < 4 || p.body.getHealth() < 14.0F) {
                  assign(p, BotProfile.Task.REGROUP, null, null, null, 200);
               }
            }
         }

         BotProfile speaker = live.get(RND.nextInt(live.size()));
         BotChat.event(speaker, BotChat.Event.PLAY_SAFE, null);
      }

      if (t.command != Command.NONE && tick < t.commandUntil) {
         applyCommand(t, live);
         return;
      }

      // Erst ausruesten
      for (BotProfile p : live) {
         if (!locked(p) && wantsToShop(p)) {
            assign(p, BotProfile.Task.SHOP, null, null, null, 300);
            p.afterShop = BotProfile.Task.REGROUP;
         }
      }

      int avgGear = 0;

      for (BotProfile p : live) {
         avgGear += p.gearScore();
      }

      avgGear /= live.size();
      // Nur gemeinsam losziehen - gegen ein Team ohne Bett, das schwaecher ist
      Game.TeamState prey = null;
      double preyScore = -1.0E9;

      for (Game.TeamState o : Game.teams()) {
         if (o != t && o.alive()) {
            double s = -o.aliveCount() * 2.0 - o.averageGear() + (o.bedAlive ? -3.0 : 2.0) + t.grudgeAgainst[o.team.ordinal()];
            if (o.hasPlayer) {
               s += t.grudgePlayer * 2.0;
            }

            if (s > preyScore) {
               preyScore = s;
               prey = o;
            }
         }
      }

      boolean strongEnough = avgGear >= 4 && live.size() >= 2 || avgGear >= 7 || Game.suddenDeath();
      if (prey != null && strongEnough && tick - t.lastSortie > (Game.suddenDeath() ? 200 : 600)) {
         t.lastSortie = tick;
         List<BotProfile> squad = live.stream()
            .filter(p -> !locked(p) || p.task == BotProfile.Task.REGROUP || p.task == BotProfile.Task.GATHER || p.task == BotProfile.Task.DEFEND || Game.suddenDeath())
            .toList();
         if (squad.size() >= Math.min(2, live.size())) {
            BotProfile lead = squad.stream().max(Comparator.comparingDouble(p -> p.gearScore() + p.blocks() * 0.05)).get();
            if (prey.bedAlive && lead.blocks() >= 14) {
               assign(lead, BotProfile.Task.RAID, prey, null, null, 1400);
            } else {
               LivingEntity victim = Game.nearestMember(prey, lead.body.position());
               if (victim != null) {
                  assign(lead, BotProfile.Task.HUNT, null, victim, null, 900);
               }
            }

            for (BotProfile q : squad) {
               if (q != lead) {
                  assign(q, BotProfile.Task.ESCORT, null, lead.body, null, 900);
               }
            }

            BotChat.event(lead, BotChat.Event.SORTIE, prey);
            return;
         }
      }

      // Sonst: zu Hause bleiben, sammeln, Insel halten
      boolean someoneGuards = false;

      for (BotProfile p : live) {
         if (!locked(p) || p.task == BotProfile.Task.IDLE) {
            if (!someoneGuards) {
               assign(p, BotProfile.Task.DEFEND, null, null, null, 400);
               someoneGuards = true;
            } else {
               assign(p, BotProfile.Task.GATHER, null, null, null, 300);
            }
         } else if (p.task == BotProfile.Task.DEFEND) {
            someoneGuards = true;
         }
      }
   }

   // ====================================================================== Befehle des Spielers

   public static void command(Game.TeamState t, Command c, Game.TeamState target, int seconds) {
      t.command = c;
      t.commandTarget = target;
      t.commandUntil = Game.tick() + seconds * 20;

      for (BotProfile p : t.bots) {
         p.taskLockUntil = 0;
      }

      requestThink(t);
   }

   private static void applyCommand(Game.TeamState t, List<BotProfile> live) {
      ServerPlayer pl = Game.player();
      switch (t.command) {
         case DEFEND -> {
            for (BotProfile p : live) {
               if (p.task != BotProfile.Task.DEFEND) {
                  assign(p, BotProfile.Task.DEFEND, null, null, null, 200);
               }
            }
         }
         case FORTIFY -> {
            for (int i = 0; i < live.size(); i++) {
               BotProfile p = live.get(i);
               if (i == 0) {
                  t.fortified = false;
                  if (p.task != BotProfile.Task.FORTIFY) {
                     assign(p, p.blocks() > 0 ? BotProfile.Task.FORTIFY : BotProfile.Task.SHOP, null, null, null, 400);
                     p.afterShop = BotProfile.Task.FORTIFY;
                  }
               } else if (p.task != BotProfile.Task.DEFEND) {
                  assign(p, BotProfile.Task.DEFEND, null, null, null, 200);
               }
            }
         }
         case FOLLOW, HELP -> {
            int n = 0;

            for (BotProfile p : live) {
               if (pl != null && pl.isAlive() && !pl.isSpectator() && n < (t.command == Command.HELP ? 3 : 2)) {
                  if (p.task != BotProfile.Task.FOLLOW_PLAYER) {
                     assign(p, BotProfile.Task.FOLLOW_PLAYER, null, pl, null, 200);
                  }

                  n++;
               }
            }
         }
         case MID -> {
            for (int i = 0; i < live.size(); i++) {
               BotProfile p = live.get(i);
               if (i < 2 && p.task != BotProfile.Task.MID) {
                  assign(p, p.blocks() >= 12 ? BotProfile.Task.MID : BotProfile.Task.SHOP, null, null, null, 400);
                  p.afterShop = BotProfile.Task.MID;
               }
            }
         }
         case ATTACK -> {
            Game.TeamState target = t.commandTarget != null && t.commandTarget.alive() ? t.commandTarget : chooseRaidTarget(t, null);
            if (target == null) {
               t.command = Command.NONE;
               return;
            }

            for (int i = 0; i < live.size(); i++) {
               BotProfile p = live.get(i);
               if (i < 2 || live.size() <= 2) {
                  if (p.task != BotProfile.Task.RAID || p.taskTeam != target) {
                     if (target.bedAlive) {
                        assign(p, p.blocks() >= 12 ? BotProfile.Task.RAID : BotProfile.Task.SHOP, target, null, null, 600);
                        p.afterShop = BotProfile.Task.RAID;
                     } else {
                        LivingEntity v = Game.nearestMember(target, p.body.position());
                        if (v != null) {
                           assign(p, BotProfile.Task.HUNT, null, v, null, 600);
                        }
                     }
                  }
               } else if (p.task != BotProfile.Task.DEFEND && t.bedAlive) {
                  assign(p, BotProfile.Task.DEFEND, null, null, null, 300);
               }
            }
         }
         default -> {
         }
      }
   }
}
