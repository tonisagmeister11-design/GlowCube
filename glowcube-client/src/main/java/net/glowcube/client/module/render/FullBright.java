package net.glowcube.client.module.render;

import com.mojang.blaze3d.platform.InputConstants;
import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.util.Gamma;

public final class FullBright extends Module {
    public FullBright() {
        super("Fullbright", "Keine Dunkelheit mehr", Category.RENDER, InputConstants.KEY_H);
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
