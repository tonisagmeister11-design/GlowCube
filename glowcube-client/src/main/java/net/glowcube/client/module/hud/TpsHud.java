package net.glowcube.client.module.hud;

import net.glowcube.client.hud.TextHudModul;
import net.minecraft.network.protocol.Packet;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Ticks pro Sekunde des Servers - aus AxolotlClient ({@code TPSHud}). Der
 * Server schickt jede Sekunde Spielzeit (20 Ticks) die Uhrzeit; wie viel
 * echte Zeit dazwischen vergeht, verraet die Tickrate.
 */
public final class TpsHud extends TextHudModul {
    private static final DecimalFormat FORMAT = new DecimalFormat("0.0", DecimalFormatSymbols.getInstance(Locale.ROOT));

    private volatile long letztesPaket;
    private volatile double tps = 20;

    public TpsHud() {
        super("TPS", "Zeigt die Tickrate des Servers");
    }

    @Override
    public void onEnable() {
        letztesPaket = 0;
        tps = 20;
    }

    @Override
    public boolean onPacketReceive(Packet<?> paket) {
        if (paket.getClass().getSimpleName().contains("SetTime")) {
            long jetzt = System.nanoTime();
            if (letztesPaket != 0) {
                double sekunden = (jetzt - letztesPaket) / 1_000_000_000.0;
                if (sekunden > 0) {
                    tps = Math.max(0, Math.min(20, 20 / sekunden));
                }
            }
            letztesPaket = jetzt;
        }
        return false;
    }

    @Override
    protected String text() {
        return FORMAT.format(tps) + " TPS";
    }
}
