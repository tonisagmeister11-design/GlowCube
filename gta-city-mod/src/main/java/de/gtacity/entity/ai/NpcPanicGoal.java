package de.gtacity.entity.ai;

import de.gtacity.entity.NpcEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/** Run away from gunfire, explosions and attackers. */
public class NpcPanicGoal extends Goal {
    private final NpcEntity npc;
    private final double speed;
    private Vec3 target;

    public NpcPanicGoal(NpcEntity npc, double speed) {
        this.npc = npc;
        this.speed = speed;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!npc.isPanicking() || npc.getTarget() != null || npc.threat() == null) {
            return false;
        }
        target = DefaultRandomPos.getPosAway(npc, 18, 6, npc.threat());
        return target != null;
    }

    @Override
    public void start() {
        npc.getNavigation().moveTo(target.x, target.y, target.z, speed);
    }

    @Override
    public boolean canContinueToUse() {
        return npc.isPanicking() && !npc.getNavigation().isDone();
    }
}
