package net.glowcube.client.module.hud;

import net.glowcube.client.core.setting.BooleanSetting;
import net.glowcube.client.hud.TextHudModul;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Die echte Uhrzeit - aus AxolotlClient ({@code IRLTimeHud}). */
public final class UhrzeitHud extends TextHudModul {
    private static final DateTimeFormatter MIT_SEKUNDEN = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter OHNE_SEKUNDEN = DateTimeFormatter.ofPattern("HH:mm");

    private final BooleanSetting sekunden = register(
            new BooleanSetting("Sekunden", "Sekunden mit anzeigen", true));

    public UhrzeitHud() {
        super("Uhrzeit", "Zeigt die echte Uhrzeit");
    }

    @Override
    protected String text() {
        return LocalTime.now().format(sekunden.get() ? MIT_SEKUNDEN : OHNE_SEKUNDEN);
    }
}
