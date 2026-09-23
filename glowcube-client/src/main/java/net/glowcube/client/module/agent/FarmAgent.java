package net.glowcube.client.module.agent;

import net.glowcube.client.agent.Auftrag;
import net.glowcube.client.core.setting.NumberSetting;

/**
 * Farm-Agent: erntet reifes Getreide, Karotten, Kartoffeln, Rote Bete und
 * Netherwarzen im Umkreis seines Startpunkts, pflanzt sofort neu und
 * sammelt die Ernte. Er bleibt bei seinem Feld und arbeitet, bis du ihn
 * zurueckrufst - mit Sammelkiste auch endlos.
 */
public final class FarmAgent extends AgentModul {
    public FarmAgent() {
        super(Auftrag.BAUER, "Erntet reife Felder, pflanzt neu und bringt dir die Ernte");
    }

    @Override
    protected NumberSetting zahlEinstellung(String vor) {
        return new NumberSetting(vor + "Radius", "Wie weit um den Startpunkt dieser Agent erntet", 16, 4, 48, 1);
    }
}
