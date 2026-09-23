package net.glowcube.client.module.agent;

import net.glowcube.client.agent.Auftrag;
import net.glowcube.client.bauplan.Bauplaene;
import net.glowcube.client.core.setting.ModeSetting;

/**
 * Builder-Agent: baut ein Schematic genau auf deiner Hoehe direkt vor dir -
 * alle Bloecke hat er dabei, was im Weg ist, raeumt er ab, und wenn er fertig
 * ist, meldet er sich im Chat. Mehrere Builder bauen nebeneinander, jeder sein
 * eigenes Schematic. Eigene Schematics (.schem, .litematic, .nbt) kommen nach
 * {@code .minecraft/config/glowcube/schematics/} und stehen nach einem
 * Neustart in der Auswahl.
 */
public final class BuilderAgent extends AgentModul {
    public BuilderAgent() {
        super(Auftrag.BAUMEISTER, "Baut Schematics vor dir auf deiner Hoehe - mehrere nebeneinander");
    }

    @Override
    protected ModeSetting artEinstellung(String vor) {
        java.util.List<String> namen = Bauplaene.namen();
        return new ModeSetting(vor + "Schematic", "Was dieser Builder baut", namen.get(0),
                namen.toArray(new String[0]));
    }
}
