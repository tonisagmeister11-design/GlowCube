package net.glowcube.client.module.hud;

import net.glowcube.client.core.setting.BooleanSetting;
import net.glowcube.client.hud.TextHudModul;

/** Belegter Arbeitsspeicher - aus AxolotlClient ({@code MemoryHud}). */
public final class SpeicherHud extends TextHudModul {
    private final BooleanSetting prozent = register(
            new BooleanSetting("Prozent", "Den Anteil in Prozent dazuschreiben", true));

    public SpeicherHud() {
        super("Arbeitsspeicher", "Zeigt, wie viel Arbeitsspeicher Minecraft belegt");
    }

    @Override
    protected String text() {
        Runtime rt = Runtime.getRuntime();
        long belegt = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
        long max = rt.maxMemory() / 1024 / 1024;
        String text = belegt + "/" + max + " MB";
        return prozent.get() && max > 0 ? text + " (" + (belegt * 100 / max) + "%)" : text;
    }
}
