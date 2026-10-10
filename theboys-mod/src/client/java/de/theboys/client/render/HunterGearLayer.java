package de.theboys.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

import de.theboys.TheBoys;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/** The 3D gear of a hunter: helmet or cap, vest and pouches, backpack and the rifle in his hands. */
public class HunterGearLayer extends RenderLayer<SupeHunterRenderer.State, SupeHunterRenderer.Model> {
	private static final Identifier WHITE = TheBoys.id("textures/entity/white.png");
	private static final int FULL_BRIGHT = 0xF000F0;
	private static final int HEAD = 0, BODY = 1, RIGHT_ARM = 2, LEFT_ARM = 3, RIGHT_LEG = 4, LEFT_LEG = 5;

	private record Part(int bone, float x0, float y0, float z0, float x1, float y1, float z1, int color, boolean glow) {
	}

	private static final java.util.List<Part> RIFLE = new java.util.ArrayList<>();
	private static final java.util.List<java.util.List<Part>> GEAR = new java.util.ArrayList<>();

	private static void rifle(int bone, float x0, float y0, float z0, float x1, float y1, float z1, int color, boolean glow) {
		RIFLE.add(new Part(bone, x0, y0, z0, x1, y1, z1, color, glow));
	}

	private static void gear(java.util.List<Part> l, int bone, float x0, float y0, float z0, float x1, float y1, float z1, int color) {
		l.add(new Part(bone, x0, y0, z0, x1, y1, z1, color, false));
	}

	static {
		// the rifle is held in the right hand, barrel along the arm; the left hand supports it from below
		rifle(RIGHT_ARM, -2.2f, 1.0f, -4.4f, 0.2f, 9.0f, -1.6f, 0x6B4A2F, false);   // stock
		rifle(RIGHT_ARM, -2.0f, 9.0f, -4.2f, 0.0f, 16.0f, -2.0f, 0x2B2D33, false);  // receiver
		rifle(RIGHT_ARM, -1.45f, 16.0f, -3.5f, -0.55f, 25.0f, -2.7f, 0x1B1C20, false); // barrel
		rifle(RIGHT_ARM, -1.7f, 23.8f, -3.8f, -0.3f, 26.2f, -2.4f, 0x111114, false);  // muzzle brake
		rifle(RIGHT_ARM, -1.9f, 10.0f, -5.4f, -0.1f, 15.0f, -4.2f, 0x2B2D33, false);  // scope
		rifle(RIGHT_ARM, -1.25f, 14.4f, -5.7f, -0.75f, 15.2f, -5.3f, 0xFF3030, true);  // red lens
		rifle(RIGHT_ARM, -1.7f, 12.0f, -2.0f, -0.3f, 14.5f, 0.6f, 0x1B1C20, false);   // magazine
		// camo
		java.util.List<Part> camo = new java.util.ArrayList<>();
		gear(camo, HEAD, -4.7f, -9.6f, -4.7f, 4.7f, -7.0f, 4.7f, 0x4C5A33);            // helmet
		gear(camo, HEAD, -4.7f, -8.3f, -5.9f, 4.7f, -7.4f, -4.5f, 0x3B4727);            // brim
		gear(camo, BODY, -3.5f, 2.0f, -2.9f, -0.4f, 4.4f, -2.0f, 0x5B6B3A);             // chest pouches
		gear(camo, BODY, 0.4f, 2.0f, -2.9f, 3.5f, 4.4f, -2.0f, 0x5B6B3A);
		gear(camo, BODY, -3.2f, 0.5f, 2.0f, 3.2f, 9.5f, 5.2f, 0x3B4727);                // backpack
		gear(camo, BODY, -4.2f, 8.4f, -2.6f, 4.2f, 9.6f, 2.6f, 0x2A2F1C);               // belt
		GEAR.add(camo);
		// tactical black
		java.util.List<Part> tac = new java.util.ArrayList<>();
		gear(tac, HEAD, -4.6f, -9.4f, -4.6f, 4.6f, -6.8f, 4.6f, 0x1B1C20);              // helmet
		gear(tac, HEAD, -3.4f, -6.9f, -5.1f, 3.4f, -5.1f, -4.0f, 0x2B2D33);             // goggles on the forehead
		gear(tac, BODY, -4.4f, 0.8f, -3.0f, 4.4f, 9.0f, -2.0f, 0x23252B);               // plate carrier front
		gear(tac, BODY, -4.4f, 0.8f, 2.0f, 4.4f, 9.0f, 3.0f, 0x23252B);                 // back plate
		gear(tac, BODY, -3.0f, 5.8f, -3.7f, -0.5f, 8.0f, -3.0f, 0x34363F);              // magazine pouches
		gear(tac, BODY, 0.5f, 5.8f, -3.7f, 3.0f, 8.0f, -3.0f, 0x34363F);
		gear(tac, RIGHT_ARM, -3.9f, -2.6f, -2.8f, 1.5f, -0.6f, 2.8f, 0x34363F);        // shoulder pads
		gear(tac, LEFT_ARM, -1.5f, -2.6f, -2.8f, 3.9f, -0.6f, 2.8f, 0x34363F);
		gear(tac, RIGHT_LEG, -2.3f, 4.8f, -2.7f, 2.3f, 7.0f, -2.0f, 0x34363F);          // knee pads
		gear(tac, LEFT_LEG, -2.3f, 4.8f, -2.7f, 2.3f, 7.0f, -2.0f, 0x34363F);
		GEAR.add(tac);
		// hi-vis worker turned hunter
		java.util.List<Part> hi = new java.util.ArrayList<>();
		gear(hi, HEAD, -4.7f, -9.6f, -4.7f, 4.7f, -7.2f, 4.7f, 0xE8C21E);               // hard hat
		gear(hi, HEAD, -4.7f, -8.0f, -6.2f, 4.7f, -7.2f, -4.5f, 0xD2AA14);
		gear(hi, BODY, -4.3f, 0.6f, -2.8f, 4.3f, 9.4f, -2.0f, 0xF2761A);                // orange vest
		gear(hi, BODY, -4.3f, 0.6f, 2.0f, 4.3f, 9.4f, 2.8f, 0xF2761A);
		gear(hi, BODY, -4.35f, 4.0f, -2.9f, 4.35f, 5.4f, -2.0f, 0xDDDDDD);              // reflective stripe
		gear(hi, BODY, -4.35f, 4.0f, 2.0f, 4.35f, 5.4f, 2.9f, 0xDDDDDD);
		gear(hi, BODY, 1.0f, 6.0f, -3.5f, 3.6f, 8.6f, -2.9f, 0x3A3A3A);                 // pocket
		GEAR.add(hi);
		// red cap, hoodie
		java.util.List<Part> cap = new java.util.ArrayList<>();
		gear(cap, HEAD, -4.6f, -9.2f, -4.6f, 4.6f, -7.4f, 4.6f, 0xB3202B);              // cap
		gear(cap, HEAD, -3.8f, -8.1f, -7.0f, 3.8f, -7.5f, -4.4f, 0x8E1822);             // brim
		gear(cap, BODY, -3.0f, 0.0f, -3.0f, 3.0f, 2.2f, 2.6f, 0x4B4F58);                // hood
		gear(cap, BODY, -3.0f, 1.0f, 2.0f, 3.0f, 8.0f, 4.4f, 0x6B3A2A);                 // small pack
		gear(cap, BODY, -2.2f, 6.0f, -3.2f, 2.2f, 8.2f, -2.0f, 0x4B4F58);               // kangaroo pocket
		GEAR.add(cap);
	}

	public HunterGearLayer(RenderLayerParent<SupeHunterRenderer.State, SupeHunterRenderer.Model> parent) {
		super(parent);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, SupeHunterRenderer.State state, float yRot, float xRot) {
		SupeHunterRenderer.Model m = getParentModel();
		ModelPart[] bones = {m.head, m.body, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg};
		java.util.List<Part> all = new java.util.ArrayList<>(GEAR.get(Math.floorMod(state.variant, GEAR.size())));
		all.addAll(RIFLE);
		for (int b = 0; b < bones.length; b++) {
			poseStack.pushPose();
			bones[b].translateAndRotate(poseStack);
			for (Part p : all) {
				if (p.bone() != b) continue;
				poseStack.pushPose();
				poseStack.translate((p.x0() + p.x1()) / 32f, (p.y0() + p.y1()) / 32f, (p.z0() + p.z1()) / 32f);
				float hx = (p.x1() - p.x0()) / 32f, hy = (p.y1() - p.y0()) / 32f, hz = (p.z1() - p.z0()) / 32f;
				int color = 0xFF000000 | p.color();
				int l = p.glow() ? FULL_BRIGHT : light;
				collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(WHITE), (pose, buffer) -> SuitPartsLayer.cube(buffer, pose, hx, hy, hz, color, l));
				poseStack.popPose();
			}
			poseStack.popPose();
		}
	}
}
