package net.glowcube.client.module.agent;

import net.glowcube.client.agent.AgentSteuerung;
import net.glowcube.client.agent.Auftrag;
import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;

/**
 * Gemeinsame Basis der drei Agenten: Einschalten schickt einen Agenten los,
 * Ausschalten ruft ihn zurueck - er kommt zu dir und wirft dir alles zu.
 * Die eigentliche Arbeit laeuft auf dem Server der eigenen Welt
 * ({@link AgentSteuerung}).
 */
public abstract class AgentModul extends Module {
    private final Auftrag auftrag;

    protected AgentModul(Auftrag auftrag, String beschreibung) {
        super(auftrag.anzeigename(), beschreibung, Category.AGENT);
        this.auftrag = auftrag;
    }

    public Auftrag auftrag() {
        return auftrag;
    }

    /** Welche Erzart - nur der Erz-Agent hat eine Auswahl. */
    protected String art() {
        return "";
    }

    @Override
    public void onEnable() {
        if (!AgentSteuerung.starten(auftrag, art())) {
            setEnabledSilently(false);
        }
    }

    @Override
    public void onDisable() {
        AgentSteuerung.zurueck(auftrag);
    }

    @Override
    public boolean bleibtNachWeltwechsel() {
        return false;
    }
}
