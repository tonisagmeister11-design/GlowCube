package de.theboys.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import de.theboys.block.CarvedBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.phys.Vec3;

/** Draws a nibbled block pixel by pixel with the texture of the block it used to be. */
public class CarvedBlockRenderer implements BlockEntityRenderer<CarvedBlockEntity, CarvedBlockRenderer.State> {
	public static class State extends BlockEntityRenderState {
		public float[] mesh;
	}

	/** Mesh data: x y z u v nx ny nz per vertex, quads. */
	private static final class Mesh {
		final int version;
		final float[] data;

		Mesh(int version, float[] data) {
			this.version = version;
			this.data = data;
		}
	}

	// face order: down, up, north, south, west, east
	private static final int[][] DIRS = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(CarvedBlockEntity be, State state, float partialTick, Vec3 camera, ModelFeatureRenderer.CrumblingOverlay crumbling) {
		BlockEntityRenderer.super.extractRenderState(be, state, partialTick, camera, crumbling);
		Mesh mesh = be.clientMesh instanceof Mesh m && m.version == be.version ? m : null;
		if (mesh == null) {
			mesh = new Mesh(be.version, build(be));
			be.clientMesh = mesh;
		}
		state.mesh = mesh.data;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		float[] d = state.mesh;
		if (d == null || d.length == 0) return;
		int light = state.lightCoords;
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS), (pose, buffer) -> draw(buffer, pose, d, light));
	}

	private static void draw(VertexConsumer b, PoseStack.Pose pose, float[] d, int light) {
		for (int i = 0; i + 8 <= d.length; i += 8) {
			b.addVertex(pose, d[i], d[i + 1], d[i + 2]).setColor(0xFFFFFFFF).setUv(d[i + 3], d[i + 4])
					.setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, d[i + 5], d[i + 6], d[i + 7]);
		}
	}

	private static TextureAtlasSprite sprite(CarvedBlockEntity be) {
		return Minecraft.getInstance().getModelManager().getBlockStateModelSet().getParticleMaterial(be.original()).sprite();
	}

	/** One quad per visible pixel face. */
	private static float[] build(CarvedBlockEntity be) {
		TextureAtlasSprite sprite = sprite(be);
		float u0 = sprite.getU0(), u1 = sprite.getU1(), v0 = sprite.getV0(), v1 = sprite.getV1();
		float[] out = new float[4096 * 8 * 4];
		int n = 0;
		float p = 1f / 16f;
		for (int z = 0; z < 16; z++) for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
			if (!be.filled(x, y, z)) continue;
			for (int f = 0; f < 6; f++) {
				int[] dir = DIRS[f];
				if (be.filled(x + dir[0], y + dir[1], z + dir[2])) continue;
				if (n + 32 > out.length) out = java.util.Arrays.copyOf(out, out.length * 2);
				float x0 = x * p, y0 = y * p, z0 = z * p, x1 = x0 + p, y1 = y0 + p, z1 = z0 + p;
				// texture pixel of this face, like the full block would show it
				float tu, tv;
				switch (f) {
					case 0, 1 -> { tu = x; tv = z; }
					case 2 -> { tu = 15 - x; tv = 15 - y; }
					case 3 -> { tu = x; tv = 15 - y; }
					case 4 -> { tu = z; tv = 15 - y; }
					default -> { tu = 15 - z; tv = 15 - y; }
				}
				float ua = u0 + (u1 - u0) * tu / 16f, ub = u0 + (u1 - u0) * (tu + 1) / 16f;
				float va = v0 + (v1 - v0) * tv / 16f, vb = v0 + (v1 - v0) * (tv + 1) / 16f;
				float[][] q = switch (f) {
					case 0 -> new float[][] {{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}};
					case 1 -> new float[][] {{x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}, {x0, y1, z0}};
					case 2 -> new float[][] {{x1, y1, z0}, {x1, y0, z0}, {x0, y0, z0}, {x0, y1, z0}};
					case 3 -> new float[][] {{x0, y1, z1}, {x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}};
					case 4 -> new float[][] {{x0, y1, z0}, {x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}};
					default -> new float[][] {{x1, y1, z1}, {x1, y0, z1}, {x1, y0, z0}, {x1, y1, z0}};
				};
				float[][] uv = {{ua, va}, {ua, vb}, {ub, vb}, {ub, va}};
				for (int k = 0; k < 4; k++) {
					out[n++] = q[k][0];
					out[n++] = q[k][1];
					out[n++] = q[k][2];
					out[n++] = uv[k][0];
					out[n++] = uv[k][1];
					out[n++] = dir[0];
					out[n++] = dir[1];
					out[n++] = dir[2];
				}
			}
		}
		return java.util.Arrays.copyOf(out, n);
	}
}
