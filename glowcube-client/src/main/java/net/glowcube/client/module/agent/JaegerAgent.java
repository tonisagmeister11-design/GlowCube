package net.glowcube.client.module.agent;

import net.glowcube.client.agent.Auftrag;
import net.glowcube.client.core.setting.ModeSetting;
import net.glowcube.client.core.setting.NumberSetting;

/**
 * Jaeger-Agent: jagt Tiere in deiner Naehe - alle oder nur eine Sorte - und
 * sammelt Fleisch, Leder, Wolle und Federn ein. Er laesst von jeder Sorte
 * immer ein paar Tiere uebrig (nie Jungtiere), damit die Herde nachwaechst.
 * Beim Zurueckrufen wirft er dir die Beute zu.
 */
public final class JaegerAgent extends AgentModul {
    public JaegerAgent() {
        super(Auftrag.JAEGER, "Jagt Tiere in deiner Naehe und bringt dir Fleisch, Leder und Wolle");
    }

    @Override
    protected ModeSetting artEinstellung(String vor) {
        return new ModeSetting(vor + "Tiere", "Welche Tiere dieser Agent jagt",
                "Alle", "Alle", "Kuh", "Schwein", "Schaf", "Huhn", "Kaninchen");
    }

    @Override
    protected NumberSetting zahlEinstellung(String vor) {
        return new NumberSetting(vor + "Uebrig lassen", "So viele Tiere jeder Sorte laesst er am Leben", 2, 0, 10, 1);
    }
}
