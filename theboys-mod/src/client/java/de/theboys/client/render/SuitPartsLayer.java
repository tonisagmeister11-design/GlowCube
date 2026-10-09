package de.theboys.client.render;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import de.theboys.TheBoys;
import de.theboys.power.ActiveState;
import de.theboys.power.Power;
import de.theboys.power.PowerAttachments;
import de.theboys.power.PowerData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

/**
 * The 3D parts of every suit that stand out from the flat skin: epaulettes, goggles, Soldier Boy's shield
 * on his back, coat tails, raised emblems, fins, hair, Black Adam's hood. Boxes in model pixels, attached to
 * the body part they belong to, so they move with every animation.
 */
public class SuitPartsLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private static final Identifier WHITE = TheBoys.id("textures/entity/white.png");
	private static final int FULL_BRIGHT = 0xF000F0;

	static final int HEAD = 0, BODY = 1, RIGHT_ARM = 2, LEFT_ARM = 3, RIGHT_LEG = 4, LEFT_LEG = 5;

	/** A box from x0/y0/z0 to x1/y1/z1 in model pixels (y down, -z is the front), tilted by rx/ry/rz degrees. */
	record Part(int bone, float x0, float y0, float z0, float x1, float y1, float z1, int color, boolean glow, float rx, float ry, float rz) {
	}

	private static final Map<Power, List<Part>> PARTS = new EnumMap<>(Power.class);

	private static List<Part> list(Power p) {
		return PARTS.computeIfAbsent(p, k -> new ArrayList<>());
	}

	private static void box(Power p, int bone, float x0, float y0, float z0, float x1, float y1, float z1, int color) {
		list(p).add(new Part(bone, x0, y0, z0, x1, y1, z1, color, false, 0, 0, 0));
	}

	private static void glow(Power p, int bone, float x0, float y0, float z0, float x1, float y1, float z1, int color) {
		list(p).add(new Part(bone, x0, y0, z0, x1, y1, z1, color, true, 0, 0, 0));
	}

	private static void tilt(Power p, int bone, float x0, float y0, float z0, float x1, float y1, float z1, int color, float rx, float ry, float rz) {
		list(p).add(new Part(bone, x0, y0, z0, x1, y1, z1, color, false, rx, ry, rz));
	}

	/** The same box on both arms: given for the right arm (outer side at -x), mirrored to the left. */
	private static void arms(Power p, float x0, float y0, float z0, float x1, float y1, float z1, int color) {
		box(p, RIGHT_ARM, x0, y0, z0, x1, y1, z1, color);
		box(p, LEFT_ARM, -x1, y0, z0, -x0, y1, z1, color);
	}

	private static void legs(Power p, float x0, float y0, float z0, float x1, float y1, float z1, int color) {
		box(p, RIGHT_LEG, x0, y0, z0, x1, y1, z1, color);
		box(p, LEFT_LEG, -x1, y0, z0, -x0, y1, z1, color);
	}

	/** Hair (or a hood) as panels around the head, leaving the face free. */
	private static void hair(Power p, float top, float bottom, float backBottom, float fringe, float thick, int color) {
		float o = 4 + thick;
		box(p, HEAD, -o, -8 - thick, -o, o, -8 + top, o, color);
		box(p, HEAD, -o, -8, -o, -4, bottom, o, color);
		box(p, HEAD, 4, -8, -o, o, bottom, o, color);
		box(p, HEAD, -o, -8, 4, o, backBottom, o, color);
		if (fringe > 0) box(p, HEAD, -o, -8, -o, o, -8 + fringe, -4, color);
	}

	static {
		// ---------------------------------------------------------- Homelander: gold eagle epaulettes, collar, buckle
		Power hl = Power.HOMELANDER;
		arms(hl, -3.7f, -2.7f, -2.7f, 1.4f, -1.3f, 2.7f, 0xE4B53C);
		arms(hl, -3.9f, -1.3f, -2.7f, -3.2f, 1.2f, 2.7f, 0xC9962A);
		for (int i = 0; i < 4; i++) arms(hl, -3.95f, 1.2f, -2.3f + i * 1.3f, -3.35f, 2.4f, -1.7f + i * 1.3f, 0xFBE38A);
		box(hl, BODY, -4.3f, -0.6f, -2.5f, 4.3f, 0.8f, 2.5f, 0xB01F2A);
		box(hl, BODY, -3.4f, 1.4f, -2.45f, 3.4f, 2.4f, -2.05f, 0xE4B53C);
		box(hl, BODY, -0.7f, 1.0f, -2.6f, 0.7f, 4.4f, -2.05f, 0xE4B53C);
		box(hl, BODY, -1.4f, 8.6f, -2.65f, 1.4f, 10.7f, -2.0f, 0xFBE38A);

		// ---------------------------------------------------------- Soldier Boy: the shield on his back, pouches, collar
		Power sb = Power.SOLDIER_BOY;
		box(sb, BODY, -4.0f, 1.6f, 2.2f, 4.0f, 9.6f, 2.9f, 0xB3202B);
		box(sb, BODY, -3.2f, 0.8f, 2.25f, 3.2f, 10.4f, 2.85f, 0xB3202B);
		box(sb, BODY, -3.0f, 2.6f, 2.9f, 3.0f, 8.6f, 3.15f, 0xE8E4DC);
		box(sb, BODY, -2.0f, 3.6f, 3.15f, 2.0f, 7.6f, 3.4f, 0x23397A);
		box(sb, BODY, -0.7f, 4.2f, 3.4f, 0.7f, 7.0f, 3.6f, 0xF2F2F2);
		box(sb, BODY, -1.6f, 5.0f, 3.4f, 1.6f, 5.9f, 3.6f, 0xF2F2F2);
		box(sb, BODY, -4.4f, -0.6f, -2.6f, 4.4f, 0.8f, 2.6f, 0x436A3C);
		box(sb, BODY, 1.6f, 8.6f, -2.75f, 3.6f, 10.6f, -2.0f, 0x23251F);
		box(sb, BODY, -3.6f, 8.6f, -2.75f, -1.6f, 10.6f, -2.0f, 0x23251F);
		arms(sb, -3.5f, -2.4f, -2.5f, 1.3f, -1.4f, 2.5f, 0x33502F);

		// ---------------------------------------------------------- A-Train: goggles, speed fins, knee pads
		Power at = Power.A_TRAIN;
		box(at, HEAD, -4.4f, -4.9f, -4.4f, 4.4f, -3.6f, 4.4f, 0x16171A);
		glow(at, HEAD, -3.5f, -5.2f, -4.75f, -0.5f, -3.3f, -4.3f, 0x3A8BFF);
		glow(at, HEAD, 0.5f, -5.2f, -4.75f, 3.5f, -3.3f, -4.3f, 0x3A8BFF);
		box(at, HEAD, -0.5f, -4.9f, -4.6f, 0.5f, -3.9f, -4.3f, 0x34373D);
		arms(at, -3.6f, -1.6f, 0.0f, -3.05f, 2.2f, 2.6f, 0xA9D5FF);
		legs(at, -2.35f, 3.6f, -2.5f, 2.35f, 5.8f, -2.0f, 0x16254F);

		// ---------------------------------------------------------- Butcher: raised coat collar and long coat tails
		Power bu = Power.BUTCHER;
		box(bu, BODY, -4.5f, -1.4f, -0.5f, 4.5f, 1.2f, 2.7f, 0x15161B);
		tilt(bu, BODY, -4.3f, -0.2f, -2.7f, -1.6f, 6.0f, -2.1f, 0x34363F, 0, 0, -6);
		tilt(bu, BODY, 1.6f, -0.2f, -2.7f, 4.3f, 6.0f, -2.1f, 0x34363F, 0, 0, 6);
		legs(bu, -2.4f, -0.5f, 2.05f, 2.4f, 8.5f, 2.6f, 0x15161B);
		legs(bu, -2.55f, -0.5f, -2.3f, -2.05f, 8.5f, 2.6f, 0x15161B);
		box(bu, RIGHT_LEG, -2.4f, -0.5f, -2.6f, -0.6f, 8.5f, -2.05f, 0x1F2128);
		box(bu, LEFT_LEG, 0.6f, -0.5f, -2.6f, 2.4f, 8.5f, -2.05f, 0x1F2128);

		// ---------------------------------------------------------- Starlight: the glowing silver star, long hair, shoulder pads
		Power sl = Power.STARLIGHT;
		glow(sl, BODY, -1.4f, 2.0f, -2.4f, 1.4f, 4.8f, -2.05f, 0xF2F5F9);
		glow(sl, BODY, -0.55f, 0.4f, -2.35f, 0.55f, 2.2f, -2.05f, 0xE6EBF1);
		glow(sl, BODY, -3.6f, 2.5f, -2.35f, 3.6f, 3.5f, -2.05f, 0xE6EBF1);
		glow(sl, BODY, -2.6f, 4.6f, -2.35f, -1.2f, 6.6f, -2.05f, 0xD8DCE2);
		glow(sl, BODY, 1.2f, 4.6f, -2.35f, 2.6f, 6.6f, -2.05f, 0xD8DCE2);
		hair(sl, 0.5f, -1.0f, 3.5f, 1.3f, 0.45f, 0xD9B76F);
		arms(sl, -2.6f, -2.5f, -2.5f, 1.3f, -1.4f, 2.5f, 0xB4BCC7);

		// ---------------------------------------------------------- The Deep: fins on the forearms and back, gill flaps
		Power dp = Power.THE_DEEP;
		arms(dp, -3.7f, 4.0f, -0.5f, -3.05f, 8.5f, 2.9f, 0x3F9C9A);
		arms(dp, -3.9f, 5.0f, 1.0f, -3.65f, 8.0f, 3.4f, 0x2F7F80);
		box(dp, BODY, -0.35f, 1.0f, 2.05f, 0.35f, 8.0f, 3.4f, 0x2F7F80);
		box(dp, BODY, -0.3f, 2.0f, 3.4f, 0.3f, 6.0f, 4.2f, 0x3F9C9A);
		for (int i = 0; i < 3; i++) {
			box(dp, BODY, -4.35f, 3.0f + i * 1.3f, -1.2f, -4.0f, 3.7f + i * 1.3f, 1.2f, 0xC46A6A);
			box(dp, BODY, 4.0f, 3.0f + i * 1.3f, -1.2f, 4.35f, 3.7f + i * 1.3f, 1.2f, 0xC46A6A);
		}
		box(dp, BODY, -4.4f, -0.5f, -2.5f, 4.4f, 0.7f, 2.5f, 0xC9A24A);

		// ---------------------------------------------------------- Black Noir: helmet visor and ear pieces, armour plates
		Power bn = Power.BLACK_NOIR;
		box(bn, HEAD, -3.9f, -5.4f, -4.5f, 3.9f, -3.4f, -4.05f, 0x2B2C32);
		box(bn, HEAD, -3.4f, -5.0f, -4.7f, -0.6f, -3.8f, -4.45f, 0x4A4F58);
		box(bn, HEAD, 0.6f, -5.0f, -4.7f, 3.4f, -3.8f, -4.45f, 0x4A4F58);
		box(bn, HEAD, -4.5f, -5.5f, -1.5f, -4.0f, -2.0f, 1.5f, 0x2B2C32);
		box(bn, HEAD, 4.0f, -5.5f, -1.5f, 4.5f, -2.0f, 1.5f, 0x2B2C32);
		box(bn, HEAD, -2.0f, -1.6f, -4.6f, 2.0f, -0.3f, -4.05f, 0x1E1F23);
		arms(bn, -3.8f, -2.8f, -2.8f, 1.6f, -0.6f, 2.8f, 0x2B2C32);
		box(bn, BODY, -3.4f, 1.0f, -2.5f, -0.3f, 4.0f, -2.05f, 0x2B2C32);
		box(bn, BODY, 0.3f, 1.0f, -2.5f, 3.4f, 4.0f, -2.05f, 0x2B2C32);
		legs(bn, -2.3f, 4.4f, -2.6f, 2.3f, 6.6f, -2.0f, 0x2B2C32);

		// ---------------------------------------------------------- Stormfront: bob hair, epaulettes, raised bolt
		Power sf = Power.STORMFRONT;
		hair(sf, 0.4f, -2.2f, -1.0f, 1.6f, 0.5f, 0x2B1E18);
		arms(sf, -2.7f, -2.6f, -2.6f, 1.3f, -1.3f, 2.6f, 0xB3212B);
		arms(sf, -2.75f, -1.3f, -2.6f, -2.3f, 0.2f, 2.6f, 0xE8E4DC);
		glow(sf, BODY, 0.4f, 0.8f, -2.4f, 1.6f, 3.0f, -2.05f, 0xFFD24A);
		glow(sf, BODY, -0.8f, 2.8f, -2.4f, 1.0f, 4.2f, -2.05f, 0xFFD24A);
		glow(sf, BODY, -1.8f, 4.0f, -2.4f, -0.4f, 7.6f, -2.05f, 0xFFD24A);
		box(sf, BODY, -1.2f, 8.0f, -2.6f, 1.2f, 9.8f, -2.0f, 0xB8913E);

		// ---------------------------------------------------------- Black Adam: hood, the glowing bolt, layered belt, bracers
		Power ba = Power.BLACK_ADAM;
		hair(ba, 0.7f, -0.5f, 1.0f, 0.0f, 0.75f, 0x0E0E10);
		box(ba, HEAD, -4.9f, -9.0f, -5.2f, 4.9f, -7.6f, -4.6f, 0x0E0E10);
		glow(ba, BODY, -2.2f, 1.0f, -2.45f, 2.2f, 2.0f, -2.05f, 0xFFB43A);
		glow(ba, BODY, -0.6f, 2.0f, -2.5f, 1.6f, 3.2f, -2.05f, 0xFFD06A);
		glow(ba, BODY, -1.6f, 3.2f, -2.5f, 1.2f, 4.2f, -2.05f, 0xFFF3C0);
		glow(ba, BODY, -0.2f, 4.2f, -2.5f, 1.0f, 6.6f, -2.05f, 0xFFB43A);
		box(ba, BODY, -4.0f, 0.0f, -2.35f, -2.2f, 3.6f, -2.05f, 0x060607);
		box(ba, BODY, 2.2f, 0.0f, -2.35f, 4.0f, 3.6f, -2.05f, 0x060607);
		box(ba, BODY, -4.35f, 7.9f, -2.5f, 4.35f, 9.0f, 2.5f, 0x1A1B1F);
		box(ba, BODY, -4.25f, 9.0f, -2.4f, 4.25f, 10.4f, 2.4f, 0x0E0E10);
		box(ba, BODY, -1.4f, 8.2f, -2.75f, 1.4f, 10.0f, -2.3f, 0x2A2C31);
		arms(ba, -3.5f, 5.6f, -2.5f, 1.5f, 9.4f, 2.5f, 0x0E0E10);
		arms(ba, -3.65f, 6.4f, -1.5f, -3.3f, 8.8f, 1.5f, 0x2A2C31);
		arms(ba, -3.6f, -2.5f, -2.6f, 1.4f, -1.2f, 2.6f, 0x1A1B1F);

		// ---------------------------------------------------------- more relief on every suit
		arms(hl, -3.3f, 7.6f, -2.3f, 1.3f, 8.6f, 2.3f, 0x741420);
		legs(hl, -2.3f, 8.6f, -2.3f, 2.3f, 9.4f, 2.3f, 0x503030);
		box(hl, BODY, -4.25f, 9.0f, -2.25f, 4.25f, 10.2f, 2.25f, 0xC9962A);

		legs(sb, -2.3f, 8.8f, -2.3f, 2.3f, 9.5f, 2.3f, 0x17181B);
		arms(sb, -3.3f, 8.8f, -2.3f, 1.3f, 9.6f, 2.3f, 0x17181B);
		box(sb, BODY, -0.5f, 1.4f, -2.35f, 0.5f, 2.6f, -2.05f, 0xC0C4C8);
		box(sb, BODY, -4.25f, 9.0f, -2.25f, 4.25f, 10.4f, 2.25f, 0x17181B);

		arms(at, -3.3f, 6.0f, -2.3f, 1.3f, 7.2f, 2.3f, 0x16171A);
		legs(at, -2.3f, 11.2f, -2.7f, 2.3f, 12.1f, 2.3f, 0xF2F6FB);
		box(at, BODY, -1.4f, 7.9f, -2.4f, 1.4f, 10.2f, -2.05f, 0xF2F6FB);

		box(bu, HEAD, -4.3f, -1.1f, -4.55f, 4.3f, 0.4f, -4.0f, 0x1C1613);
		box(bu, HEAD, -4.3f, -3.0f, -4.4f, -1.8f, -1.1f, -4.0f, 0x1C1613);
		box(bu, HEAD, 1.8f, -3.0f, -4.4f, 4.3f, -1.1f, -4.0f, 0x1C1613);
		box(bu, HEAD, -4.4f, -3.6f, -4.3f, -4.0f, 0.2f, 0.0f, 0x1C1613);
		box(bu, HEAD, 4.0f, -3.6f, -4.3f, 4.4f, 0.2f, 0.0f, 0x1C1613);
		legs(bu, -2.35f, 10.0f, -2.45f, 2.35f, 12.05f, 2.35f, 0x101113);

		box(sl, BODY, -4.25f, 7.8f, -2.25f, 4.25f, 8.8f, 2.25f, 0x8FB4D9);
		legs(sl, -2.3f, 5.8f, -2.3f, 2.3f, 6.8f, 2.3f, 0xFFFFFF);
		box(sl, BODY, -0.6f, 7.7f, -2.5f, 0.6f, 8.9f, -2.2f, 0xE8C46A);

		box(dp, BODY, -1.0f, 7.8f, -2.6f, 1.0f, 9.2f, -2.05f, 0xECC96E);
		legs(dp, -2.3f, 7.8f, -2.3f, 2.3f, 8.6f, 2.3f, 0xC9A24A);
		arms(dp, -3.3f, 8.6f, -2.3f, 1.3f, 9.4f, 2.3f, 0x123F44);

		box(bn, BODY, -4.3f, 8.0f, -2.4f, 4.3f, 9.0f, 2.4f, 0x1E1F23);
		box(bn, BODY, -3.6f, 8.4f, -2.85f, -2.0f, 10.2f, -2.3f, 0x2B2C32);
		box(bn, BODY, 2.0f, 8.4f, -2.85f, 3.6f, 10.2f, -2.3f, 0x2B2C32);
		arms(bn, -3.45f, 4.2f, -1.2f, -3.0f, 6.2f, 1.2f, 0x2B2C32);
		arms(bn, -3.3f, 8.0f, -2.3f, 1.3f, 8.8f, 2.3f, 0x1E1F23);

		arms(sf, -3.3f, 7.6f, -2.3f, 1.3f, 8.4f, 2.3f, 0xB3212B);
		box(sf, BODY, -4.25f, 7.8f, -2.25f, 4.25f, 8.7f, 2.25f, 0x0B0C10);
		legs(sf, -2.35f, 5.6f, -2.35f, 2.35f, 6.4f, 2.35f, 0xB3212B);

		// Black Adam's eyes always burn blue
		glow(ba, HEAD, -2.9f, -4.0f, -4.25f, -1.1f, -3.1f, -4.0f, 0x7FC4FF);
		glow(ba, HEAD, 1.1f, -4.0f, -4.25f, 2.9f, -3.1f, -4.0f, 0x7FC4FF);
		legs(ba, -2.35f, 4.4f, -2.6f, 2.35f, 6.4f, -2.0f, 0x1A1B1F);
		legs(ba, -2.3f, 8.0f, -2.3f, 2.3f, 8.8f, 2.3f, 0x1A1B1F);
		box(ba, BODY, -4.3f, 0.6f, -0.8f, 4.3f, 1.8f, 2.6f, 0x0E0E10);

		Power mm = Power.MINIMAUS;
		box(mm, HEAD, -0.7f, -3.3f, -4.7f, 0.7f, -2.4f, -4.0f, 0xF2A2B4);
		box(mm, HEAD, -0.7f, -1.2f, -4.45f, 0.7f, -0.2f, -4.0f, 0xFBFBF4);
		for (int i = 0; i < 2; i++) {
			box(mm, HEAD, -7.0f, -2.9f + i * 1.2f, -4.25f, -4.0f, -2.75f + i * 1.2f, -4.1f, 0x4C4E55);
			box(mm, HEAD, 4.0f, -2.9f + i * 1.2f, -4.25f, 7.0f, -2.75f + i * 1.2f, -4.1f, 0x4C4E55);
		}
		box(mm, BODY, -1.1f, 7.6f, -2.6f, 1.1f, 9.3f, -2.05f, 0xF2C230);
	}

	public SuitPartsLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
		super(parent);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || state.isInvisible) return;
		Entity entity = mc.level.getEntity(state.id);
		if (entity == null) return;
		PowerData data = PowerAttachments.power(entity);
		List<Part> parts = PARTS.get(data.power());
		if (parts == null || !data.suit()) return;
		boolean slim = data.power() == Power.STARLIGHT || data.power() == Power.STORMFRONT;
		// Black Adam's bolt burns brighter when his power is up
		boolean charged = data.power() == Power.BLACK_ADAM && (PowerAttachments.active(entity).flags() & (ActiveState.FLYING | ActiveState.HAND_BEAM | ActiveState.ZAP)) != 0;
		PlayerModel model = getParentModel();
		ModelPart[] bones = {model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg};
		for (int b = 0; b < bones.length; b++) {
			if (!bones[b].visible) continue;
			int bone = b;
			poseStack.pushPose();
			bones[b].translateAndRotate(poseStack);
			for (Part p : parts) {
				if (p.bone() != bone) continue;
				poseStack.pushPose();
				float x0 = p.x0(), x1 = p.x1();
				if (slim && bone == RIGHT_ARM) {
					x0 = -0.5f + (x0 + 1) * 0.75f;
					x1 = -0.5f + (x1 + 1) * 0.75f;
				} else if (slim && bone == LEFT_ARM) {
					x0 = 0.5f + (x0 - 1) * 0.75f;
					x1 = 0.5f + (x1 - 1) * 0.75f;
				}
				float cx = (x0 + x1) / 32f, cy = (p.y0() + p.y1()) / 32f, cz = (p.z0() + p.z1()) / 32f;
				poseStack.translate(cx, cy, cz);
				if (p.rx() != 0) poseStack.mulPose(new org.joml.Matrix4f().rotationX((float) Math.toRadians(p.rx())));
				if (p.ry() != 0) poseStack.mulPose(new org.joml.Matrix4f().rotationY((float) Math.toRadians(p.ry())));
				if (p.rz() != 0) poseStack.mulPose(new org.joml.Matrix4f().rotationZ((float) Math.toRadians(p.rz())));
				float hx = (x1 - x0) / 32f, hy = (p.y1() - p.y0()) / 32f, hz = (p.z1() - p.z0()) / 32f;
				int color = 0xFF000000 | p.color();
				int l = p.glow() ? FULL_BRIGHT : light;
				if (p.glow() && charged) color = p.color() == 0x7FC4FF ? 0xFFE6F4FF : 0xFFFFF6D0;
				int c = color;
				collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(WHITE), (pose, buffer) -> cube(buffer, pose, hx, hy, hz, c, l));
				poseStack.popPose();
			}
			poseStack.popPose();
		}
	}

	private static final float[][] NORMALS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

	private static void cube(VertexConsumer b, PoseStack.Pose pose, float hx, float hy, float hz, int color, int light) {
		for (float[] n : NORMALS) {
			float[][] q;
			if (n[0] != 0) {
				float s = n[0];
				q = new float[][] {{s * hx, -hy, -s * hz}, {s * hx, -hy, s * hz}, {s * hx, hy, s * hz}, {s * hx, hy, -s * hz}};
			} else if (n[1] != 0) {
				float s = n[1];
				q = new float[][] {{-hx, s * hy, -s * hz}, {hx, s * hy, -s * hz}, {hx, s * hy, s * hz}, {-hx, s * hy, s * hz}};
			} else {
				float s = n[2];
				q = new float[][] {{s * hx, -hy, s * hz}, {-s * hx, -hy, s * hz}, {-s * hx, hy, s * hz}, {s * hx, hy, s * hz}};
			}
			float[][] uv = {{0f, 1f}, {1f, 1f}, {1f, 0f}, {0f, 0f}};
			for (int i = 0; i < 4; i++) {
				b.addVertex(pose, q[i][0], q[i][1], q[i][2]).setColor(color).setUv(uv[i][0], uv[i][1])
						.setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, n[0], n[1], n[2]);
			}
		}
	}
}
