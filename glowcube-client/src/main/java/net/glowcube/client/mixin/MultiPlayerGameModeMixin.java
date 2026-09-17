package net.glowcube.client.mixin;

import net.glowcube.client.core.Interactions;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Die vier Tueren zur Welt.
 *
 * <p>Alles, was der Spieler mit Bloecken und Wesen anstellt, geht durch diese
 * Klasse - Meteor haengt an denselben vier Methoden (dort unter den
 * Yarn-Namen attackBlock, interactBlock, attackEntity, interactEntity).
 * NoInteract braucht sie, um einzelne Absichten fallen zu lassen, bevor sie
 * ueberhaupt ein Paket werden.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {

    @Inject(method = "startDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void glowcube$abbauen(BlockPos pos, Direction seite, CallbackInfoReturnable<Boolean> info) {
        if (Interactions.blockBreak(pos)) {
            info.setReturnValue(false);
        }
    }

    // Erster Parameter ist hier der LocalPlayer, nicht Player - in 1.21.11
    // ist die Methode auf den Client-Spieler eingeengt.
    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void glowcube$blockKlicken(LocalPlayer spieler, InteractionHand hand, BlockHitResult treffer,
                                       CallbackInfoReturnable<InteractionResult> info) {
        if (Interactions.blockUse(treffer, hand)) {
            info.setReturnValue(InteractionResult.FAIL);
        }
    }

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void glowcube$schlagen(Player spieler, Entity ziel, CallbackInfo info) {
        if (Interactions.entityAttack(ziel)) {
            info.cancel();
        }
    }

    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void glowcube$wesenKlicken(Player spieler, Entity ziel, InteractionHand hand,
                                       CallbackInfoReturnable<InteractionResult> info) {
        if (Interactions.entityUse(ziel, hand)) {
            info.setReturnValue(InteractionResult.FAIL);
        }
    }
}
