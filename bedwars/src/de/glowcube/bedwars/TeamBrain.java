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
      HELP,
      SHOP
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
      if (planning && Game.recorder != null && planCtx != null) {
         record(p, planCtx, task);
      }

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
      observeOpponents(t);
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

         // nicht alle bemerken es sofort - und nicht alle rennen los
         int responders = 0;
         int maxResponders = critical ? live.size() : Math.max(1, live.size() - 1);

         for (BotProfile p : live) {
            double d = horizontal(p.body.position(), home);
            boolean raidingFar = p.task == BotProfile.Task.RAID && p.taskTeam != null && horizontal(p.body.position(), p.taskTeam.island().centerVec()) < 14.0;
            boolean notices = RND.nextDouble() < p.hands.awareness * (d < 15.0 ? 1.0 : 0.7);
            if ((d < 30.0 || critical && d < 60.0 && !raidingFar) && notices && responders < maxResponders) {
               if (p.task != BotProfile.Task.HUNT || p.taskEntity == null || !p.taskEntity.isAlive()) {
                  assign(p, BotProfile.Task.DEFEND, null, main, null, 60);
                  responders++;
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

      if (t.command != Command.NONE && Game.tick() < t.commandUntil) {
         applyCommand(t, live);
         return;
      }

      // Einkaufen ist Pflicht: wer zu Hause ist und sich etwas Sinnvolles leisten kann, geht zum Shop
      for (BotProfile p : live) {
         boolean home2 = arena.islandAt(p.body.position(), 1.0) == t.team;
         boolean busy = p.task == BotProfile.Task.SHOP || p.task == BotProfile.Task.FORTIFY && locked(p) || p.body.isFighting();
         if (home2 && !busy && wantsToShop(p) && (!locked(p) || p.task == BotProfile.Task.GATHER || p.task == BotProfile.Task.DEFEND || p.task == BotProfile.Task.IDLE)) {
            BotProfile.Task back = p.task == BotProfile.Task.IDLE ? BotProfile.Task.IDLE : p.task;
            Game.TeamState backTeam = p.taskTeam;
            assign(p, BotProfile.Task.SHOP, backTeam, null, null, 200);
            p.afterShop = back;
         }
      }

      StrategyNet net = t.rulesOnly || Game.forceRules ? null : (t.net != null ? t.net : StrategyNet.builtIn());
      planCtx = context(t, live, intruders, approaching, bedThreat);
      planning = true;

      try {
         if (net != null) {
            planWithNet(t, live, net, planCtx);
         } else if (t.bedAlive) {
            planWithBed(t, live);
         } else {
            planWithoutBed(t, live);
         }
      } finally {
         planning = false;
      }
   }

   // ====================================================================== Strategie-Netz

   private static boolean planning;
   private static Ctx planCtx;

   /** Was das Team ueber die Lage weiss (einmal pro Denkrunde berechnet). */
   static final class Ctx {
      Game.TeamState team;
      int intruders;
      boolean bedThreat;
      int approaching;
      double defenseRatio;
      Game.TeamState raidTarget;
      double raidScore;
      double targetDefense;
      double rushThreat;
      double enemiesNoBed;
      double playerNear;
      LivingEntity huntTarget;
   }

   private static Ctx context(Game.TeamState t, List<BotProfile> live, List<LivingEntity> intruders, List<LivingEntity> approaching, LivingEntity bedThreat) {
      Ctx c = new Ctx();
      c.team = t;
      c.intruders = intruders.size();
      c.bedThreat = bedThreat != null;
      c.approaching = approaching.size();
      if (t.bedAlive) {
         int total = 0;

         for (Game.DefenseCell d : t.defensePlan()) {
            if (d.layer() <= 2) {
               total++;
            }
         }

         c.defenseRatio = total == 0 ? 1.0 : 1.0 - (double)t.missingDefense(2) / total;
      }

      c.raidTarget = chooseRaidTarget(t, live.isEmpty() ? null : live.get(0));
      if (c.raidTarget != null) {
         c.raidScore = raidScore(t, c.raidTarget, null);
         c.targetDefense = c.raidTarget.fortificationStrength() / 60.0;
      }

      int enemies = 0;
      int noBed = 0;

      for (Game.TeamState o : Game.teams()) {
         if (o != t && o.alive()) {
            enemies++;
            if (!o.bedAlive) {
               noBed++;
            }

            c.rushThreat = Math.max(c.rushThreat, t.rushPressure[o.team.ordinal()]);
         }
      }

      c.enemiesNoBed = enemies == 0 ? 0.0 : (double)noBed / enemies;
      Vec3 home = t.island().centerVec();
      for (Game.Human h : Game.humans()) {
         if (Game.isEnemy(t, h.p)) {
            c.playerNear = Math.max(c.playerNear, 1.0 - h.p.position().distanceTo(home) / 50.0);
         }
      }

      // bevorzugtes Jagdziel: Gegner, die nah und schwach sind - oder der Spieler, wenn wir Rache wollen
      double best = Double.MAX_VALUE;

      for (LivingEntity e : Game.enemiesOf(t)) {
         if (!Game.isProtected(e)) {
            double score = e.position().distanceTo(home) + e.getHealth() * 0.8 - (e instanceof ServerPlayer ? t.grudgePlayer * 10.0 : 0.0);
            if (score < best) {
               best = score;
               c.huntTarget = e;
            }
         }
      }

      return c;
   }

   private static int[] counts(Game.TeamState t, BotProfile except) {
      int[] n = new int[StrategyNet.OUT];

      for (BotProfile b : t.bots) {
         if (b != except && b.body != null && b.body.isAlive()) {
            n[planOf(b).ordinal()]++;
         }
      }

      return n;
   }

   private static StrategyNet.Plan planOf(BotProfile b) {
      BotProfile.Task task = b.task == BotProfile.Task.SHOP && b.afterShop != null ? b.afterShop : b.task;
      if (b.task == BotProfile.Task.SHOP && (task == BotProfile.Task.IDLE || task == BotProfile.Task.SHOP)) {
         return StrategyNet.Plan.SHOP;
      }

      return switch (task) {
         case FORTIFY -> StrategyNet.Plan.FORTIFY;
         case DEFEND, CUT_BRIDGE -> StrategyNet.Plan.DEFEND;
         case RAID -> StrategyNet.Plan.RAID;
         case MID -> StrategyNet.Plan.MID;
         case HUNT -> StrategyNet.Plan.HUNT;
         case ESCORT, FOLLOW_PLAYER -> StrategyNet.Plan.ESCORT;
         case REGROUP, RETREAT -> StrategyNet.Plan.REGROUP;
         case SHOP -> StrategyNet.Plan.SHOP;
         default -> StrategyNet.Plan.GATHER;
      };
   }

   /** Die 36 Eingaenge des Netzes aus Sicht eines Bots. */
   static double[] features(BotProfile b, Ctx c, int[] n) {
      Game.TeamState t = b.team;
      double[] x = new double[StrategyNet.IN];
      int i = 0;
      x[i++] = Math.min(1.5, Game.tick() / 18000.0);
      x[i++] = t.bedAlive ? 1 : 0;
      x[i++] = Game.suddenDeath() ? 1 : 0;
      x[i++] = b.body == null ? 1.0 : b.body.getHealth() / 20.0;
      x[i++] = b.gearScore() / 12.0;
      x[i++] = Math.min(2.0, b.blocks() / 64.0);
      x[i++] = Math.min(2.0, t.iron / 64.0);
      x[i++] = Math.min(2.0, t.gold / 24.0);
      x[i++] = Math.min(2.0, t.diamond / 16.0);
      x[i++] = Math.min(2.0, t.emerald / 8.0);
      x[i++] = t.aliveCount() / 4.0;
      x[i++] = Math.min(1.0, c.intruders / 3.0);
      x[i++] = c.bedThreat ? 1 : 0;
      x[i++] = Math.min(1.0, c.approaching / 3.0);
      x[i++] = c.defenseRatio;
      x[i++] = n[StrategyNet.Plan.FORTIFY.ordinal()] / 4.0;
      x[i++] = n[StrategyNet.Plan.DEFEND.ordinal()] / 4.0;
      x[i++] = n[StrategyNet.Plan.RAID.ordinal()] / 4.0;
      x[i++] = n[StrategyNet.Plan.MID.ordinal()] / 4.0;
      x[i++] = n[StrategyNet.Plan.HUNT.ordinal()] / 4.0;
      x[i++] = c.raidTarget == null ? -1.0 : Math.tanh(c.raidScore / 5.0);
      x[i++] = Math.min(1.5, c.targetDefense);
      x[i++] = Math.min(1.0, c.rushThreat);
      x[i++] = c.enemiesNoBed;
      x[i++] = c.playerNear;
      x[i++] = Math.min(1.0, t.grudgePlayer / 3.0);
      x[i++] = b.role == BotProfile.Role.RUSHER ? 1 : 0;
      x[i++] = b.role == BotProfile.Role.BUILDER ? 1 : 0;
      x[i++] = b.role == BotProfile.Role.COLLECTOR ? 1 : 0;
      x[i++] = b.role == BotProfile.Role.FIGHTER ? 1 : 0;
      x[i++] = b.aggression;
      x[i++] = b.caution;
      x[i++] = b.skill;
      x[i++] = b.task == BotProfile.Task.RAID ? 1 : 0;
      x[i++] = b.task == BotProfile.Task.DEFEND ? 1 : 0;
      x[i++] = b.body == null ? 0.0 : Math.min(1.5, b.body.position().distanceTo(t.island().centerVec()) / 50.0);
      return x;
   }

   private static void record(BotProfile p, Ctx c, BotProfile.Task task) {
      if (p.body == null) {
         return;
      }

      BotProfile.Task saved = p.task;
      BotProfile.Task savedAfter = p.afterShop;
      int[] n = counts(p.team, p);
      double[] x = features(p, c, n);
      p.task = task;
      StrategyNet.Plan a = planOf(p);
      p.task = saved;
      p.afterShop = savedAfter;
      StringBuilder sb = new StringBuilder("{\"a\":").append(a.ordinal()).append(",\"x\":[");

      for (int i = 0; i < x.length; i++) {
         if (i > 0) {
            sb.append(',');
         }

         sb.append(String.format(java.util.Locale.ROOT, "%.4f", x[i]));
      }

      Game.writeRecord(sb.append("]}").toString());
   }

   private static boolean valid(StrategyNet.Plan a, BotProfile b, Ctx c) {
      Game.TeamState t = b.team;
      return switch (a) {
         case SHOP -> wantsToShop(b);
         case FORTIFY -> t.bedAlive && t.missingDefense(t.defenseLayers()) > 0;
         case DEFEND -> true;
         case RAID -> c.raidTarget != null;
         case MID -> true;
         case HUNT -> c.huntTarget != null;
         case ESCORT -> escortLeader(b) != null;
         default -> true;
      };
   }

   private static BotProfile escortLeader(BotProfile b) {
      BotProfile best = null;
      double bd = Double.MAX_VALUE;

      for (BotProfile o : b.team.bots) {
         if (o != b && o.body != null && o.body.isAlive() && (o.task == BotProfile.Task.RAID || o.task == BotProfile.Task.HUNT)) {
            double d = o.body.distanceTo(b.body);
            if (d < bd) {
               bd = d;
               best = o;
            }
         }
      }

      return best;
   }

   /**
    * Verteilt die freien Bots mit Hilfe des Netzes: wer die eindeutigste Wahl hat, bekommt sie zuerst,
    * danach wird fuer die uebrigen neu bewertet - so lernt das Netz, Aufgaben sinnvoll aufzuteilen.
    */
   private static void planWithNet(Game.TeamState t, List<BotProfile> live, StrategyNet net, Ctx c) {
      List<BotProfile> free = new ArrayList<>();

      for (BotProfile p : live) {
         if (!locked(p) || p.task == BotProfile.Task.IDLE) {
            free.add(p);
         }
      }

      while (!free.isEmpty()) {
         BotProfile pick = null;
         StrategyNet.Plan pickPlan = null;
         double pickScore = -1.0E9;

         for (BotProfile b : free) {
            double[] u = net.forward(features(b, c, counts(t, b)));
            for (StrategyNet.Plan a : StrategyNet.Plan.values()) {
               // ein wenig Zufall: Menschen entscheiden auch nicht immer gleich
               double score = u[a.ordinal()] + RND.nextGaussian() * 0.15;
               if (score > pickScore && valid(a, b, c)) {
                  pickScore = score;
                  pick = b;
                  pickPlan = a;
               }
            }
         }

         if (pick == null) {
            break;
         }

         free.remove(pick);
         execute(pick, pickPlan, c);
      }
   }

   private static void execute(BotProfile b, StrategyNet.Plan a, Ctx c) {
      Game.TeamState t = b.team;
      switch (a) {
         case GATHER -> assign(b, BotProfile.Task.GATHER, null, null, null, 200 + RND.nextInt(200));
         case SHOP -> {
            assign(b, BotProfile.Task.SHOP, null, null, null, 300);
            b.afterShop = BotProfile.Task.IDLE;
         }
         case FORTIFY -> {
            if (b.blocks() >= 8) {
               assign(b, BotProfile.Task.FORTIFY, null, null, null, 500);
            } else {
               assign(b, BotProfile.Task.SHOP, null, null, null, 300);
               b.afterShop = BotProfile.Task.FORTIFY;
            }
         }
         case DEFEND -> assign(b, BotProfile.Task.DEFEND, null, null, null, 600);
         case RAID -> {
            Game.TeamState target = c.raidTarget;
            if (b.blocks() >= 28) {
               assign(b, BotProfile.Task.RAID, target, null, null, 1400);
               if (RND.nextInt(3) == 0) {
                  BotChat.event(b, BotChat.Event.GOING_RAID, target);
               }
            } else {
               assign(b, BotProfile.Task.SHOP, target, null, null, 300);
               b.afterShop = BotProfile.Task.RAID;
            }
         }
         case MID -> {
            if (b.blocks() >= 16) {
               assign(b, BotProfile.Task.MID, null, null, null, 700);
            } else {
               assign(b, BotProfile.Task.SHOP, null, null, null, 300);
               b.afterShop = BotProfile.Task.MID;
            }
         }
         case HUNT -> {
            assign(b, BotProfile.Task.HUNT, null, c.huntTarget, null, 400);
            if (c.huntTarget instanceof ServerPlayer sp && RND.nextInt(3) == 0) {
               BotChat.event(b, BotChat.Event.HUNT_PLAYER, sp);
            }
         }
         case ESCORT -> {
            BotProfile lead = escortLeader(b);
            assign(b, BotProfile.Task.ESCORT, null, lead == null ? null : lead.body, null, 600);
         }
         case REGROUP -> assign(b, BotProfile.Task.REGROUP, null, null, null, 200);
      }
   }

   /** Erklaert in einem Satz, was das Team gerade vorhat (fuer die Frage nach der Strategie). */
   public static String explain(Game.TeamState t) {
      int[] n = counts(t, null);
      StringBuilder sb = new StringBuilder();
      if (n[StrategyNet.Plan.RAID.ordinal()] > 0 && t.raidTarget != null) {
         sb.append(n[StrategyNet.Plan.RAID.ordinal()]).append(" von uns greifen ").append(t.raidTarget.team.label.toLowerCase()).append(" an");
      }

      if (n[StrategyNet.Plan.DEFEND.ordinal()] + n[StrategyNet.Plan.FORTIFY.ordinal()] > 0) {
         sb.append(sb.length() > 0 ? ", " : "").append(n[StrategyNet.Plan.DEFEND.ordinal()] + n[StrategyNet.Plan.FORTIFY.ordinal()]).append(" bleiben am bett");
      }

      if (n[StrategyNet.Plan.MID.ordinal()] > 0) {
         sb.append(sb.length() > 0 ? ", " : "").append("einer holt dias");
      }

      for (Game.TeamState o : Game.teams()) {
         String d = t.detected[o.team.ordinal()];
         if (o != t && o.alive() && d != null && !d.equals("normal")) {
            sb.append(sb.length() > 0 ? ". " : "").append(o.team.label.toLowerCase()).append(switch (d) {
               case "rush" -> " rusht viel, aufpassen";
               case "eco" -> " farmt mitte";
               default -> " bunkert sich ein";
            });
            break;
         }
      }

      if (!t.bedAlive) {
         sb.append(sb.length() > 0 ? ". " : "").append("ohne bett spielen wir vorsichtig");
      }

      return sb.length() == 0 ? "erstmal ressourcen sammeln und ausruesten" : sb.toString();
   }

   // ====================================================================== Gegner-Modell

   /** Beobachtet die anderen Teams und erkennt ihre Strategie: Rusher, Bunker, Wirtschaft oder ausgeglichen. */
   private static void observeOpponents(Game.TeamState t) {
      Arena arena = Game.arena();
      Vec3 home = t.island().centerVec();

      for (Game.TeamState o : Game.teams()) {
         if (o == t) {
            continue;
         }

         int k = o.team.ordinal();
         int alive = 0;
         int away = 0;
         int mid = 0;
         int atUs = 0;

         for (BotProfile b : o.bots) {
            if (b.body != null && b.body.isAlive()) {
               alive++;
               Vec3 pos = b.body.position();
               if (arena.islandAt(pos, 3.0) != o.team) {
                  away++;
               }

               if (pos.distanceTo(Vec3.atCenterOf(arena.center)) < Arena.MID_RADIUS + 4) {
                  mid++;
               }

               if (horizontal(pos, home) < Arena.ISLAND_RADIUS + 12) {
                  atUs++;
               }
            }
         }

         for (Game.Human h : o.humans) {
            if (Game.isEnemy(t, h.p) && horizontal(h.p.position(), home) < Arena.ISLAND_RADIUS + 12) {
               atUs++;
            }
         }

         t.rushPressure[k] = t.rushPressure[k] * 0.97 + atUs * 0.08;
         if (alive > 0) {
            t.awayShare[k] = t.awayShare[k] * 0.9 + (double)away / alive * 0.1;
            t.midShare[k] = t.midShare[k] * 0.9 + (double)mid / alive * 0.1;
         }

         String label;
         if (t.rushPressure[k] > 0.5 || t.awayShare[k] > 0.65) {
            label = "rush";
         } else if (t.midShare[k] > 0.35) {
            label = "eco";
         } else if (o.fortificationStrength() > 40 && t.awayShare[k] < 0.3) {
            label = "bunker";
         } else {
            label = "normal";
         }

         if (!label.equals(t.detected[k]) && o.alive() && Game.tick() > 1200) {
            t.detected[k] = label;
            if (!label.equals("normal")) {
               BotChat.strategySpotted(t, o, label);
            }
         }
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
      ServerPlayer best = null;
      double bd = range;
      for (Game.Human h : Game.humans()) {
         if (Game.isEnemy(t, h.p) && !Game.isProtected(h.p) && p.body.distanceTo(h.p) < bd) {
            bd = p.body.distanceTo(h.p);
            best = h.p;
         }
      }

      return best;
   }

   private static void planWithBed(Game.TeamState t, List<BotProfile> live) {
      int tick = Game.tick();
      int members = live.size() + t.humansInGame();
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

   public static void command(Game.TeamState t, Command c, Game.TeamState target, int seconds, ServerPlayer by) {
      t.command = c;
      t.commandBy = by;
      t.commandTarget = target;
      t.commandUntil = Game.tick() + seconds * 20;

      for (BotProfile p : t.bots) {
         p.taskLockUntil = 0;
      }

      requestThink(t);
   }

   private static void applyCommand(Game.TeamState t, List<BotProfile> live) {
      ServerPlayer pl = t.commandBy != null && Game.isHuman(t.commandBy) ? t.commandBy : Game.humanOf(t);
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
         case SHOP -> {
            for (BotProfile p : live) {
               if (p.task != BotProfile.Task.SHOP && wantsToShop(p)) {
                  assign(p, BotProfile.Task.SHOP, null, null, null, 200);
                  p.afterShop = BotProfile.Task.IDLE;
               }
            }

            t.command = Command.NONE;
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
