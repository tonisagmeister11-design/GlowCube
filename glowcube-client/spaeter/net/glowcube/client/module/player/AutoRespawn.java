package net.glowcube.client.module.player;

import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.minecraft.client.gui.screens.DeathScreen;

public final class AutoRespawn extends Module {
    public AutoRespawn() {
        super("AutoRespawn", "Sofort wieder einsteigen", Category.PLAYER);
    }

    @Override
    public void onTick() {
        if (mc.screen instanceof DeathScreen) {
            player().respawn();
            mc.setScreen(null);
        }
    }
}
