package net.glowcube.client.module.render;

import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.util.Gamma;

public final class FullBright extends Module {
    public FullBright() {
        super("Fullbright", "Keine Dunkelheit mehr", Category.RENDER);
    }

    @Override
    public void onEnable() {
        Gamma.acquire();
    }

    @Override
    public void onDisable() {
        Gamma.release();
    }
}
