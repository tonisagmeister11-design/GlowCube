package net.glowcube.client.module.movement;

import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.BooleanSetting;

/**
 * Uebertragen aus Meteor Client (GPL-3.0), Modul {@code NoSlow} - der Kern.
 *
 * <p>Wer isst, trinkt, den Bogen spannt oder ein Schild haelt, kriecht in
 * Vanilla auf etwa einem Fuenftel Tempo. NoSlow nimmt diese Bremse weg: man
 * bleibt beim Benutzen voll schnell. Auf einer Prank- oder PvP-Runde ist das
 * einer der handfestesten Alltagsvorteile - man kann fliehen und dabei
 * essen, oder mit gespanntem Bogen normal laufen.
 *
 * <p>Die Logik selbst steht wie im Original nicht hier, sondern im
 * {@link net.glowcube.client.mixin.LocalPlayerMixin}: an der einen Stelle,
 * an der das Spiel die Bewegungseingabe wegen des Benutzens herunterrechnet,
 * wird so getan, als benutze man gerade nichts. Dieses Modul haelt nur den
 * Schalter, den der Mixin abfragt.
 *
 * <p><b>Nur der Item-Teil.</b> Meteors NoSlow deckt zusaetzlich Spinnweben,
 * Honig, Seelensand und Beerenbuesche ab - jedes ueber einen eigenen Mixin
 * auf den jeweiligen Block. Die haengen an Methodennamen, die sich zwischen
 * den Fassungen bewegen; hier steht der Teil, der den groessten Vorteil
 * bringt und an einer stabilen Stelle sitzt.
 */
public final class NoSlow extends Module {
    private static NoSlow instanz;

    private final BooleanSetting items = register(new BooleanSetting("Beim Benutzen",
            "Volle Geschwindigkeit beim Essen, Trinken, Spannen, Blocken", true));

    public NoSlow() {
        super("NoSlow", "Kein Abbremsen beim Benutzen von Gegenstaenden", Category.MOVEMENT);
        instanz = this;
    }

    /** Vom LocalPlayerMixin abgefragt. */
    public static boolean beimBenutzen() {
        return instanz != null && instanz.isEnabled() && instanz.items.get();
    }
}
