package net.glowcube.client.mixin;

import net.glowcube.client.core.Packets;
import net.glowcube.client.module.movement.NoSlow;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Die Stellen, an denen der Spieler dem Server seine Lage meldet, sich selbst
 * bewegt und seine Bewegungseingabe verrechnet.
 *
 * <p>{@code sendPosition} ist dieselbe Stelle, an der Meteor sein
 * SendMovementPacketsEvent ausloest und BleachHack sein
 * EventSendMovementPackets - nur unter dem Mojang-Namen. {@code move} ist
 * BleachHacks EventClientMove. Beide treiben die Rotations und PacketFly.
 *
 * <p><b>Wichtige Korrektur.</b> NoSlow hing frueher an einem {@code @Redirect}
 * auf {@code isUsingItem()} in {@code modifyInput}. Der brachte das Spiel beim
 * Start zum Absturz - und weil die Mixin-Konfiguration als Ganzes gilt, kam
 * damit gar nichts mehr hoch, auch KillAura und der Rest nicht. Jetzt haengt
 * NoSlow an einem {@code @Inject} auf den Rueckgabewert von
 * {@code modifyInput}: das laedt sauber. Wird gerade ein Gegenstand benutzt,
 * wird die auf ein Fuenftel gebremste Eingabe wieder hochskaliert - das hebt
 * genau die Vanilla-Bremse auf.
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

    @Inject(method = "modifyInput", at = @At("RETURN"), cancellable = true)
    private void glowcube$keinTempoverlust(Vec2 eingabe, CallbackInfoReturnable<Vec2> info) {
        LocalPlayer spieler = (LocalPlayer) (Object) this;
        // Nur wenn NoSlow an ist und wirklich etwas benutzt wird - und nicht
        // gleichzeitig geschlichen (dann bliebe die Schleich-Bremse).
        if (NoSlow.beimBenutzen() && spieler.isUsingItem() && !spieler.isShiftKeyDown()) {
            Vec2 gebremst = info.getReturnValue();
            if (gebremst != null) {
                // Fuenffach hebt die Vanilla-Bremse von 0,2 genau auf.
                info.setReturnValue(gebremst.scale(5.0f));
            }
        }
    }
}
