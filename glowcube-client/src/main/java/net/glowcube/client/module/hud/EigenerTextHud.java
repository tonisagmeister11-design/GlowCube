package net.glowcube.client.module.hud;

import net.glowcube.client.core.setting.TextListSetting;
import net.glowcube.client.hud.TextHudModul;

/** Ein frei waehlbarer Text - aus AxolotlClient ({@code CustomHudEntry}). */
public final class EigenerTextHud extends TextHudModul {
    private final TextListSetting text = register(
            new TextListSetting("Text", "Was angezeigt wird (der erste Eintrag)", "GlowCube"));

    public EigenerTextHud() {
        super("Eigener Text", "Zeigt einen Text, den du selbst festlegst");
    }

    @Override
    protected String text() {
        return text.size() == 0 ? "" : text.werte().get(0);
    }
}
