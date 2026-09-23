package net.glowcube.client.module.agent;

import net.glowcube.client.agent.Auftrag;
import net.glowcube.client.core.setting.ModeSetting;

/**
 * Guardian-Agent: ein Leibwaechter in voller Ruestung. Laeuft mit dir, greift
 * jedes Monster in deiner Naehe an und alles, was dich angreift. Hast du nur
 * noch ein Herz, laesst er den Gegner und kommt zu dir.
 */
public final class GuardianAgent extends AgentModul {
    public GuardianAgent() {
        super(Auftrag.WAECHTER, "Leibwaechter, die alles Feindliche um dich herum bekaempfen");
    }

    @Override
    protected ModeSetting artEinstellung(String vor) {
        return new ModeSetting(vor + "Ausruestung", "Ruestung und Schwert dieses Waechters",
                "Diamant", "Eisen", "Diamant", "Netherite");
    }
}
