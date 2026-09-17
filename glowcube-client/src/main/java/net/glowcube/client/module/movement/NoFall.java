package net.glowcube.client.module.movement;

import com.mojang.blaze3d.platform.InputConstants;
import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.mixininterface.AmBodenSetzbar;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

/**
 * Sagt dem Server in jedem Bewegungspaket, man stehe auf dem Boden - dann
 * rechnet er keinen Sturz.
 */
public final class NoFall extends Module {
    private static NoFall instance;

    public NoFall() {
        super("NoFall", "Kein Sturzschaden", Category.MOVEMENT, InputConstants.KEY_N);
        instance = this;
    }

    @Override
    public boolean onPacketSend(Packet<?> packet) {
        if (packet instanceof ServerboundMovePlayerPacket) {
            ((AmBodenSetzbar) packet).glowcube$setzeAmBoden(true);
        }
        return false;
    }

    @Override
    public void onTick() {
        player().resetFallDistance();
    }

    public static boolean active() {
        return instance != null && instance.isEnabled();
    }
}
