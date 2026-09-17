package net.glowcube.client.mixin;

import net.glowcube.client.core.Packets;
import net.glowcube.client.module.movement.NoSlow;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Die beiden Stellen, an denen der Spieler dem Server seine Lage meldet und an
 * denen er sich selbst bewegt.
 *
 * <p>{@code sendPosition} ist dieselbe Stelle, an der Meteor sein
 * SendMovementPacketsEvent ausloest und BleachHack sein
 * EventSendMovementPackets - nur unter dem Mojang-Namen. {@code move} ist
 * BleachHacks EventClientMove. PacketFly haengt an beiden: es faehrt den
 * Spieler ueber Pakete statt ueber die Spielphysik, und dafuer muss die
 * Physik still sein.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {

    @Inject(method = "sendPosition", at = @At("HEAD"), cancellable = true)
    private void glowcube$lageMelden(CallbackInfo info) {
        if (Packets.sendMovement()) {
            info.cancel();
        }
    }

    @Inject(method = "sendPosition", at = @At("TAIL"))
    private void glowcube$lageGemeldet(CallbackInfo info) {
        Packets.afterMovement();
    }

    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void glowcube$bewegen(MoverType art, Vec3 bewegung, CallbackInfo info) {
        if (Packets.blockClientMove()) {
            info.cancel();
        }
    }

    /**
     * NoSlow. An der einen Stelle, an der das Spiel die Bewegungseingabe wegen
     * des Benutzens eines Gegenstands herunterrechnet, faengt dieser Redirect
     * die Abfrage {@code isUsingItem()} ab und liefert false - dann greift die
     * Bremse nicht. Dieselbe Stelle, die Meteor benutzt.
     */
    @Redirect(
            method = "modifyInput",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z"),
            require = 0)
    private boolean glowcube$keinTempoverlust(LocalPlayer spieler) {
        if (NoSlow.beimBenutzen()) {
            return false;
        }
        return spieler.isUsingItem();
    }
}
