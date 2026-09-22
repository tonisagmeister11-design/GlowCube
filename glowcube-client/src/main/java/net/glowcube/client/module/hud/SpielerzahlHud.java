package net.glowcube.client.module.hud;

import net.glowcube.client.hud.TextHudModul;

/** Wie viele Spieler online sind - aus AxolotlClient ({@code PlayerCountHud}). */
public final class SpielerzahlHud extends TextHudModul {
    public SpielerzahlHud() {
        super("Spielerzahl", "Zeigt, wie viele Spieler online sind");
    }

    @Override
    protected String text() {
        if (mc.player == null || mc.player.connection == null) {
            return "0 Spieler";
        }
        int anzahl = mc.player.connection.getOnlinePlayers().size();
        return anzahl == 1 ? "1 Spieler" : anzahl + " Spieler";
    }
}
