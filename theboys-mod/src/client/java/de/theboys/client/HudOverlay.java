package de.theboys.client;

import java.util.ArrayList;
import java.util.List;

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
		if (player == null || ClientState.hideHud) {
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

		// --- ability panel on the right: long names (and "Sneak + ..." parts) wrap instead of running off the screen
		int panelW = Math.min(176, w / 2 - 10);
		int x = w - panelW - 6;
		int textW = panelW - 18 - 40;
		List<List<String>> lines = new ArrayList<>();
		int rows = 0;
		for (int i = 0; i < 4; i++) {
			List<String> l = wrap(font, Component.translatable(power.abilityKey(i)).getString(), textW);
			lines.add(l);
			rows += 5 + 10 * Math.max(1, l.size()) + (l.size() > 1 ? 0 : 0);
		}
		boolean hasMeter = switch (power) {
			case HOMELANDER, SOLDIER_BOY, BUTCHER, STARLIGHT, STORMFRONT, THE_DEEP, BLACK_ADAM -> true;
			default -> false;
		};
		int height = 22 + rows + (hasMeter ? 22 : 0) + 4;
		int y = Math.max(8, h / 2 - height / 2);
		g.fill(x - 4, y - 4, x + panelW, y + height, 0x88000000);
		g.fill(x - 4, y - 4, x - 2, y + height, 0xFF000000 | power.color());
		Identifier icon = TheBoys.id("textures/gui/power/" + power.id() + ".png");
		g.blit(RenderPipelines.GUI_TEXTURED, icon, x, y, 0f, 0f, 16, 16, 16, 16);
		g.text(font, Component.translatable(power.translationKey()), x + 20, y + 4, 0xFF000000 | power.color(), true);
		y += 22;
		for (int i = 0; i < 4; i++) {
			int packed = status.cooldown(i);
			int remaining = StatusPayload.remaining(packed);
			int total = Math.max(1, StatusPayload.total(packed));
			boolean ready = remaining == 0;
			List<String> l = lines.get(i);
			int rowH = 5 + 10 * Math.max(1, l.size());
			Component key = Keys.ABILITY[i].getTranslatedKeyMessage();
			g.fill(x, y, x + 14, y + rowH - 3, ready ? 0xCC2E7D32 : 0xCC5A1A1A);
			g.centeredText(font, key, x + 7, y + (rowH - 3) / 2 - 3, 0xFFFFFFFF);
			for (int k = 0; k < l.size(); k++) {
				g.text(font, l.get(k), x + 18, y + 2 + 10 * k, ready ? 0xFFFFFFFF : 0xFF9A9A9A, true);
			}
			if (!ready) {
				int barW = (int) ((panelW - 22) * (remaining / (float) total));
				g.fill(x + 18, y + rowH - 4, x + 18 + barW, y + rowH - 3, 0xFFE53935);
				String secs = String.format("%.1fs", remaining / 20f);
				g.text(font, secs, x + panelW - 6 - font.width(secs), y + 2, 0xFFE57373, true);
			}
			y += rowH;
		}

		// meter
		String meterKey = switch (power) {
			case HOMELANDER -> "hud.theboys.heat";
			case SOLDIER_BOY -> "hud.theboys.charge";
			case BUTCHER -> "hud.theboys.rip";
			case STARLIGHT -> "hud.theboys.light";
			case STORMFRONT -> "hud.theboys.voltage";
			case THE_DEEP -> "hud.theboys.moisture";
			case BLACK_ADAM -> "hud.theboys.zap";
			default -> null;
		};
		if (meterKey != null) {
			float m = Mth.clamp(status.meter() / 1000f, 0, 1);
			int color = switch (power) {
				case STARLIGHT -> lerpColor(0xFFFFF3C4, 0xFFFFC94A, m);
				case THE_DEEP -> m < 0.15f ? 0xFFE57373 : lerpColor(0xFF4DD0E1, 0xFF1E88E5, m);
				case STORMFRONT -> lerpColor(0xFF8FC8FF, 0xFFFFFFFF, m);
				case BLACK_ADAM -> lerpColor(0xFFF2C230, 0xFFFFFBE6, m);
				default -> lerpColor(0xFFFFB300, 0xFFFF3D00, m);
			};
			g.text(font, Component.translatable(meterKey), x, y + 2, 0xFFBDBDBD, true);
			g.fill(x, y + 12, x + panelW - 6, y + 17, 0xFF222222);
			g.fill(x, y + 12, x + (int) ((panelW - 6) * m), y + 17, color);
		}

		// --- A-Train speedometer
		if (power == Power.A_TRAIN && (active.has(ActiveState.SPEED) || ClientState.speedKmh > 20)) {
			speedometer(g, font, w, h, active);
		}
	}

	/** Breaks text into lines no wider than maxW, at spaces (a single too-long word is cut). */
	private static List<String> wrap(Font font, String text, int maxW) {
		List<String> out = new ArrayList<>();
		StringBuilder cur = new StringBuilder();
		for (String word : text.split(" ")) {
			String test = cur.length() == 0 ? word : cur + " " + word;
			if (font.width(test) <= maxW) {
				cur = new StringBuilder(test);
				continue;
			}
			if (cur.length() > 0) out.add(cur.toString());
			cur = new StringBuilder(word);
			while (font.width(cur.toString()) > maxW && cur.length() > 1) {
				int cut = cur.length() - 1;
				while (cut > 1 && font.width(cur.substring(0, cut)) > maxW) cut--;
				out.add(cur.substring(0, cut));
				cur = new StringBuilder(cur.substring(cut));
			}
		}
		if (cur.length() > 0) out.add(cur.toString());
		return out;
	}

	private static void speedometer(GuiGraphicsExtractor g, Font font, int w, int h, ActiveState active) {
		int kmh = Math.round(ClientState.speedKmh);
		String number = Integer.toString(kmh);
		int cx = w / 2;
		int cy = h - 100;
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
		// blinded by Starlight
		if (ClientState.flashTicks > 0) {
			float f = ClientState.flashStrength * Math.min(1f, ClientState.flashTicks / 18f);
			g.fill(0, 0, w, h, ((int) (245 * f) << 24) | 0xFFFDF2);
		}
		// the serum hits: a pulse of colour from the edges of the screen
		if (ClientState.injectFlash > 0) {
			float f = ClientState.injectFlash / 30f;
			float pulse = 0.6f + 0.4f * Mth.sin((30 - ClientState.injectFlash) * 0.7f);
			int a = (int) (170 * f * pulse);
			int rgb = ClientState.injectColor & 0xFFFFFF;
			g.fillGradient(0, 0, w, h / 3, (a << 24) | rgb, 0x00000000);
			g.fillGradient(0, h * 2 / 3, w, h, 0x00000000, (a << 24) | rgb);
			g.fill(0, 0, w, h, ((int) (40 * f) << 24) | rgb);
		}
		// blood on the screen when someone bursts right in front of you
		if (ClientState.bloodTicks > 0) {
			float f = ClientState.bloodTicks / 50f;
			int a = (int) (120 * f);
			g.fillGradient(0, 0, w, h / 4, (a << 24) | 0x5A0000, 0x00000000);
			g.fillGradient(0, h * 3 / 4, w, h, 0x00000000, (a << 24) | 0x5A0000);
			for (int i = 0; i < 9; i++) {
				int bx = (i * 131 + 17) % (w - 40) + 10;
				int by = (i * 71 + 29) % (h / 2) + (i % 2 == 0 ? 0 : h / 2 - 20);
				int r = 3 + (i * 7) % 6;
				int color = ((int) (190 * f) << 24) | 0x6B0000;
				// round-ish drop with a running trail
				g.fill(bx - r, by - r / 2, bx + r, by + r / 2, color);
				g.fill(bx - r / 2, by - r, bx + r / 2, by + r, color);
				int run = (int) ((50 - ClientState.bloodTicks) * 0.6f) + r;
				g.fill(bx - 1, by, bx + 1, by + run, color);
			}
		}
	}

	private static int lerpColor(int a, int b, float t) {
		int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
		int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
		return 0xFF000000 | ((int) Mth.lerp(t, ar, br) << 16) | ((int) Mth.lerp(t, ag, bg) << 8) | (int) Mth.lerp(t, ab, bb);
	}
}
