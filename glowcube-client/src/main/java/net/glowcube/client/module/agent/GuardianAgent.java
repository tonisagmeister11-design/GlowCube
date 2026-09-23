package net.glowcube.client.module.agent;

import net.glowcube.client.agent.Auftrag;
import net.glowcube.client.core.setting.ModeSetting;

/**
 * Guardian-Agent: ein Leibwaechter in voller Ruestung. Laeuft mit dir, greift
 * jedes Monster in deiner Naehe an und alles, was dich angreift. Hast du nur
 * noch ein Herz, laesst er den Gegner und kommt zu dir.
 */
public final class GuardianAgent extends AgentModul {
    private final ModeSetting ruestung = register(new ModeSetting("Ausruestung",
            "Ruestung und Schwert des Waechters", "Diamant", "Eisen", "Diamant", "Netherite"));

    public GuardianAgent() {
        super(Auftrag.WAECHTER, "Ein Leibwaechter, der alles Feindliche um dich herum bekaempft");
    }

    @Override
    protected String art() {
        return ruestung.get();
    }
}
