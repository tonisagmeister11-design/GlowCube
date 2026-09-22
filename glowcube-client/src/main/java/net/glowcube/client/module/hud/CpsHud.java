package net.glowcube.client.module.hud;

import net.glowcube.client.core.Category;
import net.glowcube.client.core.setting.BooleanSetting;
import net.glowcube.client.hud.KlickZaehler;
import net.glowcube.client.hud.TextHudModul;

/** Klicks pro Sekunde - aus AxolotlClient ({@code CPSHud}, nach KronHUD). */
public final class CpsHud extends TextHudModul {
    private final BooleanSetting rechtsklick = register(
            new BooleanSetting("Rechtsklick", "Auch die Rechtsklicks zeigen: \"links | rechts CPS\"", false));

    public CpsHud() {
        super("CPS", "Zeigt deine Klicks pro Sekunde", Category.PVP_HUD);
    }

    @Override
    protected String text() {
        int links = KlickZaehler.links();
        return rechtsklick.get() ? links + " | " + KlickZaehler.rechts() + " CPS" : links + " CPS";
    }
}
