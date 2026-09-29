package de.gtacity.mixin;

import de.gtacity.entity.CarEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** In a car Shift is the handbrake (drifting), so it must not throw the player out - F leaves the car instead. */
@Mixin(Player.class)
public abstract class PlayerMixin {
    @Inject(method = "wantsToStopRiding", at = @At("HEAD"), cancellable = true)
    private void gtacity$keepSeatInCar(CallbackInfoReturnable<Boolean> cir) {
        if (((Player) (Object) this).getVehicle() instanceof CarEntity) {
            cir.setReturnValue(false);
        }
    }
}
