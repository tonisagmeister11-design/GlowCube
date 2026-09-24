package net.glowcube.client.mixin;

import net.glowcube.client.core.Interactions;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fassung fuer <b>26.x</b>: Wesen anklicken - ab 26.x
 * interact(Player, Entity, EntityHitResult, InteractionHand). Mit der alten
 * Signatur brach das Mixin beim Betreten jeder Welt ab (vom Spieltest gefunden).
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeInteractMixin {
    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void glowcube$wesenKlicken(Player spieler, Entity ziel, EntityHitResult treffer, InteractionHand hand,
                                       CallbackInfoReturnable<InteractionResult> info) {
        if (Interactions.entityUse(ziel, hand)) {
            info.setReturnValue(InteractionResult.FAIL);
        }
    }
}
