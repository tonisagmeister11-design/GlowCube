package de.theboys.client;

import de.theboys.TheBoys;
import de.theboys.net.StatusPayload;
import de.theboys.power.ActiveState;
import de.theboys.power.Power;
import de.theboys.power.PowerAttachments;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/** Power panel (abilities, keys, cooldowns, meter), A-Train's speedometer and full-screen effects. */
public final class HudOverlay {
	private HudOverlay() {
	}

	public static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null) {
			return;
		}
		int w = g.guiWidth();
		int h = g.guiHeight();

		screenEffects(g, mc, w, h);

		Power power = PowerAttachments.powerOf(player);
		if (power == Power.NONE) {
			return;
		}
		Font font = mc.font;
		ActiveState active = PowerAttachments.active(player);
		StatusPayload status = ClientState.status;

		// --- ability panel on the right
		int panelW = 132;
		int x = w - panelW - 6;
		int y = h / 2 - 52;
		g.fill(x - 4, y - 4, x + panelW, y + 98, 0x88000000);
		g.fill(x - 4, y - 4, x - 2, y + 98, 0xFF000000 | power.color());
		Identifier icon = TheBoys.id("textures/gui/power/" + power.id() + ".png");
		g.blit(RenderPipelines.GUI_TEXTURED, icon, x, y, 0f, 0f, 16, 16, 16, 16);
		g.text(font, Component.translatable(power.translationKey()), x + 20, y + 4, 0xFF000000 | power.color(), true);
		y += 22;
		for (int i = 0; i < 4; i++) {
			int packed = status.cooldown(i);
			int remaining = StatusPayload.remaining(packed);
			int total = Math.max(1, StatusPayload.total(packed));
			boolean ready = remaining == 0;
			Component key = Keys.ABILITY[i].getTranslatedKeyMessage();
			g.fill(x, y, x + 14, y + 12, ready ? 0xCC2E7D32 : 0xCC5A1A1A);
			g.centeredText(font, key, x + 7, y + 2, 0xFFFFFFFF);
			g.text(font, Component.translatable(power.abilityKey(i)), x + 18, y + 2, ready ? 0xFFFFFFFF : 0xFF9A9A9A, true);
			if (!ready) {
				int barW = (int) ((panelW - 22) * (remaining / (float) total));
				g.fill(x + 18, y + 11, x + 18 + barW, y + 12, 0xFFE53935);
				String secs = String.format("%.1fs", remaining / 20f);
				g.text(font, secs, x + panelW - 6 - font.width(secs), y + 2, 0xFFE57373, true);
			}
			y += 15;
		}

		// meter
		String meterKey = switch (power) {
			case HOMELANDER -> "hud.theboys.heat";
			case A_TRAIN -> "hud.theboys.heart";
			case SOLDIER_BOY -> "hud.theboys.charge";
			case BUTCHER -> "hud.theboys.rip";
			default -> null;
		};
		if (meterKey != null) {
			float m = Mth.clamp(status.meter() / 1000f, 0, 1);
			int color = power == Power.A_TRAIN ? lerpColor(0xFF43A047, 0xFFD32F2F, m) : lerpColor(0xFFFFB300, 0xFFFF3D00, m);
			g.text(font, Component.translatable(meterKey), x, y + 2, 0xFFBDBDBD, true);
			g.fill(x, y + 12, x + panelW - 6, y + 17, 0xFF222222);
			g.fill(x, y + 12, x + (int) ((panelW - 6) * m), y + 17, color);
			if (power == Power.A_TRAIN && m > 0.8f && player.tickCount % 10 < 5) {
				g.text(font, Component.translatable("hud.theboys.heart_warning"), x, y + 20, 0xFFFF5252, true);
			}
		}

		// --- A-Train speedometer
		if (power == Power.A_TRAIN && (active.has(ActiveState.SPEED) || ClientState.speedKmh > 20)) {
			speedometer(g, font, w, h, active);
		}
	}

	private static void speedometer(GuiGraphicsExtractor g, Font font, int w, int h, ActiveState active) {
		int kmh = Math.round(ClientState.speedKmh);
		String number = Integer.toString(kmh);
		int cx = w / 2;
		int cy = h - 72;
		boolean timeJump = active.has(ActiveState.REWIND);
		int color = timeJump ? 0xFF7FDBFF : kmh > 300 ? 0xFFFFEB3B : 0xFFFFFFFF;
		g.fill(cx - 46, cy - 6, cx + 46, cy + 26, 0x99000000);
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		g.pose().scale(2.0f, 2.0f);
		g.centeredText(font, number, 0, 0, color);
		g.pose().popMatrix();
		g.centeredText(font, Component.literal("km/h"), cx, cy + 16, 0xFFB0BEC5);
		// small bar: 0 - 1000 km/h
		float frac = Mth.clamp(kmh / 1000f, 0, 1);
		g.fill(cx - 44, cy + 24, cx + 44, cy + 26, 0xFF263238);
		g.fill(cx - 44, cy + 24, cx - 44 + (int) (88 * frac), cy + 26, timeJump ? 0xFF00E5FF : 0xFF2979FF);
	}

	private static void screenEffects(GuiGraphicsExtractor g, Minecraft mc, int w, int h) {
		// time running backwards
		if (ClientState.rewindTicks > 0) {
			float t = ClientState.rewindTicks / (float) ClientState.rewindTotal;
			float fade = Math.min(1f, Math.min(t * 6f, (1 - t) * 8f + 0.2f));
			if (ClientState.rewindIsMine) {
				// the runner sees streaks of speed
				int a = (int) (70 * fade);
				g.fillGradient(0, 0, w, h, (a << 24) | 0x00B3E5FC, 0x00000000);
				for (int i = 0; i < 24; i++) {
					int ly = (int) ((i * 37 + mc.player.tickCount * 23) % h);
					int len = 40 + (i * 53) % 120;
					int lx = (i * 97) % w;
					g.fill(lx, ly, Math.min(w, lx + len), ly + 1, ((int) (110 * fade) << 24) | 0xFFFFFF);
				}
			} else {
				int a = (int) (90 * fade);
				g.fill(0, 0, w, h, (a << 24) | 0x0D3B66);
				for (int ly = (mc.player.tickCount * 3) % 4; ly < h; ly += 4) {
					g.fill(0, ly, w, ly + 1, ((int) (40 * fade) << 24));
				}
				if (mc.player.tickCount % 20 < 14) {
					g.pose().pushMatrix();
					g.pose().translate(18, 18);
					g.pose().scale(2.0f, 2.0f);
					g.text(mc.font, Component.literal("◀◀"), 0, 0, (((int) (230 * fade)) << 24) | 0xFFFFFF, true);
					g.pose().popMatrix();
				}
				g.text(mc.font, Component.translatable("hud.theboys.rewind"), 18, 40, (((int) (220 * fade)) << 24) | 0xB3E5FC, true);
			}
		}
		// blood on the screen when someone bursts right in front of you
		if (ClientState.bloodTicks > 0) {
			float f = ClientState.bloodTicks / 50f;
			int a = (int) (150 * f);
			g.fillGradient(0, 0, w, h / 3, (a << 24) | 0x7A0000, 0x00000000);
			g.fillGradient(0, h * 2 / 3, w, h, 0x00000000, (a << 24) | 0x7A0000);
			for (int i = 0; i < 14; i++) {
				int bx = (i * 131 + 17) % w;
				int by = (i * 71 + 29) % h;
				int size = 6 + (i * 13) % 22;
				g.fill(bx, by, bx + size, by + size * 2 / 3, ((int) (170 * f) << 24) | 0x6B0000);
			}
		}
	}

	private static int lerpColor(int a, int b, float t) {
		int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
		int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
		return 0xFF000000 | ((int) Mth.lerp(t, ar, br) << 16) | ((int) Mth.lerp(t, ag, bg) << 8) | (int) Mth.lerp(t, ab, bb);
	}
}
