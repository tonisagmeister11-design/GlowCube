package net.glowcube.client.gui;

import net.glowcube.client.GlowCubeClient;
import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.BlockListSetting;
import net.glowcube.client.core.setting.BooleanSetting;
import net.glowcube.client.core.setting.ModeSetting;
import net.glowcube.client.core.setting.NumberSetting;
import net.glowcube.client.core.setting.Setting;
import net.glowcube.client.core.setting.TextListSetting;
import net.glowcube.client.integration.SeedBridge;
import net.glowcube.client.util.ColorUtil;
import net.glowcube.client.util.Render2D;
import net.glowcube.client.util.Theme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Das ClickGUI - nach dem Vorbild von Meteor Client aufgebaut.
 *
 * <p>Der Unterschied zur frueheren Fassung ist nicht die Farbe, sondern die
 * Form: statt eines festen Panels mit einer Kategorieleiste gibt es jetzt
 * <b>mehrere Fenster</b>, eines je Kategorie, frei verschiebbar und einzeln
 * einklappbar. Wer nur Combat und Render braucht, raeumt den Rest zur Seite
 * und findet ihn beim naechsten Start genauso wieder - die Anordnung steht
 * in der Konfigurationsdatei.
 *
 * <p>Bedienung, ebenfalls wie dort:
 * <ul>
 *   <li>Linksklick auf eine Zeile schaltet das Modul.</li>
 *   <li>Rechtsklick klappt seine Einstellungen auf und zu.</li>
 *   <li>Mittelklick belegt die Taste neu.</li>
 *   <li>Die Titelleiste zieht das Fenster, ein Klick darauf klappt es ein.</li>
 *   <li>Tippen sucht - dann zeigt ein einzelnes Fenster alle Treffer.</li>
 * </ul>
 */
public final class ClickGuiScreen extends Screen {
    private static final float FENSTER_B = 150.0f;
    private static final float TITEL_H = 20.0f;
    private static final float ZEILE_H = 16.0f;
    private static final float REGLER_H = 24.0f;
    private static final float RAND = 6.0f;

    /** Was gerade gezogen wird - Fenster oder Regler, nie beides. */
    private Fenster gezogen;
    private float griffX;
    private float griffY;
    private NumberSetting regler;
    private float reglerX;
    private float reglerBreite;

    /** Modul, das gerade auf eine neue Taste wartet. */
    private Module belegt;

    private String suche = "";
    private boolean sucheAktiv;

    /** Gewaehlter Bereich; null heisst: die Bereichswahl steht noch an. */
    private Category.Bereich bereich;

    /** Anklickbare Flaechen dieses Bildes - Zeichnen und Klicken aus einer Quelle. */
    private final List<Treffer> treffer = new ArrayList<>();

    private record Treffer(float x, float y, float w, float h, Module module, Setting setting,
                           Fenster fenster, Art art) {
    }

    private enum Art {
        TITEL,
        MODUL,
        SCHALTER,
        REGLER,
        AUSWAHL,
        LISTE,
        WAHL_HACKS,
        WAHL_KEIN,
        ZURUECK
    }

    public ClickGuiScreen() {
        super(Component.literal("GlowCube"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------------------------------------------------------------- Zeichnen

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        treffer.clear();
        Render2D.rect(gfx, 0, 0, width, height, Theme.BACKDROP);

        kopfzeile(gfx, mouseX, mouseY);

        if (bereich == null) {
            bereichWahlZeichnen(gfx, mouseX, mouseY);
        } else if (!suche.isEmpty()) {
            sucheZeichnen(gfx, mouseX, mouseY);
        } else {
            for (Fenster fenster : Layout.imBereich(bereich)) {
                fensterZeichnen(gfx, fenster, mouseX, mouseY);
            }
        }

        fusszeile(gfx);

        if (belegt != null) {
            hinweis(gfx, "Taste fuer " + belegt.name() + " druecken - Esc loescht die Belegung");
        }
    }

    private void kopfzeile(GuiGraphics gfx, int mouseX, int mouseY) {
        Render2D.rect(gfx, 0, 0, width, 30, Theme.PANEL);
        Render2D.rect(gfx, 0, 29, width, 1, Theme.OUTLINE);
        Render2D.textGradient(gfx, "GLOWCUBE", 14, 11, Theme.accentStart(), Theme.accentEnd());

        // In der Bereichswahl gibt es weder Zurueck noch Suche noch Zaehler.
        if (bereich == null) {
            return;
        }

        // Zurueck zur Bereichswahl - links neben dem Suchfeld.
        String zurText = "‹ " + bereich.label();
        float zurB = Render2D.width(zurText) + 16;
        float zurX = 20 + Render2D.width("GLOWCUBE") + 12;
        boolean zurUeber = Render2D.hovered(mouseX, mouseY, zurX, 7, zurB, 16);
        Render2D.roundedRect(gfx, zurX, 7, zurB, 16, 4, zurUeber ? Theme.CARD_HOVER : Theme.CARD);
        Render2D.roundedOutline(gfx, zurX, 7, zurB, 16, 4,
                zurUeber ? Theme.accentStart() : Theme.OUTLINE_SOFT);
        Render2D.text(gfx, zurText, zurX + 8, 11, zurUeber ? Theme.TEXT : Theme.TEXT_DIM);
        treffer.add(new Treffer(zurX, 7, zurB, 16, null, null, null, Art.ZURUECK));

        float suchX = width / 2.0f - 90;
        Render2D.roundedRect(gfx, suchX, 7, 180, 16, 4,
                sucheAktiv ? Theme.CARD_HOVER : Theme.CARD);
        Render2D.roundedOutline(gfx, suchX, 7, 180, 16, 4,
                sucheAktiv ? Theme.accentStart() : Theme.OUTLINE_SOFT);
        String text = suche.isEmpty() ? "Suchen ..." : suche;
        Render2D.text(gfx, Render2D.clip(text, 168), suchX + 6, 11,
                suche.isEmpty() ? Theme.TEXT_FAINT : Theme.TEXT);

        int an = GlowCubeClient.modules().enabled().size();
        int alle = GlowCubeClient.modules().all().size();
        String zaehler = an + " von " + alle + " aktiv";
        Render2D.text(gfx, zaehler, width - 14 - Render2D.width(zaehler), 11, Theme.TEXT_DIM);
    }

    private void fusszeile(GuiGraphics gfx) {
        Render2D.rect(gfx, 0, height - 22, width, 22, Theme.PANEL);
        Render2D.rect(gfx, 0, height - 22, width, 1, Theme.OUTLINE);

        String links = "Links schaltet - Rechts oeffnet Einstellungen - Mitte belegt die Taste";
        Render2D.text(gfx, links, 14, height - 15, Theme.TEXT_FAINT);

        // Der SeedCracker-Stand gehoert dorthin, wo man ihn immer sieht:
        // waehrend SeedHunt fliegt, hat man das Fenster ohnehin offen.
        String rechts;
        Long seed = SeedBridge.seed();
        if (seed != null) {
            rechts = "Seed " + seed;
        } else {
            Double bits = SeedBridge.bits();
            rechts = bits == null
                    ? "SeedCracker wartet"
                    : String.format(Locale.ROOT, "SeedCracker %.1f / 48 Bit", bits);
        }
        Render2D.text(gfx, rechts, width - 14 - Render2D.width(rechts), height - 15,
                seed != null ? Theme.accentStart() : Theme.TEXT_DIM);
    }

    private void hinweis(GuiGraphics gfx, String text) {
        float breite = Render2D.width(text) + 20;
        float x = width / 2.0f - breite / 2.0f;
        float y = height / 2.0f - 14;
        Render2D.glow(gfx, x, y, breite, 28, 6, Theme.accentStart(), 4);
        Render2D.roundedRect(gfx, x, y, breite, 28, 6, Theme.PANEL_LIGHT);
        Render2D.roundedOutline(gfx, x, y, breite, 28, 6, Theme.accentStart());
        Render2D.textCentered(gfx, text, width / 2.0f, y + 10, Theme.TEXT);
    }

    // ---------------------------------------------------------- Bereichswahl

    /**
     * Die erste Ebene: zwei grosse Kacheln, "Hacks" und "Kein Hack". Erst nach
     * der Wahl erscheint das gewohnte Fenster-Menue - und nur mit den Fenstern
     * des gewaehlten Bereichs.
     */
    private void bereichWahlZeichnen(GuiGraphics gfx, int mouseX, int mouseY) {
        Render2D.textCentered(gfx, "Waehle einen Bereich", width / 2.0f, 70, Theme.TEXT_DIM);

        float breite = 220;
        float hoehe = 150;
        float luecke = 30;
        float gesamt = breite * 2 + luecke;
        float x0 = width / 2.0f - gesamt / 2.0f;
        float y0 = height / 2.0f - hoehe / 2.0f;

        kachel(gfx, mouseX, mouseY, x0, y0, breite, hoehe,
                Category.Bereich.HACKS, "Combat, Movement, Render & mehr", Art.WAHL_HACKS);
        kachel(gfx, mouseX, mouseY, x0 + breite + luecke, y0, breite, hoehe,
                Category.Bereich.KEIN_HACK, "Performance, HUD & Optik", Art.WAHL_KEIN);
    }

    private void kachel(GuiGraphics gfx, int mouseX, int mouseY, float x, float y,
                        float w, float h, Category.Bereich bereich, String untertitel, Art art) {
        boolean ueber = Render2D.hovered(mouseX, mouseY, x, y, w, h);
        int akzent = bereich.color();
        if (ueber) {
            Render2D.glow(gfx, x, y, w, h, 10, akzent, 5);
        }
        Render2D.roundedRect(gfx, x, y, w, h, 10, ueber ? Theme.PANEL_LIGHT : Theme.PANEL);
        Render2D.roundedOutline(gfx, x, y, w, h, 10, ueber ? akzent : Theme.OUTLINE_SOFT);
        Render2D.rect(gfx, x + 18, y + 46, w - 36, 2, ColorUtil.fade(akzent, 0.9f));
        Render2D.textCentered(gfx, bereich.label().toUpperCase(Locale.ROOT),
                x + w / 2.0f, y + 28, Theme.TEXT);
        Render2D.textCentered(gfx, Render2D.clip(untertitel, (int) w - 24),
                x + w / 2.0f, y + 64, Theme.TEXT_DIM);

        int anzahl = 0;
        for (Module modul : GlowCubeClient.modules().all()) {
            if (modul.category().bereich() == bereich) {
                anzahl++;
            }
        }
        Render2D.textCentered(gfx, anzahl + (anzahl == 1 ? " Funktion" : " Funktionen"),
                x + w / 2.0f, y + h - 26, akzent);

        treffer.add(new Treffer(x, y, w, h, null, null, null, art));
    }

    // ------------------------------------------------------------- Ein Fenster

    private void fensterZeichnen(GuiGraphics gfx, Fenster fenster, int mouseX, int mouseY) {
        List<Module> module = GlowCubeClient.modules().inCategory(fenster.kategorie);
        float x = fenster.x;
        float y = fenster.y;
        int farbe = fenster.kategorie.color();

        // Titelleiste
        boolean titelUeber = Render2D.hovered(mouseX, mouseY, x, y, FENSTER_B, TITEL_H);
        Render2D.roundedRect(gfx, x, y, FENSTER_B, TITEL_H, 5, Theme.PANEL_LIGHT);
        Render2D.rect(gfx, x + 1, y + TITEL_H - 2, FENSTER_B - 2, 2, ColorUtil.fade(farbe, 0.9f));
        Render2D.text(gfx, fenster.kategorie.icon(), x + 7, y + 6, farbe);
        Render2D.text(gfx, fenster.kategorie.label().toUpperCase(Locale.ROOT), x + 20, y + 6,
                titelUeber ? Theme.TEXT : Theme.TEXT_DIM);
        String pfeil = fenster.eingeklappt ? "+" : "-";
        Render2D.text(gfx, pfeil, x + FENSTER_B - 12, y + 6, Theme.TEXT_DIM);
        treffer.add(new Treffer(x, y, FENSTER_B, TITEL_H, null, null, fenster, Art.TITEL));

        if (fenster.eingeklappt) {
            fenster.hoehe = TITEL_H;
            return;
        }

        float cursor = y + TITEL_H + 3;
        float koerperStart = cursor;

        for (Module module1 : module) {
            cursor = modulZeichnen(gfx, fenster, module1, x, cursor, mouseX, mouseY);
        }

        // Jede Zeile bringt ihren eigenen Hintergrund mit - so braucht es
        // keinen zweiten Durchgang, nur um die Gesamthoehe vorher zu kennen.
        fenster.hoehe = TITEL_H + (cursor - koerperStart) + 3;
    }

    private float modulZeichnen(GuiGraphics gfx, Fenster fenster, Module module,
                                float x, float y, int mouseX, int mouseY) {
        boolean ueber = Render2D.hovered(mouseX, mouseY, x, y, FENSTER_B, ZEILE_H);
        boolean an = module.isEnabled();
        int farbe = fenster.kategorie.color();

        Render2D.rect(gfx, x, y, FENSTER_B, ZEILE_H,
                an ? ColorUtil.fade(farbe, 0.18f) : (ueber ? Theme.CARD_HOVER : Theme.CARD));
        if (an) {
            Render2D.rect(gfx, x, y, 2, ZEILE_H, farbe);
        }
        Render2D.text(gfx, Render2D.clip(module.name(), 96), x + 8, y + 4,
                an ? Theme.TEXT : Theme.TEXT_DIM);

        String rechts = module.hasKey() ? module.keyName() : "";
        if (belegt == module) {
            rechts = "...";
        }
        if (!rechts.isEmpty()) {
            Render2D.text(gfx, rechts, x + FENSTER_B - 8 - Render2D.width(rechts), y + 4,
                    Theme.TEXT_FAINT);
        }
        treffer.add(new Treffer(x, y, FENSTER_B, ZEILE_H, module, null, fenster, Art.MODUL));

        float cursor = y + ZEILE_H + 1;
        if (!fenster.istOffen(module)) {
            return cursor;
        }

        for (Setting setting : module.settings()) {
            cursor = einstellungZeichnen(gfx, fenster, module, setting, x, cursor, mouseX, mouseY);
        }
        // Eine Trennlinie unter dem aufgeklappten Block, sonst laeuft er
        // optisch in das naechste Modul hinein.
        Render2D.rect(gfx, x + RAND, cursor, FENSTER_B - RAND * 2, 1, Theme.OUTLINE_SOFT);
        return cursor + 3;
    }

    private float einstellungZeichnen(GuiGraphics gfx, Fenster fenster, Module module,
                                      Setting setting, float x, float y,
                                      int mouseX, int mouseY) {
        float innenX = x + RAND;
        float innenB = FENSTER_B - RAND * 2;
        Render2D.rect(gfx, x, y, FENSTER_B, zeilenHoehe(setting), Theme.RAIL);

        if (setting instanceof BooleanSetting schalter) {
            boolean ueber = Render2D.hovered(mouseX, mouseY, x, y, FENSTER_B, ZEILE_H);
            Render2D.text(gfx, Render2D.clip(setting.name(), 108), innenX, y + 4,
                    ueber ? Theme.TEXT : Theme.TEXT_DIM);
            float kx = x + FENSTER_B - RAND - 16;
            Render2D.roundedRect(gfx, kx, y + 3, 16, 9, 4,
                    schalter.get() ? Theme.accentStart() : Theme.OUTLINE);
            Render2D.roundedRect(gfx, schalter.get() ? kx + 8 : kx + 1, y + 4, 7, 7, 3,
                    Theme.TEXT);
            treffer.add(new Treffer(x, y, FENSTER_B, ZEILE_H, module, setting, fenster,
                    Art.SCHALTER));
            return y + ZEILE_H;
        }

        if (setting instanceof ModeSetting auswahl) {
            boolean ueber = Render2D.hovered(mouseX, mouseY, x, y, FENSTER_B, ZEILE_H);
            Render2D.text(gfx, Render2D.clip(setting.name(), 76), innenX, y + 4,
                    ueber ? Theme.TEXT : Theme.TEXT_DIM);
            String wert = auswahl.get();
            Render2D.text(gfx, Render2D.clip(wert, 60),
                    x + FENSTER_B - RAND - Render2D.width(Render2D.clip(wert, 60)), y + 4,
                    Theme.accentStart());
            treffer.add(new Treffer(x, y, FENSTER_B, ZEILE_H, module, setting, fenster,
                    Art.AUSWAHL));
            return y + ZEILE_H;
        }

        if (setting instanceof NumberSetting zahl) {
            Render2D.text(gfx, Render2D.clip(setting.name(), 90), innenX, y + 3, Theme.TEXT_DIM);
            String wert = zahl.display();
            Render2D.text(gfx, wert, x + FENSTER_B - RAND - Render2D.width(wert), y + 3,
                    Theme.TEXT);
            float bahnY = y + 15;
            Render2D.roundedRect(gfx, innenX, bahnY, innenB, 3, 1.5f, Theme.OUTLINE);
            float gefuellt = (float) (innenB * zahl.ratio());
            Render2D.roundedGradientH(gfx, innenX, bahnY, Math.max(gefuellt, 2.0f), 3, 1.5f,
                    Theme.accentStart(), Theme.accentEnd());
            Render2D.roundedRect(gfx, innenX + gefuellt - 2, bahnY - 2, 4, 7, 2, Theme.TEXT);
            treffer.add(new Treffer(innenX, y, innenB, REGLER_H, module, setting, fenster,
                    Art.REGLER));
            return y + REGLER_H;
        }

        // Listen bekommen ein eigenes Fenster - in einer Zeile von 150 Pixeln
        // laesst sich keine Blockliste bearbeiten.
        String beschriftung;
        if (setting instanceof BlockListSetting liste) {
            beschriftung = setting.name() + " (" + liste.size() + ")";
        } else if (setting instanceof TextListSetting liste) {
            beschriftung = setting.name() + " (" + liste.size() + ")";
        } else {
            beschriftung = setting.name();
        }
        boolean ueber = Render2D.hovered(mouseX, mouseY, x, y, FENSTER_B, ZEILE_H);
        Render2D.roundedRect(gfx, innenX, y + 2, innenB, ZEILE_H - 4, 3,
                ueber ? Theme.CARD_HOVER : Theme.CARD);
        Render2D.textCentered(gfx, Render2D.clip(beschriftung, (int) innenB - 8),
                x + FENSTER_B / 2.0f, y + 4, ueber ? Theme.TEXT : Theme.TEXT_DIM);
        treffer.add(new Treffer(x, y, FENSTER_B, ZEILE_H, module, setting, fenster, Art.LISTE));
        return y + ZEILE_H;
    }

    private static float zeilenHoehe(Setting setting) {
        return setting instanceof NumberSetting ? REGLER_H : ZEILE_H;
    }

    // ----------------------------------------------------------------- Suche

    private void sucheZeichnen(GuiGraphics gfx, int mouseX, int mouseY) {
        List<Module> gefunden = new ArrayList<>();
        String muster = suche.toLowerCase(Locale.ROOT);
        for (Module module : GlowCubeClient.modules().all()) {
            if (module.category().bereich() != bereich) {
                continue;
            }
            if (module.name().toLowerCase(Locale.ROOT).contains(muster)
                    || module.description().toLowerCase(Locale.ROOT).contains(muster)) {
                gefunden.add(module);
            }
        }

        float x = width / 2.0f - FENSTER_B / 2.0f;
        float y = 44;
        Render2D.roundedRect(gfx, x, y, FENSTER_B, TITEL_H, 5, Theme.PANEL_LIGHT);
        Render2D.text(gfx, gefunden.size() + " Treffer", x + 8, y + 6, Theme.TEXT_DIM);

        float cursor = y + TITEL_H + 3;
        // Die Suche laesst sich genauso bedienen wie ein Fenster - dafuer
        // bekommt sie das Fenster der jeweiligen Kategorie mit, damit
        // Aufklappen dort landet, wo es hingehoert.
        for (Module module : gefunden) {
            if (cursor > height - 40) {
                Render2D.text(gfx, "...", x + 8, cursor, Theme.TEXT_FAINT);
                break;
            }
            cursor = modulZeichnen(gfx, Layout.fuer(module.category()), module, x, cursor,
                    mouseX, mouseY);
        }
    }

    // ------------------------------------------------------------------ Maus

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doppelklick) {
        double mx = event.x();
        double my = event.y();
        int knopf = event.button();

        if (belegt != null) {
            return true;
        }

        // Suchfeld - nur wenn ein Bereich offen ist.
        if (bereich != null) {
            float suchX = width / 2.0f - 90;
            if (Render2D.hovered(mx, my, suchX, 7, 180, 16)) {
                sucheAktiv = true;
                return true;
            }
            sucheAktiv = false;
        }

        // Rueckwaerts durchgehen: was zuletzt gezeichnet wurde, liegt oben.
        for (int i = treffer.size() - 1; i >= 0; i--) {
            Treffer t = treffer.get(i);
            if (!Render2D.hovered(mx, my, t.x(), t.y(), t.w(), t.h())) {
                continue;
            }
            return behandeln(t, knopf, mx, my);
        }
        return super.mouseClicked(event, doppelklick);
    }

    private boolean behandeln(Treffer t, int knopf, double mx, double my) {
        switch (t.art()) {
            case TITEL -> {
                if (knopf == 1) {
                    t.fenster().eingeklappt = !t.fenster().eingeklappt;
                    speichern();
                } else {
                    gezogen = t.fenster();
                    griffX = (float) mx - t.fenster().x;
                    griffY = (float) my - t.fenster().y;
                }
                return true;
            }
            case MODUL -> {
                if (knopf == 0) {
                    t.module().toggle();
                } else if (knopf == 1) {
                    t.fenster().umschalten(t.module());
                } else if (knopf == 2) {
                    belegt = t.module();
                }
                speichern();
                return true;
            }
            case SCHALTER -> {
                ((BooleanSetting) t.setting()).toggle();
                speichern();
                return true;
            }
            case AUSWAHL -> {
                ((ModeSetting) t.setting()).cycle(knopf == 1 ? -1 : 1);
                speichern();
                return true;
            }
            case REGLER -> {
                regler = (NumberSetting) t.setting();
                reglerX = t.x();
                reglerBreite = t.w();
                reglerSetzen(mx);
                return true;
            }
            case LISTE -> {
                if (t.setting() instanceof BlockListSetting liste) {
                    minecraft.setScreen(new BlockListScreen(this, liste));
                } else if (t.setting() instanceof TextListSetting liste) {
                    minecraft.setScreen(new TextListScreen(this, liste));
                }
                return true;
            }
            case WAHL_HACKS -> {
                bereich = Category.Bereich.HACKS;
                return true;
            }
            case WAHL_KEIN -> {
                bereich = Category.Bereich.KEIN_HACK;
                return true;
            }
            case ZURUECK -> {
                bereich = null;
                suche = "";
                sucheAktiv = false;
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (gezogen != null) {
            gezogen.x = (float) event.x() - griffX;
            // Nie ganz aus dem Bild schieben - sonst bekommt man das Fenster
            // nur ueber die Konfigurationsdatei zurueck.
            gezogen.y = (float) Math.max(32.0, Math.min(height - 24.0, event.y() - griffY));
            gezogen.x = Math.max(-FENSTER_B + 30, Math.min(width - 30, gezogen.x));
            return true;
        }
        if (regler != null) {
            reglerSetzen(event.x());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    private void reglerSetzen(double mx) {
        if (regler == null || reglerBreite <= 0) {
            return;
        }
        regler.setRatio((mx - reglerX) / reglerBreite);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (gezogen != null || regler != null) {
            gezogen = null;
            regler = null;
            speichern();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (bereich == null) {
            return true;
        }
        // Alle Fenster gemeinsam verschieben - so kommt man an Fenster heran,
        // die unter dem unteren Rand liegen, ohne jedes einzeln zu ziehen.
        for (Fenster fenster : Layout.imBereich(bereich)) {
            fenster.y += (float) scrollY * 18.0f;
        }
        return true;
    }

    // -------------------------------------------------------------- Tastatur

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();

        if (belegt != null) {
            belegt.setKey(key == GLFW.GLFW_KEY_ESCAPE ? GLFW.GLFW_KEY_UNKNOWN : key);
            belegt = null;
            speichern();
            return true;
        }

        if (sucheAktiv || !suche.isEmpty()) {
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (!suche.isEmpty()) {
                    suche = suche.substring(0, suche.length() - 1);
                }
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                suche = "";
                sucheAktiv = false;
                return true;
            }
        }

        // Esc geht erst einen Schritt zurueck zur Bereichswahl; erst der
        // naechste Esc schliesst das Fenster ganz.
        if (bereich != null && key == GLFW.GLFW_KEY_ESCAPE) {
            bereich = null;
            return true;
        }

        // Fenster wieder einsammeln, wenn man sie verlegt hat.
        if (bereich != null && key == GLFW.GLFW_KEY_HOME) {
            Layout.zuruecksetzen();
            speichern();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (belegt != null) {
            return true;
        }
        if (bereich == null) {
            return super.charTyped(event);
        }
        String zeichen = event.codepointAsString();
        if (!zeichen.isEmpty() && suche.length() < 32) {
            suche += zeichen;
            sucheAktiv = true;
            return true;
        }
        return super.charTyped(event);
    }

    private void speichern() {
        GlowCubeClient.config().save();
    }

    @Override
    public void onClose() {
        speichern();
        super.onClose();
    }
}
