package net.glowcube.client.module.movement;

import com.mojang.blaze3d.platform.InputConstants;
import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;

/**
 * Sagt dem Server in jedem Bewegungspaket, man stehe auf dem Boden - dann
 * rechnet er keinen Sturz. Gesetzt wird das im
 * ServerboundMovePlayerPacketMixin, hier steht nur der Schalter.
 */
public final class NoFall extends Module {
    private static NoFall instance;

    public NoFall() {
        super("NoFall", "Kein Sturzschaden", Category.MOVEMENT, InputConstants.KEY_N);
        instance = this;
    }

    @Override
    public void onTick() {
        player().resetFallDistance();
    }

    public static boolean active() {
        return instance != null && instance.isEnabled();
    }
}
