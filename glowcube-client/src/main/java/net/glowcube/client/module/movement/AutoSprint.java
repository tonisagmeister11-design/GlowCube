package net.glowcube.client.module.movement;

import com.mojang.blaze3d.platform.InputConstants;
import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.util.Movement;

public final class AutoSprint extends Module {
    public AutoSprint() {
        super("AutoSprint", "Immer sprinten", Category.MOVEMENT, InputConstants.KEY_J);
    }

    @Override
    public void onTick() {
        if (Movement.forward() > 0.0 && !player().isUsingItem()) {
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
