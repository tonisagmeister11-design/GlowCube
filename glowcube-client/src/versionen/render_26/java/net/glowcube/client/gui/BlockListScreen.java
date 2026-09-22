package net.glowcube.client.gui;

import net.glowcube.client.core.setting.BlockListSetting;
import net.glowcube.client.util.Anim;
import net.glowcube.client.util.ColorUtil;
import net.glowcube.client.util.Render2D;
import net.glowcube.client.util.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.glowcube.client.render.Netz;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Fassung fuer <b>26.3 und neuer</b>: die Blockliste von X-Ray bearbeiten.
 * Wortgleich zur 1.21-Fassung, nur auf {@code GuiGraphicsExtractor} und mit
 * versionssicherem Bildschirmwechsel.
 */
public final class BlockListScreen extends Screen {
    private static final int PANEL_W = 300;
    private static final int PANEL_H = 260;
    private static final int ROW_H = 15;
    private static final int PAD = 12;

    private final Screen parent;
    private final BlockListSetting list;
    private final Anim opening = new Anim(0.0f, 14.0f);

    private String input = "";
    private float scroll;
    private float scrollTarget;
    private int panelX;
    private int panelY;

    public BlockListScreen(Screen parent, BlockListSetting list) {
        super(Component.literal("X-Ray Blocks"));
        this.parent = parent;
        this.list = list;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        opening.snap(0.0f);
        opening.target(1.0f);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
        float alpha = Anim.easeOut(opening.value());
        panelX = (width - PANEL_W) / 2;
        panelY = (height - PANEL_H) / 2;
        float y = panelY + (1.0f - alpha) * 12.0f;

        Render2D.rect(gfx, 0, 0, width, height, ColorUtil.fade(Theme.BACKDROP, alpha));
        Render2D.glow(gfx, panelX, y, PANEL_W, PANEL_H, 10, ColorUtil.fade(Theme.accentStart(), alpha), 4);
        Render2D.roundedRect(gfx, panelX, y, PANEL_W, PANEL_H, 10, ColorUtil.fade(Theme.PANEL, alpha));
        Render2D.roundedOutline(gfx, panelX, y, PANEL_W, PANEL_H, 10, ColorUtil.fade(Theme.OUTLINE, alpha));

        Render2D.textGradient(gfx, list.name().toUpperCase(java.util.Locale.ROOT), panelX + PAD, y + 13,
                ColorUtil.fade(Theme.accentStart(), alpha), ColorUtil.fade(Theme.accentEnd(), alpha));
        String count = list.size() + " Eintraege";
        Render2D.text(gfx, count, panelX + PANEL_W - PAD - Render2D.width(count), y + 13,
                ColorUtil.fade(Theme.TEXT_FAINT, alpha));

        // Eingabefeld.
        float fieldY = y + 30;
        Render2D.roundedRect(gfx, panelX + PAD, fieldY, PANEL_W - PAD * 2, 18, 9,
                ColorUtil.fade(Theme.RAIL, alpha));
        Render2D.roundedOutline(gfx, panelX + PAD, fieldY, PANEL_W - PAD * 2, 18, 9,
                ColorUtil.fade(input.isEmpty() ? Theme.OUTLINE_SOFT : Theme.accentStart(), alpha));
        String shown = input.isEmpty() ? "Block suchen, Enter fuegt hinzu" : input;
        Render2D.text(gfx, shown, panelX + PAD + 10, fieldY + 5,
                ColorUtil.fade(input.isEmpty() ? Theme.TEXT_FAINT : Theme.TEXT, alpha));

        float listY = fieldY + 26;
        float listH = PANEL_H - (listY - y) - PAD;
        List<String> entries = entries();

        float maxScroll = Math.max(0.0f, entries.size() * ROW_H - listH);
        scrollTarget = Math.max(0.0f, Math.min(maxScroll, scrollTarget));
        scroll += (scrollTarget - scroll) * 0.35f;

        Render2D.pushScissor(gfx, panelX + PAD, listY, PANEL_W - PAD * 2, listH);
        for (int i = 0; i < entries.size(); i++) {
            float rowY = listY + i * ROW_H - scroll;
            if (rowY + ROW_H < listY || rowY > listY + listH) {
                continue;
            }
            String id = entries.get(i);
            boolean present = list.contains(id);
            boolean hover = Render2D.hovered(mouseX, mouseY, panelX + PAD, rowY, PANEL_W - PAD * 2, ROW_H);

            if (hover) {
                Render2D.roundedRect(gfx, panelX + PAD, rowY, PANEL_W - PAD * 2, ROW_H, 4,
                        ColorUtil.fade(Theme.CARD_HOVER, alpha));
            }
            if (present) {
                Render2D.roundedGradientH(gfx, panelX + PAD + 2, rowY + 4, 2, ROW_H - 8, 1,
                        ColorUtil.fade(Theme.accentStart(), alpha), ColorUtil.fade(Theme.accentEnd(), alpha));
            }
            Render2D.text(gfx, Render2D.clip(id.replace("minecraft:", ""), PANEL_W - PAD * 2 - 40),
                    panelX + PAD + 10, rowY + 4,
                    ColorUtil.fade(present ? Theme.TEXT : Theme.TEXT_FAINT, alpha));
            if (hover) {
                String action = present ? "−" : "+";
                Render2D.text(gfx, action, panelX + PANEL_W - PAD - 14, rowY + 4,
                        ColorUtil.fade(present ? Theme.ACCENT_C : Theme.ACCENT_A, alpha));
            }
        }
        Render2D.popScissor(gfx);
    }

    /** Ohne Eingabe die aktive Liste, mit Eingabe die passenden Bloecke aus der Registry. */
    private List<String> entries() {
        if (input.isEmpty()) {
            return new ArrayList<>(list.ids());
        }
        String needle = input.toLowerCase(Locale.ROOT);
        List<String> found = new ArrayList<>();
        for (Identifier id : (list.istWesen()
                ? BuiltInRegistries.ENTITY_TYPE.keySet()
                : BuiltInRegistries.BLOCK.keySet())) {
            String text = id.toString();
            if (text.contains(needle)) {
                found.add(text);
                if (found.size() >= 64) {
                    break;
                }
            }
        }
        return found;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doppelklick) {
        double mouseX = event.x();
        double mouseY = event.y();
        float listY = panelY + 56;
        float listH = PANEL_H - 56 - PAD;
        List<String> entries = entries();

        for (int i = 0; i < entries.size(); i++) {
            float rowY = listY + i * ROW_H - scroll;
            if (rowY < listY - ROW_H || rowY > listY + listH) {
                continue;
            }
            if (Render2D.hovered(mouseX, mouseY, panelX + PAD, rowY, PANEL_W - PAD * 2, ROW_H)) {
                String id = entries.get(i);
                if (list.contains(id)) {
                    list.remove(id);
                } else {
                    list.add(id);
                }
                return true;
            }
        }
        return super.mouseClicked(event, doppelklick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scrollTarget -= (float) scrollY * 24.0f;
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == Netz.TASTE_RUECK) {
            if (!input.isEmpty()) {
                input = input.substring(0, input.length() - 1);
            }
            return true;
        }
        if (key == Netz.TASTE_ENTER && !input.isEmpty()) {
            List<String> found = entries();
            if (!found.isEmpty()) {
                list.add(found.get(0));
                input = "";
            }
            return true;
        }
        if (key == Netz.TASTE_ESC) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        int zeichen = event.codepoint();
        if (zeichen >= ' ' && zeichen != 127) {
            input += event.codepointAsString();
            scrollTarget = 0.0f;
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public void onClose() {
        net.glowcube.client.render.Netz.bildschirmSetzen(parent);
    }
}
