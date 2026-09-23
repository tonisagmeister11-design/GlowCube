package net.glowcube.client.module.agent;

import net.glowcube.client.agent.Auftrag;
import net.glowcube.client.core.setting.ModeSetting;

/** Baut Erz ab - alle Erze oder gezielt eine Sorte, etwa nur Diamanten. */
public final class ErzAgent extends AgentModul {
    private final ModeSetting erz = register(new ModeSetting("Erz", "Welches Erz der Agent sucht",
            "Alle", "Alle", "Diamant", "Eisen", "Gold", "Redstone", "Lapis", "Kohle", "Kupfer", "Smaragd",
            "Antiker Schrott", "Quarz"));

    public ErzAgent() {
        super(Auftrag.ERZ, "Schickt einen Agenten los, der Erz abbaut (Diamanten usw. waehlbar)");
    }

    @Override
    protected String art() {
        return erz.get();
    }
}
