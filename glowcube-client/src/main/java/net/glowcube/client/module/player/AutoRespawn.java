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
        if (net.glowcube.client.render.Netz.bildschirm() instanceof DeathScreen) {
            player().respawn();
            net.glowcube.client.render.Netz.bildschirmSetzen(null);
        }
    }
}
