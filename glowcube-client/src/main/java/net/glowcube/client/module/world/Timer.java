package net.glowcube.client.module.world;

import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.NumberSetting;

/**
 * Beschleunigt die Weltuhr clientseitig.
 *
 * Uebertragen von Timer aus BleachHack (GPL-3.0). Wie dort steht die Logik
 * nicht hier, sondern im Mixin auf den Taktgeber - dieses Modul haelt nur den
 * Faktor und den Zustand. Der Mixin fragt beides ueber die statischen
 * Methoden ab.
 *
 * Quelle: org/bleachhack/module/mods/Timer.java und dessen MixinRenderTickCounter
 */
public final class Timer extends Module {
    private static Timer instance;

    private final NumberSetting speed = register(new NumberSetting("Speed",
            "Wie schnell die Welt tickt - 1 ist normal", 1.0, 0.1, 10.0, 0.1));

    public Timer() {
        super("Timer", "Beschleunigt die Weltuhr", Category.EXPLOIT);
        instance = this;
    }

    /** Der Faktor, oder 1 wenn das Modul aus ist - so rechnet der Mixin nie mit Muell. */
    public static float factor() {
        return instance != null && instance.isEnabled() ? instance.speed.getFloat() : 1.0f;
    }

    @Override
    public String hudSuffix() {
        return speed.display() + "x";
    }
}
