package net.glowcube.client.hud;

import net.glowcube.client.GlowCubeClient;
import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.gui.ClickGuiScreen;
import net.glowcube.client.util.ColorUtil;
import net.glowcube.client.util.Render2D;
import net.glowcube.client.util.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Fassung fuer <b>26.3 und neuer</b>: Wasserzeichen links oben, Modulliste
 * rechts oben. Wortgleich zur 1.21-Fassung, nur auf {@code GuiGraphicsExtractor}.
 * Der Bildschirm liegt ab 26.x privat, deshalb ueber {@link net.glowcube.client.render.Netz};
 * die Breite kommt aus dem Fenster statt aus dem Grafikobjekt.
 */
public final class HudRenderer {
    private static final int MARGIN = 6;

    public HudRenderer() {
    }

    public void render(GuiGraphicsExtractor gfx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) {
            return;
        }
        // Im eigenen Fenster waere es doppelt gemoppelt.
        if (net.glowcube.client.render.Netz.bildschirm() instanceof ClickGuiScreen) {
            return;
        }
        drawWatermark(gfx);
        drawModuleList(gfx, mc.getWindow().getGuiScaledWidth());
    }

    private void drawWatermark(GuiGraphicsExtractor gfx) {
        String name = "GLOWCUBE";
        String tag = GlowCubeClient.target();
        float w = Render2D.width(name) + Render2D.width(tag) + 22;
        float h = 16;

        Render2D.roundedRect(gfx, MARGIN, MARGIN, w, h, 8, 0xB0101420);
        Render2D.roundedGradientH(gfx, MARGIN + 5, MARGIN + 5, 2, 6, 1, Theme.accentStart(), Theme.accentEnd());
        Render2D.textGradient(gfx, name, MARGIN + 11, MARGIN + 4, Theme.accentStart(), Theme.accentEnd());
        Render2D.text(gfx, tag, MARGIN + 11 + Render2D.width(name) + 6, MARGIN + 4, Theme.TEXT_FAINT);
    }

    private void drawModuleList(GuiGraphicsExtractor gfx, int screenWidth) {
        List<Module> active = new ArrayList<>();
        for (Module module : GlowCubeClient.modules().all()) {
            if (module.isEnabled() && module.category() != Category.MISC) {
                active.add(module);
            }
        }
        // Nach Breite sortiert ergibt die Treppe, die man von Wurst kennt.
        active.sort(Comparator.comparingInt((Module module) -> Render2D.width(label(module))).reversed());

        float y = MARGIN;
        int index = 0;
        for (Module module : active) {
            String text = label(module);
            float textWidth = Render2D.width(text);
            float x = screenWidth - MARGIN - textWidth - 8;

            int accent = Theme.accentAt(index * 0.06f);
            Render2D.rect(gfx, x - 4, y, textWidth + 12, 12, 0x99101420);
            Render2D.rect(gfx, screenWidth - MARGIN, y, 2, 12, accent);
            Render2D.text(gfx, module.name(), x, y + 2, Theme.TEXT);

            String suffix = module.hudSuffix();
            if (suffix != null) {
                Render2D.text(gfx, suffix, x + Render2D.width(module.name()) + 4, y + 2,
                        ColorUtil.fade(accent, 0.9f));
            }
            y += 13;
            index++;
        }
    }

    private String label(Module module) {
        String suffix = module.hudSuffix();
        return suffix == null ? module.name() : module.name() + " " + suffix;
    }
}
