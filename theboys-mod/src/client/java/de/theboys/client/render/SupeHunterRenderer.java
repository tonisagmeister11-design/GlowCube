package de.theboys.client.render;

import de.theboys.TheBoys;
import de.theboys.entity.SupeHunter;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/** Supe hunters: ordinary people (a player-shaped model with their own skins), a rifle in their hands. */
public class SupeHunterRenderer extends MobRenderer<SupeHunter, SupeHunterRenderer.State, SupeHunterRenderer.Model> {
	public static final int VARIANTS = 4;

	public static final class State extends HumanoidRenderState {
		public int variant;
		public boolean aiming;
		public boolean moving;
	}

	/** The player model with the arm poses of a soldier: rifle at the chest, or raised to the shoulder. */
	public static final class Model extends HumanoidModel<State> {
		public Model(net.minecraft.client.model.geom.ModelPart root) {
			super(root);
		}

		@Override
		public void setupAnim(State s) {
			super.setupAnim(s);
			if (s.aiming) {
				rightArm.xRot = -1.5f + head.xRot;
				rightArm.yRot = -0.12f + head.yRot;
				rightArm.zRot = 0f;
				leftArm.xRot = -1.45f + head.xRot;
				leftArm.yRot = 0.55f + head.yRot;
				leftArm.zRot = 0f;
			} else {
				float walk = Mth.cos(s.walkAnimationPos * 0.6662f) * 0.15f * s.walkAnimationSpeed;
				rightArm.xRot = -0.75f + walk;
				rightArm.yRot = -0.1f;
				leftArm.xRot = -0.95f - walk;
				leftArm.yRot = 0.45f;
			}
		}
	}

	public SupeHunterRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new Model(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5f);
		addLayer(new HunterGearLayer(this));
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(SupeHunter hunter, State state, float partialTicks) {
		super.extractRenderState(hunter, state, partialTicks);
		state.variant = Math.floorMod(hunter.getUUID().hashCode(), VARIANTS);
		state.aiming = hunter.isAiming();
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return TheBoys.id("textures/entity/supe_hunter_" + state.variant + ".png");
	}
}
