package net.glowcube.client.util;

public final class ColorUtil {
    private ColorUtil() {
    }

    public static int argb(int a, int r, int g, int b) {
        return (a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | (b & 0xFF);
    }

    public static int alpha(int color) {
        return color >>> 24 & 0xFF;
    }

    public static int red(int color) {
        return color >> 16 & 0xFF;
    }

    public static int green(int color) {
        return color >> 8 & 0xFF;
    }

    public static int blue(int color) {
        return color & 0xFF;
    }

    /** Setzt die Deckkraft neu, 0..1. */
    public static int withAlpha(int color, float alpha) {
        int a = (int) (Math.max(0.0f, Math.min(1.0f, alpha)) * 255.0f);
        return a << 24 | (color & 0x00FFFFFF);
    }

    /** Multipliziert die vorhandene Deckkraft - fuer Ein-/Ausblenden. */
    public static int fade(int color, float factor) {
        return withAlpha(color, alpha(color) / 255.0f * factor);
    }

    public static int lerp(int from, int to, float t) {
        float f = Math.max(0.0f, Math.min(1.0f, t));
        return argb(
                Math.round(alpha(from) + (alpha(to) - alpha(from)) * f),
                Math.round(red(from) + (red(to) - red(from)) * f),
                Math.round(green(from) + (green(to) - green(from)) * f),
                Math.round(blue(from) + (blue(to) - blue(from)) * f));
    }

    /** Ringfoermiger Verlauf ueber drei Farben, t = 0..1. */
    public static int gradient(int a, int b, int c, float t) {
        float p = t % 1.0f;
        if (p < 1.0f / 3.0f) {
            return lerp(a, b, p * 3.0f);
        }
        if (p < 2.0f / 3.0f) {
            return lerp(b, c, (p - 1.0f / 3.0f) * 3.0f);
        }
        return lerp(c, a, (p - 2.0f / 3.0f) * 3.0f);
    }

    /** Heller/dunkler machen, ohne den Farbton zu verlieren. */
    public static int shade(int color, float factor) {
        return argb(alpha(color),
                Math.min(255, Math.round(red(color) * factor)),
                Math.min(255, Math.round(green(color) * factor)),
                Math.min(255, Math.round(blue(color) * factor)));
    }
}
