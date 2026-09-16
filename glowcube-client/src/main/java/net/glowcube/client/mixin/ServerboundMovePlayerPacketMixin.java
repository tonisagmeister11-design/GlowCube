package net.glowcube.client.mixin;

import net.glowcube.client.module.movement.NoFall;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NoFall. Der Server rechnet den Sturzschaden aus dem, was der Client ueber
 * seine eigene Lage meldet. Steht in jedem Bewegungspaket "ich stehe",
 * entsteht nie ein Sturz.
 */
@Mixin(ServerboundMovePlayerPacket.class)
public abstract class ServerboundMovePlayerPacketMixin {

    @Shadow
    @Final
    @Mutable
    private boolean onGround;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void glowcube$noFall(CallbackInfo info) {
        if (NoFall.active()) {
            this.onGround = true;
        }
    }
}
