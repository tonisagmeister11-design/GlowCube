package net.glowcube.client.mixin;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import net.glowcube.client.core.Packets;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Der eine Punkt, an dem GlowCube den Paketstrom sieht.
 *
 * <p>Beide Stellen sind die, die Meteor benutzt: {@code send} fuer alles, was
 * hinausgeht, und {@code channelRead0} kurz vor {@code genericsFtw} fuer
 * alles, was hereinkommt. Vor genau diesem Aufruf, weil das Spiel das Paket
 * dort zum ersten Mal anfasst - wer frueher abbricht, laesst es nie ankommen,
 * wer spaeter abbricht, kommt zu spaet.
 */
@Mixin(Connection.class)
public abstract class ConnectionMixin {

    @Inject(
            method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void glowcube$senden(Packet<?> packet, ChannelFutureListener listener, CallbackInfo info) {
        if (Packets.send(packet)) {
            info.cancel();
        }
    }

    @Inject(
            method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/Connection;genericsFtw(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;)V",
                    shift = At.Shift.BEFORE),
            cancellable = true)
    private void glowcube$empfangen(ChannelHandlerContext ctx, Packet<?> packet, CallbackInfo info) {
        if (Packets.receive(packet)) {
            info.cancel();
        }
    }
}
