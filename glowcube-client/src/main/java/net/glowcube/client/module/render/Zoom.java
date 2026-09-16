package net.glowcube.client.module.render;

import com.mojang.blaze3d.platform.InputConstants;
import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.NumberSetting;
import net.glowcube.client.mixin.OptionInstanceAccessor;

public final class Zoom extends Module {
    private final NumberSetting factor = register(new NumberSetting("Factor",
            "Wie stark herangeholt wird", 4.0, 1.5, 12.0, 0.5));

    private int original = -1;

    public Zoom() {
        super("Zoom", "Fernglas auf Tastendruck", Category.RENDER, InputConstants.KEY_C);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onEnable() {
        original = mc.options.fov().get();
        ((OptionInstanceAccessor<Integer>) (Object) mc.options.fov())
                .glowcube$setValue((int) Math.max(1, Math.round(original / factor.get())));
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onDisable() {
        if (original > 0) {
            ((OptionInstanceAccessor<Integer>) (Object) mc.options.fov()).glowcube$setValue(original);
            original = -1;
        }
    }

    @Override
    public String hudSuffix() {
        return factor.display() + "x";
    }
}
