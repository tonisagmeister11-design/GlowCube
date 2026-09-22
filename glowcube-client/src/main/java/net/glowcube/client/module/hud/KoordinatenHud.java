package net.glowcube.client.module.hud;

import net.glowcube.client.core.setting.BooleanSetting;
import net.glowcube.client.core.setting.NumberSetting;
import net.glowcube.client.hud.HudModul;
import net.glowcube.client.hud.HudZeichner;
import net.glowcube.client.util.Theme;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Koordinaten mit Himmelsrichtung - aus AxolotlClient ({@code CoordsHud},
 * nach KronHUD). Dieselbe Aufteilung: X/Y/Z untereinander, rechts daneben
 * die Richtung und je Achse "+"/"++" bzw. "-"/"--", in welche Richtung die
 * Zahl beim Geradeauslaufen waechst. "Kompakt" legt alles in eine Zeile.
 */
public final class KoordinatenHud extends HudModul {
    private static final String[] RICHTUNGEN = {"", "N", "NO", "O", "SO", "S", "SW", "W", "NW"};

    private final BooleanSetting kompakt = register(
            new BooleanSetting("Kompakt", "Alles in einer Zeile: XYZ 1, 2, 3", false));
    private final NumberSetting stellen = register(
            new NumberSetting("Nachkommastellen", "Wie genau die Koordinaten angezeigt werden", 0, 0, 3, 1));
    private final BooleanSetting hintergrund = register(
            new BooleanSetting("Hintergrund", "Dunkler Kasten hinter dem Text", true));

    private String fx = "0";
    private String fy = "0";
    private String fz = "0";
    private int richtung = 1;

    public KoordinatenHud() {
        super("Koordinaten", "Zeigt Position und Himmelsrichtung", false);
    }

    /**
     * Richtung aus dem Blickwinkel: 1 = Nord, 2 = Nordost, 3 = Ost ... 8 = Nordwest.
     * Wortgleich zu AxolotlClients {@code getDirection}.
     */
    static int richtung(double gier) {
        gier %= 360;
        if (gier < 0) {
            gier += 360;
        }
        int[] grenzen = {0, 23, 68, 113, 158, 203, 248, 293, 338, 360};
        for (int i = 0; i < grenzen.length; i++) {
            int min = grenzen[i];
            int max = i + 1 >= grenzen.length ? grenzen[0] : grenzen[i + 1];
            if (gier >= min && gier < max) {
                return i >= 8 ? 1 : i + 1;
            }
        }
        return 0;
    }

    static String xRichtung(int r) {
        return switch (r) {
            case 3 -> "++";
            case 2, 4 -> "+";
            case 6, 8 -> "-";
            case 7 -> "--";
            default -> "";
        };
    }

    static String zRichtung(int r) {
        return switch (r) {
            case 5 -> "++";
            case 4, 6 -> "+";
            case 8, 2 -> "-";
            case 1 -> "--";
            default -> "";
        };
    }

    @Override
    public void vorbereiten() {
        if (mc.player == null) {
            return;
        }
        StringBuilder muster = new StringBuilder("0");
        if (stellen.getInt() > 0) {
            muster.append('.').append("0".repeat(stellen.getInt()));
        }
        DecimalFormat format = new DecimalFormat(muster.toString(), DecimalFormatSymbols.getInstance(Locale.ROOT));
        // Abrunden wie die Blockkoordinate im F3-Bildschirm.
        format.setRoundingMode(RoundingMode.FLOOR);
        fx = format.format(mc.player.getX());
        fy = format.format(mc.player.getY());
        fz = format.format(mc.player.getZ());
        // Minecraft zaehlt den Blickwinkel ab Sued; +180 macht Nord zur 0.
        richtung = richtung(mc.player.getYRot() + 180);
    }

    private String zeile() {
        return "XYZ " + fx + ", " + fy + ", " + fz;
    }

    private float zahlenBreite(HudZeichner z) {
        return Math.max(z.breite("X " + fx), Math.max(z.breite("Y " + fy), z.breite("Z " + fz)));
    }

    @Override
    public float breite(HudZeichner z) {
        if (kompakt.get()) {
            return z.breite(zeile()) + 6;
        }
        return Math.max(60, zahlenBreite(z) + 6) + 18;
    }

    @Override
    public float hoehe() {
        return kompakt.get() ? 13 : 33;
    }

    @Override
    public void zeichnen(HudZeichner z, float x, float y) {
        if (hintergrund.get()) {
            z.rundRect(x, y, breite(z), hoehe(), 3, 0x99101420);
        }
        int erst = Theme.ACCENT_A;
        int zweit = Theme.TEXT;
        float tx = x + 3;
        if (kompakt.get()) {
            z.text("XYZ ", tx, y + 3, erst, true);
            z.text(fx + ", " + fy + ", " + fz, tx + z.breite("XYZ "), y + 3, zweit, true);
            return;
        }
        String[] namen = {"X ", "Y ", "Z "};
        String[] werte = {fx, fy, fz};
        for (int i = 0; i < 3; i++) {
            float zy = y + 2 + i * 10;
            z.text(namen[i], tx, zy, erst, true);
            z.text(werte[i], tx + z.breite(namen[i]), zy, zweit, true);
        }
        float rx = x + Math.max(60, zahlenBreite(z) + 6);
        z.text(xRichtung(richtung), rx, y + 2, zweit, true);
        z.text(RICHTUNGEN[richtung], rx, y + 12, erst, true);
        z.text(zRichtung(richtung), rx, y + 22, zweit, true);
    }
}
