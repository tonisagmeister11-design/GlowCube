package net.glowcube.client.module.agent;

import net.glowcube.client.agent.Auftrag;
import net.glowcube.client.core.setting.ModeSetting;

/** Baut Erz ab - alle Erze oder gezielt eine Sorte, etwa nur Diamanten. Jeder Agent mit eigener Sorte. */
public final class ErzAgent extends AgentModul {
    public ErzAgent() {
        super(Auftrag.ERZ, "Schickt Agenten los, die Erz abbauen (Diamanten usw. waehlbar)");
    }

    @Override
    protected ModeSetting artEinstellung(String vor) {
        return new ModeSetting(vor + "Erz", "Welches Erz dieser Agent sucht",
                "Alle", "Alle", "Diamant", "Eisen", "Gold", "Redstone", "Lapis", "Kohle", "Kupfer", "Smaragd",
                "Antiker Schrott", "Quarz");
    }
}
