package de.theboys.client;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.sdl.SDLScancode;

import de.theboys.TheBoys;
import de.theboys.item.SyringeItem;
import de.theboys.net.AbilityKeyPayload;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;

/** Key bindings: four ability keys, inject, suit toggle. */
public final class Keys {
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(TheBoys.id("the_boys"));

	public static final KeyMapping[] ABILITY = {
			register("ability_1", SDLScancode.SDL_SCANCODE_R),
			register("ability_2", SDLScancode.SDL_SCANCODE_G),
			register("ability_3", SDLScancode.SDL_SCANCODE_C),
			register("ability_4", SDLScancode.SDL_SCANCODE_X),
	};
	public static final KeyMapping INJECT = register("inject", SDLScancode.SDL_SCANCODE_V);
	public static final KeyMapping SUIT = register("suit", SDLScancode.SDL_SCANCODE_J);

	private static final boolean[] WAS_DOWN = new boolean[4];
	private static boolean pressedFlight;
	private static int injectHold;

	private Keys() {
	}

	/** Homelander's flight key: take off right away (the server only grants the ability). */
	private static void homelanderFlight(Minecraft mc) {
		var player = mc.player;
		if (de.theboys.power.PowerAttachments.powerOf(player) != de.theboys.power.Power.HOMELANDER || player.isCreative() || player.isSpectator()) {
			return;
		}
		var abilities = player.getAbilities();
		if (!abilities.mayfly) {
			// flight is being switched on: the key packet was sent first, so the server allows it
			abilities.mayfly = true;
			abilities.flying = true;
			player.setDeltaMovement(player.getDeltaMovement().x, 0.9, player.getDeltaMovement().z);
		} else {
			abilities.flying = false;
		}
		player.onUpdateAbilities();
	}

	private static KeyMapping register(String name, int scancode) {
		return KeyMappingHelper.registerKeyMapping(new KeyMapping("key.theboys." + name, InputConstants.Type.KEYBOARD, scancode, CATEGORY));
	}

	public static void init() {
	}

	public static void tick(Minecraft mc) {
		if (mc.player == null || !ClientPlayNetworking.canSend(AbilityKeyPayload.TYPE)) {
			return;
		}
		boolean flightKey = ABILITY[1].isDown() && !WAS_DOWN[1];
		for (int i = 0; i < 4; i++) {
			boolean down = ABILITY[i].isDown();
			boolean clicked = false;
			while (ABILITY[i].consumeClick()) {
				clicked = true;
			}
			if (clicked && !WAS_DOWN[i] && !down) {
				if (i == 1) pressedFlight = true;
				// pressed and released within one tick
				ClientPlayNetworking.send(new AbilityKeyPayload(i, true));
				ClientPlayNetworking.send(new AbilityKeyPayload(i, false));
				continue;
			}
			if (clicked && !WAS_DOWN[i]) {
				down = true;
			}
			if (down != WAS_DOWN[i]) {
				WAS_DOWN[i] = down;
				ClientPlayNetworking.send(new AbilityKeyPayload(i, down));
			}
		}
		if (flightKey || pressedFlight) {
			homelanderFlight(mc);
		}
		pressedFlight = false;
		while (SUIT.consumeClick()) {
			ClientPlayNetworking.send(new AbilityKeyPayload(AbilityKeyPayload.SUIT, true));
		}
		while (INJECT.consumeClick()) {
			if (mc.player.getMainHandItem().getItem() instanceof SyringeItem && !mc.player.isUsingItem() && mc.gameMode != null) {
				mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
				injectHold = SyringeItem.INJECT_TICKS + 4;
			}
		}
		// one key press is enough: keep "use" held until the needle is in
		if (injectHold > 0) {
			injectHold--;
			boolean injecting = mc.player.isUsingItem() && mc.player.getUseItem().getItem() instanceof SyringeItem;
			mc.options.keyUse.setDown(injecting && injectHold > 0);
			if (!injecting) injectHold = 0;
		}
	}
}
