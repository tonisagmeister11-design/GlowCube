package net.glowcube.client.util;

import net.glowcube.client.mixin.OptionInstanceAccessor;
import net.minecraft.client.Minecraft;

/**
 * Helligkeit ueber den erlaubten Bereich hinaus. Der Regler im Menue klemmt bei
 * 1.0, der Wert dahinter nicht - deshalb wird er direkt gesetzt.
 * Mehrere Module wollen das gleichzeitig, darum ein Zaehler statt eines Schalters.
 */
public final class Gamma {
    private Gamma() {
    }

    private static int holders;
    private static double original = -1.0;

    public static void acquire() {
        if (holders++ == 0) {
            Minecraft mc = Minecraft.getInstance();
            original = mc.options.gamma().get();
            setRaw(16.0);
        }
    }

    public static void release() {
        if (holders > 0 && --holders == 0 && original >= 0.0) {
            setRaw(original);
            original = -1.0;
        }
    }

    @SuppressWarnings("unchecked")
    private static void setRaw(double value) {
        Minecraft mc = Minecraft.getInstance();
        ((OptionInstanceAccessor<Double>) (Object) mc.options.gamma()).glowcube$setValue(value);
    }
}
