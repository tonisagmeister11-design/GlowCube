package de.theboys.net;

import de.theboys.power.PowerManager;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class ModNetworking {
	private ModNetworking() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(AbilityKeyPayload.TYPE, AbilityKeyPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(StatusPayload.TYPE, StatusPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(FxPayload.TYPE, FxPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(PixelMinePayload.TYPE, PixelMinePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(PixelMinePayload.TYPE, (payload, context) ->
				de.theboys.block.PixelCarving.mine(context.player(), payload));

		ServerPlayNetworking.registerGlobalReceiver(AbilityKeyPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			PowerManager.onKey(player, payload.slot(), payload.pressed());
		});
	}

	/** Sends an effect to every player that can see the given position. */
	public static void sendFx(ServerLevel level, Vec3 around, FxPayload payload) {
		for (ServerPlayer p : PlayerLookup.around(level, around, 160)) {
			ServerPlayNetworking.send(p, payload);
		}
	}
}
