package de.gtacity.entity.ai;

import de.gtacity.entity.NpcEntity;
import de.gtacity.world.CityLayout;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/** Pedestrians stroll along the sidewalks of the city grid and cross at zebra crossings. */
public class SidewalkWalkGoal extends Goal {
    private final NpcEntity npc;
    private final double speed;
    private Direction heading;
    private Vec3 target;

    public SidewalkWalkGoal(NpcEntity npc, double speed) {
        this.npc = npc;
        this.speed = speed;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (npc.isPanicking() || npc.getTarget() != null || npc.isPassenger()) {
            return false;
        }
        if (npc.getRandom().nextInt(npc.getNavigation().isDone() ? 10 : 60) != 0) {
            return false;
        }
        target = pickTarget();
        return target != null;
    }

    private Vec3 pickTarget() {
        int x = Mth.floor(npc.getX()), z = Mth.floor(npc.getZ());
        if (!CityLayout.isSidewalk(x, z)) {
            // walk back to the nearest sidewalk
            for (int r = 1; r <= 12; r++) {
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    int tx = x + d.getStepX() * r, tz = z + d.getStepZ() * r;
                    if (CityLayout.isSidewalk(tx, tz)) {
                        return new Vec3(tx + 0.5, npc.getY(), tz + 0.5);
                    }
                }
            }
            return DefaultRandomPos.getPos(npc, 10, 4);
        }
        int lx = CityLayout.local(x), lz = CityLayout.local(z);
        boolean nsStreet = lx < CityLayout.CORRIDOR;
        boolean ewStreet = lz < CityLayout.CORRIDOR;
        if (heading == null || npc.getRandom().nextInt(8) == 0) {
            heading = Direction.Plane.HORIZONTAL.getRandomDirection(npc.getRandom());
        }
        if (nsStreet && ewStreet) {
            // at a corner: maybe turn
            if (npc.getRandom().nextInt(3) == 0) {
                heading = npc.getRandom().nextBoolean() ? heading.getClockWise() : heading.getCounterClockWise();
            }
        } else if (nsStreet && heading.getAxis() != Direction.Axis.Z) {
            heading = npc.getRandom().nextBoolean() ? Direction.NORTH : Direction.SOUTH;
        } else if (ewStreet && heading.getAxis() != Direction.Axis.X) {
            heading = npc.getRandom().nextBoolean() ? Direction.EAST : Direction.WEST;
        }
        int dist = 6 + npc.getRandom().nextInt(12);
        for (int d = dist; d >= 3; d--) {
            int tx = x + heading.getStepX() * d, tz = z + heading.getStepZ() * d;
            if (CityLayout.isSidewalk(tx, tz)) {
                return new Vec3(tx + 0.5, npc.getY(), tz + 0.5);
            }
        }
        heading = heading.getOpposite();
        return null;
    }

    @Override
    public void start() {
        npc.getNavigation().moveTo(target.x, target.y, target.z, speed);
    }

    @Override
    public boolean canContinueToUse() {
        return !npc.getNavigation().isDone() && !npc.isPanicking() && npc.getTarget() == null;
    }
}
