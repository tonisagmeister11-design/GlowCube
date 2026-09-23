package net.glowcube.client.module.agent;

import net.glowcube.client.agent.Auftrag;

/** Baut Stein ab - Stein, Tiefenschiefer, Andesit, Diorit, Granit, Tuff. */
public final class SteinAgent extends AgentModul {
    public SteinAgent() {
        super(Auftrag.STEIN, "Schickt einen Agenten los, der Stein abbaut");
    }
}
