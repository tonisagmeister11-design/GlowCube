package net.glowcube.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Alles, was GlowCube auf den Bildschirm malt. Bewusst nur mit fill() und
 * fillGradient() gebaut - das sind die zwei Zeichenbefehle, die sich zwischen
 * den Minecraft-Fassungen am wenigsten bewegen.
 */
public final class Render2D {
    private Render2D() {
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }

    // ------------------------------------------------------------- Rechtecke

    public static void rect(GuiGraphics gfx, float x, float y, float w, float h, int color) {
        gfx.fill(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h), color);
    }

    /** Senkrechter Verlauf - ein einziger Zeichenbefehl. */
    public static void gradientV(GuiGraphics gfx, float x, float y, float w, float h, int top, int bottom) {
        gfx.fillGradient(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h), top, bottom);
    }

    /** Rechteck mit runden Ecken, zeilenweise aufgebaut. */
    public static void roundedRect(GuiGraphics gfx, float x, float y, float w, float h, float radius, int color) {
        int xi = Math.round(x);
        int yi = Math.round(y);
        int wi = Math.round(w);
        int hi = Math.round(h);
        if (wi <= 0 || hi <= 0) {
            return;
        }
        int r = Math.max(0, Math.min(Math.round(radius), Math.min(wi, hi) / 2));

        for (int i = 0; i < r; i++) {
            int inset = arcInset(r, i);
            gfx.fill(xi + inset, yi + i, xi + wi - inset, yi + i + 1, color);
            gfx.fill(xi + inset, yi + hi - i - 1, xi + wi - inset, yi + hi - i, color);
        }
        gfx.fill(xi, yi + r, xi + wi, yi + hi - r, color);
    }

    /** Waagerechter Verlauf mit runden Enden - spaltenweise. */
    public static void roundedGradientH(GuiGraphics gfx, float x, float y, float w, float h,
                                        float radius, int left, int right) {
        int xi = Math.round(x);
        int yi = Math.round(y);
        int wi = Math.round(w);
        int hi = Math.round(h);
        if (wi <= 0 || hi <= 0) {
            return;
        }
        int r = Math.max(0, Math.min(Math.round(radius), Math.min(wi, hi) / 2));

        for (int col = 0; col < wi; col++) {
            int distance = Math.min(col, wi - col - 1);
            int inset = distance < r ? arcInset(r, distance) : 0;
            int color = ColorUtil.lerp(left, right, wi <= 1 ? 0.0f : (float) col / (wi - 1));
            gfx.fill(xi + col, yi + inset, xi + col + 1, yi + hi - inset, color);
        }
    }

    /** Wie weit eine Zeile/Spalte im Abstand {@code i} von der Ecke eingerueckt ist. */
    private static int arcInset(int radius, int i) {
        double d = radius - i - 0.5;
        return (int) Math.round(radius - Math.sqrt(Math.max(0.0, radius * radius - d * d)));
    }

    /** 1px-Rahmen mit runden Ecken. */
    public static void roundedOutline(GuiGraphics gfx, float x, float y, float w, float h, float radius, int color) {
        int xi = Math.round(x);
        int yi = Math.round(y);
        int wi = Math.round(w);
        int hi = Math.round(h);
        int r = Math.max(0, Math.min(Math.round(radius), Math.min(wi, hi) / 2));

        for (int i = 0; i < r; i++) {
            int inset = arcInset(r, i);
            int next = i + 1 < r ? arcInset(r, i + 1) : 0;
            int thickness = Math.max(1, inset - next);
            gfx.fill(xi + inset, yi + i, xi + inset + thickness, yi + i + 1, color);
            gfx.fill(xi + wi - inset - thickness, yi + i, xi + wi - inset, yi + i + 1, color);
            gfx.fill(xi + inset, yi + hi - i - 1, xi + inset + thickness, yi + hi - i, color);
            gfx.fill(xi + wi - inset - thickness, yi + hi - i - 1, xi + wi - inset, yi + hi - i, color);
        }
        gfx.fill(xi, yi + r, xi + 1, yi + hi - r, color);
        gfx.fill(xi + wi - 1, yi + r, xi + wi, yi + hi - r, color);
        gfx.fill(xi + r, yi, xi + wi - r, yi + 1, color);
        gfx.fill(xi + r, yi + hi - 1, xi + wi - r, yi + hi, color);
    }

    /** Weiches Leuchten hinter einer Flaeche. */
    public static void glow(GuiGraphics gfx, float x, float y, float w, float h, float radius, int color, int layers) {
        for (int i = layers; i >= 1; i--) {
            float spread = i * 1.5f;
            int faded = ColorUtil.withAlpha(color, 0.10f * (1.0f - (float) i / (layers + 1)));
            roundedRect(gfx, x - spread, y - spread, w + spread * 2, h + spread * 2, radius + spread, faded);
        }
    }

    // ------------------------------------------------------------------ Text

    public static void text(GuiGraphics gfx, String text, float x, float y, int color) {
        gfx.drawString(font(), text, Math.round(x), Math.round(y), color, false);
    }

    public static void textShadow(GuiGraphics gfx, String text, float x, float y, int color) {
        gfx.drawString(font(), text, Math.round(x), Math.round(y), color, true);
    }

    public static void textCentered(GuiGraphics gfx, String text, float centerX, float y, int color) {
        text(gfx, text, centerX - width(text) / 2.0f, y, color);
    }

    /** Buchstabe fuer Buchstabe durch einen Verlauf gefaerbt. */
    public static void textGradient(GuiGraphics gfx, String text, float x, float y, int from, int to) {
        float cursor = x;
        int length = text.length();
        for (int i = 0; i < length; i++) {
            String ch = String.valueOf(text.charAt(i));
            int color = ColorUtil.lerp(from, to, length <= 1 ? 0.0f : (float) i / (length - 1));
            text(gfx, ch, cursor, y, color);
            cursor += width(ch);
        }
    }

    public static int width(String text) {
        return font().width(text);
    }

    public static int lineHeight() {
        return font().lineHeight;
    }

    /** Kuerzt mit "..." ab, damit nichts aus seiner Karte laeuft. */
    public static String clip(String text, int maxWidth) {
        if (width(text) <= maxWidth) {
            return text;
        }
        StringBuilder builder = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (width(builder.toString() + c + "...") > maxWidth) {
                break;
            }
            builder.append(c);
        }
        return builder + "...";
    }

    // --------------------------------------------------------------- Bereich

    public static void pushScissor(GuiGraphics gfx, float x, float y, float w, float h) {
        gfx.enableScissor(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h));
    }

    public static void popScissor(GuiGraphics gfx) {
        gfx.disableScissor();
    }

    public static boolean hovered(double mouseX, double mouseY, float x, float y, float w, float h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }
}
