package net.glowcube.client.module.agent;

import net.glowcube.client.agent.Auftrag;

/** Faellt Baeume - alle Stammsorten. */
public final class HolzAgent extends AgentModul {
    public HolzAgent() {
        super(Auftrag.HOLZ, "Schickt einen Agenten los, der Holz faellt");
    }
}
