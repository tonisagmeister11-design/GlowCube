package net.glowcube.client.hud;

import net.minecraft.world.item.ItemStack;

/**
 * Die paar Zeichenbefehle, die die HUD-Anzeigen brauchen - ohne den
 * Grafiktyp zu nennen. Der heisst auf 1.21.x {@code GuiGraphics} und ab 26.x
 * {@code GuiGraphicsExtractor}; jede {@code HudRenderer}-Fassung reicht einen
 * passenden Zeichner herein. So liegen alle Anzeigen nur einmal in
 * {@code src/main} und laufen auf beiden Fassungen.
 */
public interface HudZeichner {
    void rect(float x, float y, float w, float h, int farbe);

    void rundRect(float x, float y, float w, float h, float radius, int farbe);

    void text(String text, float x, float y, int farbe, boolean schatten);

    int breite(String text);

    /** Gegenstand samt Haltbarkeitsbalken und Anzahl, 16x16. */
    void gegenstand(ItemStack stack, int x, int y);
}
