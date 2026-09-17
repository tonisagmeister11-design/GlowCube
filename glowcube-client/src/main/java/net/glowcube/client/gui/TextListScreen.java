package net.glowcube.client.gui;

import net.glowcube.client.GlowCubeClient;
import net.glowcube.client.core.setting.TextListSetting;
import net.glowcube.client.util.Render2D;
import net.glowcube.client.util.Theme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;

/**
 * Freie Textlisten bearbeiten - derzeit die Nachrichten von Spammer.
 *
 * <p>Bewusst schlicht: ein Eingabefeld, Enter legt an, ein Klick auf das
 * Kreuz nimmt weg. Mehr braucht es fuer eine Handvoll Zeilen nicht, und
 * alles Weitere waere ein zweites GUI im GUI.
 */
public final class TextListScreen extends Screen {
    private static final float PANEL_B = 320.0f;
    private static final float ZEILE_H = 18.0f;

    private final Screen zurueck;
    private final TextListSetting liste;
    private String eingabe = "";

    public TextListScreen(Screen zurueck, TextListSetting liste) {
        super(Component.literal(liste.name()));
        this.zurueck = zurueck;
        this.liste = liste;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private float panelX() {
        return width / 2.0f - PANEL_B / 2.0f;
    }

    private float panelY() {
        return 48;
    }

    private float panelH() {
        return Math.min(height - 96, 90 + liste.size() * ZEILE_H);
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        Render2D.rect(gfx, 0, 0, width, height, Theme.BACKDROP);

        float x = panelX();
        float y = panelY();
        float h = panelH();

        Render2D.glow(gfx, x, y, PANEL_B, h, 8, Theme.accentStart(), 5);
        Render2D.roundedRect(gfx, x, y, PANEL_B, h, 8, Theme.PANEL);
        Render2D.roundedOutline(gfx, x, y, PANEL_B, h, 8, Theme.OUTLINE);

        Render2D.textGradient(gfx, liste.name().toUpperCase(Locale.ROOT), x + 14, y + 13,
                Theme.accentStart(), Theme.accentEnd());
        Render2D.text(gfx, liste.description(), x + 14, y + 27, Theme.TEXT_FAINT);

        // Eingabefeld
        float eingabeY = y + 44;
        Render2D.roundedRect(gfx, x + 14, eingabeY, PANEL_B - 28, 18, 4, Theme.CARD);
        Render2D.roundedOutline(gfx, x + 14, eingabeY, PANEL_B - 28, 18, 4, Theme.accentStart());
        String anzeige = eingabe.isEmpty() ? "Neue Zeile - Enter legt an" : eingabe + "_";
        Render2D.text(gfx, Render2D.clip(anzeige, (int) PANEL_B - 40), x + 20, eingabeY + 5,
                eingabe.isEmpty() ? Theme.TEXT_FAINT : Theme.TEXT);

        // Zeilen
        float cursor = eingabeY + 26;
        List<String> werte = liste.werte();
        for (int i = 0; i < werte.size(); i++) {
            if (cursor + ZEILE_H > y + h - 8) {
                break;
            }
            boolean ueber = Render2D.hovered(mouseX, mouseY, x + 14, cursor, PANEL_B - 28, ZEILE_H);
            Render2D.roundedRect(gfx, x + 14, cursor, PANEL_B - 28, ZEILE_H - 2, 3,
                    ueber ? Theme.CARD_HOVER : Theme.CARD);
            Render2D.text(gfx, Render2D.clip(werte.get(i), (int) PANEL_B - 56), x + 20,
                    cursor + 4, Theme.TEXT);
            Render2D.text(gfx, "x", x + PANEL_B - 26, cursor + 4,
                    ueber ? Theme.ACCENT_C : Theme.TEXT_FAINT);
            cursor += ZEILE_H;
        }

        Render2D.textCentered(gfx, "Esc schliesst", width / 2.0f, y + h - 14, Theme.TEXT_FAINT);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doppelklick) {
        float x = panelX();
        float cursor = panelY() + 70;
        for (int i = 0; i < liste.size(); i++) {
            if (Render2D.hovered(event.x(), event.y(), x + PANEL_B - 32, cursor, 24, ZEILE_H)) {
                liste.remove(i);
                GlowCubeClient.config().save();
                return true;
            }
            cursor += ZEILE_H;
        }
        return super.mouseClicked(event, doppelklick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (!eingabe.isEmpty()) {
                eingabe = eingabe.substring(0, eingabe.length() - 1);
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            liste.add(eingabe);
            eingabe = "";
            GlowCubeClient.config().save();
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        String zeichen = event.codepointAsString();
        if (!zeichen.isEmpty() && eingabe.length() < 240) {
            eingabe += zeichen;
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public void onClose() {
        GlowCubeClient.config().save();
        minecraft.setScreen(zurueck);
    }
}
