package de.glowcube.bedwars;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Der Koerper eines Bots: ein Spielermodell (Steve oder Alex) mit eigener Steuerung.
 * Er laeuft ueber den A*-Pfad, baut Bruecken, baut hoch, baut Verteidigungen ab,
 * kaempft mit Strafen, Krits und W-Tap, isst Goldaepfel und rettet sich mit Clutch-Bloecken.
 */
public final class BedwarsBot extends Mannequin {
   private static final Random RND = new Random();
   private static final float DEG = (float)(Math.PI / 180.0);
   private static EntityDataAccessor<Optional<Component>> descriptionAccessor;
   private static boolean descriptionLookedUp;

   public BotProfile profile;
   private int ticks;

   // Absicht fuer diesen Tick
   private Vec3 moveDir;
   private boolean sprint;
   private boolean jump;
   private boolean crouch;
   private Float faceYaw;
   private Vec3 lookPos;
   private double speedFactor = 1.0;

   // Pfad
   private List<Pathfinder.Step> path;
   private int pathIdx;
   private String pathKey;
   private int pathPlannedAt = -1000;
   private int pathProgressAt;
   private boolean pathComplete;
   private int failedPlans;
   /** Die letzte Wegsuche ist an fehlenden Bloecken gescheitert. */
   private boolean shortOfBlocks;

   // Kampf
   private LivingEntity enemy;
   private int attackCd;
   private int wtap;
   private int strafeDir = 1;
   private int strafeUntil;
   private LivingEntity lastAttacker;
   private int lastAttackedAt = -1000;
   // Fadenkreuz und Klicks
   private boolean aiming;
   private Vec3 aimPoint;
   private float aimYaw;
   private float aimPitch;
   private double aimNoiseYaw;
   private double aimNoisePitch;
   private int sinceSwing = 20;
   private int nextClickAt;
   private boolean timedClicker;
   private boolean strafing;
   private int freezeUntil;
   private Vec3 freezeDir;
   private LivingEntity engagedWith;
   private int engagedSince;
   private boolean edgeCareful = true;
   private boolean wantsPillarEscape;
   private int pillarLeft;
   private BlockPos pillarBase;
   private int gappleDecisionAt;
   public int hits;
   public int misses;

   // Handlungen
   private BlockPos breakPos;
   private int breakProgress;
   private int breakTotal;
   private int placeCd;
   private int fireCd = 60;
   private int clutchCd;
   private int eatTicks;
   private int bedMining;
   private int stayUntil;
   private int idleTurnAt;
   private Vec3 lastPos = Vec3.ZERO;
   private Vec3 anchor;
   private int anchorTick;
   private int stillTicks;
   private int shownHealth = -1;
   private HeldItem held = HeldItem.NONE;

   private enum HeldItem {
      NONE,
      SWORD,
      BLOCK,
      SHEARS,
      PICKAXE,
      FIREBALL,
      GAPPLE
   }

   public BedwarsBot(Level level) {
      super(EntityTypes.MANNEQUIN, level);
   }

   public void setup(BotProfile profile) {
      this.profile = profile;
      this.entityData.set(DATA_PROFILE, skin(profile.alex));
      this.setCustomName(this.displayName());
      this.setCustomNameVisible(true);
      this.addTag("bw_bot");
      profile.equipArmor(this);
      this.hold(HeldItem.SWORD);
      this.updateHealthTag();
   }

   /** Standard-Skin ueber den Skin-Patch des Profils: Steve (breit) oder Alex (schmal). */
   private static ResolvableProfile skin(boolean alex) {
      try {
         JsonObject json = new JsonObject();
         json.addProperty("texture", alex ? "minecraft:entity/player/slim/alex" : "minecraft:entity/player/wide/steve");
         json.addProperty("model", alex ? "slim" : "wide");
         return (ResolvableProfile)ResolvableProfile.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
      } catch (Exception e) {
         return Mannequin.DEFAULT_PROFILE;
      }
   }

   @SuppressWarnings("unchecked")
   private void setBelowName(Component text) {
      if (!descriptionLookedUp) {
         descriptionLookedUp = true;

         try {
            Field f = Mannequin.class.getDeclaredField("DATA_DESCRIPTION");
            f.setAccessible(true);
            descriptionAccessor = (EntityDataAccessor<Optional<Component>>)f.get(null);
         } catch (Throwable t) {
            descriptionAccessor = null;
         }
      }

      if (descriptionAccessor != null) {
         this.entityData.set(descriptionAccessor, Optional.ofNullable(text));
      }
   }

   private void updateHealthTag() {
      int hp = (int)Math.ceil(this.getHealth() + this.getAbsorptionAmount());
      if (hp != this.shownHealth) {
         this.shownHealth = hp;
         this.setBelowName(Component.literal(hp + " ❤").withStyle(this.getAbsorptionAmount() > 0.0F ? ChatFormatting.GOLD : ChatFormatting.RED));
      }
   }

   public Component displayName() {
      return Component.literal("[" + this.profile.team.team.label + "] " + this.profile.name).withStyle(this.profile.team.team.format);
   }

   @Override
   public boolean shouldBeSaved() {
      return false;
   }

   @Override
   public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
      Entity attacker = source.getEntity();
      if (this.profile != null && attacker != null && Game.teamOf(attacker) == this.profile.team.team) {
         return false;
      } else {
         boolean hurt = super.hurtServer(level, source, damage);
         if (hurt && attacker instanceof LivingEntity living && attacker != this) {
            this.lastAttacker = living;
            this.lastAttackedAt = this.ticks;
            if (this.profile != null) {
               TeamBrain.onMemberHurt(this.profile, living);
            }
         }

         return hurt;
      }
   }

   // ======================================================================== Tick

   @Override
   protected void serverAiStep() {
      super.serverAiStep();
      if (this.profile == null || !Game.running() || !(this.level() instanceof ServerLevel level)) {
         this.xxa = 0.0F;
         this.zza = 0.0F;
         this.setJumping(false);
         return;
      }

      this.ticks++;
      if (this.attackCd > 0) {
         this.attackCd--;
      }

      if (this.placeCd > 0) {
         this.placeCd--;
      }

      if (this.fireCd > 0) {
         this.fireCd--;
      }

      if (this.clutchCd > 0) {
         this.clutchCd--;
      }

      if (this.wtap > 0) {
         this.wtap--;
      }

      this.sinceSwing++;
      this.aiming = false;
      this.moveDir = null;
      this.sprint = false;
      this.jump = false;
      this.crouch = false;
      this.faceYaw = null;
      this.lookPos = null;
      this.speedFactor = 1.0;
      if (this.ticks % 10 == 0) {
         this.collectItems(level);
         this.updateHealthTag();
      }

      this.tickEating();
      if (!this.clutch(level)) {
         this.think(level);
         if (this.tickPillar(level)) {
            this.moveDir = null;
            this.sprint = false;
         }
      }

      this.updateAim();

      this.detectStuck();
      this.giveUpIfTrapped(level);
      this.applyIntent();
   }

   /**
    * Sitzt er abseits aller Inseln fest (z.B. unter einer Insel, ohne Bloecke), springt er nach
    * einer Weile in die Leere - wie ein echter Spieler. Sonst koennte eine Runde nie enden.
    */
   private void giveUpIfTrapped(ServerLevel level) {
      if (this.ticks % 100 == 0) {
         if (this.anchor == null || this.position().distanceTo(this.anchor) > 3.0) {
            this.anchor = this.position();
            this.anchorTick = this.ticks;
         }

         BotProfile.Task task = this.profile.task;
         boolean waiting = task == BotProfile.Task.ESCORT || task == BotProfile.Task.FOLLOW_PLAYER;
         boolean stalled = this.ticks - this.anchorTick > 900 && this.enemy == null && !waiting;
         if (stalled && this.profile.blocks() < 4 && !Game.arena().onAnyIsland(this.position())) {
            this.kill(level);
         }
      }
   }

   private void think(ServerLevel level) {
      if (this.ticks % 4 == 0 || this.enemy != null && !this.validEnemy(this.enemy)) {
         this.enemy = this.chooseEnemy();
      }

      if (this.eatTicks > 0) {
         this.retreatWhileEating(level);
         return;
      }

      if (this.shouldEat()) {
         this.startEating();
         return;
      }

      if (this.enemy != null && this.shouldEngage(this.enemy)) {
         this.cancelBreak(level);
         this.fight(level, this.enemy);
         return;
      }

      this.doTask(level);
   }

   // ======================================================================== Bewegung

   private static float yawOf(double dx, double dz) {
      return (float)(Mth.atan2(-dx, dz) * (180.0 / Math.PI));
   }

   private void applyIntent() {
      float yaw;
      if (this.aiming) {
         yaw = this.aimYaw;
      } else if (this.faceYaw != null) {
         yaw = this.faceYaw;
      } else if (this.moveDir != null && this.moveDir.horizontalDistanceSqr() > 1.0E-4) {
         yaw = yawOf(this.moveDir.x, this.moveDir.z);
      } else {
         yaw = this.getYRot();
      }

      float maxTurn = this.aiming ? 360.0F : (float)(25.0 + this.profile.skill * 35.0);
      float newYaw = this.getYRot() + Mth.clamp(Mth.wrapDegrees(yaw - this.getYRot()), -maxTurn, maxTurn);
      this.setYRot(newYaw);
      float headYaw = newYaw;
      float pitch = 0.0F;
      if (this.aiming) {
         headYaw = this.aimYaw;
         pitch = this.aimPitch;
      } else if (this.lookPos != null) {
         Vec3 eye = this.getEyePosition();
         double dx = this.lookPos.x - eye.x;
         double dy = this.lookPos.y - eye.y;
         double dz = this.lookPos.z - eye.z;
         headYaw = yawOf(dx, dz);
         pitch = (float)(-Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * (180.0 / Math.PI));
      }

      this.setYHeadRot(headYaw);
      this.setXRot(Mth.clamp(pitch, -90.0F, 90.0F));
      if (this.moveDir != null) {
         double len = Math.min(1.0, this.moveDir.horizontalDistance());
         Vec3 d = this.moveDir.horizontalDistanceSqr() > 1.0E-6 ? new Vec3(this.moveDir.x, 0.0, this.moveDir.z).normalize().scale(len) : Vec3.ZERO;
         float rad = newYaw * DEG;
         double sin = Mth.sin(rad);
         double cos = Mth.cos(rad);
         this.xxa = (float)(d.x * cos + d.z * sin);
         this.zza = (float)(-d.x * sin + d.z * cos);
      } else {
         this.xxa = 0.0F;
         this.zza = 0.0F;
      }

      boolean sprinting = this.sprint && this.wtap == 0 && this.zza > 0.6F && !this.crouch && this.eatTicks == 0;
      this.setSprinting(sprinting);
      float speed = sprinting ? 0.13F : (this.crouch ? 0.045F : 0.1F);
      if (this.eatTicks > 0) {
         speed *= 0.35F;
      }

      this.setSpeed((float)(speed * this.speedFactor));
      this.setJumping(this.jump && this.onGround());
      Pose pose = this.crouch ? Pose.CROUCHING : Pose.STANDING;
      if (this.getPose() != pose) {
         this.setPose(pose);
      }
   }

   private void go(Vec3 target, boolean sprint) {
      Vec3 d = new Vec3(target.x - this.getX(), 0.0, target.z - this.getZ());
      double len = d.length();
      if (len > 0.08) {
         this.moveDir = d.scale(1.0 / len).scale(Math.min(1.0, len * 2.5));
         this.sprint = sprint && len > 1.5;
      }
   }

   private void detectStuck() {
      if (this.ticks % 10 == 0) {
         boolean wants = this.moveDir != null && this.moveDir.horizontalDistanceSqr() > 0.2;
         if (wants && this.position().distanceToSqr(this.lastPos) < 0.01) {
            this.stillTicks += 10;
         } else {
            this.stillTicks = 0;
         }

         this.lastPos = this.position();
      }

      if (this.stillTicks >= 20 && this.onGround()) {
         this.jump = true;
      }

      if (this.stillTicks >= 60) {
         this.stillTicks = 0;
         this.path = null;
      }
   }

   /** Liegt dort, wohin er treten will, Boden (bis drei Bloecke tiefer)? */
   private boolean groundAt(Vec3 pos) {
      BlockPos base = BlockPos.containing(pos.x, this.getY() - 0.2, pos.z);

      for (int i = 0; i <= 3; i++) {
         BlockPos p = base.below(i);
         if (!this.level().getBlockState(p).getCollisionShape(this.level(), p).isEmpty()) {
            return true;
         }
      }

      return false;
   }

   /** Begrenzt eine freie Bewegung so, dass er nicht in die Leere laeuft. */
   private Vec3 safe(Vec3 dir) {
      if (dir == null) {
         return null;
      } else {
         Vec3 ahead = this.position().add(dir.normalize().scale(0.9));
         if (this.groundAt(ahead)) {
            return dir;
         } else {
            Vec3 alt = new Vec3(dir.x, 0.0, 0.0);
            if (alt.lengthSqr() > 0.01 && this.groundAt(this.position().add(alt.normalize().scale(0.9)))) {
               return alt;
            } else {
               alt = new Vec3(0.0, 0.0, dir.z);
               return alt.lengthSqr() > 0.01 && this.groundAt(this.position().add(alt.normalize().scale(0.9))) ? alt : null;
            }
         }
      }
   }

   // ======================================================================== Pfade

   private enum Nav {
      MOVING,
      ARRIVED,
      WAITING,
      FAILED
   }

   private Nav navigate(ServerLevel level, Pathfinder.Goal goal, String key, boolean bridge, boolean breaking, int replan) {
      BlockPos feet = this.feet();
      if (goal.reached(feet.getX(), feet.getY(), feet.getZ()) && this.onGround()) {
         this.path = null;
         return Nav.ARRIVED;
      } else {
         boolean need = this.path == null
            || !key.equals(this.pathKey)
            || this.pathIdx >= this.path.size()
            || this.ticks - this.pathPlannedAt >= replan
            || this.ticks - this.pathProgressAt > 80;
         if (need) {
            if (!Game.takePlanBudget()) {
               if (this.path != null && key.equals(this.pathKey) && this.pathIdx < this.path.size()) {
                  return this.followPath(level);
               }

               return Nav.WAITING;
            }

            Pathfinder.Options opt = new Pathfinder.Options();
            opt.blocks = bridge ? this.profile.blocks() : 0;
            opt.pickaxe = this.profile.pickaxe;
            opt.shears = this.profile.shears;
            opt.allowBreak = breaking;
            opt.maxNodes = (int)Mth.clamp(1500.0 + goal.estimate(feet.getX(), feet.getY(), feet.getZ()) * 90.0, 1500.0, 7000.0);
            opt.bridgeCost = 2.2 + this.profile.caution;
            Pathfinder.Result res = Pathfinder.find(level, Game.arena(), feet, goal, opt);
            this.path = res.steps;
            this.pathIdx = 0;
            this.pathKey = key;
            this.pathPlannedAt = this.ticks;
            this.pathProgressAt = this.ticks;
            this.pathComplete = res.complete;
            this.shortOfBlocks = bridge && !res.complete && res.blocksUsed >= opt.blocks - 1;
            if (res.steps.isEmpty()) {
               this.failedPlans++;
               this.path = null;
               return Nav.FAILED;
            }

            this.failedPlans = 0;
         }

         return this.followPath(level);
      }
   }

   private BlockPos feet() {
      BlockPos p = this.blockPosition();
      // auf einer Kante: die Zelle nehmen, in der wirklich Boden ist
      if (this.onGround() && this.level().getBlockState(p.below()).getCollisionShape(this.level(), p.below()).isEmpty()) {
         AABB box = this.getBoundingBox();

         for (double ox : new double[]{-0.3, 0.3}) {
            for (double oz : new double[]{-0.3, 0.3}) {
               BlockPos q = BlockPos.containing(box.getCenter().x + ox, this.getY() - 0.5, box.getCenter().z + oz);
               if (!this.level().getBlockState(q).getCollisionShape(this.level(), q).isEmpty()) {
                  return q.above();
               }
            }
         }
      }

      return p;
   }

   private Nav followPath(ServerLevel level) {
      if (this.path == null || this.pathIdx >= this.path.size()) {
         return Nav.FAILED;
      } else {
         Pathfinder.Step step = this.path.get(this.pathIdx);
         Vec3 c = Vec3.atBottomCenterOf(step.pos);
         double hd = Math.sqrt((c.x - this.getX()) * (c.x - this.getX()) + (c.z - this.getZ()) * (c.z - this.getZ()));
         double dy = step.pos.getY() - this.getY();
         if (hd < 0.4 && Math.abs(dy) < 0.75 && (this.onGround() || step.move == Pathfinder.Move.DROP && dy > -0.2)) {
            this.pathIdx++;
            this.pathProgressAt = this.ticks;
            if (this.pathIdx >= this.path.size()) {
               return this.pathComplete ? Nav.ARRIVED : Nav.FAILED;
            }

            step = this.path.get(this.pathIdx);
            c = Vec3.atBottomCenterOf(step.pos);
            hd = Math.sqrt((c.x - this.getX()) * (c.x - this.getX()) + (c.z - this.getZ()) * (c.z - this.getZ()));
         }

         if (hd > 4.5 || Math.abs(step.pos.getY() - this.getY()) > 4.0) {
            this.path = null;
            return Nav.WAITING;
         }

         Pathfinder.Move move = step.move;
         if (move == Pathfinder.Move.WALK && !this.solid(step.pos.below()) && this.profile.blocks() > 0) {
            move = Pathfinder.Move.BRIDGE;
         }

         switch (move) {
            case BREAK:
               for (BlockPos b : step.breaks) {
                  if (!level.getBlockState(b).isAir()) {
                     this.mine(level, b);
                     return Nav.MOVING;
                  }
               }

               this.go(c, false);
               break;
            case BRIDGE:
               BlockPos place = step.place != null ? step.place : step.pos.below();
               this.crouch = this.profile.hands.style != Hands.Style.SWEAT;
               if (!this.solid(place)) {
                  this.lookPos = Vec3.atCenterOf(place);
                  if (this.placeCd == 0 && this.onGround() && hd < 1.7) {
                     if (RND.nextDouble() < this.profile.hands.bridgeSlip * (1.0 + this.profile.tilt)) {
                        // verschaetzt: ohne Block weitergelaufen
                        Game.count("abgerutscht");
                        this.go(c, false);
                        this.placeCd = 10;
                        return Nav.MOVING;
                     }

                     if (!this.place(level, place, false, 1)) {
                        this.path = null;
                     }
                  }

                  return Nav.MOVING;
               }

               this.go(c, false);
               break;
            case PILLAR:
               BlockPos under = step.place != null ? step.place : step.pos.below();
               if (!this.solid(under) && this.onGround()) {
                  Game.count("hochbauen-weg");
               }

               if (!this.solid(under)) {
                  this.go(c, false);
                  this.lookPos = Vec3.atCenterOf(under);
                  if (this.onGround()) {
                     this.jump = true;
                  } else if (this.getY() >= under.getY() + 1.0 && this.placeCd == 0) {
                     this.place(level, under, false, 1);
                  }

                  return Nav.MOVING;
               }

               this.go(c, false);
               break;
            case JUMP:
               this.go(c, false);
               if (this.onGround() && hd < 1.4) {
                  this.jump = true;
               }
               break;
            default:
               this.go(c, hd > 2.0);
               if (this.horizontalCollision && this.onGround()) {
                  this.jump = true;
               }
         }

         return Nav.MOVING;
      }
   }

   private boolean solid(BlockPos pos) {
      return !this.level().getBlockState(pos).getCollisionShape(this.level(), pos).isEmpty();
   }

   // ======================================================================== Bloecke

   private boolean place(ServerLevel level, BlockPos pos, boolean defensive, int layer) {
      BlockState there = level.getBlockState(pos);
      if (!there.canBeReplaced() || !Game.arena().inBuildArea(pos) || Game.arena().isMap(pos)) {
         return false;
      } else if (!level.getEntities((Entity)null, new AABB(pos), e -> e instanceof LivingEntity && e.isAlive()).isEmpty()) {
         return false;
      } else {
         Block block = this.profile.takeBlock(defensive, layer);
         if (block == null) {
            return false;
         } else {
            BlockState state = block.defaultBlockState();
            level.setBlock(pos, state, 3);
            level.playSound(null, pos, state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 0.9F + RND.nextFloat() * 0.2F);
            this.holdBlock(block);
            this.swingArm();
            this.placeCd = Math.max(1, this.profile.hands.bridgeDelay + RND.nextInt(3) - 1);
            return true;
         }
      }
   }

   private void mine(ServerLevel level, BlockPos pos) {
      BlockState state = level.getBlockState(pos);
      if (state.isAir() || Game.arena().isMap(pos) || state.getBlock() instanceof BedBlock) {
         this.cancelBreak(level);
      } else {
         if (!pos.equals(this.breakPos)) {
            this.cancelBreak(level);
            this.breakPos = pos.immutable();
            this.breakProgress = 0;
            int base = Pathfinder.breakTicks(state, this.profile.pickaxe, this.profile.shears);
            this.breakTotal = Math.max(2, (int)(base * (1.25 - this.profile.skill * 0.35)));
         }

         this.lookPos = Vec3.atCenterOf(pos);
         this.hold(state.is(net.minecraft.tags.BlockTags.WOOL) && this.profile.shears ? HeldItem.SHEARS : (this.profile.pickaxe > 0 ? HeldItem.PICKAXE : HeldItem.SWORD));
         this.breakProgress++;
         if (this.breakProgress % 4 == 1) {
            this.swingArm();
            level.playSound(null, pos, state.getSoundType().getHitSound(), SoundSource.BLOCKS, 0.5F, 0.8F);
         }

         level.destroyBlockProgress(this.getId(), pos, Math.min(9, this.breakProgress * 10 / Math.max(1, this.breakTotal)));
         if (this.breakProgress >= this.breakTotal) {
            level.destroyBlockProgress(this.getId(), pos, -1);
            level.destroyBlock(pos, false, this);
            this.breakPos = null;
            this.pathProgressAt = this.ticks;
         }
      }
   }

   private void cancelBreak(ServerLevel level) {
      if (this.breakPos != null) {
         level.destroyBlockProgress(this.getId(), this.breakPos, -1);
         this.breakPos = null;
      }
   }

   /** Faellt er in die Leere, setzt er (je nach Koennen) einen Block unter sich. */
   private boolean clutch(ServerLevel level) {
      if (this.onGround() || this.getDeltaMovement().y > -0.25 || this.clutchCd > 0 || this.profile.blocks() == 0) {
         return false;
      } else {
         BlockPos feet = this.blockPosition();

         for (int i = 1; i <= 8; i++) {
            if (this.solid(feet.below(i))) {
               return false;
            }
         }

         this.clutchCd = 14;
         if (RND.nextDouble() < this.profile.hands.clutch) {
            BlockPos under = feet.below();
            if (this.place(level, under, false, 1)) {
               Game.count("clutch");
               this.setDeltaMovement(this.getDeltaMovement().multiply(0.3, 0.0, 0.3));
               this.path = null;
               if (RND.nextInt(3) == 0) {
                  BotChat.event(this.profile, BotChat.Event.CLUTCH, null);
               }

               return true;
            }
         }

         return false;
      }
   }

   // ======================================================================== Kampf

   private boolean validEnemy(LivingEntity e) {
      return e != null && e.isAlive() && !e.isRemoved() && Game.isEnemy(this.profile.team, e) && !Game.isProtected(e);
   }

   private double awareness() {
      return switch (this.profile.task) {
         case DEFEND -> 26.0;
         case HUNT -> 40.0;
         case FORTIFY, GATHER -> 18.0;
         default -> 16.0;
      };
   }

   private LivingEntity chooseEnemy() {
      double range = this.awareness() * this.profile.hands.awareness;
      LivingEntity best = null;
      double bestScore = Double.MAX_VALUE;

      for (LivingEntity e : Game.enemiesOf(this.profile.team)) {
         if (!Game.isProtected(e)) {
            double d = this.distanceTo(e);
            boolean target = e == this.profile.taskEntity;
            boolean attacker = e == this.lastAttacker && this.ticks - this.lastAttackedAt < 100;
            if (d <= range || target && d < 60.0 || attacker && d < 24.0) {
               double score = d;
               if (target) {
                  score -= 12.0;
               }

               if (attacker) {
                  score -= 8.0;
               }

               score -= (20.0 - e.getHealth()) * 0.3;
               if (d > 6.0 && !this.hasLineOfSight(e)) {
                  score += 10.0;
               }

               if (score < bestScore) {
                  bestScore = score;
                  best = e;
               }
            }
         }
      }

      return best;
   }

   private boolean shouldEngage(LivingEntity e) {
      double d = this.distanceTo(e);
      BotProfile p = this.profile;
      boolean attacked = e == this.lastAttacker && this.ticks - this.lastAttackedAt < 80;
      boolean onOurIsland = Game.arena().islandAt(e.position(), 9.0) == p.team.team;
      if (p.task == BotProfile.Task.RETREAT && this.getHealth() < 10.0F && d > 4.0) {
         return false;
      } else if (p.task == BotProfile.Task.RAID && p.taskTeam != null && p.taskTeam.bedAlive
         && this.position().distanceTo(p.taskTeam.island().bedCenter()) < 10.0 && !onOurIsland) {
         // am gegnerischen Bett: Bett zuerst, gekaempft wird nur, wenn einer direkt im Weg steht
         return d < 2.4 || this.getHealth() < 6.0F && attacked && d < 3.5;
      } else if (p.task == BotProfile.Task.HUNT && e == p.taskEntity) {
         return true;
      } else if (onOurIsland) {
         return d < 28.0;
      } else if (d < 4.5 || attacked && d < 14.0) {
         return true;
      } else if (!p.team.bedAlive) {
         // ohne Bett: nur kaempfen, wenn es sich lohnt
         return d < 9.0 && this.advantageOver(e) > 0.0;
      } else {
         double range = switch (p.task) {
            case RAID -> 5.5 + p.aggression * 3.0;
            case DEFEND, ESCORT, FOLLOW_PLAYER -> 14.0;
            case FORTIFY, SHOP, CUT_BRIDGE -> 6.0;
            case HUNT -> 16.0;
            default -> 7.0 + p.aggression * 7.0;
         };
         return d < range;
      }
   }

   /** Grobe Einschaetzung: positiv = er ist staerker als der Gegner. */
   private double advantageOver(LivingEntity e) {
      double mine = this.getHealth() + this.getAbsorptionAmount() + this.profile.gearScore() * 2.5;
      double theirs = e.getHealth() + e.getAbsorptionAmount();
      if (e instanceof BedwarsBot b && b.profile != null) {
         theirs += b.profile.gearScore() * 2.5;
      } else if (e instanceof ServerPlayer sp) {
         theirs += Game.playerGearScore(sp) * 2.5;
      }

      return mine - theirs + (this.profile.aggression - 0.5) * 8.0;
   }

   private void fight(ServerLevel level, LivingEntity e) {
      Hands h = this.profile.hands;
      double d = this.distanceTo(e);
      Vec3 seen = Game.pastPosition(e, h.reaction + (this.profile.tilt > 0.5 ? 2 : 0));
      this.aimAt(seen.add(0.0, e.getBbHeight() * 0.72, 0.0));
      if (this.engagedWith != e) {
         this.engagedWith = e;
         this.engagedSince = this.ticks;
         this.edgeCareful = RND.nextDouble() < h.edge;
         this.wantsPillarEscape = RND.nextDouble() < h.pillar;
         this.timedClicker = RND.nextDouble() < h.timing;
      }

      if (this.ticks % 20 == 0) {
         this.edgeCareful = RND.nextDouble() < h.edge;
      }

      // kurzer Aussetzer: stehen bleiben oder falsch laufen, wie ein Mensch, der kurz nicht aufpasst
      if (this.ticks < this.freezeUntil) {
         if (this.freezeDir != null) {
            this.moveDir = this.edgeCareful ? this.safe(this.freezeDir) : this.freezeDir;
         }

         this.clickAt(level, e, d);
         return;
      } else if (RND.nextDouble() < h.brainfart * (1.0 + this.profile.tilt)) {
         this.freezeUntil = this.ticks + 5 + RND.nextInt(16);
         Game.count("aussetzer");
         this.freezeDir = RND.nextBoolean() ? null : new Vec3(RND.nextGaussian(), 0.0, RND.nextGaussian());
      }

      boolean lowAndLosing = this.getHealth() < 7.0F && this.advantageOver(e) < -4.0 && this.profile.caution > 0.35;
      if (lowAndLosing && this.wantsPillarEscape && this.profile.blocks() >= 3 && this.pillarLeft == 0 && d < 5.0) {
         // in Not hochbauen
         this.pillarLeft = 3;
         this.wantsPillarEscape = false;
         Game.count("flucht-hochbauen");
      }

      if (lowAndLosing && d > 2.0) {
         // zurueckweichen statt sinnlos sterben
         Vec3 away = this.position().subtract(seen).multiply(1.0, 0.0, 1.0);
         Vec3 home = this.profile.team.island().spawn().subtract(this.position()).multiply(1.0, 0.0, 1.0);
         Vec3 dir = away.normalize().add(home.normalize().scale(0.8));
         this.moveDir = this.edgeCareful ? this.safe(dir) : dir;
         this.sprint = true;
         this.hold(HeldItem.SWORD);
         this.clickAt(level, e, d);
         return;
      }

      // Gegner steht ueber ihm: hochbauen
      double above = e.getY() - this.getY();
      if (above > 1.8 && Math.hypot(e.getX() - this.getX(), e.getZ() - this.getZ()) < 2.8 && this.profile.blocks() > 0 && this.pillarLeft == 0
         && RND.nextDouble() < h.pillar * 0.2) {
         this.pillarLeft = (int)Math.ceil(above);
         Game.count("hochbauen-zum-gegner");
      }

      boolean direct = d < 7.0 && Math.abs(e.getY() - this.getY()) < 1.3 && this.hasLineOfSight(e) && this.walkableTowards(e.position());
      if (this.profile.fireballs > 0 && this.fireCd == 0 && d > 9.0 && d < 30.0 && this.hasLineOfSight(e)) {
         this.throwFireball(level, e);
         return;
      }

      if (!direct) {
         boolean bridge = this.profile.task == BotProfile.Task.HUNT || this.profile.aggression > 0.45 || Game.arena().islandAt(e.position(), 6.0) != null;
         Nav nav = this.navigate(level, Pathfinder.near(e.blockPosition(), 1.6), "enemy" + e.getId(), bridge, true, 15);
         if (this.held != HeldItem.BLOCK && this.breakPos == null) {
            this.hold(HeldItem.SWORD);
         }

         if (nav == Nav.FAILED || nav == Nav.WAITING) {
            this.go(seen, true);
            this.moveDir = this.edgeCareful ? this.safe(this.moveDir) : this.moveDir;
         }

         // unterwegs schaut er dorthin, wohin er laeuft - ausser der Gegner ist schon nah
         if (d > 6.0) {
            this.aiming = false;
         }
      } else {
         this.path = null;
         this.hold(HeldItem.SWORD);
         Vec3 to = new Vec3(seen.x - this.getX(), 0.0, seen.z - this.getZ());
         if (to.lengthSqr() < 1.0E-4) {
            to = new Vec3(1.0, 0.0, 0.0);
         }

         to = to.normalize();
         if (this.ticks >= this.strafeUntil || this.horizontalCollision) {
            this.strafing = RND.nextDouble() < h.strafe;
            this.strafeDir = RND.nextBoolean() ? 1 : -1;
            this.strafeUntil = this.ticks + 6 + RND.nextInt(h.style == Hands.Style.NOOB ? 30 : 18);
         }

         Vec3 side = new Vec3(-to.z, 0.0, to.x).scale(this.strafeDir);
         Vec3 move;
         if (d > 2.8) {
            move = this.strafing ? to.add(side.scale(0.3)) : to;
            this.sprint = true;
         } else if (d < 1.4 && h.strafe > 0.3) {
            move = to.scale(-0.7).add(side.scale(0.5));
         } else if (this.strafing) {
            move = side.scale(0.5 + h.strafe * 0.5).add(to.scale(0.35));
            this.sprint = true;
         } else {
            // wer nicht strafed, laeuft einfach weiter drauf
            move = to;
            this.sprint = true;
         }

         this.moveDir = this.edgeCareful ? this.safe(move) : move;
         if (this.moveDir == null) {
            this.strafeDir = -this.strafeDir;
         }

         if (this.onGround() && d < 3.4 && RND.nextDouble() < h.crits * 0.12) {
            this.jump = true;
         }
      }

      this.clickAt(level, e, d);
   }

   /** Entscheidet, ob er in diesem Tick klickt - wie ein Mensch, nicht wie eine Kill-Aura. */
   private void clickAt(ServerLevel level, LivingEntity e, double d) {
      Hands h = this.profile.hands;
      if (d > h.clickRange + 0.4 || this.ticks < this.nextClickAt || this.eatTicks > 0) {
         return;
      }

      if (this.timedClicker) {
         if (d > 3.05) {
            return;
         }

         // wartet die Abklingzeit ab, mal etwas zu frueh, mal zu spaet
         int ready = 11 + (int)Math.round(RND.nextGaussian() * (1.6 - h.timing));
         if (this.sinceSwing < ready) {
            this.nextClickAt = this.ticks + Math.max(1, ready - this.sinceSwing);
            return;
         }

         // Krit-Timing: kurz warten, bis er faellt
         boolean rising = !this.onGround() && this.getDeltaMovement().y > 0.05;
         if (rising && RND.nextDouble() < h.crits && d < 3.0) {
            return;
         }
      }

      this.swingSword(level);
      double interval = 20.0 / h.cps * (0.6 + RND.nextDouble() * 0.8);
      this.nextClickAt = this.ticks + Math.max(1, (int)Math.round(interval));
      if (RND.nextDouble() > 0.92) {
         this.timedClicker = RND.nextDouble() < h.timing;
      }
   }

   /** Schwingt das Schwert entlang des Fadenkreuzes. Getroffen wird nur, wenn der Strahl die Hitbox trifft. */
   private void swingSword(ServerLevel level) {
      float strength = Mth.clamp((this.sinceSwing + 0.5F) / 12.5F, 0.0F, 1.0F);
      this.sinceSwing = 0;
      this.swingArm();
      Vec3 eye = this.getEyePosition();
      Vec3 dir = Vec3.directionFromRotation(this.aimPitch, this.aimYaw);
      double reach = 3.0;
      Vec3 end = eye.add(dir.scale(reach));
      BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
      if (block.getType() != HitResult.Type.MISS) {
         end = block.getLocation();
      }

      LivingEntity hit = null;
      double best = Double.MAX_VALUE;

      for (LivingEntity c : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(4.5), x -> x != this && x.isAlive())) {
         Optional<Vec3> on = c.getBoundingBox().inflate(0.1).clip(eye, end);
         if (on.isPresent()) {
            double dd = on.get().distanceToSqr(eye);
            if (dd < best) {
               best = dd;
               hit = c;
            }
         }
      }

      if (hit == null || Game.teamOf(hit) == this.profile.team.team || hit instanceof net.minecraft.world.entity.npc.villager.Villager) {
         if (hit == null) {
            this.misses++;
         }

         return;
      }

      float dmg = this.profile.swordDamage() * (0.2F + strength * strength * 0.8F);
      boolean full = strength > 0.9F;
      boolean crit = full && !this.onGround() && this.getDeltaMovement().y < 0.0 && this.fallDistance > 0.0;
      if (crit) {
         dmg *= 1.5F;
      }

      boolean sprintHit = full && this.isSprinting();
      DamageSource src = this.damageSources().mobAttack(this);
      if (hit.hurtServer(level, src, dmg)) {
         if (sprintHit) {
            hit.knockback(0.5, Mth.sin(this.getYRot() * DEG), -Mth.cos(this.getYRot() * DEG), src, dmg);
            this.setDeltaMovement(this.getDeltaMovement().multiply(0.6, 1.0, 0.6));
            if (RND.nextDouble() < this.profile.hands.wtap) {
               this.wtap = 3 + RND.nextInt(3);
            }
         }

         if (crit) {
            level.sendParticles(ParticleTypes.CRIT, hit.getX(), hit.getY() + hit.getBbHeight() * 0.5, hit.getZ(), 10, 0.3, 0.4, 0.3, 0.2);
            level.playSound(null, hit.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 1.0F);
         } else {
            level.playSound(null, hit.blockPosition(), !full ? SoundEvents.PLAYER_ATTACK_WEAK : (sprintHit ? SoundEvents.PLAYER_ATTACK_KNOCKBACK : SoundEvents.PLAYER_ATTACK_STRONG), SoundSource.PLAYERS, 1.0F, 1.0F);
         }

         this.setLastHurtMob(hit);
         this.hits++;
      }
   }

   /** Fadenkreuz Richtung Ziel bewegen: begrenzte Drehgeschwindigkeit, Nachziehen, Zittern, Ueberschiessen. */
   private void aimAt(Vec3 point) {
      this.aiming = true;
      this.aimPoint = point;
   }

   private void updateAim() {
      Hands h = this.profile.hands;
      if (!this.aiming || this.aimPoint == null) {
         this.aimYaw = this.getYHeadRot();
         this.aimPitch = this.getXRot();
         return;
      }

      Vec3 eye = this.getEyePosition();
      double dx = this.aimPoint.x - eye.x;
      double dy = this.aimPoint.y - eye.y;
      double dz = this.aimPoint.z - eye.z;
      float wantYaw = yawOf(dx, dz);
      float wantPitch = (float)(-Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * (180.0 / Math.PI));
      float errYaw = Mth.wrapDegrees(wantYaw - this.aimYaw);
      float errPitch = wantPitch - this.aimPitch;
      double gain = h.aimGain * (1.0 - this.profile.tilt * 0.3);
      if (Math.abs(errYaw) > 25.0F) {
         gain *= 1.0 + h.overshoot;
      }

      double max = h.aimSpeed();
      this.aimNoiseYaw = this.aimNoiseYaw * 0.75 + RND.nextGaussian() * h.aimNoise * 0.45 * (1.0 + this.profile.tilt);
      this.aimNoisePitch = this.aimNoisePitch * 0.75 + RND.nextGaussian() * h.aimNoise * 0.25;
      this.aimYaw += (float)(Mth.clamp(errYaw * gain, -max, max) + this.aimNoiseYaw * 0.35);
      this.aimPitch = Mth.clamp(this.aimPitch + (float)(Mth.clamp(errPitch * gain, -max, max) + this.aimNoisePitch * 0.35), -90.0F, 90.0F);
   }

   private boolean walkableTowards(Vec3 target) {
      Vec3 from = this.position();
      Vec3 delta = target.subtract(from).multiply(1.0, 0.0, 1.0);
      double len = delta.length();
      if (len < 0.5) {
         return true;
      } else {
         Vec3 step = delta.scale(1.0 / len);

         for (double t = 0.8; t < len; t += 0.8) {
            Vec3 p = from.add(step.scale(t));
            if (!this.groundAt(p)) {
               return false;
            }

            BlockPos feet = BlockPos.containing(p.x, this.getY() + 0.1, p.z);
            if (this.solid(feet) && this.solid(feet.above())) {
               return false;
            }
         }

         return true;
      }
   }

   /** Hochbauen: springen und im hoechsten Punkt einen Block unter sich setzen. */
   private boolean tickPillar(ServerLevel level) {
      if (this.pillarLeft <= 0) {
         return false;
      } else if (this.profile.blocks() == 0 || this.solid(this.blockPosition().above(2))) {
         this.pillarLeft = 0;
         return false;
      } else {
         if (this.onGround()) {
            this.pillarBase = this.blockPosition();
            this.jump = true;
         } else if (this.pillarBase != null && this.getY() >= this.pillarBase.getY() + 1.02 && this.placeCd == 0) {
            if (this.place(level, this.pillarBase, false, 1)) {
               Game.count("hochgebaut");
               this.pillarLeft--;
               this.pillarBase = null;
            }
         }

         this.lookPos = Vec3.atCenterOf(this.blockPosition().below());
         return true;
      }
   }

   private void throwFireball(ServerLevel level, LivingEntity e) {
      Vec3 eye = this.getEyePosition();
      double d = this.distanceTo(e);
      Vec3 seen = Game.pastPosition(e, this.profile.hands.reaction);
      Vec3 aim = seen.add(0.0, e.getBbHeight() * 0.5, 0.0).add(e.getDeltaMovement().scale(d * 0.9 * this.profile.skill));
      Vec3 dir = aim.subtract(eye).normalize();
      double spread = this.profile.hands.aimNoise * 0.02;
      dir = dir.add(RND.nextGaussian() * spread, RND.nextGaussian() * spread * 0.5, RND.nextGaussian() * spread).normalize();
      this.hold(HeldItem.FIREBALL);
      this.lookPos = aim;
      LargeFireball fb = new LargeFireball(level, this, dir.scale(0.1), 1);
      fb.setPos(eye.x + dir.x * 1.4, eye.y + dir.y * 1.4, eye.z + dir.z * 1.4);
      level.addFreshEntity(fb);
      level.playSound(null, this.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0F, 1.0F);
      this.swingArm();
      this.profile.fireballs--;
      this.fireCd = 70 + RND.nextInt(50);
   }

   // ======================================================================== Essen

   private boolean shouldEat() {
      if (this.ticks < this.gappleDecisionAt) {
         return false;
      } else if (this.profile.gapples > 0 && this.getHealth() < 10.0F && RND.nextDouble() < this.profile.hands.forgetGapple * 0.05) {
         // vergisst den Goldapfel - passiert
         Game.count("apfel-vergessen");
         this.gappleDecisionAt = this.ticks + 100;
         return false;
      }

      return this.profile.gapples > 0
         && this.eatTicks == 0
         && this.getHealth() < 9.0F + (float)(this.profile.caution * 4.0)
         && !this.hasEffect(MobEffects.REGENERATION)
         && (this.enemy == null || this.distanceTo(this.enemy) > 3.5 || this.getHealth() < 5.0F);
   }

   private void startEating() {
      this.profile.gapples--;
      this.eatTicks = 33;
      this.hold(HeldItem.GAPPLE);
      this.startUsingItem(InteractionHand.MAIN_HAND);
   }

   private void tickEating() {
      if (this.eatTicks > 0) {
         this.eatTicks--;
         if (this.eatTicks == 0) {
            if (!this.hasEffect(MobEffects.REGENERATION)) {
               this.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
               this.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 2400, 0));
               this.level().playSound(null, this.blockPosition(), SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 0.6F, 1.0F);
            }

            this.stopUsingItem();
            this.hold(HeldItem.SWORD);
         }
      }
   }

   private void retreatWhileEating(ServerLevel level) {
      if (this.enemy != null && this.distanceTo(this.enemy) < 6.0) {
         Vec3 away = this.position().subtract(this.enemy.position()).multiply(1.0, 0.0, 1.0);
         this.moveDir = this.safe(away);
         this.lookPos = this.enemy.getEyePosition();
         if (this.distanceTo(this.enemy) < 2.2 && this.profile.hands.style != Hands.Style.NOOB && RND.nextDouble() < 0.1) {
            this.stopUsingItem();
            this.eatTicks = 0;
            this.profile.gapples++;
            this.hold(HeldItem.SWORD);
         }
      }
   }

   // ======================================================================== Auftraege

   private void doTask(ServerLevel level) {
      BotProfile p = this.profile;
      switch (p.task) {
         case GATHER -> this.taskGather(level);
         case SHOP -> this.taskShop(level);
         case FORTIFY -> this.taskFortify(level, false);
         case DEFEND -> this.taskDefend(level);
         case RAID -> this.taskRaid(level);
         case MID -> this.taskMid(level);
         case HUNT -> this.taskHunt(level);
         case ESCORT, FOLLOW_PLAYER -> this.taskEscort(level);
         case RETREAT, REGROUP -> this.taskHome(level);
         case CUT_BRIDGE -> this.taskCutBridge(level);
         default -> this.idle(level);
      }
   }

   public void finishTask() {
      this.profile.task = BotProfile.Task.IDLE;
      this.profile.taskEntity = null;
      this.profile.taskTeam = null;
      this.profile.taskPos = null;
      this.profile.taskLockUntil = 0;
      this.path = null;
      this.bedMining = 0;
      TeamBrain.requestThink(this.profile.team);
   }

   private void idle(ServerLevel level) {
      this.hold(HeldItem.SWORD);
      if (this.ticks >= this.idleTurnAt) {
         this.idleTurnAt = this.ticks + 20 + RND.nextInt(50);
         this.faceYaw = this.getYRot() + (float)(RND.nextGaussian() * 60.0);
      }
   }

   private void taskGather(ServerLevel level) {
      Arena.Island is = this.profile.team.island();
      BlockPos gen = is.generator();
      Nav nav = this.navigate(level, Pathfinder.near(gen, 0.9), "gather", false, false, 80);
      if (nav == Nav.ARRIVED || nav == Nav.FAILED) {
         this.go(Vec3.atBottomCenterOf(gen), false);
         this.idle(level);
         if (this.ticks % 60 == 0 && TeamBrain.wantsToShop(this.profile)) {
            this.profile.afterShop = BotProfile.Task.GATHER;
            this.profile.task = BotProfile.Task.SHOP;
         }
      }
   }

   /** Am eigenen Generator warten, bis das Team sich etwas leisten kann - ohne den Auftrag zu wechseln. */
   private void waitAtGenerator(ServerLevel level) {
      BlockPos gen = this.profile.team.island().generator();
      Nav nav = this.navigate(level, Pathfinder.near(gen, 0.9), "gather", false, false, 80);
      if (nav == Nav.ARRIVED || nav == Nav.FAILED) {
         this.go(Vec3.atBottomCenterOf(gen), false);
         this.idle(level);
      }
   }

   private void taskShop(ServerLevel level) {
      Arena.Island is = this.profile.team.island();
      Nav nav = this.navigate(level, Pathfinder.near(is.shopFront(), 1.3), "shop", true, false, 80);
      if (nav == Nav.ARRIVED || this.position().distanceTo(Vec3.atBottomCenterOf(is.shopFront())) < 2.0) {
         this.lookPos = is.shop().add(0.0, 1.5, 0.0);
         String bought = this.profile.shop();
         this.profile.equipArmor(this);
         this.hold(HeldItem.NONE);
         this.hold(HeldItem.SWORD);
         if (bought != null) {
            BotChat.purchase(this.profile, bought);
         }

         BotProfile.Task next = this.profile.afterShop;
         this.profile.afterShop = BotProfile.Task.IDLE;
         if (next == BotProfile.Task.IDLE || next == BotProfile.Task.SHOP) {
            this.finishTask();
         } else {
            this.profile.task = next;
            this.path = null;
         }
      } else if (nav == Nav.FAILED && this.failedPlans > 3) {
         this.finishTask();
      }
   }

   private void gotoShop(BotProfile.Task after) {
      this.profile.afterShop = after;
      this.profile.task = BotProfile.Task.SHOP;
      this.path = null;
   }

   /** Baut die Verteidigung ums eigene Bett, Schicht fuer Schicht von innen nach aussen. */
   private boolean taskFortify(ServerLevel level, boolean repairOnly) {
      Game.TeamState t = this.profile.team;
      if (!t.bedAlive) {
         if (!repairOnly) {
            this.finishTask();
         }

         return false;
      } else {
         int maxLayer = repairOnly ? 2 : t.defenseLayers();
         BlockPos cell = null;
         int layer = 0;

         for (Game.DefenseCell dc : t.defensePlan()) {
            if (dc.layer() <= maxLayer && level.getBlockState(dc.pos()).canBeReplaced()) {
               cell = dc.pos();
               layer = dc.layer();
               break;
            }
         }

         if (cell == null) {
            if (!repairOnly) {
               t.fortified = true;
               BotChat.event(this.profile, BotChat.Event.FORTIFIED, null);
               this.finishTask();
            }

            return false;
         } else if (this.profile.blocks() == 0) {
            if (!repairOnly) {
               if (TeamBrain.canAfford(t, 4)) {
                  this.gotoShop(BotProfile.Task.FORTIFY);
               } else {
                  this.waitAtGenerator(level);
               }
            }

            return false;
         } else {
            Vec3 cc = Vec3.atCenterOf(cell);
            double reach = this.getEyePosition().distanceTo(cc);
            boolean inside = this.getBoundingBox().intersects(new AABB(cell));
            if (reach <= 4.6 && !inside && this.onGround()) {
               this.lookPos = cc;
               if (this.placeCd == 0) {
                  this.place(level, cell, true, layer);
               }

               return true;
            } else {
               BlockPos spot = t.nearestDefenseSpot(cell);
               Nav nav = this.navigate(level, Pathfinder.near(spot, 0.8), "fortify" + spot.asLong(), false, false, 80);
               if (nav == Nav.FAILED && this.failedPlans > 4) {
                  this.finishTask();
               }

               return true;
            }
         }
      }
   }

   private void taskDefend(ServerLevel level) {
      Game.TeamState t = this.profile.team;
      if (this.ticks % 50 == 0 && t.bedAlive && this.profile.blocks() > 0) {
         this.stayUntil = 0;
      }

      if (t.bedAlive && this.profile.blocks() > 0 && t.missingDefense(2) > 0 && this.taskFortify(level, true)) {
         return;
      }

      Arena.Island is = t.island();
      BlockPos guard = t.bedAlive ? is.at(this.profile.name.hashCode() % 2 == 0 ? 2 : -2, 1, 1) : is.at(0, 1, -2);
      Nav nav = this.navigate(level, Pathfinder.near(guard, 1.5), "guard", false, false, 100);
      if (nav == Nav.ARRIVED || nav == Nav.FAILED) {
         this.idle(level);
         if (this.ticks % 80 == 0 && TeamBrain.wantsToShop(this.profile)) {
            this.gotoShop(BotProfile.Task.DEFEND);
         }
      }
   }

   private void taskRaid(ServerLevel level) {
      BotProfile p = this.profile;
      Game.TeamState target = p.taskTeam;
      if (target == null || !target.alive() || target == p.team) {
         this.finishTask();
      } else if (!target.bedAlive) {
         LivingEntity victim = Game.nearestMember(target, this.position());
         if (victim == null) {
            this.finishTask();
         } else {
            p.task = BotProfile.Task.HUNT;
            p.taskEntity = victim;
            this.path = null;
         }
      } else {
         List<BlockPos> bed = Game.arena().bedBlocks(target.team);
         if (bed.isEmpty()) {
            this.finishTask();
         } else {
            BlockPos reachable = this.bedInReach(level, bed);
            if (Game.debug && this.ticks % 20 == 0 && this.position().distanceTo(target.island().bedCenter()) < 9.0) {
               Pathfinder.Step st = this.path != null && this.pathIdx < this.path.size() ? this.path.get(this.pathIdx) : null;
               System.out.println("[BW raid] " + p.name + "->" + target.team.label + " feet=" + this.feet().subtract(bed.get(0)).toShortString() + " y=" + String.format("%.2f", this.getY())
                  + " reach=" + (reachable != null) + " path=" + (this.path == null ? "null" : this.pathIdx + "/" + this.path.size() + (this.pathComplete ? "" : "~"))
                  + " step=" + (st == null ? "-" : st.move + "@" + st.pos.subtract(bed.get(0)).toShortString() + " br" + st.breaks.size())
                  + " brk=" + (this.breakPos == null ? "-" : this.breakPos.subtract(bed.get(0)).toShortString() + " " + this.breakProgress + "/" + this.breakTotal)
                  + " fail=" + this.failedPlans + " bl=" + p.blocks() + " enemy=" + (this.enemy == null ? "-" : String.format("%.1f", this.distanceTo(this.enemy))));
            }

            if (reachable != null) {
               this.path = null;
               this.lookPos = Vec3.atCenterOf(reachable);
               this.hold(HeldItem.SWORD);
               this.bedMining++;
               if (this.bedMining % 4 == 1) {
                  this.swingArm();
               }

               level.destroyBlockProgress(this.getId(), reachable, Math.min(9, this.bedMining * 10 / 18));
               if (this.bedMining >= 18 - (int)(this.profile.skill * 6.0)) {
                  level.destroyBlockProgress(this.getId(), reachable, -1);
                  this.bedMining = 0;
                  Game.destroyBed(target, this.profile.team, this.profile, null);
                  this.finishTask();
               }
            } else {
               this.bedMining = 0;
               boolean atHome = Game.arena().islandAt(this.position(), 2.0) == p.team.team;
               if (atHome && (p.blocks() < 12 || this.shortOfBlocks && p.blocks() < 64) && TeamBrain.canAfford(p.team, 4)) {
                  this.shortOfBlocks = false;
                  this.gotoShop(BotProfile.Task.RAID);
                  return;
               }

               Nav nav = this.navigate(level, Pathfinder.adjacent(bed), "raid" + target.team.ordinal(), true, true, 70);
               if (nav == Nav.FAILED) {
                  if (p.blocks() < 6) {
                     if (TeamBrain.canAfford(p.team, 4)) {
                        this.gotoShop(BotProfile.Task.RAID);
                     } else if (atHome && this.ticks - p.taskSince < 1200) {
                        this.waitAtGenerator(level);
                     } else {
                        this.finishTask();
                     }
                  } else if (this.failedPlans > 5) {
                     this.finishTask();
                  }
               }
            }
         }
      }
   }

   /** Ein Bettblock, den er von hier aus abbauen kann (er beruehrt ihn oder sieht ihn frei). */
   private BlockPos bedInReach(ServerLevel level, List<BlockPos> bed) {
      BlockPos feet = this.feet();

      for (BlockPos b : bed) {
         int dx = Math.abs(feet.getX() - b.getX());
         int dz = Math.abs(feet.getZ() - b.getZ());
         int dy = feet.getY() - b.getY();
         if (dx + dz == 1 && dy == 0 || dx == 0 && dz == 0 && (dy == 0 || dy == 1)) {
            return b;
         }
      }

      Vec3 eye = this.getEyePosition();

      for (BlockPos b : bed) {
         for (Vec3 off : new Vec3[]{new Vec3(0.5, 0.45, 0.5), new Vec3(0.1, 0.3, 0.5), new Vec3(0.9, 0.3, 0.5), new Vec3(0.5, 0.3, 0.1), new Vec3(0.5, 0.3, 0.9)}) {
            Vec3 c = Vec3.atLowerCornerOf(b).add(off);
            if (eye.distanceTo(c) <= 4.2) {
               BlockHitResult hit = level.clip(new ClipContext(eye, c, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
               if (hit.getType() == HitResult.Type.MISS || level.getBlockState(hit.getBlockPos()).getBlock() instanceof BedBlock) {
                  return b;
               }
            }
         }
      }

      return null;
   }

   private void taskMid(ServerLevel level) {
      BotProfile p = this.profile;
      if (p.taskPos == null) {
         p.taskPos = TeamBrain.pickMidSpot(p);
      }

      BlockPos spot = p.taskPos;
      Nav nav = this.navigate(level, Pathfinder.near(spot, 1.0), "mid" + spot.asLong(), true, true, 90);
      if (nav == Nav.ARRIVED) {
         if (this.stayUntil < this.ticks - 1000) {
            this.stayUntil = this.ticks + 260 + RND.nextInt(200);
         }

         this.go(Vec3.atBottomCenterOf(spot), false);
         this.idle(level);
         if (this.ticks >= this.stayUntil) {
            this.stayUntil = 0;
            this.finishTask();
         }
      } else if (nav == Nav.FAILED) {
         if (p.blocks() < 8 && Game.arena().islandAt(this.position(), 2.0) == p.team.team && TeamBrain.canAfford(p.team, 4)) {
            this.gotoShop(BotProfile.Task.MID);
         } else if (this.failedPlans > 4) {
            this.finishTask();
         }
      }
   }

   private void taskHunt(ServerLevel level) {
      LivingEntity target = this.profile.taskEntity;
      if (target == null || !target.isAlive() || target.isRemoved() || !Game.isEnemy(this.profile.team, target)) {
         this.finishTask();
      } else if (target instanceof ServerPlayer sp && sp.isSpectator()) {
         this.finishTask();
      } else {
         boolean bridge = this.profile.blocks() > 0;
         Nav nav = this.navigate(level, Pathfinder.near(target.blockPosition(), 1.8), "hunt" + target.getId(), bridge, true, 25);
         this.hold(HeldItem.SWORD);
         if (nav == Nav.FAILED && this.failedPlans > 6) {
            this.finishTask();
         }
      }
   }

   private void taskEscort(ServerLevel level) {
      LivingEntity lead = this.profile.taskEntity;
      if (lead == null || !lead.isAlive() || lead.isRemoved() || lead instanceof ServerPlayer sp && sp.isSpectator()) {
         this.finishTask();
      } else {
         double d = this.distanceTo(lead);
         if (d > 4.0) {
            Nav nav = this.navigate(level, Pathfinder.near(lead.blockPosition(), 2.5), "escort" + lead.getId(), true, true, 25);
            if (nav == Nav.FAILED && this.failedPlans > 8) {
               this.finishTask();
            }
         } else {
            this.path = null;
            this.lookPos = lead.getEyePosition();
            this.idle(level);
         }
      }
   }

   private void taskHome(ServerLevel level) {
      Arena.Island is = this.profile.team.island();
      BlockPos spot = BlockPos.containing(is.spawn());
      Nav nav = this.navigate(level, Pathfinder.near(spot, 2.0), "home", true, true, 80);
      if (nav == Nav.ARRIVED || nav == Nav.FAILED && this.failedPlans > 4) {
         this.idle(level);
         if (this.getHealth() > 15.0F && this.ticks % 40 == 0) {
            if (TeamBrain.wantsToShop(this.profile)) {
               this.gotoShop(BotProfile.Task.IDLE);
            } else {
               this.finishTask();
            }
         }
      }
   }

   private void taskCutBridge(ServerLevel level) {
      BlockPos target = this.profile.taskPos;
      if (target == null || level.getBlockState(target).isAir() || Game.arena().isMap(target)) {
         this.finishTask();
      } else {
         double reach = this.getEyePosition().distanceTo(Vec3.atCenterOf(target));
         if (reach <= 4.3) {
            this.path = null;
            this.mine(level, target);
         } else {
            Nav nav = this.navigate(level, Pathfinder.near(target.above(), 2.5), "cut" + target.asLong(), false, false, 60);
            if (nav == Nav.FAILED && this.failedPlans > 3) {
               this.finishTask();
            }
         }
      }
   }

   // ======================================================================== Gegenstaende

   private void collectItems(ServerLevel level) {
      for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, this.getBoundingBox().inflate(1.6, 1.0, 1.6))) {
         ItemStack stack = item.getItem();
         int n = stack.getCount();
         Game.TeamState t = this.profile.team;
         if (stack.is(Items.IRON_INGOT)) {
            t.iron += n;
         } else if (stack.is(Items.GOLD_INGOT)) {
            t.gold += n;
         } else if (stack.is(Items.DIAMOND)) {
            t.diamond += n;
         } else if (stack.is(Items.EMERALD)) {
            t.emerald += n;
         } else {
            continue;
         }

         level.playSound(null, this.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.2F, 1.4F + RND.nextFloat() * 0.4F);
         item.discard();
      }
   }

   private void hold(HeldItem item) {
      if (item != this.held || item == HeldItem.SWORD && !this.getMainHandItem().is(this.profile.swordStack().getItem())) {
         this.held = item;
         ItemStack stack = switch (item) {
            case NONE -> ItemStack.EMPTY;
            case SWORD -> this.profile.swordStack();
            case BLOCK -> new ItemStack(this.profile.team.team.wool.asItem());
            case SHEARS -> new ItemStack(Items.SHEARS);
            case PICKAXE -> new ItemStack(switch (this.profile.pickaxe) {
               case 1 -> Items.WOODEN_PICKAXE;
               case 2 -> Items.IRON_PICKAXE;
               default -> Items.DIAMOND_PICKAXE;
            });
            case FIREBALL -> new ItemStack(Items.FIRE_CHARGE);
            case GAPPLE -> new ItemStack(Items.GOLDEN_APPLE);
         };
         this.setItemSlot(EquipmentSlot.MAINHAND, stack);
      }
   }

   public void refreshHeldItem() {
      HeldItem h = this.held;
      this.held = HeldItem.NONE;
      this.hold(h == HeldItem.NONE ? HeldItem.SWORD : h);
   }

   private void holdBlock(Block block) {
      if (this.held != HeldItem.BLOCK || !this.getMainHandItem().is(block.asItem())) {
         this.held = HeldItem.BLOCK;
         this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(block.asItem()));
      }
   }

   private void swingArm() {
      this.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
   }

   // ======================================================================== Auskunft

   public LivingEntity currentEnemy() {
      return this.enemy;
   }

   public boolean isFighting() {
      return this.enemy != null && this.distanceTo(this.enemy) < 8.0;
   }

   public String debugState() {
      return this.profile.task + (this.path == null ? "" : " p" + this.pathIdx + "/" + this.path.size() + (this.pathComplete ? "" : "~"))
         + (this.enemy != null ? " vs" + (int)this.distanceTo(this.enemy) : "")
         + (this.breakPos != null ? " brk" : "");
   }

   public static List<BedwarsBot> list(List<BotProfile> profiles) {
      List<BedwarsBot> out = new ArrayList<>();

      for (BotProfile p : profiles) {
         if (p.body != null && p.body.isAlive()) {
            out.add(p.body);
         }
      }

      return out;
   }
}
