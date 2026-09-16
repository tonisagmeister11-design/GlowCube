package net.glowcube.client.module.movement;

import com.mojang.blaze3d.platform.InputConstants;
import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.BooleanSetting;
import net.glowcube.client.core.setting.NumberSetting;
import net.glowcube.client.util.Movement;
import net.minecraft.world.phys.Vec3;

public final class Speed extends Module {
    private final NumberSetting factor = register(new NumberSetting("Factor",
            "Vielfaches der normalen Laufgeschwindigkeit", 2.0, 1.1, 6.0, 0.1));
    private final BooleanSetting onlyOnGround = register(new BooleanSetting("Ground",
            "Nur am Boden - in der Luft faellt es sofort auf", true));

    public Speed() {
        super("Speed", "Schneller laufen", Category.MOVEMENT, InputConstants.KEY_G);
    }

    @Override
    public void onTick() {
        if (!Movement.moving()) {
            return;
        }
        if (onlyOnGround.get() && !player().onGround()) {
            return;
        }
        // 0.13 entspricht ungefaehr dem Sprinttempo eines Spielers pro Tick.
        Vec3 push = Movement.direction(player(), 0.13 * factor.get());
        Vec3 current = player().getDeltaMovement();
        player().setDeltaMovement(push.x, current.y, push.z);
    }

    @Override
    public String hudSuffix() {
        return factor.display() + "x";
    }
}
