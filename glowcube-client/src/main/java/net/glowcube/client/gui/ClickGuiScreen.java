package net.glowcube.client.gui;

import net.glowcube.client.GlowCubeClient;
import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.BlockListSetting;
import net.glowcube.client.core.setting.BooleanSetting;
import net.glowcube.client.core.setting.ModeSetting;
import net.glowcube.client.core.setting.NumberSetting;
import net.glowcube.client.core.setting.Setting;
import net.glowcube.client.util.Anim;
import net.glowcube.client.util.ColorUtil;
import net.glowcube.client.util.Render2D;
import net.glowcube.client.util.Theme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Das Fenster von GlowCube. */
public final class ClickGuiScreen extends Screen {
    private static final int PANEL_W = 478;
    private static final int PANEL_H = 302;
    private static final int HEADER_H = 46;
    private static final int RAIL_W = 122;
    private static final int CARD_H = 34;
    private static final int ROW_H = 16;
    private static final int GAP = 5;
    private static final int PAD = 12;

    // Bleibt zwischen zwei Oeffnungen stehen - man findet sich schneller zurecht.
    private static Category category = Category.RENDER;
    private static final Set<String> expanded = new HashSet<>();
    private static String search = "";

    private final Anim opening = new Anim(0.0f, 14.0f);
    private final Map<String, Anim> toggles = new HashMap<>();
    private final Map<Category, Anim> tabs = new HashMap<>();

    private float scroll;
    private float scrollTarget;
    private Module binding;
    private NumberSetting dragging;

    private int panelX;
    private int panelY;

    public ClickGuiScreen() {
        super(Component.literal(GlowCubeClient.NAME));
    }

    @Override
    protected void init() {
        opening.snap(0.0f);
        opening.target(1.0f);
        panelX = (width - PANEL_W) / 2;
        panelY = (height - PANEL_H) / 2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------------------------------------------------------------- Zeichnen

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        float progress = Anim.easeOut(opening.value());
        panelX = (width - PANEL_W) / 2;
        panelY = (height - PANEL_H) / 2;
        // Faehrt beim Oeffnen ein paar Pixel hoch und blendet dabei ein.
        float lift = (1.0f - progress) * 14.0f;
        float y = panelY + lift;

        Render2D.rect(gfx, 0, 0, width, height, ColorUtil.fade(Theme.BACKDROP, progress));

        Render2D.glow(gfx, panelX, y, PANEL_W, PANEL_H, 10, ColorUtil.fade(Theme.accentStart(), progress), 5);
        Render2D.roundedRect(gfx, panelX, y, PANEL_W, PANEL_H, 10, ColorUtil.fade(Theme.PANEL, progress));
        Render2D.roundedOutline(gfx, panelX, y, PANEL_W, PANEL_H, 10, ColorUtil.fade(Theme.OUTLINE, progress));

        drawHeader(gfx, y, mouseX, mouseY, progress);
        drawRail(gfx, y, mouseX, mouseY, progress);
        drawContent(gfx, y, mouseX, mouseY, progress);
    }

    private void drawHeader(GuiGraphics gfx, float top, int mouseX, int mouseY, float alpha) {
        float x = panelX + PAD;
        float textY = top + 13;

        Render2D.textGradient(gfx, "GLOWCUBE", x, textY,
                ColorUtil.fade(Theme.accentStart(), alpha), ColorUtil.fade(Theme.accentEnd(), alpha));
        Render2D.text(gfx, "v" + GlowCubeClient.VERSION + "  ·  " + GlowCubeClient.TARGET,
                x, textY + 12, ColorUtil.fade(Theme.TEXT_FAINT, alpha));

        // Suchfeld rechts im Kopf.
        float boxW = 150;
        float boxX = panelX + PANEL_W - PAD - boxW;
        float boxY = top + 14;
        boolean hover = Render2D.hovered(mouseX, mouseY, boxX, boxY, boxW, 18);
        Render2D.roundedRect(gfx, boxX, boxY, boxW, 18, 9, ColorUtil.fade(Theme.RAIL, alpha));
        Render2D.roundedOutline(gfx, boxX, boxY, boxW, 18, 9,
                ColorUtil.fade(hover || !search.isEmpty() ? Theme.accentStart() : Theme.OUTLINE_SOFT, alpha));

        String label = search.isEmpty() ? "Suchen …" : search;
        int labelColor = search.isEmpty() ? Theme.TEXT_FAINT : Theme.TEXT;
        Render2D.text(gfx, "⌕", boxX + 8, boxY + 5, ColorUtil.fade(Theme.TEXT_DIM, alpha));
        Render2D.text(gfx, Render2D.clip(label, (int) boxW - 30), boxX + 20, boxY + 5,
                ColorUtil.fade(labelColor, alpha));
        if (!search.isEmpty() && (System.currentTimeMillis() / 500) % 2 == 0) {
            float caret = boxX + 20 + Render2D.width(Render2D.clip(label, (int) boxW - 30));
            Render2D.rect(gfx, caret + 1, boxY + 4, 1, 10, ColorUtil.fade(Theme.accentStart(), alpha));
        }

        Render2D.rect(gfx, panelX + 1, top + HEADER_H - 1, PANEL_W - 2, 1,
                ColorUtil.fade(Theme.OUTLINE_SOFT, alpha));
    }

    private void drawRail(GuiGraphics gfx, float top, int mouseX, int mouseY, float alpha) {
        float x = panelX + 1;
        float y = top + HEADER_H;
        float h = PANEL_H - HEADER_H - 1;

        Render2D.rect(gfx, x, y, RAIL_W, h, ColorUtil.fade(Theme.RAIL, alpha));
        Render2D.rect(gfx, x + RAIL_W, y, 1, h, ColorUtil.fade(Theme.OUTLINE_SOFT, alpha));

        float cursor = y + 10;
        for (Category value : Category.values()) {
            boolean active = value == category;
            boolean hover = Render2D.hovered(mouseX, mouseY, x + 8, cursor, RAIL_W - 16, 28);

            Anim anim = tabs.computeIfAbsent(value, key -> new Anim(0.0f, 16.0f));
            anim.target(active ? 1.0f : hover ? 0.4f : 0.0f);
            float lit = anim.value();

            if (lit > 0.01f) {
                Render2D.roundedRect(gfx, x + 8, cursor, RAIL_W - 16, 28, 7,
                        ColorUtil.fade(Theme.PANEL_LIGHT, lit * 0.9f * alpha));
            }
            if (active) {
                // Der leuchtende Streifen links markiert die offene Kategorie.
                Render2D.roundedGradientH(gfx, x + 8, cursor + 6, 3, 16, 1,
                        ColorUtil.fade(Theme.accentStart(), alpha), ColorUtil.fade(Theme.accentEnd(), alpha));
            }

            int textColor = active ? Theme.TEXT : hover ? Theme.TEXT_DIM : Theme.TEXT_FAINT;
            Render2D.text(gfx, value.icon(), x + 20, cursor + 10, ColorUtil.fade(textColor, alpha));
            Render2D.text(gfx, value.label(), x + 34, cursor + 10, ColorUtil.fade(textColor, alpha));

            int count = countEnabled(value);
            if (count > 0) {
                String badge = String.valueOf(count);
                float badgeX = x + RAIL_W - 16 - Render2D.width(badge) - 6;
                Render2D.roundedRect(gfx, badgeX, cursor + 8, Render2D.width(badge) + 10, 12, 6,
                        ColorUtil.fade(Theme.accentStart(), 0.20f * alpha));
                Render2D.text(gfx, badge, badgeX + 5, cursor + 10, ColorUtil.fade(Theme.accentStart(), alpha));
            }
            cursor += 32;
        }

        String hint = binding != null ? "Taste druecken …" : "Rechtsklick = Optionen";
        Render2D.text(gfx, Render2D.clip(hint, RAIL_W - 16), x + 10, top + PANEL_H - 18,
                ColorUtil.fade(binding != null ? Theme.accentStart() : Theme.TEXT_FAINT, alpha));
    }

    private void drawContent(GuiGraphics gfx, float top, int mouseX, int mouseY, float alpha) {
        float areaX = panelX + RAIL_W + 1;
        float areaY = top + HEADER_H;
        float areaW = PANEL_W - RAIL_W - 2;
        float areaH = PANEL_H - HEADER_H - 1;

        List<Row> rows = buildRows(areaY);
        float contentHeight = rows.isEmpty() ? 0 : rows.get(rows.size() - 1).y + rows.get(rows.size() - 1).height - areaY;
        float maxScroll = Math.max(0.0f, contentHeight + PAD * 2 - areaH);
        scrollTarget = Math.max(0.0f, Math.min(maxScroll, scrollTarget));
        scroll += (scrollTarget - scroll) * 0.35f;

        Render2D.pushScissor(gfx, areaX, areaY, areaW, areaH);

        if (rows.isEmpty()) {
            Render2D.textCentered(gfx, "Nichts gefunden", areaX + areaW / 2, areaY + areaH / 2 - 4,
                    ColorUtil.fade(Theme.TEXT_FAINT, alpha));
        }

        float cardX = areaX + PAD;
        float cardW = areaW - PAD * 2;

        for (Row row : rows) {
            float y = row.y - scroll;
            if (y + row.height < areaY - 4 || y > areaY + areaH + 4) {
                continue;
            }
            if (row.isCard()) {
                drawCard(gfx, row.module, cardX, y, cardW, mouseX, mouseY, alpha);
            } else {
                drawSetting(gfx, row.module, row.setting, cardX + 10, y, cardW - 20, mouseX, mouseY, alpha);
            }
        }

        Render2D.popScissor(gfx);

        if (maxScroll > 0) {
            float trackH = areaH - PAD * 2;
            float thumbH = Math.max(20.0f, trackH * (areaH / (contentHeight + PAD * 2)));
            float thumbY = areaY + PAD + (trackH - thumbH) * (scroll / maxScroll);
            Render2D.roundedRect(gfx, areaX + areaW - 5, areaY + PAD, 2, trackH, 1,
                    ColorUtil.fade(Theme.OUTLINE_SOFT, alpha));
            Render2D.roundedGradientH(gfx, areaX + areaW - 5, thumbY, 2, thumbH, 1,
                    ColorUtil.fade(Theme.accentStart(), alpha), ColorUtil.fade(Theme.accentEnd(), alpha));
        }
    }

    private void drawCard(GuiGraphics gfx, Module module, float x, float y, float w,
                          int mouseX, int mouseY, float alpha) {
        boolean hover = Render2D.hovered(mouseX, mouseY, x, y, w, CARD_H);
        boolean on = module.isEnabled();

        Anim anim = toggles.computeIfAbsent(module.name(), key -> new Anim(on ? 1.0f : 0.0f, 16.0f));
        anim.target(on ? 1.0f : 0.0f);
        float lit = anim.value();

        Render2D.roundedRect(gfx, x, y, w, CARD_H, 7,
                ColorUtil.fade(hover ? Theme.CARD_HOVER : Theme.CARD, alpha));
        if (lit > 0.01f) {
            Render2D.roundedRect(gfx, x, y, w, CARD_H, 7,
                    ColorUtil.fade(Theme.accentStart(), 0.07f * lit * alpha));
            Render2D.roundedGradientH(gfx, x + 1, y + 7, 3, CARD_H - 14, 1,
                    ColorUtil.fade(Theme.accentStart(), lit * alpha), ColorUtil.fade(Theme.accentEnd(), lit * alpha));
        }
        if (hover) {
            Render2D.roundedOutline(gfx, x, y, w, CARD_H, 7, ColorUtil.fade(Theme.OUTLINE, alpha));
        }

        float textX = x + 12;
        String name = module.name();
        String suffix = module.hudSuffix();
        Render2D.text(gfx, name, textX, y + 8,
                ColorUtil.fade(on ? Theme.TEXT : Theme.TEXT_DIM, alpha));
        if (suffix != null) {
            Render2D.text(gfx, suffix, textX + Render2D.width(name) + 6, y + 8,
                    ColorUtil.fade(Theme.accentStart(), (on ? 1.0f : 0.45f) * alpha));
        }
        Render2D.text(gfx, Render2D.clip(module.description(), (int) w - 120), textX, y + 19,
                ColorUtil.fade(Theme.TEXT_FAINT, alpha));

        // Schalter rechts.
        float pillW = 26;
        float pillH = 13;
        float pillX = x + w - 14 - pillW;
        float pillY = y + (CARD_H - pillH) / 2.0f;
        if (lit > 0.01f) {
            Render2D.roundedGradientH(gfx, pillX, pillY, pillW, pillH, pillH / 2,
                    ColorUtil.fade(Theme.accentStart(), lit * alpha), ColorUtil.fade(Theme.accentEnd(), lit * alpha));
        }
        if (lit < 0.99f) {
            Render2D.roundedRect(gfx, pillX, pillY, pillW, pillH, pillH / 2,
                    ColorUtil.fade(Theme.PANEL_LIGHT, (1.0f - lit) * alpha));
        }
        float knobX = pillX + 2 + (pillW - pillH) * lit;
        Render2D.roundedRect(gfx, knobX, pillY + 2, pillH - 4, pillH - 4, (pillH - 4) / 2.0f,
                ColorUtil.fade(0xFFFFFFFF, alpha));

        // Taste und Aufklapp-Zeichen.
        boolean waiting = binding == module;
        String key = waiting ? "…" : module.keyName();
        Render2D.text(gfx, key, pillX - 8 - Render2D.width(key), y + 13,
                ColorUtil.fade(waiting ? Theme.accentStart() : Theme.TEXT_FAINT, alpha));

        if (!module.settings().isEmpty()) {
            String chevron = expanded.contains(module.name()) ? "▾" : "▸";
            Render2D.text(gfx, chevron, x + w - 8, y + 13, ColorUtil.fade(Theme.TEXT_FAINT, alpha));
        }
    }

    private void drawSetting(GuiGraphics gfx, Module module, Setting setting, float x, float y, float w,
                             int mouseX, int mouseY, float alpha) {
        boolean hover = Render2D.hovered(mouseX, mouseY, x, y, w, ROW_H);
        if (hover) {
            Render2D.roundedRect(gfx, x, y, w, ROW_H, 4, ColorUtil.fade(Theme.PANEL_LIGHT, 0.6f * alpha));
        }
        Render2D.text(gfx, setting.name(), x + 10, y + 4, ColorUtil.fade(Theme.TEXT_DIM, alpha));

        float right = x + w - 10;

        if (setting instanceof BooleanSetting flag) {
            float boxW = 18;
            float boxH = 9;
            float boxX = right - boxW;
            float boxY = y + (ROW_H - boxH) / 2.0f;
            if (flag.get()) {
                Render2D.roundedGradientH(gfx, boxX, boxY, boxW, boxH, boxH / 2,
                        ColorUtil.fade(Theme.accentStart(), alpha), ColorUtil.fade(Theme.accentEnd(), alpha));
            } else {
                Render2D.roundedRect(gfx, boxX, boxY, boxW, boxH, boxH / 2,
                        ColorUtil.fade(Theme.CARD_HOVER, alpha));
            }
            float knob = boxX + 1.5f + (boxW - boxH) * (flag.get() ? 1.0f : 0.0f);
            Render2D.roundedRect(gfx, knob, boxY + 1.5f, boxH - 3, boxH - 3, (boxH - 3) / 2.0f,
                    ColorUtil.fade(0xFFFFFFFF, alpha));

        } else if (setting instanceof NumberSetting number) {
            String value = number.display();
            Render2D.text(gfx, value, right - Render2D.width(value), y + 4,
                    ColorUtil.fade(Theme.TEXT, alpha));
            float trackW = 86;
            float trackX = right - Render2D.width(value) - 8 - trackW;
            float trackY = y + ROW_H / 2.0f - 1;
            Render2D.roundedRect(gfx, trackX, trackY, trackW, 3, 1.5f,
                    ColorUtil.fade(Theme.CARD_HOVER, alpha));
            float filled = trackW * (float) number.ratio();
            if (filled > 1) {
                Render2D.roundedGradientH(gfx, trackX, trackY, filled, 3, 1.5f,
                        ColorUtil.fade(Theme.accentStart(), alpha), ColorUtil.fade(Theme.accentEnd(), alpha));
            }
            Render2D.roundedRect(gfx, trackX + filled - 2.5f, trackY - 2.5f, 6, 8, 3,
                    ColorUtil.fade(0xFFFFFFFF, alpha));

        } else if (setting instanceof ModeSetting mode) {
            String value = mode.get();
            float pillW = Render2D.width(value) + 14;
            Render2D.roundedRect(gfx, right - pillW, y + 2, pillW, ROW_H - 4, (ROW_H - 4) / 2.0f,
                    ColorUtil.fade(Theme.accentStart(), 0.16f * alpha));
            Render2D.text(gfx, value, right - pillW + 7, y + 4, ColorUtil.fade(Theme.accentStart(), alpha));

        } else if (setting instanceof BlockListSetting list) {
            String value = list.size() + " Bloecke  ›";
            Render2D.text(gfx, value, right - Render2D.width(value), y + 4,
                    ColorUtil.fade(Theme.accentStart(), alpha));
        }
    }

    // ------------------------------------------------------------------ Aufbau

    /** Die sichtbaren Zeilen in Reihenfolge - Grundlage fuer Zeichnen und Klicken. */
    private List<Row> buildRows(float areaY) {
        List<Row> rows = new ArrayList<>();
        float cursor = areaY + PAD;

        for (Module module : visibleModules()) {
            rows.add(new Row(module, null, cursor, CARD_H));
            cursor += CARD_H;
            if (expanded.contains(module.name()) && !module.settings().isEmpty()) {
                cursor += 2;
                for (Setting setting : module.settings()) {
                    rows.add(new Row(module, setting, cursor, ROW_H));
                    cursor += ROW_H;
                }
                cursor += 4;
            }
            cursor += GAP;
        }
        return rows;
    }

    private List<Module> visibleModules() {
        List<Module> modules = new ArrayList<>();
        String needle = search.toLowerCase(Locale.ROOT);
        for (Module module : GlowCubeClient.modules().all()) {
            if (!needle.isEmpty()) {
                // Bei aktiver Suche zaehlt die Kategorie nicht mehr.
                if (module.name().toLowerCase(Locale.ROOT).contains(needle)
                        || module.description().toLowerCase(Locale.ROOT).contains(needle)) {
                    modules.add(module);
                }
            } else if (module.category() == category) {
                modules.add(module);
            }
        }
        modules.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return modules;
    }

    private int countEnabled(Category value) {
        int count = 0;
        for (Module module : GlowCubeClient.modules().inCategory(value)) {
            if (module.isEnabled()) {
                count++;
            }
        }
        return count;
    }

    // ------------------------------------------------------------------ Maus

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        float top = panelY;

        // Kategorien.
        float railX = panelX + 1;
        float cursor = top + HEADER_H + 10;
        for (Category value : Category.values()) {
            if (Render2D.hovered(mouseX, mouseY, railX + 8, cursor, RAIL_W - 16, 28)) {
                category = value;
                scrollTarget = 0.0f;
                search = "";
                return true;
            }
            cursor += 32;
        }

        float areaX = panelX + RAIL_W + 1;
        float areaY = top + HEADER_H;
        float areaW = PANEL_W - RAIL_W - 2;
        float areaH = PANEL_H - HEADER_H - 1;
        float cardX = areaX + PAD;
        float cardW = areaW - PAD * 2;

        // Weggescrollte Zeilen liegen rechnerisch weiter aussen, sind aber
        // abgeschnitten - ohne diese Schranke waeren sie trotzdem anklickbar.
        if (!Render2D.hovered(mouseX, mouseY, areaX, areaY, areaW, areaH)) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        for (Row row : buildRows(areaY)) {
            float y = row.y - scroll;

            if (row.isCard() && Render2D.hovered(mouseX, mouseY, cardX, y, cardW, CARD_H)) {
                if (button == 1) {
                    if (!row.module.settings().isEmpty()) {
                        if (!expanded.remove(row.module.name())) {
                            expanded.add(row.module.name());
                        }
                    }
                } else if (button == 2) {
                    binding = row.module;
                } else {
                    row.module.toggle();
                    GlowCubeClient.config().save();
                }
                return true;
            }

            if (!row.isCard() && Render2D.hovered(mouseX, mouseY, cardX + 10, y, cardW - 20, ROW_H)) {
                clickSetting(row.setting, button, mouseX, cardX + 10, cardW - 20);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void clickSetting(Setting setting, int button, double mouseX, float rowX, float rowW) {
        if (setting instanceof BooleanSetting flag) {
            flag.toggle();
        } else if (setting instanceof ModeSetting mode) {
            mode.cycle(button == 1 ? -1 : 1);
        } else if (setting instanceof NumberSetting number) {
            dragging = number;
            applySlider(number, mouseX, rowX, rowW);
        } else if (setting instanceof BlockListSetting list) {
            minecraft.setScreen(new BlockListScreen(this, list));
            return;
        }
        GlowCubeClient.config().save();
    }

    private void applySlider(NumberSetting number, double mouseX, float rowX, float rowW) {
        float right = rowX + rowW - 10;
        float trackW = 86;
        float trackX = right - Render2D.width(number.display()) - 8 - trackW;
        number.setRatio((mouseX - trackX) / trackW);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging != null) {
            float areaX = panelX + RAIL_W + 1;
            float areaW = PANEL_W - RAIL_W - 2;
            applySlider(dragging, mouseX, areaX + PAD + 10, areaW - PAD * 2 - 20);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging != null) {
            dragging = null;
            GlowCubeClient.config().save();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scrollTarget -= (float) scrollY * 26.0f;
        return true;
    }

    // ---------------------------------------------------------------- Tastatur

    @Override
    public boolean keyPressed(int key, int scancode, int modifiers) {
        if (binding != null) {
            binding.setKey(key == GLFW.GLFW_KEY_ESCAPE ? GLFW.GLFW_KEY_UNKNOWN : key);
            binding = null;
            GlowCubeClient.config().save();
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE && !search.isEmpty()) {
            search = search.substring(0, search.length() - 1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE && !search.isEmpty()) {
            search = "";
            return true;
        }
        return super.keyPressed(key, scancode, modifiers);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        if (character >= ' ' && character != 127) {
            search += character;
            scrollTarget = 0.0f;
            return true;
        }
        return super.charTyped(character, modifiers);
    }

    @Override
    public void onClose() {
        GlowCubeClient.config().save();
        super.onClose();
    }
}
