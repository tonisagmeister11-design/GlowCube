package de.glowcube.bedwars;

import java.util.List;
import java.util.Random;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Ein Bedwars-Bot: ein Husk-Koerper (brennt nicht in der Sonne), dessen vanilla-KI komplett
 * abgeschaltet ist. Gedacht, gelaufen, gebaut und geschlagen wird nur in {@link #customServerAiStep}.
 */
public final class BedwarsBot extends Husk {
    private static final Random RND = new Random();

    public BotProfile profile;

    private int age;
    private int attackCd;
    private int placeCd;
    private int strafeDir = 1;
    private int strafeTicks;
    private int mining;
    private int fortifyIndex;
    private int wallMining;
    private int eatCd;
    private LivingEntity enemy;
    private int enemyCheck;
    private int stuckTicks;
    private Vec3 lastPos = Vec3.ZERO;

    public BedwarsBot(EntityType<? extends Husk> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        // bewusst leer: die Bots denken selbst
    }

    @Override
    protected boolean isSunSensitive() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        // kein Friendly Fire
        if (profile != null && source.getEntity() != null && Game.teamOf(source.getEntity()) == profile.team.team) {
            return false;
        }
        return super.hurtServer(level, source, amount);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (profile == null || !Game.running()) {
            return;
        }
        age++;
        if (attackCd > 0) attackCd--;
        if (placeCd > 0) placeCd--;
        if (eatCd > 0) eatCd--;

        if (age % 10 == 0) {
            collectItems(level);
        }
        if (age % 40 == 0 && profile.shop()) {
            profile.equip(this);
        }

        if (enemyCheck-- <= 0) {
            enemy = Game.nearestEnemy(this, 13.0);
            enemyCheck = 4 + RND.nextInt(4);
        }
        if (enemy != null && !enemy.isAlive()) {
            enemy = null;
        }

        if (getHealth() < 8.0f && profile.gapples > 0 && eatCd == 0) {
            profile.gapples--;
            heal(8.0f);
            eatCd = 60;
            level.playSound(null, blockPosition(), SoundEvents.GENERIC_EAT.value(), SoundSource.NEUTRAL, 1.0f, 1.0f);
        }

        boolean raider = profile.role == BotProfile.Role.RAIDER;
        boolean atHome = distanceToSqr(Vec3.atCenterOf(profile.team.island)) < 9 * 9;
        double engage;
        if (raider) {
            // Raider haben ein Ziel: nur kaempfen, wenn der Gegner direkt dran ist oder sie angreift
            engage = enemy != null && enemy == getLastHurtByMob() ? 8.0 : 4.0;
        } else {
            engage = atHome || profile.role == BotProfile.Role.DEFENDER ? 12.0 : 7.0;
        }
        if (enemy != null && distanceToSqr(enemy) < engage * engage) {
            fight(level, enemy);
            mining = 0;
        } else {
            routine(level);
        }
        unstick(level);
    }

    // --- Kampf ------------------------------------------------------------------------------

    private void fight(ServerLevel level, LivingEntity target) {
        getLookControl().setLookAt(target, 60.0f, 60.0f);
        double d = Math.sqrt(distanceToSqr(target));
        Vec3 to = target.position().subtract(position());
        Vec3 flat = new Vec3(to.x, 0, to.z);
        Vec3 dir = flat.lengthSqr() > 1.0E-4 ? flat.normalize() : Vec3.ZERO;

        if (d > 2.4) {
            stepTowards(level, target.position(), 1.18, true);
        } else if (d < 1.4) {
            // zu nah dran: kurz zurueck, dann wieder rein - wirkt wie Spacing
            Vec3 back = position().subtract(dir.scale(1.2));
            if (groundAt(level, back)) walk(back, 1.0);
        } else {
            if (strafeTicks-- <= 0) {
                strafeDir = RND.nextBoolean() ? 1 : -1;
                strafeTicks = 8 + RND.nextInt(14);
            }
            Vec3 side = new Vec3(-dir.z, 0, dir.x).scale(strafeDir);
            Vec3 goal = position().add(side.scale(1.4)).add(dir.scale(0.4));
            if (groundAt(level, goal)) walk(goal, 1.15);
        }

        if (onGround() && d < 3.6 && RND.nextInt(16) == 0) {
            getJumpControl().jump();
        }

        if (d <= 3.0 && attackCd <= 0 && hasLineOfSight(target)) {
            doHurtTarget(level, target);
            swingForAttack(InteractionHand.MAIN_HAND);
            attackCd = 9 + RND.nextInt(5);
        }
    }

    // --- Alltag -----------------------------------------------------------------------------

    private void routine(ServerLevel level) {
        Game.TeamState team = profile.team;
        BotProfile.Role role = profile.role;

        // Raider, die nichts mehr zum Bauen haben, holen erst Nachschub
        if (role == BotProfile.Role.RAIDER && profile.blocks < 6 && profile.team.iron < 4) {
            role = BotProfile.Role.GATHERER;
        }
        // Mit der Zeit wird jeder zum Angreifer, sonst steht die Runde still
        if (profile.role == BotProfile.Role.DEFENDER && age > 1800 && age % 200 == 0 && RND.nextInt(3) == 0) {
            profile.role = BotProfile.Role.RAIDER;
        }
        // Sammler werden irgendwann zu Raidern, wenn sie gut ausgestattet sind
        if (profile.role == BotProfile.Role.GATHERER && profile.blocks >= 24 && profile.swordTier >= 1 && age % 200 == 0 && RND.nextInt(3) == 0) {
            profile.role = BotProfile.Role.RAIDER;
        }

        switch (role) {
            case GATHERER -> gather(level, team);
            case DEFENDER -> defend(level, team);
            case RAIDER -> raid(level, team);
        }
    }

    private void gather(ServerLevel level, Game.TeamState team) {
        // Direkt am Generator stehen - Items werden nur im Umkreis von ~2.5 Bloecken eingesammelt
        Vec3 gen = Vec3.atBottomCenterOf(team.generator);
        if (distanceToSqr(gen.x, gen.y - 1.0, gen.z + 1.0) > 1.6 * 1.6) {
            stepTowards(level, new Vec3(gen.x, gen.y - 1.0, gen.z + 1.0), 1.0, false);
        } else if (age % 80 == 0 && RND.nextInt(6) == 0 && profile.swordTier >= 1) {
            profile.role = BotProfile.Role.RAIDER; // gut ausgeruestet: Zeit fuer einen Angriff
        }
    }

    private void defend(ServerLevel level, Game.TeamState team) {
        if (!profile.fortified && profile.blocks >= 10 && team.bedAlive) {
            fortify(level, team);
            return;
        }
        Vec3 bed = Vec3.atCenterOf(team.island.offset(-1, 1, 0));
        if (distanceToSqr(bed) > 5 * 5) {
            stepTowards(level, bed, 1.0, false);
        } else if (age % 50 == 0) {
            Vec3 goal = bed.add(RND.nextDouble() * 6 - 3, 0, RND.nextDouble() * 6 - 3);
            if (groundAt(level, goal)) walk(goal, 0.8);
        }
    }

    private List<BlockPos> fortifyList;

    /** Baut eine Wolle-Huelle um das Bett: darueber, daneben und an den Enden. */
    private void fortify(ServerLevel level, Game.TeamState team) {
        if (fortifyList == null) {
            List<BlockPos> bed = Game.arena().bedBlocks(team.team);
            if (bed.isEmpty()) {
                return;
            }
            fortifyList = new java.util.ArrayList<>();
            for (BlockPos b : bed) {
                for (Direction d : new Direction[] {Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                    BlockPos c = b.relative(d);
                    if (!bed.contains(c) && !fortifyList.contains(c)) {
                        fortifyList.add(c);
                    }
                }
            }
        }
        if (age % 3 != 0) {
            return;
        }
        while (fortifyIndex < fortifyList.size() && !level.getBlockState(fortifyList.get(fortifyIndex)).isAir()) {
            fortifyIndex++;
        }
        if (fortifyIndex >= fortifyList.size() || profile.blocks <= 0) {
            profile.fortified = true;
            return;
        }
        BlockPos p = fortifyList.get(fortifyIndex);
        if (distanceToSqr(Vec3.atCenterOf(p)) < 36) {
            placeBlock(level, p);
            fortifyIndex++;
        } else {
            stepTowards(level, Vec3.atCenterOf(p), 1.0, false);
        }
    }

    private void raid(ServerLevel level, Game.TeamState team) {
        Game.TeamState target = Game.raidTarget(team);
        if (target == null) {
            Vec3 foe = Game.anyEnemyPosition(team);
            if (foe != null) stepTowards(level, foe, 1.15, true);
            return;
        }
        Vec3 goal;
        if (target.bedAlive) {
            goal = Vec3.atCenterOf(target.island.offset(-1, 1, 0));
        } else {
            goal = Vec3.atCenterOf(target.island.offset(0, 1, 0));
        }
        double d = Math.sqrt(distanceToSqr(goal));
        // Ist das Bett eingemauert, erst die Wolle davor aufbrechen
        if (target.bedAlive && d < 7.0) {
            BlockPos wall = blockingBlock(level, goal);
            if (wall != null) {
                getLookControl().setLookAt(wall.getX() + 0.5, wall.getY() + 0.5, wall.getZ() + 0.5);
                if (distanceToSqr(Vec3.atCenterOf(wall)) > 3.6 * 3.6) {
                    stepTowards(level, goal, 1.1, true);
                    return;
                }
                if (wallMining++ % 5 == 0) swingForAttack(InteractionHand.MAIN_HAND);
                if (wallMining >= 12) {
                    wallMining = 0;
                    level.destroyBlock(wall, false);
                }
                return;
            }
        }
        wallMining = 0;
        if (target.bedAlive && d < 3.4) {
            getLookControl().setLookAt(goal.x, goal.y, goal.z);
            if (mining % 6 == 0) swingForAttack(InteractionHand.MAIN_HAND);
            if (++mining >= 26 + RND.nextInt(10)) {
                mining = 0;
                Game.destroyBed(target, profile.name + " (" + team.team.label + ")", team);
            }
            return;
        }
        mining = 0;
        stepTowards(level, goal, 1.1, true);
    }

    /** Erster abbaubarer Block (kein Teil der Karte, kein Bett) auf dem Weg zum Ziel, sonst null. */
    private BlockPos blockingBlock(ServerLevel level, Vec3 goal) {
        Vec3 from = getEyePosition();
        Vec3 dir = goal.subtract(from);
        double len = dir.length();
        if (len < 1.0E-3) return null;
        dir = dir.scale(1.0 / len);
        for (double t = 0.8; t < len - 0.9; t += 0.4) {
            BlockPos p = BlockPos.containing(from.add(dir.scale(t)));
            BlockState st = level.getBlockState(p);
            if (st.isAir() || !st.getFluidState().isEmpty()) continue;
            if (st.getBlock() instanceof net.minecraft.world.level.block.BedBlock) return null;
            return Game.arena().map.contains(p.asLong()) ? null : p;
        }
        return null;
    }

    // --- Bewegung und Bruecken --------------------------------------------------------------

    /** Ein Schritt Richtung Ziel; mit {@code bridge} werden Luecken mit Wolle geschlossen. */
    private void stepTowards(ServerLevel level, Vec3 goal, double speed, boolean bridge) {
        double dx = goal.x - getX();
        double dz = goal.z - getZ();
        if (Math.abs(dx) < 0.6 && Math.abs(dz) < 0.6) {
            return;
        }
        Direction primary = Math.abs(dx) >= Math.abs(dz)
                ? (dx > 0 ? Direction.EAST : Direction.WEST)
                : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
        Direction secondary = Math.abs(dx) >= Math.abs(dz)
                ? (dz > 0 ? Direction.SOUTH : Direction.NORTH)
                : (dx > 0 ? Direction.EAST : Direction.WEST);

        BlockPos feet = blockPosition();
        for (Direction dir : new Direction[] {primary, secondary}) {
            BlockPos next = feet.relative(dir);
            BlockState at = level.getBlockState(next);
            BlockState head = level.getBlockState(next.above());
            if (!at.getCollisionShape(level, next).isEmpty() && !head.getCollisionShape(level, next.above()).isEmpty()) {
                continue; // zwei hoch zu - anderen Weg versuchen
            }
            if (!at.getCollisionShape(level, next).isEmpty()) {
                getJumpControl().jump(); // eine Stufe hoch
                walk(Vec3.atBottomCenterOf(next.above()), speed);
                return;
            }
            BlockPos below = next.below();
            if (level.getBlockState(below).getCollisionShape(level, below).isEmpty()
                    && level.getBlockState(below.below()).getCollisionShape(level, below.below()).isEmpty()) {
                if (!bridge || profile.blocks <= 0) {
                    continue;
                }
                if (placeCd == 0) {
                    placeBlock(level, below);
                    placeCd = 4 + RND.nextInt(3);
                }
                walk(Vec3.atBottomCenterOf(feet).add(dir.getStepX() * 0.3, 0, dir.getStepZ() * 0.3), speed);
                getLookControl().setLookAt(goal.x, getEyeY(), goal.z);
                return;
            }
            walk(Vec3.atBottomCenterOf(next), speed);
            getLookControl().setLookAt(goal.x, getEyeY(), goal.z);
            return;
        }
    }

    private void placeBlock(ServerLevel level, BlockPos pos) {
        level.setBlock(pos, profile.team.team.wool.defaultBlockState(), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 1.0f, 1.0f);
        swingForAttack(InteractionHand.MAIN_HAND);
        profile.blocks--;
    }

    private void walk(Vec3 p, double speed) {
        getMoveControl().setWantedPosition(p.x, p.y, p.z, speed);
    }

    private boolean groundAt(ServerLevel level, Vec3 p) {
        BlockPos below = BlockPos.containing(p.x, p.y - 0.5, p.z);
        return !level.getBlockState(below).getCollisionShape(level, below).isEmpty()
                || !level.getBlockState(below.below()).getCollisionShape(level, below.below()).isEmpty();
    }

    /** Haengt der Bot fest, kurz springen. */
    private void unstick(ServerLevel level) {
        // Rettung: wer tief unter der Inselhoehe auf irgendetwas gelandet ist, kommt zum Team-Spawn zurueck
        if (age % 40 == 0 && getY() < Arena.Y - 3 && onGround()) {
            Vec3 spawn = profile.team.spawn;
            teleportTo(spawn.x, spawn.y, spawn.z);
            fallDistance = 0;
            setDeltaMovement(Vec3.ZERO);
            return;
        }
        if (age % 20 != 0) return;
        if (position().distanceToSqr(lastPos) < 0.04 && (enemy != null || profile.role == BotProfile.Role.RAIDER)) {
            if (++stuckTicks >= 2) {
                getJumpControl().jump();
                stuckTicks = 0;
            }
        } else {
            stuckTicks = 0;
        }
        lastPos = position();
    }

    // --- Ressourcen -------------------------------------------------------------------------

    private void collectItems(ServerLevel level) {
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(2.6, 1.5, 2.6))) {
            var stack = item.getItem();
            int n = stack.getCount();
            if (stack.is(Items.IRON_INGOT)) profile.team.iron += n;
            else if (stack.is(Items.GOLD_INGOT)) profile.team.gold += n;
            else if (stack.is(Items.DIAMOND)) profile.team.diamond += n;
            else if (stack.is(Items.EMERALD)) profile.team.emerald += n;
            else continue;
            item.discard();
        }
    }

    public Component displayName() {
        return Component.literal("[" + profile.team.team.label + "] " + profile.name).withStyle(profile.team.team.format);
    }
}
