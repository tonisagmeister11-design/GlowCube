package net.glowcube.client.module.agent;

import net.glowcube.client.agent.AgentSteuerung;
import net.glowcube.client.agent.AgentWerte;
import net.glowcube.client.agent.Auftrag;
import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.BooleanSetting;
import net.glowcube.client.core.setting.NumberSetting;

/**
 * Gemeinsame Basis der drei Agenten: Einschalten schickt einen Agenten los,
 * Ausschalten ruft ihn zurueck - er kommt zu dir und wirft dir alles zu.
 * Die eigentliche Arbeit laeuft auf dem Server ({@link AgentSteuerung}):
 * in der eigenen Welt direkt, auf einem Server ueber das GlowCube-Plugin.
 *
 * <p>Tempo, Abbau-Tempo und X-Ray lassen sich auch waehrend der Arbeit
 * aendern - der laufende Agent uebernimmt sie sofort.
 */
public abstract class AgentModul extends Module {
    private final Auftrag auftrag;

    private final NumberSetting tempo = register(new NumberSetting("Tempo",
            "Wie schnell der Agent laeuft (1 = wie ein Spieler)", 2, 1, 4, 0.5));
    private final NumberSetting abbauTempo = register(new NumberSetting("Abbau-Tempo",
            "Wie viel schneller er abbaut als mit Diamantwerkzeug", 3, 1, 20, 1));
    private final BooleanSetting xray = register(new BooleanSetting("X-Ray",
            "Sieht Zielbloecke durch Stein und geht direkt zu ihnen", true));
    private final NumberSetting xrayWeite = register(new NumberSetting("X-Ray-Weite",
            "Wie weit X-Ray reicht, in Chunks um den Agenten", 3, 1, 6, 1));

    private AgentWerte gesendet;

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

    private AgentWerte werte() {
        return new AgentWerte(tempo.get(), abbauTempo.get(), xray.get(), xrayWeite.getInt());
    }

    @Override
    public void onEnable() {
        gesendet = werte();
        if (!AgentSteuerung.starten(auftrag, art(), gesendet)) {
            setEnabledSilently(false);
        }
    }

    @Override
    public void onDisable() {
        AgentSteuerung.zurueck(auftrag);
    }

    /** Geaenderte Einstellungen an den laufenden Agenten weiterreichen. */
    @Override
    public void onTick() {
        AgentWerte jetzt = werte();
        if (!jetzt.equals(gesendet)) {
            gesendet = jetzt;
            AgentSteuerung.einstellen(auftrag, jetzt);
        }
    }

    @Override
    public boolean bleibtNachWeltwechsel() {
        return false;
    }
}
