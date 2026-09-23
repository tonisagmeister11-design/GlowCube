package net.glowcube.client.module.agent;

import net.glowcube.client.GlowCubeClient;
import net.glowcube.client.agent.AgentSteuerung;
import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;

/**
 * Ein Knopf: ruft alle Agenten zurueck. Ist einer nah genug, laeuft oder
 * graebt er sich zu dir; ist er weit weg, teleportiert er sich. Dann wirft er
 * dir alles zu, was er gesammelt hat.
 */
public final class AgentZurueck extends Module {
    public AgentZurueck() {
        super("Agent zurueckschicken", "Ruft alle Agenten zurueck - sie bringen dir ihre Beute", Category.AGENT);
    }

    @Override
    public void onEnable() {
        for (Module modul : GlowCubeClient.modules().all()) {
            if (modul instanceof AgentModul agent && agent.isEnabled()) {
                // Ruft ueber onDisable zurueck.
                agent.setEnabled(false);
            }
        }
        AgentSteuerung.alleZurueck();
        setEnabledSilently(false);
    }

    @Override
    public boolean bleibtNachWeltwechsel() {
        return false;
    }
}
