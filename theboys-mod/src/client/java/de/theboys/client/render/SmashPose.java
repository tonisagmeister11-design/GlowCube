package de.theboys.client.render;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;

import de.theboys.power.ActiveState;
import de.theboys.power.MiniMausMath;
import de.theboys.power.Power;
import de.theboys.power.PowerAttachments;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.Entity;

/** Turns a victim of Multi Smash around its feet so it swings with MiniMaus' arms. */
public final class SmashPose {
	public static final RenderStateDataKey<Integer> ENTITY_ID = RenderStateDataKey.create(() -> "theboys:entity_id");

	private SmashPose() {
	}

	/** The MiniMaus currently swinging the entity with this id, or null. */
	public static AbstractClientPlayer holder(int id) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return null;
		for (AbstractClientPlayer p : mc.level.players()) {
			if (PowerAttachments.powerOf(p) != Power.MINIMAUS) continue;
			ActiveState a = PowerAttachments.active(p);
			if (a.has(ActiveState.SMASH) && a.targetId() == id) return p;
		}
		return null;
	}

	public static void apply(LivingEntityRenderState state, PoseStack poseStack) {
		Integer id = state.getData(ENTITY_ID);
		if (id == null) return;
		AbstractClientPlayer holder = holder(id);
		if (holder == null) return;
		Entity victim = holder.level().getEntity(id);
		if (victim == null) return;
		float pt = state.ageInTicks - (float) Math.floor(state.ageInTicks);
		float angle = MiniMausMath.angle(PowerAttachments.active(holder).charge() + pt, MiniMausMath.maxAngle(holder, victim));
		// rotate around the feet (they are in her hands), in the plane of her shoulders
		poseStack.mulPose(new Matrix4f().rotationZ((float) Math.toRadians(-angle)));
	}
}
