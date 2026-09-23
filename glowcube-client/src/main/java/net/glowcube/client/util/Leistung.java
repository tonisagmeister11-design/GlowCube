package net.glowcube.client.util;

import net.glowcube.client.GlowCubeClient;
import net.glowcube.client.mixin.OptionInstanceAccessor;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.GraphicsPreset;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.PrioritizeChunkUpdates;
import net.minecraft.client.TextureFilteringMethod;
import net.minecraft.server.level.ParticleStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Der Ultra-Performance-Modus: dreht in einem Rutsch alles herunter, was Bild
 * kostet, und stellt beim Ausschalten jeden Wert genau so wieder her, wie er
 * beim Einschalten war - man muss also nichts von Hand zuruecksetzen.
 *
 * <p>Jede Option wird direkt angesprochen ({@code options.renderDistance()}
 * usw.). Die fruehere Fassung suchte sie per Spiegelung nach Namen - das
 * scheiterte auf 1.21.11 still, weil die Namen dort in der fertigen JAR
 * verschleiert sind; der Modus tat dort also gar nichts. Die Optionen hier
 * gibt es auf 1.21.11 und 26.3 gleich (per CI-Probe ausgelesen).
 *
 * <p>Gesetzt wird ueber {@code OptionInstance.set(..)}, damit die Folgen
 * mitlaufen (Chunks neu bauen usw.). Ein Wert ausserhalb des erlaubten
 * Bereichs wird von Minecraft abgelehnt - dann bleibt die Option einfach,
 * wie sie war, und steht auch nicht auf der Rueckweg-Liste.
 */
public final class Leistung {
    private Leistung() {
    }

    /** Was beim Ausschalten zu tun ist, in der Reihenfolge des Einschaltens. */
    private static final List<Runnable> RUECKWEG = new ArrayList<>();
    private static boolean aktiv;

    /**
     * @param sichtweite Sichtweite in Chunks (2 = kleinstmoeglich)
     * @param pixel      das Pixel-Ressourcenpaket dazuschalten
     */
    public static void an(int sichtweite, boolean pixel) {
        if (aktiv) {
            return;
        }
        aktiv = true;
        RUECKWEG.clear();
        Minecraft mc = Minecraft.getInstance();
        Options o = mc.options;

        // Die Grafikstufe nur roh umstellen: ueber set() wuerde sie ihre
        // Voreinstellungen ueber alles Folgende schreiben - und beim
        // Zuruecksetzen ueber die wiederhergestellten Werte.
        roh(o.graphicsPreset(), GraphicsPreset.FAST);

        // Die grossen Hebel: wie weit gezeichnet und gerechnet wird.
        setze(o.renderDistance(), sichtweite);
        setze(o.simulationDistance(), 5);
        setze(o.entityDistanceScaling(), 0.5);
        setze(o.prioritizeChunkUpdates(), PrioritizeChunkUpdates.NONE);
        setze(o.chunkSectionFadeInTime(), 0.0);

        // Bildrate ohne Grenze, kein Warten auf den Bildschirm.
        setze(o.framerateLimit(), 260);
        setze(o.enableVsync(), false);

        // Was an Welt-Optik Leistung frisst.
        setze(o.cloudStatus(), CloudStatus.OFF);
        setze(o.cloudRange(), 2);
        setze(o.weatherRadius(), 3);
        setze(o.cutoutLeaves(), false);
        setze(o.improvedTransparency(), false);
        setze(o.ambientOcclusion(), false);
        setze(o.entityShadows(), false);
        setze(o.biomeBlendRadius(), 0);
        setze(o.mipmapLevels(), 0);
        setze(o.textureFiltering(), TextureFilteringMethod.values()[0]);
        setze(o.particles(), ParticleStatus.MINIMAL);
        setze(o.hideLightningFlash(), true);

        // Bildschirmeffekte - kosten nur Rechenzeit.
        setze(o.vignette(), false);
        setze(o.bobView(), false);
        setze(o.screenEffectScale(), 0.0);
        setze(o.fovEffectScale(), 0.0);
        setze(o.darknessEffectScale(), 0.0);
        setze(o.damageTiltStrength(), 0.0);
        setze(o.glintSpeed(), 0.0);
        setze(o.glintStrength(), 0.0);
        setze(o.menuBackgroundBlurriness(), 0);

        neuZeichnen();
        if (pixel) {
            PixelPaket.an();
        }
        GlowCubeClient.LOGGER.info("GlowCube: Ultra-Performance an ({} Werte geaendert)", RUECKWEG.size());
    }

    /** Jeden gemerkten Wert wieder auf den Ausgangsstand setzen. */
    public static void aus() {
        if (!aktiv) {
            return;
        }
        aktiv = false;
        PixelPaket.aus();
        for (int i = RUECKWEG.size() - 1; i >= 0; i--) {
            try {
                RUECKWEG.get(i).run();
            } catch (RuntimeException fehler) {
                // Ein Wert, der sich nicht zuruecksetzen laesst, haelt die
                // uebrigen nicht auf.
                GlowCubeClient.LOGGER.warn("GlowCube: Wert nicht zurueckgesetzt", fehler);
            }
        }
        RUECKWEG.clear();
        // Falls zwischendurch das Optionsmenue gespeichert hat, stehen die
        // heruntergedrehten Werte in options.txt - jetzt wieder die echten.
        try {
            Minecraft.getInstance().options.save();
        } catch (RuntimeException ignoriert) {
            // Nur ein Sicherheitsnetz.
        }
        neuZeichnen();
    }

    public static boolean istAktiv() {
        return aktiv;
    }

    // --------------------------------------------------------------- intern

    private static <T> void setze(OptionInstance<T> option, T ziel) {
        try {
            T alt = option.get();
            if (Objects.equals(alt, ziel)) {
                return;
            }
            option.set(ziel);
            if (!Objects.equals(option.get(), alt)) {
                RUECKWEG.add(() -> option.set(alt));
            }
        } catch (RuntimeException fehler) {
            GlowCubeClient.LOGGER.warn("GlowCube: Option nicht gesetzt", fehler);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> void roh(OptionInstance<T> option, T ziel) {
        try {
            T alt = option.get();
            if (Objects.equals(alt, ziel)) {
                return;
            }
            OptionInstanceAccessor<T> zugriff = (OptionInstanceAccessor<T>) (Object) option;
            zugriff.glowcube$setValue(ziel);
            RUECKWEG.add(() -> zugriff.glowcube$setValue(alt));
        } catch (RuntimeException fehler) {
            GlowCubeClient.LOGGER.warn("GlowCube: Option nicht roh gesetzt", fehler);
        }
    }

    private static void neuZeichnen() {
        try {
            net.glowcube.client.render.Netz.chunksNeuZeichnen();
        } catch (RuntimeException ignoriert) {
            // Ohne Welt gibt es nichts neu zu zeichnen - kein Grund zur Sorge.
        }
    }
}
