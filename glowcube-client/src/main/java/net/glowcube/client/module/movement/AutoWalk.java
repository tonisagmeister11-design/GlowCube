package net.glowcube.client.module.movement;

import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.BooleanSetting;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Laeuft von selbst geradeaus weiter. Nachempfunden AutoWalk aus BleachHack (GPL-3.0). */
public final class AutoWalk extends Module {
    private final BooleanSetting sprint = register(new BooleanSetting("Sprint",
            "Dabei sprinten", true));

    public AutoWalk() {
        super("AutoWalk", "Laeuft von allein geradeaus", Category.MOVEMENT);
    }

    @Override
    public void onTick() {
        float yaw = player().getYRot() * Mth.DEG_TO_RAD;
        double tempo = sprint.get() ? 0.26 : 0.20;
        Vec3 jetzt = player().getDeltaMovement();
        player().setDeltaMovement(-Mth.sin(yaw) * tempo, jetzt.y, Mth.cos(yaw) * tempo);
        if (sprint.get()) {
            player().setSprinting(true);
        }
    }

    @Override
    public void onDisable() {
        if (inGame()) {
            player().setSprinting(false);
        }
    }
}
