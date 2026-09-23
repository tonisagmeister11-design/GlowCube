package net.glowcube.client.module.performance;

import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.BooleanSetting;
import net.glowcube.client.core.setting.NumberSetting;
import net.glowcube.client.util.Leistung;

/**
 * Ein Schalter fuer maximale Bildrate: an dreht alles herunter, was Leistung
 * frisst - Sichtweite, Wolken, Schatten, Partikel, Effekte, Bildsync - und
 * schaltet auf Wunsch das Pixel-Ressourcenpaket dazu, in dem jeder Block nur
 * noch ein Pixel ist (Spieler und Mobs behalten ihre Texturen). Nochmals
 * draufdruecken stellt jeden Wert und die Paketliste wieder auf den Stand von
 * vorher; von Hand muss man nichts zuruecksetzen.
 *
 * <p>Kein Hack, darum im Bereich "Kein Hack". Die eigentliche Arbeit machen
 * {@link Leistung} und {@link net.glowcube.client.util.PixelPaket}.
 */
public final class UltraPerformance extends Module {
    private final NumberSetting sichtweite = register(new NumberSetting("Sichtweite",
            "Sichtweite in Chunks, solange der Modus laeuft (2 = am schnellsten)", 2, 2, 8, 1));
    private final BooleanSetting pixel = register(new BooleanSetting("Pixel-Texturen",
            "Jeder Block wird ein Pixel (eigenes Ressourcenpaket, laedt kurz neu)", true));

    public UltraPerformance() {
        super("Ultra-Performance",
                "Dreht fuer maximale FPS alles herunter und stellt es beim Ausschalten zurueck",
                Category.PERFORMANCE);
    }

    @Override
    public void onEnable() {
        if (mc.options == null) {
            return;
        }
        Leistung.an(sichtweite.getInt(), pixel.get());
    }

    @Override
    public void onDisable() {
        Leistung.aus();
    }
}
