package de.theboys.mixin;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import de.theboys.power.ActiveState;
import de.theboys.power.Power;
import de.theboys.power.PowerAttachments;
import net.minecraft.world.entity.player.Player;

/**
 * Black Adam flies straight through blocks: like a spectator, he has no collision while he flies
 * (on the client, which moves him, and on the server, which checks the movement).
 */
@Mixin(Player.class)
public abstract class PlayerPhaseMixin {
	@Inject(method = "tick", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/player/Player;noPhysics:Z", opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER), require = 0)
	private void theboys$phase(CallbackInfo ci) {
		Player self = (Player) (Object) this;
		if (PowerAttachments.powerOf(self) == Power.BLACK_ADAM && PowerAttachments.active(self).has(ActiveState.PHASE) && self.getAbilities().flying) {
			self.noPhysics = true;
		}
	}
}
