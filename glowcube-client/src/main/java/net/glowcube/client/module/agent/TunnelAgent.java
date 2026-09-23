package net.glowcube.client.module.agent;

import net.glowcube.client.agent.Auftrag;
import net.glowcube.client.core.setting.ModeSetting;
import net.glowcube.client.core.setting.NumberSetting;

/**
 * Tunnel-Agent: graebt einen geraden Tunnel in deine Blickrichtung - 1x2,
 * 2x2 oder 3x3, so lang wie eingestellt. Er setzt alle acht Bloecke eine
 * Fackel, dichtet Lava und Wasser mit Bruchstein ab und nimmt Erze aus den
 * Waenden mit. Ist der Tunnel fertig, kommt er mit der Beute zurueck.
 */
public final class TunnelAgent extends AgentModul {
    public TunnelAgent() {
        super(Auftrag.TUNNEL, "Graebt einen Tunnel in deine Blickrichtung, mit Fackeln, und sammelt die Erze");
    }

    @Override
    protected ModeSetting artEinstellung(String vor) {
        return new ModeSetting(vor + "Groesse", "Breite x Hoehe des Tunnels", "3x3", "1x2", "2x2", "3x3");
    }

    @Override
    protected NumberSetting zahlEinstellung(String vor) {
        return new NumberSetting(vor + "Laenge", "Wie viele Bloecke weit dieser Agent graebt", 64, 8, 512, 8);
    }
}
