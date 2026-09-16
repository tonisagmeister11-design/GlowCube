package net.glowcube.client.module.combat;

import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.BooleanSetting;
import net.glowcube.client.core.setting.ModeSetting;
import net.glowcube.client.core.setting.NumberSetting;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.List;

/** Schlaegt zu, was in Reichweite kommt. */
public final class KillAura extends Module {
    private final NumberSetting range = register(new NumberSetting("Range",
            "Reichweite in Bloecken", 4.0, 2.0, 6.0, 0.1));
    private final ModeSetting priority = register(new ModeSetting("Priority",
            "Wer zuerst drankommt", "Distance", "Distance", "Health"));
    private final BooleanSetting players = register(new BooleanSetting("Players", "Spieler", true));
    private final BooleanSetting hostile = register(new BooleanSetting("Hostile", "Monster", true));
    private final BooleanSetting passive = register(new BooleanSetting("Passive", "Tiere", false));
    private final BooleanSetting throughWalls = register(new BooleanSetting("Walls",
            "Auch durch Waende - faellt sofort auf", false));
    private final BooleanSetting waitCooldown = register(new BooleanSetting("Cooldown",
            "Auf die Waffenaufladung warten", true));

    public KillAura() {
        super("KillAura", "Greift Ziele in Reichweite an", Category.COMBAT);
    }

    @Override
    public void onTick() {
        if (waitCooldown.get() && player().getAttackStrengthScale(0.0f) < 1.0f) {
            return;
        }

        LivingEntity target = findTarget();
        if (target == null) {
            return;
        }
        mc.gameMode.attack(player(), target);
        player().swing(InteractionHand.MAIN_HAND);
    }

    private LivingEntity findTarget() {
        double reach = range.get();
        AABB area = player().getBoundingBox().inflate(reach);
        List<Entity> candidates = level().getEntities(player(), area, this::isTarget);

        Comparator<Entity> order = priority.is("Health")
                ? Comparator.comparingDouble(entity -> ((LivingEntity) entity).getHealth())
                : Comparator.comparingDouble(entity -> entity.distanceToSqr(player()));

        return candidates.stream()
                .filter(entity -> entity.distanceTo(player()) <= reach)
                .min(order)
                .map(entity -> (LivingEntity) entity)
                .orElse(null);
    }

    private boolean isTarget(Entity entity) {
        if (!(entity instanceof LivingEntity living) || living == player()) {
            return false;
        }
        if (!living.isAlive() || living.isInvulnerable()) {
            return false;
        }
        if (!throughWalls.get() && !player().hasLineOfSight(living)) {
            return false;
        }
        if (living instanceof Player) {
            return players.get();
        }
        if (living instanceof Monster) {
            return hostile.get();
        }
        if (living instanceof Animal) {
            return passive.get();
        }
        return false;
    }

    @Override
    public String hudSuffix() {
        return range.display();
    }
}
