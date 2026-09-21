package net.glowcube.client.gui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Fassung fuer <b>26.3 und neuer</b>: das grafische Menue wird gerade auf
 * Mojangs neues 2D-System (GuiGraphicsExtractor) portiert. Bis dahin oeffnet
 * sich hier kein Fenster - stattdessen ein kurzer Hinweis, dann wieder zu.
 * Alle Module lassen sich per Tastenkuerzel schalten.
 */
public final class ClickGuiScreen extends Screen {
    public ClickGuiScreen() {
        super(Component.literal("GlowCube"));
    }

    @Override
    protected void init() {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.sendSystemMessage(Component.literal(
                    "[GlowCube] ClickGUI auf 26.3 folgt - Module bis dahin per Tastenkuerzel."));
        }
        onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
