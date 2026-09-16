package net.glowcube.client.module.player;

import com.mojang.blaze3d.platform.InputConstants;
import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.BooleanSetting;
import net.glowcube.client.core.setting.NumberSetting;

import java.util.Random;

/** Kleine Bewegungen gegen den AFK-Rauswurf. */
public final class AntiAfk extends Module {
    private final NumberSetting interval = register(new NumberSetting("Interval",
            "Sekunden zwischen zwei Regungen", 20, 5, 120, 5));
    private final BooleanSetting turn = register(new BooleanSetting("Turn", "Umsehen", true));
    private final BooleanSetting jump = register(new BooleanSetting("Jump", "Springen", false));

    private final Random random = new Random();
    private int ticks;

    public AntiAfk() {
        super("AntiAFK", "Haelt dich auf dem Server", Category.PLAYER, InputConstants.KEY_M);
    }

    @Override
    public void onEnable() {
        ticks = 0;
    }

    @Override
    public void onTick() {
        if (++ticks < interval.getInt() * 20) {
            return;
        }
        ticks = 0;

        if (turn.get()) {
            player().setYRot(player().getYRot() + (random.nextFloat() - 0.5f) * 60.0f);
        }
        if (jump.get() && player().onGround()) {
            player().jumpFromGround();
        }
    }
}
