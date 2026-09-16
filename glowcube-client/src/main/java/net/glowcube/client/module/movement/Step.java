package net.glowcube.client.module.movement;

import com.mojang.blaze3d.platform.InputConstants;
import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.NumberSetting;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Stufenhoehe hochsetzen - man laeuft Bloecke hoch, ohne zu springen. */
public final class Step extends Module {
    private static final double VANILLA = 0.6;

    private final NumberSetting height = register(new NumberSetting("Height",
            "Wie hoch ohne Sprung", 1.0, 0.6, 3.0, 0.1));

    public Step() {
        super("Step", "Stufen hochlaufen", Category.MOVEMENT, InputConstants.KEY_V);
    }

    @Override
    public void onTick() {
        apply(height.get());
    }

    @Override
    public void onDisable() {
        if (inGame()) {
            apply(VANILLA);
        }
    }

    private void apply(double value) {
        AttributeInstance attribute = player().getAttribute(Attributes.STEP_HEIGHT);
        if (attribute != null && attribute.getBaseValue() != value) {
            attribute.setBaseValue(value);
        }
    }

    @Override
    public String hudSuffix() {
        return height.display();
    }
}
