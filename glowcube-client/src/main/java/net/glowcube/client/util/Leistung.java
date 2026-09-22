package net.glowcube.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Der Ultra-Performance-Modus: dreht in einem Rutsch alles herunter, was Bild
 * kostet, und stellt beim Ausschalten jeden Wert genau so wieder her, wie er
 * beim Einschalten war - man muss also nichts von Hand zuruecksetzen.
 *
 * <p>Warum ueber Spiegelung statt fester Aufrufe: die Optionen von Minecraft
 * heissen zwischen 1.21.x und 26.x nicht ueberall gleich, und ein Teil davon
 * gibt es nur in einer der beiden Fassungen. Jede Regel wird darum einzeln
 * versucht; was es in der laufenden Fassung nicht gibt, wird still uebergangen,
 * statt den ganzen Schalter scheitern zu lassen. So laeuft dieselbe Klasse aus
 * {@code src/main} auf beiden Fassungen.
 *
 * <p>Gesetzt wird ueber {@code OptionInstance.set(..)} - nicht ueber den
 * Rohzugriff wie bei {@link Gamma}: alle Zielwerte liegen im erlaubten Bereich,
 * und {@code set} loest nebenbei die richtigen Folgen aus (etwa das Neuladen der
 * Chunks bei der Sichtweite). Der Typ des Zielwerts richtet sich nach dem, was
 * die Option gerade zurueckgibt - so passt Ganzzahl, Kommazahl, Wahrheitswert
 * oder Aufzaehlung von selbst.
 */
public final class Leistung {
    private Leistung() {
    }

    /**
     * Eine Regel je Option: der Name des Zugriffs auf {@code Options} und die
     * drei moeglichen Zielformen. Welche gilt, entscheidet der Laufzeittyp des
     * aktuellen Werts.
     *
     * @param getter    Name der Methode auf {@code Options}, z.B. "renderDistance"
     * @param zahl      Ziel fuer Zahlenoptionen (Chunks, Skalen ...)
     * @param flag      Ziel fuer Wahrheitswert-Optionen
     * @param aufzaehl  bevorzugte Namen fuer Aufzaehlungs-Optionen (erster Treffer gilt)
     */
    private record Regel(String getter, double zahl, boolean flag, String[] aufzaehl) {
        static Regel zahl(String getter, double wert) {
            return new Regel(getter, wert, false, null);
        }

        static Regel flag(String getter, boolean wert) {
            return new Regel(getter, 0, wert, null);
        }

        static Regel aufzaehl(String getter, String... namen) {
            return new Regel(getter, 0, false, namen);
        }
    }

    // Reihenfolge egal - jede Regel steht fuer sich.
    private static final Regel[] REGELN = {
            // Die grossen Hebel: Sicht- und Simulationsweite klein halten.
            Regel.zahl("renderDistance", 4),
            Regel.zahl("simulationDistance", 5),
            // Feineres, das trotzdem spuerbar Bild kostet.
            Regel.zahl("biomeBlendRadius", 0),
            Regel.zahl("mipmapLevels", 0),
            Regel.zahl("entityDistanceScaling", 0.5),
            // Bildrate nach oben aufmachen (260 = ohne Grenze).
            Regel.zahl("framerateLimit", 260),
            // Bildschirmeffekte kosten nur Rechenzeit, hier weg.
            Regel.zahl("screenEffectScale", 0.0),
            Regel.zahl("fovEffectScale", 0.0),
            Regel.zahl("distortionEffectScale", 0.0),
            Regel.zahl("damageTiltStrength", 0.0),
            Regel.zahl("glintSpeed", 0.0),
            Regel.zahl("glintStrength", 0.0),
            Regel.zahl("darknessEffectScale", 0.0),
            // Schatten, Wackeln, Bildsync und Umgebungsverdeckung aus.
            Regel.flag("entityShadows", false),
            Regel.flag("bobView", false),
            Regel.flag("enableVsync", false),
            Regel.flag("ambientOcclusion", false),
            // Aufzaehlungen: schnellstes Bild, keine Wolken, kaum Partikel.
            Regel.aufzaehl("graphicsMode", "FAST"),
            Regel.aufzaehl("cloudStatus", "OFF"),
            Regel.aufzaehl("particles", "MINIMAL"),
            Regel.aufzaehl("prioritizeChunkUpdates", "NEARBY", "NONE"),
    };

    private static final Map<String, Object> GESICHERT = new LinkedHashMap<>();
    private static boolean aktiv;

    /** Alles herunterdrehen und den Ausgangszustand merken. */
    public static void an() {
        if (aktiv) {
            return;
        }
        aktiv = true;
        GESICHERT.clear();
        Minecraft mc = Minecraft.getInstance();
        for (Regel regel : REGELN) {
            anwenden(mc, regel);
        }
        neuZeichnen();
    }

    /** Jeden gemerkten Wert wieder auf den Ausgangsstand setzen. */
    public static void aus() {
        if (!aktiv) {
            return;
        }
        aktiv = false;
        Minecraft mc = Minecraft.getInstance();
        for (Map.Entry<String, Object> eintrag : GESICHERT.entrySet()) {
            try {
                OptionInstance<?> option = option(mc, eintrag.getKey());
                if (option != null && eintrag.getValue() != null) {
                    setzen(option, eintrag.getValue());
                }
            } catch (RuntimeException ignoriert) {
                // Ein einzelner Wert, der sich nicht zuruecksetzen laesst, darf
                // die uebrigen nicht aufhalten.
            }
        }
        GESICHERT.clear();
        neuZeichnen();
    }

    public static boolean istAktiv() {
        return aktiv;
    }

    // --------------------------------------------------------------- intern

    private static void anwenden(Minecraft mc, Regel regel) {
        try {
            OptionInstance<?> option = option(mc, regel.getter());
            if (option == null) {
                return;
            }
            Object jetzt = option.get();
            GESICHERT.put(regel.getter(), jetzt);
            Object ziel = zielWert(jetzt, regel);
            if (ziel != null) {
                setzen(option, ziel);
            }
        } catch (RuntimeException ignoriert) {
            // Option in dieser Fassung nicht vorhanden oder anders gebaut:
            // still uebergehen.
        }
    }

    private static OptionInstance<?> option(Minecraft mc, String getter) {
        try {
            Method methode = mc.options.getClass().getMethod(getter);
            Object wert = methode.invoke(mc.options);
            return wert instanceof OptionInstance<?> option ? option : null;
        } catch (ReflectiveOperationException fehlt) {
            return null;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setzen(OptionInstance option, Object wert) {
        option.set(wert);
    }

    /** Passt das Ziel an den Laufzeittyp des aktuellen Werts an. */
    private static Object zielWert(Object jetzt, Regel regel) {
        if (jetzt instanceof Boolean) {
            return regel.flag();
        }
        if (jetzt instanceof Integer) {
            return (int) regel.zahl();
        }
        if (jetzt instanceof Long) {
            return (long) regel.zahl();
        }
        if (jetzt instanceof Double) {
            return regel.zahl();
        }
        if (jetzt instanceof Float) {
            return (float) regel.zahl();
        }
        if (jetzt instanceof Enum<?> aktuell && regel.aufzaehl() != null) {
            for (Object konstante : aktuell.getDeclaringClass().getEnumConstants()) {
                for (String name : regel.aufzaehl()) {
                    if (((Enum<?>) konstante).name().equalsIgnoreCase(name)) {
                        return konstante;
                    }
                }
            }
        }
        return null;
    }

    private static void neuZeichnen() {
        try {
            net.glowcube.client.render.Netz.chunksNeuZeichnen();
        } catch (RuntimeException ignoriert) {
            // Ohne Welt gibt es nichts neu zu zeichnen - kein Grund zur Sorge.
        }
    }
}
