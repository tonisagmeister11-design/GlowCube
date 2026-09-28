package de.gtacity.entity.ai;

import de.gtacity.entity.PoliceEntity;
import de.gtacity.gameplay.WantedSystem;
import de.gtacity.item.Weapons;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/** Police keep some distance and shoot at wanted players with two or more stars. */
public class PoliceGunGoal extends Goal {
    private final PoliceEntity cop;
    private int cooldown;
    private int repath;

    public PoliceGunGoal(PoliceEntity cop) {
        this.cop = cop;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = cop.getTarget();
        return target != null && target.isAlive() && WantedSystem.level(target) >= 2;
    }

    @Override
    public void start() {
        cop.setAggressive(true); // synced to the client: the renderer raises the arms to aim
    }

    @Override
    public void stop() {
        cop.setAggressive(false);
        cop.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = cop.getTarget();
        if (target == null) {
            return;
        }
        cop.getLookControl().setLookAt(target, 30.0F, 30.0F);
        double dist = cop.distanceTo(target);
        boolean sees = cop.getSensing().hasLineOfSight(target);
        if (--repath <= 0) {
            repath = 10;
            if (dist > 14 || !sees) {
                cop.getNavigation().moveTo(target, 1.2);
            } else if (dist < 5) {
                cop.getNavigation().stop();
            }
        }
        if (--cooldown <= 0 && sees && dist < 40) {
            int stars = WantedSystem.level(target);
            boolean swat = cop.isSwat();
            float damage = swat ? 4.0F : 3.0F;
            float spread = Math.max(2.0F, 9.0F - stars * 1.2F);
            Weapons.npcShoot(cop, target, damage, spread);
            cooldown = swat ? 6 + cop.getRandom().nextInt(6) : 18 + cop.getRandom().nextInt(14);
        }
    }
}
