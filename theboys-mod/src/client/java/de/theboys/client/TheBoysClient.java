package de.theboys.client;

import de.theboys.TheBoys;
import de.theboys.client.render.EffectRenderer;
import de.theboys.net.FxPayload;
import de.theboys.net.StatusPayload;
import de.theboys.power.Power;
import de.theboys.power.PowerAttachments;
import de.theboys.registry.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.world.phys.Vec3;

public class TheBoysClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		Keys.init();
		EntityRendererRegistry.register(ModEntities.THROWN_SHIELD, ctx -> new ThrownItemRenderer<>(ctx, 1.6f, true));

		ClientPlayNetworking.registerGlobalReceiver(StatusPayload.TYPE, (payload, context) -> ClientState.status = payload);
		ClientPlayNetworking.registerGlobalReceiver(FxPayload.TYPE, (payload, context) -> onFx(context.client(), payload));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientState.reset());

		ClientTickEvents.END_CLIENT_TICK.register(TheBoysClient::tick);
		HudElementRegistry.addLast(TheBoys.id("hud"), HudOverlay::extract);
		LevelRenderEvents.COLLECT_SUBMITS.register(EffectRenderer::renderCollect);
		LevelRenderEvents.BEFORE_TRANSLUCENT_TERRAIN.register(EffectRenderer::renderBeforeTranslucent);
		LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(EffectRenderer::renderAfterTranslucentFeatures);
	}

	private static void onFx(Minecraft mc, FxPayload fx) {
		switch (fx.kind()) {
			case FxPayload.TENDRIL_STRIKE -> ClientState.EFFECTS.add(new ClientState.Fx(fx, fx.x2() > 0 ? (int) fx.x2() : 12));
			case FxPayload.NUKE -> ClientState.EFFECTS.add(new ClientState.Fx(fx, 34));
			case FxPayload.SHOCKWAVE -> ClientState.EFFECTS.add(new ClientState.Fx(fx, 14));
			case FxPayload.REWIND_START -> {
				ClientState.rewindTotal = Math.max(1, (int) fx.x2());
				ClientState.rewindTicks = ClientState.rewindTotal;
				ClientState.rewindIsMine = mc.player != null && mc.player.getId() == fx.entityId();
			}
			case FxPayload.SHAKE -> {
				if (mc.player != null) {
					double d = mc.player.position().distanceTo(new Vec3(fx.x(), fx.y(), fx.z()));
					float s = (float) (fx.x2() * Math.max(0, 1 - d / 40));
					if (s > 0.05f) {
						ClientState.shakeStrength = Math.max(ClientState.shakeStrength, s);
						ClientState.shakeTicks = 12;
					}
				}
			}
			case FxPayload.TORN -> de.theboys.client.render.TornBodies.spawn(mc.level == null ? null : mc.level.getEntity(fx.entityId()), new Vec3(fx.x(), fx.y(), fx.z()), fx.x2(), fx.y2(), fx.z2());
			case FxPayload.GORE -> {
				if (mc.player != null && mc.player.getEyePosition().distanceTo(new Vec3(fx.x(), fx.y(), fx.z())) < 4.5) {
					ClientState.bloodTicks = 50;
				}
			}
			default -> { }
		}
	}

	private static void tick(Minecraft mc) {
		Keys.tick(mc);
		LocalPlayer player = mc.player;
		if (player == null || mc.isPaused()) {
			return;
		}
		ATrainMovement.tick(player);
		SupeMovement.tick(player);
		de.theboys.client.render.TornBodies.tick(mc.level);
		CombatAnim.tick(mc.level);

		// speedometer
		Vec3 pos = player.position();
		if (ClientState.lastPos != null) {
			double blocksPerTick = pos.subtract(ClientState.lastPos).length();
			float kmh = (float) (blocksPerTick * 20 * 3.6);
			ClientState.speedKmh += (kmh - ClientState.speedKmh) * 0.35f;
		}
		ClientState.lastPos = pos;

		if (ClientState.rewindTicks > 0) ClientState.rewindTicks--;
		if (ClientState.bloodTicks > 0) ClientState.bloodTicks--;
		if (ClientState.injectFlash > 0) ClientState.injectFlash--;
		boolean injecting = player.isUsingItem() && player.getUseItem().getItem() instanceof de.theboys.item.SyringeItem;
		if (ClientState.wasInjecting && !injecting && ClientState.lastInjectRemaining <= 2) {
			ClientState.injectFlash = 30;
			ClientState.injectColor = ClientState.lastInjectColor;
		}
		ClientState.wasInjecting = injecting;
		if (injecting) {
			ClientState.lastInjectRemaining = player.getUseItemRemainingTicks();
			ClientState.lastInjectColor = ((de.theboys.item.SyringeItem) player.getUseItem().getItem()).kind().color;
		}
		if (ClientState.shakeTicks > 0) {
			ClientState.shakeTicks--;
			float s = ClientState.shakeStrength * ClientState.shakeTicks / 12f;
			player.setYRot(player.getYRot() + (player.getRandom().nextFloat() - 0.5f) * s * 2);
			player.setXRot(player.getXRot() + (player.getRandom().nextFloat() - 0.5f) * s * 2);
			if (ClientState.shakeTicks == 0) ClientState.shakeStrength = 0;
		}

		ClientState.EFFECTS.removeIf(fx -> ++fx.age > fx.duration);
		EffectRenderer.tick(mc);
		if (PowerAttachments.powerOf(player) == Power.NONE) {
			ClientState.status = new StatusPayload(0, 0, 0, 0, 0);
		}
	}
}
