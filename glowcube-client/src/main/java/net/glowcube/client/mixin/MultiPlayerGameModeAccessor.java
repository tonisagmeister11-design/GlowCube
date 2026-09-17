package net.glowcube.client.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.multiplayer.prediction.PredictiveAction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Zwei geschuetzte Wege in den Interaktionsmanager.
 *
 * <p>{@code ensureHasSentCarriedItem} meldet dem Server sofort, was man
 * jetzt in der Hand haelt. Das Spiel macht das von selbst, aber erst im
 * naechsten Tick - wer im selben Tick tauschen, bauen und zuruecktauschen
 * will (Scaffold, Nuker, AutoTool), kaeme zu spaet.
 *
 * <p>{@code startPrediction} schickt eine Aktion mit fortlaufender Nummer.
 * Nuker braucht das fuer den Paketabbau; ohne die Nummer weist der Server
 * die Aktion zurueck.
 */
@Mixin(MultiPlayerGameMode.class)
public interface MultiPlayerGameModeAccessor {
    @Invoker("ensureHasSentCarriedItem")
    void glowcube$auswahlMelden();

    @Invoker("startPrediction")
    void glowcube$vorhersagen(ClientLevel welt, PredictiveAction aktion);
}
