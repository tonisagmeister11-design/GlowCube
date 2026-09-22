package net.glowcube.client.module.performance;

import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.util.Leistung;

/**
 * Ein Schalter fuer maximale Bildrate: an schaltet alles Sichtbare ab, was
 * Leistung frisst - Sichtweite auf vier Chunks, keine Schatten, keine Wolken,
 * kaum Partikel, schnellstes Bild - und geht dabei auch ueber das hinaus, was
 * das normale Einstellungsmenue zulaesst. Nochmals draufdruecken stellt jeden
 * Wert wieder auf den Stand von vorher; von Hand muss man nichts zuruecksetzen.
 *
 * <p>Kein Hack, darum im Bereich "Kein Hack". Die eigentliche Arbeit macht
 * {@link Leistung}; hier steht nur der Ein-/Aus-Anschluss.
 */
public final class UltraPerformance extends Module {
    public UltraPerformance() {
        super("Ultra-Performance",
                "Dreht fuer maximale FPS alles herunter und stellt es beim Ausschalten zurueck",
                Category.PERFORMANCE);
    }

    @Override
    public void onEnable() {
        Leistung.an();
    }

    @Override
    public void onDisable() {
        Leistung.aus();
    }
}
