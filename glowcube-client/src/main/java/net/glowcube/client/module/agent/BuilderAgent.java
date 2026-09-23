package net.glowcube.client.module.agent;

import net.glowcube.client.agent.Auftrag;
import net.glowcube.client.bauplan.Bauplaene;
import net.glowcube.client.core.setting.ModeSetting;

/**
 * Builder-Agent: baut ein Schematic genau auf deiner Hoehe direkt vor dir -
 * alle Bloecke hat er dabei, was im Weg ist, raeumt er ab, und wenn er fertig
 * ist, meldet er sich im Chat. Eigene Schematics (.schem, .litematic, .nbt)
 * kommen nach {@code .minecraft/config/glowcube/schematics/} und stehen nach
 * einem Neustart in der Auswahl.
 */
public final class BuilderAgent extends AgentModul {
    private final ModeSetting plan;

    public BuilderAgent() {
        super(Auftrag.BAUMEISTER, "Baut ein Schematic vor dir auf deiner Hoehe");
        java.util.List<String> namen = Bauplaene.namen();
        plan = register(new ModeSetting("Schematic", "Was gebaut wird", namen.get(0),
                namen.toArray(new String[0])));
    }

    @Override
    protected String art() {
        return plan.get();
    }
}
