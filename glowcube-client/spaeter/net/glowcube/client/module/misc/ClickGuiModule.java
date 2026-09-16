package net.glowcube.client.module.misc;

import com.mojang.blaze3d.platform.InputConstants;
import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.gui.ClickGuiScreen;

/**
 * Kein Dauerzustand, sondern ein Knopf: einschalten heisst Fenster auf, und
 * das Modul schaltet sich selbst wieder aus.
 */
public final class ClickGuiModule extends Module {
    public ClickGuiModule() {
        super("ClickGUI", "Das Fenster hier", Category.MISC, InputConstants.KEY_RSHIFT);
    }

    @Override
    public void onEnable() {
        mc.setScreen(new ClickGuiScreen());
        setEnabledSilently(false);
    }
}
